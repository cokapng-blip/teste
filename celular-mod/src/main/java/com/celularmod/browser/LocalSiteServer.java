package com.celularmod.browser;

import com.celularmod.core.Text;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servidor HTTP mínimo, só em 127.0.0.1, que entrega ao Chromium os sites aprovados já baixados no cache.
 *
 * Modelo de segurança (cada camada é independente):
 *  1. Cada site tem sua própria ORIGEM: http://&lt;id&gt;.localhost:PORTA (Chromium resolve *.localhost para loopback).
 *     Sites não leem cookies/localStorage uns dos outros.
 *  2. A página-moldura (celular-host.localhost) embute o site num iframe com sandbox sem allow-top-navigation,
 *     allow-popups, allow-downloads ou allow-modals, e com CSP frame-src restrito a *.localhost:PORTA —
 *     o iframe NÃO consegue navegar para a internet.
 *  3. Cada resposta de site traz CSP (default-src 'self'…) bloqueando requisições a servidores externos,
 *     a menos que o administrador ligue allowExternalResources.
 *  4. Só GET/HEAD, validação do cabeçalho Host (anti DNS-rebinding), caminhos sem "..", sem arquivos ocultos.
 */
public final class LocalSiteServer implements Closeable {
    public static final String HOST_LABEL = "celular-host";
    private static final Pattern HOST = Pattern.compile("^([a-z0-9-]+)\\.localhost:(\\d+)$");

    private final SiteCache cache;
    private final ServerSocket socket;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "celular-site-server");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean allowExternal;
    private volatile BiConsumer<String, String> navListener = (id, path) -> {};

    public LocalSiteServer(SiteCache cache, boolean allowExternal) throws IOException {
        this.cache = cache;
        this.allowExternal = allowExternal;
        this.socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "celular-site-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public int port() { return socket.getLocalPort(); }

    public void setAllowExternal(boolean b) { this.allowExternal = b; }

    /** Chamado a cada documento HTML carregado no iframe (id do site, caminho com query). */
    public void setNavigationListener(BiConsumer<String, String> l) { this.navListener = l; }

    /** URL da moldura que mostra {@code siteId + path}. Esta é a URL que o Chromium abre. */
    public String frameUrl(String siteId, String path) {
        String p = path == null || path.isEmpty() ? "/" : (path.startsWith("/") ? path : "/" + path);
        return "http://" + HOST_LABEL + ".localhost:" + port() + "/?u=" + enc(siteId + p);
    }

    public String siteUrl(String siteId, String path) {
        return "http://" + siteId + ".localhost:" + port() + (path.startsWith("/") ? path : "/" + path);
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    @Override public void close() {
        try { socket.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try {
                Socket s = socket.accept();
                pool.execute(() -> handle(s));
            } catch (IOException e) {
                if (socket.isClosed()) return;
            }
        }
    }

    // ------------------------------------------------------------------
    private void handle(Socket s) {
        try (s) {
            s.setSoTimeout(10_000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            String reqLine = readLine(in);
            if (reqLine == null) return;
            Map<String, String> h = new HashMap<>();
            String line;
            int count = 0;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                if (++count > 60) { respond(out, 431, "text/plain", "Cabeçalhos demais".getBytes(StandardCharsets.UTF_8), null, false); return; }
                int c = line.indexOf(':');
                if (c > 0) h.put(line.substring(0, c).trim().toLowerCase(Locale.ROOT), line.substring(c + 1).trim());
            }
            String[] parts = reqLine.split(" ");
            if (parts.length < 2) { respond(out, 400, "text/plain", "Requisição inválida".getBytes(StandardCharsets.UTF_8), null, false); return; }
            String method = parts[0];
            boolean head = method.equals("HEAD");
            if (!method.equals("GET") && !head) { respond(out, 405, "text/plain", "Método não permitido".getBytes(StandardCharsets.UTF_8), null, false); return; }

            Matcher hm = HOST.matcher(h.getOrDefault("host", "").toLowerCase(Locale.ROOT));
            if (!hm.matches() || Integer.parseInt(hm.group(2)) != port()) {
                respond(out, 421, "text/plain", "Host inválido".getBytes(StandardCharsets.UTF_8), null, head);
                return;
            }
            String label = hm.group(1);
            String target = parts[1];
            String rawPath = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) { rawPath = target.substring(0, q); query = target.substring(q + 1); }
            String path;
            try { path = URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8); }
            catch (IllegalArgumentException e) { respond(out, 400, "text/plain", "URL inválida".getBytes(StandardCharsets.UTF_8), null, head); return; }

            if (label.equals(HOST_LABEL)) { serveFrame(out, query, head); return; }
            serveSite(out, label, path, query, h, head);
        } catch (IOException ignored) {
            // cliente fechou a conexão
        } catch (RuntimeException e) {
            // nunca derrube a thread por causa de uma requisição ruim
        }
    }

    private void serveFrame(OutputStream out, String query, boolean head) throws IOException {
        String u = null;
        for (String kv : query.split("&")) {
            if (kv.startsWith("u=")) u = URLDecoder.decode(kv.substring(2), StandardCharsets.UTF_8);
        }
        String id = null, path = "/";
        if (u != null) {
            int sl = u.indexOf('/');
            id = sl < 0 ? u : u.substring(0, sl);
            path = sl < 0 ? "/" : u.substring(sl);
        }
        if (id == null || !Text.validSiteId(id) || !cache.has(id) || path.contains("\\") || path.indexOf('\0') >= 0) {
            respond(out, 404, "text/html; charset=utf-8", errorPage("Site indisponível",
                    "Este site não está disponível ou ainda está sendo baixado.").getBytes(StandardCharsets.UTF_8), null, head);
            return;
        }
        String src = siteUrl(id, path.replace("\"", "%22").replace("<", "%3C").replace(">", "%3E"));
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<style>html,body{margin:0;height:100%;background:#fff;overflow:hidden}iframe{border:0;width:100%;height:100%;display:block}</style></head>"
                + "<body><iframe id=\"f\" src=\"" + src + "\" sandbox=\"allow-scripts allow-forms allow-same-origin\"></iframe></body></html>";
        String csp = "default-src 'none'; frame-src http://*.localhost:" + port() + "; style-src 'unsafe-inline'; base-uri 'none'";
        respond(out, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8), csp, head);
    }

    private void serveSite(OutputStream out, String id, String path, String query, Map<String, String> h, boolean head) throws IOException {
        if (!Text.validSiteId(id) || !cache.has(id)) {
            respond(out, 404, "text/html; charset=utf-8", errorPage("Site não encontrado", "Não existe um site aprovado com o endereço \"" + esc(id) + "\".").getBytes(StandardCharsets.UTF_8), null, head);
            return;
        }
        String rel = path.startsWith("/") ? path.substring(1) : path;
        if (rel.isEmpty() || rel.endsWith("/")) rel += "index.html";
        byte[] data = rel.contains("\\") || rel.indexOf('\0') >= 0 ? null : cache.read(id, rel);
        if (data == null) {
            respond(out, 404, "text/html; charset=utf-8", errorPage("Página não encontrada", "O arquivo \"" + esc(path) + "\" não existe neste site.").getBytes(StandardCharsets.UTF_8), siteCsp(), head);
            return;
        }
        String type = contentType(rel);
        if (type.startsWith("text/html")) {
            String dest = h.getOrDefault("sec-fetch-dest", "");
            if (dest.equals("iframe") || dest.equals("document")) navListener.accept(id, "/" + rel + (query.isEmpty() ? "" : "?" + query));
            data = injectShim(data);
        }
        respond(out, 200, type, data, siteCsp(), head);
    }

    private String siteCsp() {
        String frameAnc = "frame-ancestors http://" + HOST_LABEL + ".localhost:" + port();
        if (allowExternal)
            return "default-src * data: blob: 'unsafe-inline' 'unsafe-eval'; " + frameAnc;
        return "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; frame-src 'self'; media-src 'self' data: blob:; "
                + "form-action 'self'; base-uri 'self'; object-src 'none'; " + frameAnc;
    }

    /** Permite links no formato celular://outro-site/pagina.html entre sites aprovados. */
    private byte[] injectShim(byte[] html) {
        String shim = "<script>document.addEventListener('click',function(e){var a=e.target&&e.target.closest&&e.target.closest('a[href^=\"celular://\"]');"
                + "if(!a)return;e.preventDefault();var m=/^celular:\\/\\/([a-z0-9-]+)(\\/.*)?$/.exec(a.getAttribute('href'));"
                + "if(m)location.href='http://'+m[1]+'.localhost:'+location.port+(m[2]||'/');},true);</script>";
        String s = new String(html, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<head[^>]*>", Pattern.CASE_INSENSITIVE).matcher(s);
        String r = m.find() ? s.substring(0, m.end()) + shim + s.substring(m.end()) : shim + s;
        return r.getBytes(StandardCharsets.UTF_8);
    }

    private static void respond(OutputStream out, int code, String type, byte[] body, String csp, boolean head) throws IOException {
        String status = switch (code) {
            case 200 -> "OK"; case 400 -> "Bad Request"; case 404 -> "Not Found"; case 405 -> "Method Not Allowed";
            case 421 -> "Misdirected Request"; default -> "Error";
        };
        StringBuilder sb = new StringBuilder("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n");
        sb.append("Content-Type: ").append(type).append("\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\nCache-Control: no-cache\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n");
        if (csp != null) sb.append("Content-Security-Policy: ").append(csp).append("\r\n");
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (!head) out.write(body);
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 16 * 1024) throw new IOException("linha muito longa");
        }
        if (c == -1 && b.size() == 0) return null;
        return b.toString(StandardCharsets.ISO_8859_1);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String errorPage(String title, String msg) {
        return "<!doctype html><meta charset=utf-8><title>" + esc(title) + "</title><body style=\"font-family:sans-serif;background:#f4f6f8;"
                + "color:#233;text-align:center;padding:12vh 16px\"><h2>" + esc(title) + "</h2><p>" + msg + "</p></body>";
    }

    static String contentType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int d = n.lastIndexOf('.');
        String ext = d < 0 ? "" : n.substring(d + 1);
        switch (ext) {
            case "html": case "htm": return "text/html; charset=utf-8";
            case "css": return "text/css; charset=utf-8";
            case "js": case "mjs": return "text/javascript; charset=utf-8";
            case "json": case "map": return "application/json; charset=utf-8";
            case "txt": return "text/plain; charset=utf-8";
            case "svg": return "image/svg+xml";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "ico": return "image/x-icon";
            case "woff": return "font/woff";
            case "woff2": return "font/woff2";
            case "ttf": return "font/ttf";
            case "otf": return "font/otf";
            default: return "application/octet-stream";
        }
    }
}
