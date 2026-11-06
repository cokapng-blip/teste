package com.celularmod.browser;

import com.celularmod.core.Json;
import com.celularmod.core.SiteService;
import com.celularmod.core.Text;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Cache local (no cliente) dos sites aprovados, recebidos do servidor.
 * Cada arquivo é verificado por tamanho e SHA-256 antes do site ser "instalado" (rename atômico).
 */
public final class SiteCache {
    private static final Pattern REL = Pattern.compile("^[A-Za-z0-9_\\-][A-Za-z0-9_\\-./]*$");
    private static final long MAX_TOTAL = 64L * 1024 * 1024;   // teto de segurança do lado do cliente
    private static final String META = ".celular-site.json";

    public static final class Entry {
        public final String path; public final long size; public final String sha256;
        public Entry(String path, long size, String sha256) { this.path = path; this.size = size; this.sha256 = sha256; }
    }

    private static final class Pending {
        String id, title, description, hash;
        final Map<String, Entry> files = new LinkedHashMap<>();
        final Map<String, Long> received = new HashMap<>();
        Path dir;
    }

    private final Path root;
    private final Map<String, Pending> pending = new HashMap<>();

    public SiteCache(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
        // restos de downloads interrompidos
        try (Stream<Path> s = Files.list(root)) {
            for (Path p : (Iterable<Path>) s::iterator) if (p.getFileName().toString().endsWith(".part")) delete(p);
        }
    }

    public Path root() { return root; }

    public Path dirOf(String id) { return root.resolve(id); }

    /** id → hash dos sites instalados localmente. */
    public synchronized Map<String, String> installed() {
        Map<String, String> m = new HashMap<>();
        try (Stream<Path> s = Files.list(root)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path meta = p.resolve(META);
                if (Files.isDirectory(p) && Files.exists(meta)) {
                    try {
                        Map<String, Object> o = Json.asMap(Json.parse(Files.readString(meta, StandardCharsets.UTF_8)));
                        m.put(p.getFileName().toString(), Json.str(o, "hash", ""));
                    } catch (IOException | IllegalArgumentException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        return m;
    }

    public synchronized boolean isInstalled(String id, String hash) {
        return hash.equals(installed().get(id));
    }

    /** Remove sites que o servidor não lista mais (foram removidos pelo administrador). */
    public synchronized void retainOnly(Set<String> ids) {
        for (String id : installed().keySet()) if (!ids.contains(id)) {
            try { delete(dirOf(id)); } catch (IOException ignored) {}
        }
    }

    /** Começa a receber um site. Retorna mensagem de erro ou null. */
    public synchronized String begin(String id, String title, String description, String hash, List<Entry> files) {
        if (!Text.validSiteId(id)) return "ID inválido";
        long total = 0;
        Pending p = new Pending();
        p.id = id; p.title = title; p.description = description; p.hash = hash;
        for (Entry e : files) {
            if (!REL.matcher(e.path).matches() || e.path.contains("..") || e.path.contains("//")) return "Caminho inválido: " + e.path;
            if (e.size < 0 || e.sha256.length() != 64) return "Manifesto inválido";
            total += e.size;
            p.files.put(e.path, e);
        }
        if (total > MAX_TOTAL || !p.files.containsKey("index.html")) return "Site inválido ou grande demais";
        try {
            p.dir = root.resolve(id + ".part");
            delete(p.dir);
            Files.createDirectories(p.dir);
        } catch (IOException e) { return "Erro de disco: " + e.getMessage(); }
        pending.put(id, p);
        if (files.isEmpty()) return "Site vazio";
        return null;
    }

    /** Recebe um pedaço. Retorna "done" quando o site foi concluído e instalado, null se continua, ou "erro:..." */
    public synchronized String chunk(String id, String path, long offset, byte[] data) {
        Pending p = pending.get(id);
        if (p == null) return "erro:sem download ativo";
        Entry e = p.files.get(path);
        if (e == null) { abort(id); return "erro:arquivo fora do manifesto"; }
        long have = p.received.getOrDefault(path, 0L);
        if (offset != have || have + data.length > e.size) { abort(id); return "erro:pedaço fora de ordem"; }
        try {
            Path t = p.dir.resolve(path).normalize();
            if (!t.startsWith(p.dir)) { abort(id); return "erro:caminho"; }
            Files.createDirectories(t.getParent());
            try (OutputStream out = Files.newOutputStream(t, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) { out.write(data); }
            p.received.put(path, have + data.length);
            if (have + data.length == e.size) {
                String h = SiteService.sha256(Files.readAllBytes(t));
                if (!h.equals(e.sha256)) { abort(id); return "erro:hash divergente em " + path; }
            }
            for (Entry x : p.files.values()) if (p.received.getOrDefault(x.path, 0L) != x.size) return null;
            // zero-byte files nunca recebem chunk: garantimos criação
            for (Entry x : p.files.values()) if (x.size == 0) Files.createFile(p.dir.resolve(x.path));
            Files.writeString(p.dir.resolve(META), Json.stringify(Json.obj("id", id, "title", p.title,
                    "description", p.description, "hash", p.hash)), StandardCharsets.UTF_8);
            Path dest = dirOf(id);
            delete(dest);
            Files.move(p.dir, dest);
            pending.remove(id);
            return "done";
        } catch (IOException ex) {
            abort(id);
            return "erro:" + ex.getMessage();
        }
    }

    public synchronized void abort(String id) {
        Pending p = pending.remove(id);
        if (p != null) try { delete(p.dir); } catch (IOException ignored) {}
    }

    /** Lê um arquivo de um site instalado (com proteção contra path traversal e symlinks). */
    public byte[] read(String id, String rel) {
        if (!Text.validSiteId(id) || rel == null || !REL.matcher(rel).matches() || rel.contains("..")) return null;
        try {
            Path base = dirOf(id).toAbsolutePath().normalize();
            Path p = base.resolve(rel).normalize();
            if (!p.startsWith(base) || p.getFileName().toString().startsWith(".")) return null;
            if (Files.isDirectory(p)) p = p.resolve("index.html");
            if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) return null;
            return Files.readAllBytes(p);
        } catch (IOException | InvalidPathException e) { return null; }
    }

    public boolean has(String id) { return Text.validSiteId(id) && Files.exists(dirOf(id).resolve(META)); }

    private static void delete(Path p) throws IOException {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> w = Files.walk(p)) {
            for (Path x : (Iterable<Path>) w.sorted(Comparator.reverseOrder())::iterator) Files.delete(x);
        }
    }
}
