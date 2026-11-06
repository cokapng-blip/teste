package com.celularmod.client;

import com.celularmod.browser.LocalSiteServer;
import com.celularmod.browser.SiteCache;
import com.celularmod.core.Json;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Lado do cliente da "distribuição" de sites: baixa/atualiza os sites aprovados para um cache local
 * (por servidor) e serve esse cache ao Chromium através do {@link LocalSiteServer} em 127.0.0.1.
 */
public final class BrowserManager {
    private BrowserManager() {}

    private static SiteCache cache;
    private static LocalSiteServer server;
    private static String cacheKey = "";
    /** Histórico do navegador (pilha própria): entradas "siteId/caminho". */
    public static final Deque<String> history = new ArrayDeque<>();
    public static volatile String current = "";   // "siteId/caminho" atual (atualizado pelo servidor local)

    public static boolean ready() { return server != null && cache != null; }

    public static SiteCache cache() { return cache; }

    public static LocalSiteServer server() { return server; }

    /** Chamado quando entramos num mundo/servidor. */
    public static synchronized void start() {
        stop();
        try {
            Minecraft mc = Minecraft.getInstance();
            String key;
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                key = "sp-" + mc.getSingleplayerServer().getWorldData().getLevelName();
            } else if (mc.getCurrentServer() != null) {
                key = "mp-" + mc.getCurrentServer().ip;
            } else key = "unknown";
            cacheKey = hash(key);
            Path dir = FMLPaths.GAMEDIR.get().resolve("celular").resolve("cache").resolve(cacheKey);
            cache = new SiteCache(dir);
            server = new LocalSiteServer(cache, false);
            server.setNavigationListener((id, path) -> {
                String cur = id + path;
                if (!cur.equals(current)) {
                    current = cur;
                    navEvents++;
                }
            });
        } catch (IOException | RuntimeException e) {
            cache = null;
            server = null;
            ClientState.siteErrors.put("*", "Falha ao iniciar o navegador local: " + e.getMessage());
        }
    }

    public static volatile int navEvents;

    public static synchronized void stop() {
        if (server != null) server.close();
        server = null;
        cache = null;
        history.clear();
        current = "";
    }

    private static String hash(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static void onHello(boolean allowExternal) {
        if (server != null) server.setAllowExternal(allowExternal);
    }

    /** O servidor listou os sites aprovados: baixa o que falta/está desatualizado e apaga o que sumiu. */
    public static void onSiteIndex(List<Map<String, Object>> sites) {
        if (cache == null) return;
        Set<String> ids = new HashSet<>();
        Map<String, String> installed = cache.installed();
        for (Map<String, Object> s : sites) {
            String id = Json.str(s, "id", "");
            String hash = Json.str(s, "hash", "");
            ids.add(id);
            if (!hash.equals(installed.get(id)) && !ClientState.downloading.contains(id)) {
                ClientState.downloading.add(id);
                ClientState.siteErrors.remove(id);
                ClientNet.send("site.get", "id", id);
            }
        }
        cache.retainOnly(ids);
    }

    public static void onManifest(Map<String, Object> m) {
        if (cache == null) return;
        String id = Json.str(m, "id", "");
        List<SiteCache.Entry> files = new ArrayList<>();
        for (Object o : Json.asList(m.get("files"))) {
            Map<String, Object> f = Json.asMap(o);
            files.add(new SiteCache.Entry(Json.str(f, "p", ""), Json.num(f, "s", -1), Json.str(f, "h", "")));
        }
        String err = cache.begin(id, Json.str(m, "title", id), Json.str(m, "description", ""), Json.str(m, "hash", ""), files);
        if (err != null) {
            ClientState.downloading.remove(id);
            ClientState.siteErrors.put(id, err);
        }
    }

    public static void onChunk(Map<String, Object> m, byte[] bin) {
        if (cache == null) return;
        String id = Json.str(m, "id", "");
        String r = cache.chunk(id, Json.str(m, "p", ""), Json.num(m, "o", -1), bin);
        if (r == null) return;
        ClientState.downloading.remove(id);
        if (!r.equals("done")) ClientState.siteErrors.put(id, r.substring(Math.min(5, r.length())));
    }

    public static boolean isReady(String siteId) { return cache != null && cache.has(siteId); }
}
