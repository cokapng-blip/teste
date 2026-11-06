package com.celularmod.client.apps;

import com.celularmod.client.App;
import com.celularmod.client.ClientNet;
import com.celularmod.client.ClientState;
import com.celularmod.client.ui.TextField;
import com.celularmod.client.ui.Ui;
import com.celularmod.core.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Threads: rede social de texto compartilhada entre os jogadores do servidor. */
public final class ThreadsApp extends App {
    private enum View { FEED, COMPOSE, PROFILE, SHARE, SETTINGS }

    private static final int HDR = 0xFF2B2B33, WHITE = 0xFFFFFFFF, LIKE = 0xFFE5484D;
    private View view = View.FEED;
    private final TextField nameField = new TextField("Seu nome de exibição", 24, false);
    private final TextField postField = new TextField("O que está acontecendo?", 280, true);
    private final TextField renameField = new TextField("Novo nome", 24, false);
    private final Map<Long, List<String>> wrapCache = new HashMap<>();
    private long shareId = -1;
    private boolean awaitingPost;
    private int ticks;

    public ThreadsApp() {
        nameField.onEnter = () -> ClientNet.send("th.create", "name", nameField.take());
        renameField.onEnter = () -> { String n = renameField.take(); if (!n.isEmpty()) ClientNet.send("th.rename", "name", n); };
    }

    @Override public String id() { return "threads"; }
    @Override public String label() { return "Threads"; }
    @Override public ResourceLocation icon() { return ClientState.icon("threads"); }
    @Override public int accent() { return HDR; }

    @Override
    public void onOpen() { view = View.FEED; loadFeed("foryou", ""); }

    @Override
    public void tick() {
        if (++ticks % 200 == 0 && (view == View.FEED || view == View.PROFILE) && ClientState.thAccount != null) refresh();
    }

    @Override
    public boolean onBack() {
        if (view == View.FEED) return false;
        if (view == View.PROFILE || view == View.SHARE || view == View.COMPOSE || view == View.SETTINGS) {
            if (view == View.PROFILE) loadFeed("foryou", "");
            view = View.FEED;
            return true;
        }
        return false;
    }

    private void loadFeed(String tab, String user) {
        ClientState.feedTab = tab;
        ClientState.feedUser = user;
        ClientState.feedLoaded = false;
        ClientState.feed = new ArrayList<>();
        PhoneUiHelper.resetScroll("feed");
        refresh();
    }

    private void refresh() { ClientNet.send("th.feed", "tab", ClientState.feedTab, "user", ClientState.feedUser); }

    private void openProfile(String user) {
        loadFeed("profile", user);
        ClientState.thProfile = null;
        ClientNet.send("th.profile", "user", user);
        view = View.PROFILE;
    }

    // ------------------------------------------------------------------ desenho
    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        if (!ClientState.hello) {
            Common.header(ui, x, y, w, "Threads", HDR, false);
            Common.centeredNote(ui, x, y + 60, w, "Sem conexão. O servidor precisa ter o mod Celular instalado.", ui.th.muted);
            return;
        }
        if (ClientState.thAccount == null) { onboarding(ui, x, y, w, h); return; }
        if (awaitingPost && ClientState.lastAction.equals("th.post") && System.currentTimeMillis() - ClientState.lastResultAt < 1500) {
            awaitingPost = false;
            if (ClientState.lastOk) { view = View.FEED; loadFeed("recent", ""); }
        }
        switch (view) {
            case FEED -> feed(ui, x, y, w, h);
            case COMPOSE -> compose(ui, x, y, w, h);
            case PROFILE -> profile(ui, x, y, w, h);
            case SHARE -> share(ui, x, y, w, h);
            case SETTINGS -> settings(ui, x, y, w, h);
        }
    }

    private void onboarding(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "Threads", HDR, false);
        ui.icon(icon(), x + (w - 40) / 2f, y + 34, 40);
        ui.textC("Entre no Threads", x + w / 2f, y + 82, ui.th.text);
        Common.centeredNote(ui, x, y + 96, w, "Publique textos e veja o que os outros jogadores estão dizendo.", ui.th.muted);
        ui.field(nameField, x + 10, y + 140, w - 20, 16, WHITE);
        if (ui.button(x + 10, y + 162, w - 20, 16, "Criar conta", 0xFF4A4A58, WHITE)) ClientNet.send("th.create", "name", nameField.take());
        Common.banner(ui, x, y + 186, w, "th.create");
    }

    private void bottomTabs(Ui ui, int x, int y, int w, int h, int sel) {
        int c = Common.tabBar(ui, x, y + h - 16, w, new String[]{"Início", "Novo", "Perfil"}, sel, WHITE, null);
        if (c == 0) { view = View.FEED; loadFeed(ClientState.feedTab.equals("recent") ? "recent" : "foryou", ""); }
        if (c == 1) view = View.COMPOSE;
        if (c == 2) openProfile(Json.str(ClientState.thAccount, "user", ""));
    }

    private List<String> lines(Ui ui, Map<String, Object> p, int width) {
        long id = Json.num(p, "id", 0);
        return wrapCache.computeIfAbsent(id, k -> ui.wrap(Json.str(p, "text", ""), width));
    }

    /** Desenha uma lista de posts; retorna a altura total. */
    private void postList(Ui ui, String scrollId, int x, int y, int w, int h, boolean empty) {
        List<Map<String, Object>> feed = ClientState.feed;
        if (!ClientState.feedLoaded) { Common.centeredNote(ui, x, y + 40, w, "Carregando...", ui.th.muted); return; }
        if (feed.isEmpty()) { Common.centeredNote(ui, x, y + 40, w, "Nada por aqui ainda. Seja o primeiro a publicar!", ui.th.muted); return; }
        int total = 0;
        List<List<String>> ls = new ArrayList<>();
        for (Map<String, Object> p : feed) {
            List<String> l = lines(ui, p, w - 14);
            ls.add(l);
            total += 12 + l.size() * 9 + 6 + 14 + 4;
        }
        ui.scrollArea(scrollId, x, y, w, h, total, false, () -> {
            int cy = 0;
            for (int i = 0; i < feed.size(); i++) {
                Map<String, Object> p = feed.get(i);
                List<String> l = ls.get(i);
                int ph = 12 + l.size() * 9 + 6 + 14 + 4;
                long id = Json.num(p, "id", 0);
                String user = Json.str(p, "user", "?");
                ui.rect(0, cy + ph - 1, w, 1, ui.th.line);
                // autor (clicável)
                String name = Json.str(p, "name", "?");
                int nw = ui.textW(name);
                if (ui.area(5, cy + 2, nw + ui.textW(" @" + user) + 2, 10)) { openProfile(user); return; }
                ui.textBold(name, 6, cy + 3, ui.th.text);
                ui.text(" @" + user, 6 + nw, cy + 3, ui.th.muted);
                ui.textR(Ui.shortDate(Json.num(p, "time", 0)), w - 4, cy + 3, ui.th.muted);
                for (int k = 0; k < l.size(); k++) ui.text(l.get(k), 7, cy + 14 + k * 9, ui.th.text);
                int ay = cy + 14 + l.size() * 9 + 3;
                boolean liked = Json.bool(p, "liked", false);
                long likes = Json.num(p, "likes", 0);
                String heart = "♥ " + likes;
                if (ui.area(5, ay, ui.textW(heart) + 4, 11)) {
                    p.put("liked", !liked);
                    p.put("likes", Math.max(0, likes + (liked ? -1 : 1)));
                    ClientNet.send("th.like", "id", id, "like", !liked);
                }
                ui.text(heart, 7, ay + 1, liked ? LIKE : ui.th.muted);
                int sx = 7 + ui.textW(heart) + 12;
                if (ui.area(sx - 2, ay, ui.textW("Compartilhar") + 4, 11)) { shareId = id; view = View.SHARE; return; }
                ui.text("Compartilhar", sx, ay + 1, ui.th.muted);
                if (Json.bool(p, "mine", false) || ClientState.admin) {
                    String del = "Apagar";
                    if (ui.area(w - ui.textW(del) - 8, ay, ui.textW(del) + 4, 11)) ClientNet.send("th.delete", "id", id);
                    ui.textR(del, w - 6, ay + 1, ui.th.danger);
                }
                cy += ph;
            }
        });
    }

    private void feed(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "Threads", HDR, false);
        // abas "Para você" / "Recentes"
        int ty = y + Common.HEADER_H;
        float half = w / 2f;
        boolean fy = ClientState.feedTab.equals("foryou");
        if (ui.area(x, ty, half, 14)) { if (!fy) loadFeed("foryou", ""); }
        if (ui.area(x + half, ty, half, 14)) { if (fy) loadFeed("recent", ""); }
        ui.textC("Para você", x + half / 2, ty + 3, fy ? ui.th.text : ui.th.muted);
        ui.textC("Recentes", x + half + half / 2, ty + 3, fy ? ui.th.muted : ui.th.text);
        ui.rect(x + (fy ? 0 : half) + 10, ty + 13, half - 20, 1, WHITE);
        int by = ty + 15;
        postList(ui, "feed", x, by, w, h - (by - y) - 16, false);
        Common.banner(ui, x, by + 2, w, "th.like", "th.delete", "th.share", "th.rename");
        bottomTabs(ui, x, y, w, h, 0);
    }

    private void compose(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "Nova publicação", HDR, false);
        ui.field(postField, x + 6, y + 30, w - 12, 80, WHITE);
        ui.textR(postField.text.length() + "/280", x + w - 6, y + 114, ui.th.muted);
        boolean can = !postField.text.trim().isEmpty();
        if (ui.button(x + w - 62, y + 126, 56, 16, "Publicar", can ? 0xFF4A4A58 : ui.th.surface2, can ? WHITE : ui.th.muted) && can) {
            ClientNet.send("th.post", "text", postField.take());
            awaitingPost = true;
        }
        Common.banner(ui, x, y + 150, w, "th.post");
        bottomTabs(ui, x, y, w, h, 1);
    }

    private void profile(Ui ui, int x, int y, int w, int h) {
        Map<String, Object> pr = ClientState.thProfile;
        if (Common.header(ui, x, y, w, pr == null ? "Perfil" : Json.str(pr, "name", "Perfil"), HDR, true)) { onBack(); return; }
        int by = y + Common.HEADER_H;
        if (pr == null) { Common.centeredNote(ui, x, by + 40, w, "Carregando perfil...", ui.th.muted); bottomTabs(ui, x, y, w, h, 2); return; }
        Common.avatar(ui, x + 6, by + 4, 24, Json.str(pr, "name", "?"));
        ui.textBold(ui.ellipsize(Json.str(pr, "name", "?"), w - 40), x + 36, by + 5, ui.th.text);
        ui.text("@" + Json.str(pr, "user", ""), x + 36, by + 16, ui.th.muted);
        ui.text(Json.num(pr, "posts", 0) + " publicações · " + Json.num(pr, "likes", 0) + " curtidas", x + 6, by + 32, ui.th.muted);
        int top = by + 44;
        if (Json.bool(pr, "mine", false)) {
            if (ui.button(x + w - 70, by + 6, 64, 14, "Editar nome", ui.th.surface2, ui.th.text)) view = View.SETTINGS;
        }
        ui.rect(x, top - 1, w, 1, ui.th.line);
        postList(ui, "profile", x, top, w, h - (top - y) - 16, false);
        Common.banner(ui, x, top + 2, w, "th.like", "th.delete", "th.share");
        bottomTabs(ui, x, y, w, h, 2);
    }

    private void settings(Ui ui, int x, int y, int w, int h) {
        if (Common.header(ui, x, y, w, "Editar nome", HDR, true)) { view = View.PROFILE; return; }
        Map<String, Object> acc = ClientState.thAccount;
        ui.text("Nome atual: " + Json.str(acc, "name", "?"), x + 6, y + 30, ui.th.text);
        ui.text("Usuário: @" + Json.str(acc, "user", ""), x + 6, y + 42, ui.th.muted);
        ui.field(renameField, x + 6, y + 60, w - 50, 16, WHITE);
        if (ui.button(x + w - 40, y + 60, 34, 16, "Salvar", 0xFF4A4A58, WHITE)) {
            String n = renameField.take();
            if (!n.isEmpty()) ClientNet.send("th.rename", "name", n);
        }
        Common.centeredNote(ui, x, y + 84, w, "Você pode trocar o nome a cada 5 minutos. O @usuário não muda.", ui.th.muted);
        Common.banner(ui, x, y + 124, w, "th.rename");
    }

    private void share(Ui ui, int x, int y, int w, int h) {
        if (Common.header(ui, x, y, w, "Compartilhar pelo WhatsApp", HDR, true)) { view = View.FEED; return; }
        if (ClientState.waAccount == null) {
            Common.centeredNote(ui, x, y + 50, w, "Crie sua conta no WhatsApp para compartilhar.", ui.th.muted);
            return;
        }
        List<Map<String, Object>> convs = ClientState.convs;
        if (convs.isEmpty()) { Common.centeredNote(ui, x, y + 50, w, "Você ainda não tem contatos no WhatsApp.", ui.th.muted); return; }
        final int rowH = 24;
        ui.scrollArea("share", x, y + Common.HEADER_H, w, h - Common.HEADER_H, convs.size() * rowH, false, () -> {
            for (int i = 0; i < convs.size(); i++) {
                Map<String, Object> c = convs.get(i);
                int ry = i * rowH;
                String name = Json.str(c, "name", "?");
                if (ui.area(0, ry, w, rowH)) {
                    ClientNet.send("th.share", "id", shareId, "to", Json.str(c, "handle", ""));
                    view = View.FEED;
                    return;
                }
                Common.avatar(ui, 4, ry + 2, 20, name);
                ui.text(ui.ellipsize(name, w - 40), 28, ry + 3, ui.th.text);
                ui.text("Enviar para este contato", 28, ry + 13, ui.th.muted);
            }
        });
    }
}
