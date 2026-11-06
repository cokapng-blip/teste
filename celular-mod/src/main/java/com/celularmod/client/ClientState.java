package com.celularmod.client;

import com.celularmod.CelularMod;
import com.celularmod.core.Json;
import com.celularmod.net.S2CPacket;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Estado do cliente: espelho dos dados que o servidor enviou. Só a thread do cliente acessa. */
public final class ClientState {
    private ClientState() {}

    // conexão / servidor
    public static boolean hello;                 // o servidor tem o mod e já respondeu
    public static String discordUrl = "";
    public static boolean admin;
    public static boolean allowExternal;
    public static String serverMotd = "";

    // WhatsApp
    public static Map<String, Object> waAccount;                 // null = sem conta
    public static List<Map<String, Object>> convs = new ArrayList<>();
    public static int waUnread;
    public static final Map<String, List<Map<String, Object>>> histories = new HashMap<>();
    public static List<Map<String, Object>> waSearch = new ArrayList<>();
    public static String openChat;                               // handle da conversa aberta (ou null)
    public static long waVersion;                                // incrementa a cada mudança (UI rola p/ o fim)

    // Threads
    public static Map<String, Object> thAccount;
    public static String feedTab = "foryou", feedUser = "";
    public static List<Map<String, Object>> feed = new ArrayList<>();
    public static Map<String, Object> thProfile;
    public static boolean feedLoaded;

    // sites
    public static List<Map<String, Object>> sites = new ArrayList<>();
    public static int pendingSites;
    public static final Set<String> downloading = new HashSet<>();
    public static final Map<String, String> siteErrors = new HashMap<>();

    // app Minecraft
    public static Map<String, Object> mcInfo;
    public static long mcInfoAt;

    // retorno das ações (para mostrar erros/sucesso na tela)
    public static String lastAction = "", lastMsg = "";
    public static boolean lastOk;
    public static long lastResultAt;

    public static void reset() {
        hello = false; discordUrl = ""; admin = false; allowExternal = false; serverMotd = "";
        waAccount = null; convs = new ArrayList<>(); waUnread = 0; histories.clear(); waSearch = new ArrayList<>(); openChat = null;
        thAccount = null; feed = new ArrayList<>(); thProfile = null; feedLoaded = false;
        sites = new ArrayList<>(); pendingSites = 0; downloading.clear(); siteErrors.clear();
        mcInfo = null; lastMsg = "";
    }

    public static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(CelularMod.MODID, "textures/gui/icons/" + name + ".png");
    }

    // ------------------------------------------------------------------ recebimento
    public static void handle(S2CPacket pkt) {
        Map<String, Object> m;
        try { m = Json.asMap(Json.parse(pkt.json())); } catch (IllegalArgumentException e) { return; }
        String t = Json.str(m, "t", "");
        switch (t) {
            case "hello" -> {
                boolean first = !hello;
                hello = true;
                discordUrl = Json.str(m, "discord", "");
                admin = Json.bool(m, "admin", false);
                allowExternal = Json.bool(m, "allowExternal", false);
                serverMotd = Json.str(m, "server", "");
                BrowserManager.onHello(allowExternal);
                if (first) ClientNet.send("th.feed", "tab", feedTab, "user", feedUser);
            }
            case "result" -> {
                lastAction = Json.str(m, "action", "");
                lastOk = Json.bool(m, "ok", false);
                lastMsg = Json.str(m, "msg", "");
                lastResultAt = System.currentTimeMillis();
            }
            case "wa.state" -> {
                Object acc = m.get("account");
                waAccount = acc == null ? null : Json.asMap(acc);
                convs = mapList(m.get("convs"));
                waUnread = (int) Json.num(m, "unread", 0);
            }
            case "wa.msg" -> onWaMsg(m);
            case "wa.history" -> histories.put(Json.str(m, "handle", ""), mapList(m.get("msgs")));
            case "wa.search" -> waSearch = mapList(m.get("results"));
            case "th.state" -> {
                Object acc = m.get("account");
                thAccount = acc == null ? null : Json.asMap(acc);
            }
            case "th.feed" -> {
                if (Json.str(m, "tab", "").equals(feedTab) && Json.str(m, "user", "").equals(feedUser)) {
                    feed = mapList(m.get("posts"));
                    feedLoaded = true;
                }
            }
            case "th.post" -> onPost(Json.asMap(m.get("post")));
            case "th.gone" -> {
                long id = Json.num(m, "id", -1);
                feed.removeIf(p -> Json.num(p, "id", -2) == id);
            }
            case "th.profile" -> thProfile = m.get("profile") == null ? null : Json.asMap(m.get("profile"));
            case "mc.info" -> { mcInfo = m; mcInfoAt = System.currentTimeMillis(); }
            case "site.index" -> {
                sites = mapList(m.get("sites"));
                pendingSites = (int) Json.num(m, "pending", 0);
                BrowserManager.onSiteIndex(sites);
            }
            case "site.manifest" -> BrowserManager.onManifest(m);
            case "site.chunk" -> BrowserManager.onChunk(m, pkt.bin());
            default -> { }
        }
    }

    private static void onWaMsg(Map<String, Object> m) {
        Map<String, Object> msg = Json.asMap(m.get("msg"));
        String peer = Json.str(msg, "peer", "");
        convs = mapList(m.get("convs"));
        waUnread = (int) Json.num(m, "unread", 0);
        List<Map<String, Object>> h = histories.get(peer);
        if (h != null) h.add(msg);
        waVersion++;
        boolean mine = Json.bool(msg, "mine", false);
        if (!mine) {
            boolean viewing = peer.equals(openChat) && PhoneRenderer.isOpen();
            if (viewing) {
                ClientNet.send("wa.read", "handle", peer);
            } else {
                String name = Json.str(m, "peerName", "?");
                String text = ClientSettings.hidePreview ? "Nova mensagem" : Json.str(msg, "text", "");
                Toasts.push(icon("whatsapp"), name, text);
                if (ClientSettings.notifications) Sfx.notification();
            }
        }
    }

    private static void onPost(Map<String, Object> post) {
        long id = Json.num(post, "id", -1);
        for (int i = 0; i < feed.size(); i++) {
            if (Json.num(feed.get(i), "id", -2) == id) { feed.set(i, post); return; }
        }
        // novo post: entra no topo se for relevante para a aba atual
        boolean relevant = feedTab.equals("foryou") || feedTab.equals("recent")
                || (feedTab.equals("profile") && Json.str(post, "user", "").equalsIgnoreCase(feedUser));
        if (relevant && feedLoaded) feed.add(0, post);
    }

    public static List<Map<String, Object>> mapList(Object o) {
        List<Map<String, Object>> l = new ArrayList<>();
        for (Object x : Json.asList(o)) l.add(Json.asMap(x));
        return l;
    }

    public static String utf8(byte[] b) { return new String(b, StandardCharsets.UTF_8); }
}
