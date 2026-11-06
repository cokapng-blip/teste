package com.celularmod.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Sites aprovados do navegador.
 * <pre>
 * celular/sites/submissions/&lt;id&gt;/  ou  &lt;id&gt;.zip   ← o administrador coloca aqui os arquivos recebidos
 * celular/sites/approved/&lt;id&gt;/…                     ← cópia validada e servida aos jogadores
 * celular/sites/requests.json                         ← solicitações pendentes
 * </pre>
 */
public final class SiteService {
    public static final Set<String> ALLOWED_EXT = new HashSet<>(Arrays.asList(
            "html", "htm", "css", "js", "mjs", "json", "txt", "svg", "png", "jpg", "jpeg", "gif", "webp", "ico",
            "woff", "woff2", "ttf", "otf", "map"));
    private static final Pattern REL_PATH = Pattern.compile("^[A-Za-z0-9_\\-][A-Za-z0-9_\\-./]*$");
    private static final Pattern EXTERNAL = Pattern.compile("(https?:)?//[A-Za-z0-9.-]+\\.[a-z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final String META = ".celular-site.json";

    public static final class FileInfo {
        public final String path; public final long size; public final String sha256;
        FileInfo(String p, long s, String h) { path = p; size = s; sha256 = h; }
    }

    public static final class Site {
        public String id, title, description, hash, approvedBy;
        public long size, approvedAt;
        public int files;

        public Map<String, Object> toMap() {
            return Json.obj("id", id, "title", title, "description", description, "hash", hash, "size", size,
                    "files", files, "approvedBy", approvedBy, "approvedAt", approvedAt);
        }

        public static Site fromMap(Map<String, Object> m) {
            Site s = new Site();
            s.id = Json.str(m, "id", ""); s.title = Json.str(m, "title", s.id);
            s.description = Json.str(m, "description", ""); s.hash = Json.str(m, "hash", "");
            s.size = Json.num(m, "size", 0); s.files = (int) Json.num(m, "files", 0);
            s.approvedBy = Json.str(m, "approvedBy", ""); s.approvedAt = Json.num(m, "approvedAt", 0);
            return s;
        }
    }

    public static final class Request {
        public String id, title, requesterName; public UUID requester; public long time;
        public Map<String, Object> toMap() {
            return Json.obj("id", id, "title", title, "requester", requester.toString(), "requesterName", requesterName, "time", time);
        }
        static Request fromMap(Map<String, Object> m) {
            Request r = new Request();
            r.id = Json.str(m, "id", ""); r.title = Json.str(m, "title", r.id);
            r.requester = UUID.fromString(Json.str(m, "requester", new UUID(0, 0).toString()));
            r.requesterName = Json.str(m, "requesterName", "?"); r.time = Json.num(m, "time", 0);
            return r;
        }
    }

    public static final class Review {
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final List<FileInfo> files = new ArrayList<>();
        public long totalSize;
        public String title = "", description = "";
        public boolean ok() { return errors.isEmpty(); }
    }

    private final Path root, approved, submissions, requestsFile;
    private final ServerSettings settings;
    private final java.util.function.LongSupplier clock;
    private final Map<String, Site> sites = new TreeMap<>();
    private final Map<String, Request> requests = new LinkedHashMap<>();

    public SiteService(Path dir, ServerSettings settings, java.util.function.LongSupplier clock) throws IOException {
        this.root = dir.resolve("sites");
        this.approved = root.resolve("approved");
        this.submissions = root.resolve("submissions");
        this.requestsFile = root.resolve("requests.json");
        this.settings = settings;
        this.clock = clock;
        Files.createDirectories(approved);
        Files.createDirectories(submissions);
        reload();
    }

    public Path submissionsDir() { return submissions; }

    public synchronized void reload() throws IOException {
        sites.clear();
        requests.clear();
        try (Stream<Path> s = Files.list(approved)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path meta = p.resolve(META);
                if (Files.isDirectory(p) && Files.exists(meta)) {
                    try {
                        Site site = Site.fromMap(Json.asMap(Json.parse(Files.readString(meta, StandardCharsets.UTF_8))));
                        if (Text.validSiteId(site.id) && site.id.equals(p.getFileName().toString())) sites.put(site.id, site);
                    } catch (IllegalArgumentException ignored) { /* meta inválido: site ignorado */ }
                }
            }
        }
        if (Files.exists(requestsFile)) {
            try {
                for (Object o : Json.asList(Json.asMap(Json.parse(Files.readString(requestsFile, StandardCharsets.UTF_8))).get("requests"))) {
                    Request r = Request.fromMap(Json.asMap(o));
                    requests.put(r.id, r);
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveRequests() throws IOException {
        List<Object> l = new ArrayList<>();
        for (Request r : requests.values()) l.add(r.toMap());
        Files.writeString(requestsFile, Json.stringify(Json.obj("requests", l)), StandardCharsets.UTF_8);
    }

    // ---------------- consulta ----------------
    public synchronized List<Site> list() { return new ArrayList<>(sites.values()); }
    public synchronized Site get(String id) { return sites.get(id); }
    public synchronized List<Request> pending() { return new ArrayList<>(requests.values()); }

    // ---------------- solicitação (qualquer jogador) ----------------
    public synchronized Res<Request> request(UUID player, String playerName, String id, String title) {
        if (!Text.validSiteId(id)) return Res.fail("ID inválido: use 2-32 caracteres a-z, 0-9 e '-' (ex.: loja-do-joao).");
        if (sites.containsKey(id)) return Res.fail("Já existe um site aprovado com esse ID.");
        if (requests.containsKey(id)) return Res.fail("Já existe uma solicitação pendente com esse ID.");
        long mine = requests.values().stream().filter(r -> r.requester.equals(player)).count();
        if (mine >= 3) return Res.fail("Você já tem 3 solicitações pendentes.");
        String t = Text.clean(title, false);
        if (t.length() < 2 || t.length() > 40) return Res.fail("O título deve ter 2-40 caracteres.");
        Request r = new Request();
        r.id = id; r.title = t; r.requester = player; r.requesterName = playerName; r.time = clock.getAsLong();
        requests.put(id, r);
        try { saveRequests(); } catch (IOException e) { requests.remove(id); return Res.fail("Erro ao salvar a solicitação."); }
        return Res.ok(r);
    }

    // ---------------- revisão (administrador) ----------------
    /** Analisa a pasta/zip enviada sem aprová-la: lista arquivos, erros e avisos. */
    public synchronized Res<Review> review(String id) {
        if (!Text.validSiteId(id)) return Res.fail("ID inválido.");
        Path dir = submissions.resolve(id);
        Path zip = submissions.resolve(id + ".zip");
        Review rv = new Review();
        try {
            if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                collectDir(dir, rv);
            } else if (Files.isRegularFile(zip, LinkOption.NOFOLLOW_LINKS)) {
                collectZip(zip, rv);
            } else {
                return Res.fail("Nada encontrado em " + submissions + " (esperado: pasta '" + id + "' ou '" + id + ".zip').");
            }
        } catch (IOException e) {
            return Res.fail("Erro lendo os arquivos: " + e.getMessage());
        }
        return Res.ok(rv);
    }

    private interface Sink { void file(String path, byte[] data); }

    private void collectDir(Path dir, Review rv) throws IOException {
        List<Path> all = new ArrayList<>();
        try (Stream<Path> w = Files.walk(dir)) { w.forEach(all::add); }
        for (Path p : all) {
            if (p.equals(dir)) continue;
            if (Files.isSymbolicLink(p)) { rv.errors.add("Link simbólico proibido: " + dir.relativize(p)); continue; }
            if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) continue;
            String rel = dir.relativize(p).toString().replace('\\', '/');
            long size = Files.size(p);
            if (size > settings.maxSiteFileBytes) { rv.errors.add("Arquivo grande demais: " + rel); continue; }
            validateAndAdd(rv, rel, Files.readAllBytes(p));
        }
        finish(rv);
    }

    private void collectZip(Path zip, Review rv) throws IOException {
        long budget = settings.maxSiteBytes * 2;
        try (InputStream in = Files.newInputStream(zip); ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                    budget -= n;
                    if (budget < 0 || bos.size() > settings.maxSiteFileBytes) {
                        rv.errors.add("Zip excede os limites (possível zip bomb) em: " + name);
                        return;
                    }
                }
                validateAndAdd(rv, name, bos.toByteArray());
            }
        }
        finish(rv);
    }

    private void validateAndAdd(Review rv, String rel, byte[] data) {
        if (rel.startsWith("__MACOSX/") || rel.endsWith(".DS_Store")) return;
        if (!REL_PATH.matcher(rel).matches() || rel.contains("..") || rel.contains("//") || rel.endsWith("/")) {
            rv.errors.add("Nome de arquivo inválido: " + rel); return;
        }
        if (rel.startsWith(".")) { rv.errors.add("Arquivos ocultos não são permitidos: " + rel); return; }
        String ext = rel.contains(".") ? rel.substring(rel.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXT.contains(ext)) { rv.errors.add("Extensão não permitida (." + ext + "): " + rel); return; }
        rv.files.add(new FileInfo(rel, data.length, sha256(data)));
        rv.totalSize += data.length;
        if (rel.equals("site.json")) {
            try {
                Map<String, Object> m = Json.asMap(Json.parse(new String(data, StandardCharsets.UTF_8)));
                rv.title = Text.clean(Json.str(m, "title", ""), false);
                rv.description = Text.clean(Json.str(m, "description", ""), false);
            } catch (IllegalArgumentException ex) { rv.warnings.add("site.json inválido (ignorado)."); }
        }
        if (Set.of("html", "htm", "js", "mjs", "css", "svg").contains(ext)) {
            String t = new String(data, StandardCharsets.UTF_8);
            Matcher m = EXTERNAL.matcher(t);
            if (m.find()) rv.warnings.add("Referência a endereço externo em " + rel + ": \"" + m.group() + "\" (bloqueado pela CSP, salvo configuração do administrador).");
            if (ext.startsWith("htm") && rv.title.isEmpty()) {
                Matcher tm = Pattern.compile("<title>([^<]{1,80})</title>", Pattern.CASE_INSENSITIVE).matcher(t);
                if (rel.equals("index.html") && tm.find()) rv.title = Text.clean(tm.group(1), false);
            }
        }
    }

    private void finish(Review rv) {
        rv.files.sort(Comparator.comparing(f -> f.path));
        if (rv.files.stream().noneMatch(f -> f.path.equals("index.html"))) rv.errors.add("Falta o arquivo index.html na raiz do site.");
        if (rv.files.size() > settings.maxSiteFiles) rv.errors.add("Arquivos demais (máx. " + settings.maxSiteFiles + ").");
        if (rv.totalSize > settings.maxSiteBytes) rv.errors.add("Tamanho total acima do limite (" + settings.maxSiteBytes + " bytes).");
    }

    // ---------------- aprovação / rejeição / remoção ----------------
    public synchronized Res<Site> approve(String id, String adminName) {
        Res<Review> r = review(id);
        if (!r.ok) return Res.fail(r.error);
        Review rv = r.value;
        if (!rv.ok()) return Res.fail("Reprovado na validação: " + String.join(" | ", rv.errors));
        if (sites.containsKey(id)) return Res.fail("Já existe um site aprovado com esse ID. Remova-o antes.");
        Request req = requests.get(id);
        try {
            Path dest = approved.resolve(id);
            Path tmp = approved.resolve(id + ".tmp");
            deleteTree(tmp);
            Files.createDirectories(tmp);
            Path dir = submissions.resolve(id);
            Path zip = submissions.resolve(id + ".zip");
            if (Files.isDirectory(dir)) {
                for (FileInfo f : rv.files) {
                    Path t = tmp.resolve(f.path);
                    Files.createDirectories(t.getParent());
                    Files.copy(dir.resolve(f.path), t);
                }
            } else {
                extractZip(zip, tmp, rv);
            }
            Site s = new Site();
            s.id = id;
            s.title = !rv.title.isEmpty() ? rv.title : (req != null ? req.title : id);
            if (req != null && !req.title.isEmpty()) s.title = req.title;
            s.description = rv.description;
            s.size = rv.totalSize;
            s.files = rv.files.size();
            s.hash = combinedHash(rv.files);
            s.approvedBy = adminName;
            s.approvedAt = clock.getAsLong();
            Files.writeString(tmp.resolve(META), Json.stringify(s.toMap()), StandardCharsets.UTF_8);
            Files.move(tmp, dest);
            sites.put(id, s);
            requests.remove(id);
            saveRequests();
            // arquiva o envio original
            Path arch = root.resolve("archive");
            Files.createDirectories(arch);
            Path src = Files.isDirectory(dir) ? dir : zip;
            Files.move(src, arch.resolve(id + "-" + clock.getAsLong() + (Files.isDirectory(src) ? "" : ".zip")));
            return Res.ok(s);
        } catch (IOException e) {
            return Res.fail("Erro ao aprovar: " + e.getMessage());
        }
    }

    private void extractZip(Path zip, Path dest, Review rv) throws IOException {
        Set<String> wanted = new HashSet<>();
        for (FileInfo f : rv.files) wanted.add(f.path);
        try (InputStream in = Files.newInputStream(zip); ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (e.isDirectory() || !wanted.contains(name)) continue;
                Path t = dest.resolve(name).normalize();
                if (!t.startsWith(dest)) throw new IOException("Zip slip: " + name);
                Files.createDirectories(t.getParent());
                Files.copy(zis, t);
            }
        }
    }

    public synchronized Res<Request> reject(String id, String reason) {
        Request r = requests.remove(id);
        boolean existed = r != null;
        try {
            Path dir = submissions.resolve(id);
            Path zip = submissions.resolve(id + ".zip");
            if (Files.exists(dir) || Files.exists(zip)) {
                Path rej = root.resolve("rejected");
                Files.createDirectories(rej);
                Files.move(Files.exists(dir) ? dir : zip, rej.resolve(id + "-" + clock.getAsLong() + (Files.exists(dir) ? "" : ".zip")));
                existed = true;
            }
            saveRequests();
        } catch (IOException e) {
            return Res.fail("Erro ao rejeitar: " + e.getMessage());
        }
        if (!existed) return Res.fail("Nenhuma solicitação/envio com esse ID.");
        return Res.ok(r);
    }

    public synchronized Res<Site> remove(String id) {
        Site s = sites.remove(id);
        if (s == null) return Res.fail("Site não encontrado.");
        try { deleteTree(approved.resolve(id)); } catch (IOException e) { return Res.fail("Erro ao remover: " + e.getMessage()); }
        return Res.ok(s);
    }

    // ---------------- leitura de arquivos aprovados (para distribuir aos clientes) ----------------
    public synchronized List<FileInfo> manifest(String id) throws IOException {
        Site s = sites.get(id);
        if (s == null) return null;
        List<FileInfo> out = new ArrayList<>();
        Path dir = approved.resolve(id);
        try (Stream<Path> w = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) w::iterator) {
                if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || p.getFileName().toString().equals(META)) continue;
                byte[] d = Files.readAllBytes(p);
                out.add(new FileInfo(dir.relativize(p).toString().replace('\\', '/'), d.length, sha256(d)));
            }
        }
        out.sort(Comparator.comparing(f -> f.path));
        return out;
    }

    public synchronized byte[] readFile(String id, String rel) throws IOException {
        if (!sites.containsKey(id) || !REL_PATH.matcher(rel).matches() || rel.contains("..")) return null;
        Path base = approved.resolve(id).toAbsolutePath().normalize();
        Path p = base.resolve(rel).normalize();
        if (!p.startsWith(base) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) return null;
        return Files.readAllBytes(p);
    }

    // ---------------- utilidades ----------------
    public static String combinedHash(List<FileInfo> files) {
        StringBuilder sb = new StringBuilder();
        for (FileInfo f : files) sb.append(f.path).append(':').append(f.sha256).append('\n');
        return sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] data) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> w = Files.walk(p)) {
            for (Path x : (Iterable<Path>) w.sorted(Comparator.reverseOrder())::iterator) Files.delete(x);
        }
    }
}
