package com.celularmod.server;

import com.celularmod.core.*;
import com.celularmod.core.Models.Account;
import com.celularmod.core.Models.Msg;
import com.celularmod.core.Models.Post;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Processa TODOS os pacotes do cliente. Regra de ouro: o cliente só informa "o que quer fazer";
 * identidade (UUID), permissões, limites e conteúdo são decididos/validados aqui.
 */
public final class ServerHandler {
    private ServerHandler() {}

    public static void handle(ServerPlayer p, String raw) {
        ServerState s = ServerState.get();
        if (s == null || raw == null || raw.length() > 8000) return;
        Map<String, Object> m;
        try {
            m = Json.asMap(Json.parse(raw));
        } catch (IllegalArgumentException e) {
            return;
        }
        String t = Json.str(m, "t", "");
        UUID me = p.getUUID();
        if (!s.anyLimiter.allow(me, "any")) return;     // flood geral
        try {
            if (t.equals("hello")) { hello(s, p); return; }
            if (!s.modded.contains(me)) return;          // ignora quem não se apresentou
            switch (t) {
                case "wa.create": waCreate(s, p, m); break;
                case "wa.rename": waRename(s, p, m); break;
                case "wa.privacy": waPrivacy(s, p, m); break;
                case "wa.search": waSearch(s, p, m); break;
                case "wa.add": waAdd(s, p, m); break;
                case "wa.remove": waRemove(s, p, m); break;
                case "wa.history": waHistory(s, p, m); break;
                case "wa.read": waRead(s, p, m); break;
                case "wa.send": waSend(s, p, m); break;
                case "th.create": thCreate(s, p, m); break;
                case "th.rename": thRename(s, p, m); break;
                case "th.post": thPost(s, p, m); break;
                case "th.like": thLike(s, p, m); break;
                case "th.delete": thDelete(s, p, m); break;
                case "th.feed": thFeed(s, p, m); break;
                case "th.profile": thProfile(s, p, m); break;
                case "th.share": thShare(s, p, m); break;
                case "site.get": siteGet(s, p, m); break;
                case "site.list": s.sendSiteIndex(p); break;
                case "mc.info": mcInfo(s, p); break;
                default: break;
            }
        } catch (Exception e) {
            ServerState.LOG.error("Celular: erro tratando '{}' de {}", t, p.getGameProfile().getName(), e);
            result(s, p, t, false, "Erro interno no servidor.");
        }
    }

    // ------------------------------------------------------------ utilidades
    static void result(ServerState s, ServerPlayer p, String action, boolean ok, String msg) {
        s.send(p, Json.obj("t", "result", "action", action, "ok", ok, "msg", msg == null ? "" : msg));
    }

    static <T> boolean fail(ServerState s, ServerPlayer p, String action, Res<T> r) {
        if (r.ok) return false;
        result(s, p, action, false, r.error);
        return true;
    }

    static boolean limited(ServerState s, ServerPlayer p, RateLimiter l, String action, String what) {
        if (l.allow(p.getUUID(), action)) return false;
        result(s, p, action, false, "Calma! Muitas ações seguidas (" + what + "). Tente de novo em instantes.");
        return true;
    }

    static void hello(ServerState s, ServerPlayer p) {
        s.modded.add(p.getUUID());
        s.send(p, Json.obj("t", "hello", "discord", s.settings.discordUrl, "allowExternal", s.settings.allowExternalResources,
                "admin", s.isAdmin(p), "server", s.server.getMotd(), "proto", 1L));
        pushWa(s, p);
        pushTh(s, p);
        s.sendSiteIndex(p);
    }

    // ------------------------------------------------------------ WhatsApp
    static Map<String, Object> waAccountView(Account a) {
        return a == null ? null : Json.obj("handle", a.handle, "name", a.name, "strangers", a.allowStrangers, "searchable", a.searchable);
    }

    static void pushWa(ServerState s, ServerPlayer p) {
        Map<String, Object> st = Json.obj("t", "wa.state", "account", waAccountView(s.wa.account(p.getUUID())),
                "convs", s.wa.account(p.getUUID()) == null ? new ArrayList<>() : s.wa.conversations(p.getUUID()),
                "unread", s.wa.totalUnread(p.getUUID()));
        s.send(p, st);
    }

    static void waCreate(ServerState s, ServerPlayer p, Map<String, Object> m) {
        Res<Account> r = s.wa.createAccount(p.getUUID(), Json.str(m, "name", ""));
        if (fail(s, p, "wa.create", r)) return;
        result(s, p, "wa.create", true, "Conta criada! Seu número: " + r.value.handle);
        pushWa(s, p);
    }

    static void waRename(ServerState s, ServerPlayer p, Map<String, Object> m) {
        Res<Account> r = s.wa.rename(p.getUUID(), Json.str(m, "name", ""));
        if (fail(s, p, "wa.rename", r)) return;
        result(s, p, "wa.rename", true, "Nome alterado.");
        pushWa(s, p);
    }

    static void waPrivacy(ServerState s, ServerPlayer p, Map<String, Object> m) {
        Res<Account> r = s.wa.setPrivacy(p.getUUID(), Json.bool(m, "strangers", true), Json.bool(m, "searchable", true));
        if (fail(s, p, "wa.privacy", r)) return;
        pushWa(s, p);
    }

    static void waSearch(ServerState s, ServerPlayer p, Map<String, Object> m) {
        if (limited(s, p, s.searchLimiter, "search", "buscas")) return;
        Res<List<Account>> r = s.wa.search(p.getUUID(), Json.str(m, "q", ""));
        if (fail(s, p, "wa.search", r)) return;
        Set<UUID> contacts = s.wa.contacts(p.getUUID());
        List<Object> out = new ArrayList<>();
        for (Account a : r.value) out.add(Json.obj("handle", a.handle, "name", a.name, "contact", contacts.contains(a.owner)));
        s.send(p, Json.obj("t", "wa.search", "results", out));
    }

    static void waAdd(ServerState s, ServerPlayer p, Map<String, Object> m) {
        Res<Account> r = s.wa.addContact(p.getUUID(), Json.str(m, "handle", ""));
        if (fail(s, p, "wa.add", r)) return;
        result(s, p, "wa.add", true, r.value.name + " adicionado aos contatos.");
        pushWa(s, p);
    }

    static void waRemove(ServerState s, ServerPlayer p, Map<String, Object> m) {
        Res<Boolean> r = s.wa.removeContact(p.getUUID(), Json.str(m, "handle", ""));
        if (fail(s, p, "wa.remove", r)) return;
        result(s, p, "wa.remove", true, "Contato removido.");
        pushWa(s, p);
    }

    static void waHistory(ServerState s, ServerPlayer p, Map<String, Object> m) {
        String h = Json.str(m, "handle", "");
        Res<List<Msg>> r = s.wa.history(p.getUUID(), h);
        if (fail(s, p, "wa.history", r)) return;
        s.wa.markRead(p.getUUID(), h);
        List<Map<String, Object>> views = new ArrayList<>();
        for (Msg x : r.value) views.add(s.wa.view(p.getUUID(), x));
        // cabe no pacote? remove as mais antigas até caber
        while (views.size() > 1 && Json.stringify(views).length() > 90_000) views.remove(0);
        s.send(p, Json.obj("t", "wa.history", "handle", h, "msgs", views));
        pushWa(s, p);
    }

    static void waRead(ServerState s, ServerPlayer p, Map<String, Object> m) {
        Res<Integer> r = s.wa.markRead(p.getUUID(), Json.str(m, "handle", ""));
        if (r.ok && r.value > 0) pushWa(s, p);
    }

    static void waSend(ServerState s, ServerPlayer p, Map<String, Object> m) {
        if (limited(s, p, s.msgLimiter, "msg", "mensagens")) return;
        Res<Msg> r = s.wa.send(p.getUUID(), Json.str(m, "to", ""), Json.str(m, "text", ""));
        if (fail(s, p, "wa.send", r)) return;
        deliver(s, p.getUUID(), r.value);
    }

    /** Entrega em tempo real ao remetente (confirmação) e ao destinatário, se estiver online. */
    static void deliver(ServerState s, UUID from, Msg msg) {
        for (UUID viewer : new UUID[]{from, msg.to}) {
            ServerPlayer vp = s.online(viewer);
            if (vp == null) continue;
            Account other = s.db.waAccounts.get(msg.from.equals(viewer) ? msg.to : msg.from);
            Map<String, Object> view = s.wa.view(viewer, msg);
            s.send(vp, Json.obj("t", "wa.msg", "msg", view, "peerName", other == null ? "?" : other.name,
                    "convs", s.wa.conversations(viewer), "unread", s.wa.totalUnread(viewer)));
        }
    }

    // ------------------------------------------------------------ Threads
    static void pushTh(ServerState s, ServerPlayer p) {
        Account a = s.th.account(p.getUUID());
        s.send(p, Json.obj("t", "th.state", "account", a == null ? null : Json.obj("user", a.handle, "name", a.name)));
    }

    static void thCreate(ServerState s, ServerPlayer p, Map<String, Object> m) {
        Res<Account> r = s.th.createAccount(p.getUUID(), Json.str(m, "name", ""));
        if (fail(s, p, "th.create", r)) return;
        result(s, p, "th.create", true, "Conta criada: @" + r.value.handle);
        pushTh(s, p);
    }

    static void thRename(ServerState s, ServerPlayer p, Map<String, Object> m) {
        Res<Account> r = s.th.rename(p.getUUID(), Json.str(m, "name", ""));
        if (fail(s, p, "th.rename", r)) return;
        result(s, p, "th.rename", true, "Nome alterado.");
        pushTh(s, p);
    }

    static void thPost(ServerState s, ServerPlayer p, Map<String, Object> m) {
        if (limited(s, p, s.postLimiter, "post", "publicações")) return;
        Res<Post> r = s.th.post(p.getUUID(), Json.str(m, "text", ""));
        if (fail(s, p, "th.post", r)) return;
        result(s, p, "th.post", true, "Publicado!");
        broadcastPost(s, r.value);
    }

    static void thLike(ServerState s, ServerPlayer p, Map<String, Object> m) {
        if (limited(s, p, s.likeLimiter, "like", "curtidas")) return;
        Res<Post> r = s.th.like(p.getUUID(), Json.num(m, "id", -1), Json.bool(m, "like", true));
        if (fail(s, p, "th.like", r)) return;
        broadcastPost(s, r.value);
    }

    static void thDelete(ServerState s, ServerPlayer p, Map<String, Object> m) {
        long id = Json.num(m, "id", -1);
        Res<Post> r = s.th.delete(p.getUUID(), id, s.isAdmin(p));
        if (fail(s, p, "th.delete", r)) return;
        result(s, p, "th.delete", true, "Publicação removida.");
        for (ServerPlayer o : s.server.getPlayerList().getPlayers()) s.send(o, Json.obj("t", "th.gone", "id", id));
    }

    /** Cada jogador recebe sua própria visão (campo "liked"/"mine" depende de quem vê). */
    static void broadcastPost(ServerState s, Post post) {
        for (ServerPlayer o : s.server.getPlayerList().getPlayers()) {
            if (s.th.account(o.getUUID()) == null) continue;
            s.send(o, Json.obj("t", "th.post", "post", s.th.view(o.getUUID(), post)));
        }
    }

    static void thFeed(ServerState s, ServerPlayer p, Map<String, Object> m) {
        if (limited(s, p, s.searchLimiter, "feed", "atualizações")) return;
        String tab = Json.str(m, "tab", "foryou");
        if (!tab.equals("foryou") && !tab.equals("recent") && !tab.equals("profile")) tab = "foryou";
        String user = Json.str(m, "user", "");
        Res<List<Post>> r = s.th.feed(p.getUUID(), tab, user);
        if (fail(s, p, "th.feed", r)) return;
        List<Object> views = new ArrayList<>();
        for (Post x : r.value) views.add(s.th.view(p.getUUID(), x));
        s.send(p, Json.obj("t", "th.feed", "tab", tab, "user", user, "posts", views));
    }

    static void thProfile(ServerState s, ServerPlayer p, Map<String, Object> m) {
        Map<String, Object> prof = s.th.profile(p.getUUID(), Json.str(m, "user", ""));
        s.send(p, Json.obj("t", "th.profile", "profile", prof));
    }

    static void thShare(ServerState s, ServerPlayer p, Map<String, Object> m) {
        if (limited(s, p, s.msgLimiter, "msg", "mensagens")) return;
        Res<Msg> r = s.th.share(p.getUUID(), Json.num(m, "id", -1), Json.str(m, "to", ""));
        if (fail(s, p, "th.share", r)) return;
        result(s, p, "th.share", true, "Compartilhado pelo WhatsApp!");
        deliver(s, p.getUUID(), r.value);
    }

    // ------------------------------------------------------------ sites / servidor
    static void siteGet(ServerState s, ServerPlayer p, Map<String, Object> m) throws java.io.IOException {
        if (limited(s, p, s.siteLimiter, "site", "downloads de sites")) return;
        String id = Json.str(m, "id", "");
        if (!Text.validSiteId(id) || s.sites.get(id) == null) { result(s, p, "site.get", false, "Site não encontrado."); return; }
        s.queueSite(p, id);
    }

    static void mcInfo(ServerState s, ServerPlayer p) {
        if (limited(s, p, s.searchLimiter, "info", "atualizações")) return;
        List<Object> players = new ArrayList<>();
        for (ServerPlayer o : s.server.getPlayerList().getPlayers()) {
            players.add(Json.obj("name", o.getGameProfile().getName(), "ping", (long) o.connection.latency(),
                    "me", o.getUUID().equals(p.getUUID())));
        }
        Level lvl = p.level();
        long day = lvl.getDayTime() / 24000L;
        long tod = lvl.getDayTime() % 24000L;
        s.send(p, Json.obj("t", "mc.info",
                "name", s.server.getMotd(),
                "version", s.server.getServerVersion(),
                "online", (long) s.server.getPlayerCount(),
                "max", (long) s.server.getMaxPlayers(),
                "players", players,
                "me", p.getGameProfile().getName(),
                "ping", (long) p.connection.latency(),
                "dimension", lvl.dimension().location().toString(),
                "day", day, "timeOfDay", tod,
                "raining", lvl.isRaining(), "thundering", lvl.isThundering(),
                "difficulty", lvl.getDifficulty().getKey(),
                "gamemode", p.gameMode.getGameModeForPlayer().getName(),
                "dedicated", s.server.isDedicatedServer()));
    }
}
