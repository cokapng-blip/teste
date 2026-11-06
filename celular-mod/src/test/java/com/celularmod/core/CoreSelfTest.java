package com.celularmod.core;

import com.celularmod.browser.LocalSiteServer;
import com.celularmod.browser.SiteCache;
import com.celularmod.core.Models.Account;
import com.celularmod.core.Models.Msg;
import com.celularmod.core.Models.Post;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Testes sem dependências (sem JUnit): rode com tools/run-core-tests.sh */
public class CoreSelfTest {
    static int checks = 0;

    static void check(boolean c, String what) {
        checks++;
        if (!c) throw new AssertionError("FALHOU: " + what);
    }

    static final long[] NOW = {1_000_000L};

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");   // permite forçar o cabeçalho Host
        Path tmp = Files.createTempDirectory("celular-test");
        testJson();
        testWhatsApp(tmp.resolve("a"));
        testThreads(tmp.resolve("b"));
        testPersistence(tmp.resolve("c"));
        testSites(tmp.resolve("d"));
        System.out.println("OK — " + checks + " verificações passaram");
    }

    static void testJson() {
        Map<String, Object> m = Json.obj("a", "x\"y\n", "n", 5L, "l", List.of(1L, 2L), "b", true, "z", null);
        Map<String, Object> back = Json.asMap(Json.parse(Json.stringify(m)));
        check("x\"y\n".equals(back.get("a")), "json string escape");
        check(Json.num(back, "n", 0) == 5, "json number");
        check(Json.asList(back.get("l")).size() == 2, "json list");
        boolean threw = false;
        try { Json.parse("{\"a\":"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "json inválido lança erro");
    }

    static Database db(Path dir) throws Exception {
        Database d = new Database(dir, () -> NOW[0]);
        d.load();
        return d;
    }

    static void testWhatsApp(Path dir) throws Exception {
        Database d = db(dir);
        WhatsAppService wa = new WhatsAppService(d);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        check(!wa.send(a, "x", "oi").ok, "enviar sem conta falha");
        Account A = wa.createAccount(a, "  Alice  ").value;
        Account B = wa.createAccount(b, "Bob").value;
        check(A.name.equals("Alice"), "nome é normalizado");
        check(!wa.createAccount(a, "Outra").ok, "uma conta por jogador");
        check(!wa.createAccount(c, "x").ok, "nome curto rejeitado");
        check(!wa.createAccount(c, "A§b§c" + "x".repeat(30)).ok, "nome longo rejeitado");
        check(!wa.addContact(a, "9999-9999").ok, "não adiciona conta inexistente");
        check(!wa.addContact(a, A.handle).ok, "não adiciona a si mesmo");
        check(wa.search(a, "bo").value.size() == 1, "busca por nome");
        check(wa.search(a, B.handle).value.size() == 1, "busca por número");
        check(wa.addContact(a, B.handle).ok, "adiciona contato");
        check(!wa.addContact(a, B.handle).ok, "contato duplicado rejeitado");
        Res<Msg> m = wa.send(a, B.handle, "Olá, Bob!");
        check(m.ok, "envio funciona");
        check(!wa.send(a, B.handle, "   ").ok, "mensagem vazia rejeitada");
        check(!wa.send(a, B.handle, "x".repeat(501)).ok, "mensagem longa rejeitada");
        check(wa.totalUnread(b) == 1, "não lida para o destinatário");
        check(wa.totalUnread(a) == 0, "remetente não tem não-lidas");
        List<Map<String, Object>> convB = wa.conversations(b);
        check(convB.size() == 1 && Json.num(convB.get(0), "unread", 0) == 1, "conversa aparece p/ destinatário com unread=1");
        check(wa.history(b, A.handle).value.size() == 1, "histórico");
        wa.markRead(b, A.handle);
        check(wa.totalUnread(b) == 0, "marcar como lida");
        // privacidade
        wa.setPrivacy(b, false, true);
        UUID e = UUID.randomUUID();
        Account E = wa.createAccount(e, "Eva").value;
        check(!wa.send(e, B.handle, "oi").ok, "estranho bloqueado pela privacidade");
        wa.addContact(b, E.handle);
        check(wa.send(e, B.handle, "oi").ok, "contato liberado pela privacidade");
        // nenhum jogador lê conversa alheia
        check(wa.history(c, A.handle).ok == false, "sem conta não lê histórico");
        check(wa.history(e, A.handle).value.isEmpty(), "terceiro vê só a própria conversa (vazia)");
        check(wa.removeContact(a, B.handle).ok && !wa.removeContact(a, B.handle).ok, "remover contato");
        // renomear mantém número
        String num = A.handle;
        wa.rename(a, "Alice 2");
        check(wa.account(a).handle.equals(num) && wa.account(a).name.equals("Alice 2"), "renomear mantém identificação única");
        // rate limiter
        RateLimiter rl = new RateLimiter(3, 1000, () -> NOW[0]);
        check(rl.allow(a, "x") && rl.allow(a, "x") && rl.allow(a, "x") && !rl.allow(a, "x"), "rate limiter bloqueia o 4º");
        NOW[0] += 1500;
        check(rl.allow(a, "x"), "rate limiter libera depois da janela");
    }

    static void testThreads(Path dir) throws Exception {
        Database d = db(dir);
        WhatsAppService wa = new WhatsAppService(d);
        ThreadsService th = new ThreadsService(d, wa);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        check(!th.post(a, "oi").ok, "post sem conta falha");
        th.createAccount(a, "Ana");
        th.createAccount(b, "Beto");
        check(th.account(a).handle.startsWith("ana"), "usuário gerado");
        Post p = th.post(a, "Meu primeiro post").value;
        check(!th.post(a, "x".repeat(281)).ok, "post longo rejeitado");
        check(th.like(b, p.id, true).value.likes.size() == 1, "curtir");
        check(th.like(b, p.id, true).value.likes.size() == 1, "curtida duplicada não conta");
        check(th.like(b, p.id, false).value.likes.isEmpty(), "descurtir");
        th.like(b, p.id, true);
        check(th.feed(b, "foryou", null).value.size() == 1, "feed For You");
        Map<String, Object> v = th.view(b, p);
        check(Json.bool(v, "liked", false) && Json.num(v, "likes", 0) == 1, "visão do post para quem curtiu");
        check(th.feed(b, "profile", th.account(a).handle).value.size() == 1, "feed do perfil");
        check(!th.delete(b, p.id, false).ok, "não apaga post alheio");
        // compartilhar via WhatsApp
        wa.createAccount(a, "Ana"); wa.createAccount(b, "Beto");
        Res<Msg> sh = th.share(b, p.id, wa.account(a).handle);
        check(sh.ok && sh.value.text.contains("Meu primeiro post") && sh.value.text.contains("Ana"), "compartilhamento inclui texto e autor");
        check(sh.value.kind.equals("share"), "tipo share");
        check(wa.totalUnread(a) == 1, "destinatário do share recebe");
        check(th.delete(b, p.id, true).ok, "admin remove post");
        check(th.feed(a, "foryou", null).value.isEmpty(), "post removido some do feed");
        check(!th.share(b, p.id, wa.account(a).handle).ok, "post removido não pode ser compartilhado");
        // renomear com cooldown
        check(!th.rename(a, "Ana Nova").ok, "cooldown de renomeação");
        NOW[0] += ThreadsService.RENAME_COOLDOWN_MS + 1;
        check(th.rename(a, "Ana Nova").ok, "renomeia após cooldown");
    }

    static void testPersistence(Path dir) throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Database d = db(dir);
        WhatsAppService wa = new WhatsAppService(d);
        ThreadsService th = new ThreadsService(d, wa);
        wa.createAccount(a, "Ana"); wa.createAccount(b, "Beto");
        wa.addContact(a, wa.account(b).handle);
        wa.send(a, wa.account(b).handle, "persistir isto");
        th.createAccount(a, "Ana"); long pid = th.post(a, "post salvo").value.id;
        th.like(b, pid, false); th.createAccount(b, "Beto"); th.like(b, pid, true);
        d.flushIfDirty();
        Database d2 = db(dir);
        WhatsAppService wa2 = new WhatsAppService(d2);
        ThreadsService th2 = new ThreadsService(d2, wa2);
        check(wa2.account(a).name.equals("Ana"), "conta persistida");
        check(wa2.history(b, wa2.account(a).handle).value.get(0).text.equals("persistir isto"), "mensagem persistida");
        check(wa2.contacts(a).contains(b), "contatos persistidos");
        check(th2.find(pid).likes.contains(b), "curtida persistida");
        long nid = th2.post(a, "novo").value.id;
        check(nid > pid, "ids continuam crescendo após reiniciar");
    }

    static void testSites(Path dir) throws Exception {
        Files.createDirectories(dir);
        ServerSettings st = ServerSettings.load(dir);
        SiteService ss = new SiteService(dir, st, () -> NOW[0]);
        UUID u = UUID.randomUUID();
        check(!ss.request(u, "Joao", "Bad_ID", "x").ok, "id inválido");
        check(!ss.request(u, "Joao", "celular-host", "Titulo").ok, "id reservado");
        check(ss.request(u, "Joao", "loja-joao", "Loja do João").ok, "solicitação criada");
        check(!ss.request(u, "Joao", "loja-joao", "Loja").ok, "solicitação duplicada");
        check(!ss.review("loja-joao").ok, "sem envio ainda");
        // envio malicioso
        Path sub = ss.submissionsDir().resolve("loja-joao");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("index.html"), "<html><head><title>Loja</title></head><body><a href=\"celular://outro/\">x</a><script src=\"app.js\"></script></body></html>");
        Files.writeString(sub.resolve("app.js"), "document.title='ok';fetch('https://evil.example.com/x')");
        Files.writeString(sub.resolve("virus.exe"), "MZ");
        Res<SiteService.Review> rv = ss.review("loja-joao");
        check(rv.ok && !rv.value.ok(), "extensão .exe reprovada");
        check(!ss.approve("loja-joao", "Admin").ok, "não aprova site reprovado");
        check(rv.value.warnings.stream().anyMatch(w -> w.contains("externo")), "aviso de URL externa");
        Files.delete(sub.resolve("virus.exe"));
        check(ss.review("loja-joao").value.ok(), "limpo passa");
        Res<SiteService.Site> ap = ss.approve("loja-joao", "Admin");
        check(ap.ok && ap.value.files == 2, "aprovado");
        check(ss.list().size() == 1 && ss.pending().isEmpty(), "lista e pendências");
        check(ss.readFile("loja-joao", "../../x") == null, "path traversal bloqueado no servidor");
        // zip com zip-slip
        Path zip = ss.submissionsDir().resolve("evil.zip");
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            z.putNextEntry(new java.util.zip.ZipEntry("index.html")); z.write("<p>hi</p>".getBytes()); z.closeEntry();
            z.putNextEntry(new java.util.zip.ZipEntry("../escape.html")); z.write("x".getBytes()); z.closeEntry();
        }
        check(!ss.review("evil").value.ok(), "zip-slip reprovado");
        // zip válido
        Path zip2 = ss.submissionsDir().resolve("zipado.zip");
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip2))) {
            z.putNextEntry(new java.util.zip.ZipEntry("index.html")); z.write("<title>Z</title><p>zip</p>".getBytes()); z.closeEntry();
            z.putNextEntry(new java.util.zip.ZipEntry("css/a.css")); z.write("p{color:red}".getBytes()); z.closeEntry();
        }
        check(ss.approve("zipado", "Admin").ok, "zip aprovado");
        // transferência para o cache do cliente (como pelo protocolo de rede)
        Path cdir = dir.resolve("client-cache");
        SiteCache cache = new SiteCache(cdir);
        for (SiteService.Site s : ss.list()) {
            List<SiteCache.Entry> es = new ArrayList<>();
            for (SiteService.FileInfo f : ss.manifest(s.id)) es.add(new SiteCache.Entry(f.path, f.size, f.sha256));
            check(cache.begin(s.id, s.title, s.description, s.hash, es) == null, "begin " + s.id);
            String res = null;
            for (SiteCache.Entry e : es) {
                byte[] all = ss.readFile(s.id, e.path);
                for (int off = 0; off < all.length; off += 7) {   // chunks minúsculos de propósito
                    res = cache.chunk(s.id, e.path, off, Arrays.copyOfRange(all, off, Math.min(all.length, off + 7)));
                }
            }
            check("done".equals(res), "download concluído " + s.id + " (" + res + ")");
            check(cache.isInstalled(s.id, s.hash), "instalado com hash correto");
        }
        // download adulterado é recusado
        SiteService.FileInfo fi = ss.manifest("zipado").get(0);
        List<SiteCache.Entry> es = List.of(new SiteCache.Entry("index.html", 4, "0".repeat(64)));
        check(cache.begin("falso", "F", "", "h", es) == null, "begin falso");
        check(cache.chunk("falso", "index.html", 0, "abcd".getBytes()).startsWith("erro"), "hash divergente rejeitado");
        check(cache.begin("falso2", "F", "", "h", List.of(new SiteCache.Entry("../x.html", 1, "0".repeat(64)))) != null, "manifesto com .. rejeitado");

        // servidor HTTP local
        try (LocalSiteServer srv = new LocalSiteServer(cache, false)) {
            String[] last = new String[1];
            srv.setNavigationListener((id, p) -> last[0] = id + p);
            int port = srv.port();
            check(get(port, "loja-joao.localhost", "/", 0).contains("Loja"), "serve index");
            HttpURLConnection c = open(port, "loja-joao.localhost", "/app.js");
            check(c.getResponseCode() == 200 && c.getContentType().startsWith("text/javascript"), "content-type js");
            check(c.getHeaderField("Content-Security-Policy").contains("connect-src 'self'"), "CSP presente");
            check(open(port, "loja-joao.localhost", "/%2e%2e/%2e%2e/etc/passwd").getResponseCode() == 404, "traversal 404");
            check(open(port, "loja-joao.localhost", "/.celular-site.json").getResponseCode() == 404, "meta oculto");
            check(open(port, "nao-existe.localhost", "/").getResponseCode() == 404, "site inexistente 404");
            check(open(port, "evil.com", "/").getResponseCode() == 421, "Host inválido recusado");
            HttpURLConnection fr = open(port, "celular-host.localhost", "/?u=loja-joao/");
            String html = new String(fr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            check(html.contains("sandbox=\"allow-scripts allow-forms allow-same-origin\"") && !html.contains("allow-top-navigation"), "moldura sandbox");
            check(fr.getHeaderField("Content-Security-Policy").contains("frame-src http://*.localhost:" + port), "frame-src restrito");
            HttpURLConnection post = open(port, "loja-joao.localhost", "/");
            post.setRequestMethod("POST");
            check(post.getResponseCode() == 405, "POST recusado");
        }
        check(ss.remove("loja-joao").ok && ss.get("loja-joao") == null, "remoção");
        cache.retainOnly(Set.of("zipado"));
        check(!cache.has("loja-joao") && cache.has("zipado"), "cache descarta site removido");
    }

    static HttpURLConnection open(int port, String host, String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + path).toURL().openConnection();
        c.setRequestProperty("Host", host + ":" + port);
        return c;
    }

    static String get(int port, String host, String path, int unused) throws Exception {
        try (InputStream in = open(port, host, path).getInputStream()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }
}
