package com.celularmod.client.apps;

import com.celularmod.client.App;
import com.celularmod.client.ClientNet;
import com.celularmod.client.ClientState;
import com.celularmod.client.ui.TextField;
import com.celularmod.client.ui.Ui;
import com.celularmod.core.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** WhatsApp: conta, contatos, conversas em tempo real, privacidade. Todos os dados vêm do servidor. */
public final class WhatsAppApp extends App {
    private enum View { CHATS, CONTACTS, ADD, CHAT, SETTINGS }

    private static final int GREEN = 0xFF1FA855;
    private View view = View.CHATS;
    private String chatHandle = "", chatName = "";
    private final TextField nameField = new TextField("Seu nome de exibição", 24, false);
    private final TextField searchField = new TextField("Número ou nome", 24, false);
    private final TextField msgField = new TextField("Mensagem", 500, false);
    private final TextField renameField = new TextField("Novo nome", 24, false);
    private final Map<Long, List<String>> wrapCache = new HashMap<>();
    private long lastWaVersion = -1;

    public WhatsAppApp() {
        nameField.onEnter = this::createAccount;
        searchField.onEnter = this::search;
        msgField.onEnter = this::send;
        renameField.onEnter = this::rename;
    }

    @Override public String id() { return "whatsapp"; }
    @Override public String label() { return "WhatsApp"; }
    @Override public ResourceLocation icon() { return ClientState.icon("whatsapp"); }
    @Override public int accent() { return GREEN; }

    @Override
    public void onOpen() { view = View.CHATS; ClientState.openChat = null; }

    @Override
    public void onClose() { ClientState.openChat = null; }

    @Override
    public boolean onBack() {
        if (view == View.CHAT || view == View.ADD) {
            view = view == View.ADD ? View.CONTACTS : View.CHATS;
            ClientState.openChat = null;
            return true;
        }
        if (view != View.CHATS) { view = View.CHATS; return true; }
        return false;
    }

    // ------------------------------------------------------------------ ações
    private void createAccount() { ClientNet.send("wa.create", "name", nameField.take()); }

    private void search() { ClientNet.send("wa.search", "q", searchField.text.trim()); }

    private void rename() { String n = renameField.take(); if (!n.isEmpty()) ClientNet.send("wa.rename", "name", n); }

    private void send() {
        String t = msgField.take();
        if (!t.isEmpty()) ClientNet.send("wa.send", "to", chatHandle, "text", t);
    }

    private void openChat(String handle, String name) {
        chatHandle = handle; chatName = name; view = View.CHAT;
        ClientState.openChat = handle;
        ClientNet.send("wa.history", "handle", handle);
        PhoneUiHelper.resetScroll("chat");
    }

    // ------------------------------------------------------------------ desenho
    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        if (!ClientState.hello) {
            Common.header(ui, x, y, w, "WhatsApp", GREEN, false);
            Common.centeredNote(ui, x, y + 60, w, "Sem conexão. O servidor precisa ter o mod Celular instalado.", ui.th.muted);
            return;
        }
        if (ClientState.waAccount == null) { renderOnboarding(ui, x, y, w, h); return; }
        switch (view) {
            case CHATS -> renderChats(ui, x, y, w, h);
            case CONTACTS -> renderContacts(ui, x, y, w, h);
            case ADD -> renderAdd(ui, x, y, w, h);
            case CHAT -> renderChat(ui, x, y, w, h);
            case SETTINGS -> renderSettings(ui, x, y, w, h);
        }
    }

    private void renderOnboarding(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "WhatsApp", GREEN, false);
        ui.icon(icon(), x + (w - 40) / 2f, y + 34, 40);
        ui.textC("Bem-vindo!", x + w / 2f, y + 82, ui.th.text);
        Common.centeredNote(ui, x, y + 96, w, "Crie sua conta para conversar com outros jogadores. Ela fica ligada ao seu personagem.", ui.th.muted);
        ui.field(nameField, x + 10, y + 140, w - 20, 16, GREEN);
        if (ui.button(x + 10, y + 162, w - 20, 16, "Criar conta", GREEN, 0xFFFFFFFF)) createAccount();
        Common.banner(ui, x, y + 186, w, "wa.create");
    }

    private void tabs(Ui ui, int x, int y, int w, int h, int sel) {
        int clicked = Common.tabBar(ui, x, y + h - 16, w, new String[]{"Conversas", "Contatos", "Config."}, sel, GREEN,
                new int[]{ClientState.waUnread, 0, 0});
        if (clicked == 0) view = View.CHATS;
        if (clicked == 1) view = View.CONTACTS;
        if (clicked == 2) { view = View.SETTINGS; }
    }

    private void renderChats(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "WhatsApp", GREEN, false);
        List<Map<String, Object>> convs = ClientState.convs;
        int bodyY = y + Common.HEADER_H, bodyH = h - Common.HEADER_H - 16;
        if (convs.isEmpty()) {
            Common.centeredNote(ui, x, bodyY + 40, w, "Nenhuma conversa ainda. Vá em Contatos e adicione alguém pelo número ou nome.", ui.th.muted);
        } else {
            final int rowH = 26;
            ui.scrollArea("chats", x, bodyY, w, bodyH, convs.size() * rowH, false, () -> {
                for (int i = 0; i < convs.size(); i++) {
                    Map<String, Object> c = convs.get(i);
                    int ry = i * rowH;
                    String name = Json.str(c, "name", "?");
                    long unread = Json.num(c, "unread", 0);
                    if (ui.area(0, ry, w, rowH)) { openChat(Json.str(c, "handle", ""), name); return; }
                    Common.avatar(ui, 4, ry + 3, 20, name);
                    ui.text(ui.ellipsize(name, w - 62), 28, ry + 4, ui.th.text);
                    String last = Json.str(c, "last", "");
                    ui.text(ui.ellipsize(last.isEmpty() ? "Toque para conversar" : last, w - 50), 28, ry + 14, ui.th.muted);
                    long lt = Json.num(c, "lastTime", 0);
                    if (lt > 0) ui.textR(Ui.shortDate(lt), w - 4, ry + 4, unread > 0 ? GREEN : ui.th.muted);
                    if (unread > 0) {
                        String b = String.valueOf(Math.min(99, unread));
                        int bw = Math.max(10, ui.textW(b) + 5);
                        ui.rrect(w - 4 - bw, ry + 14, bw, 10, GREEN);
                        ui.text(b, w - 4 - bw + (bw - ui.textW(b)) / 2f, ry + 15, 0xFFFFFFFF);
                    }
                    ui.rect(28, ry + rowH - 1, w - 28, 1, ui.th.line);
                }
            });
        }
        tabs(ui, x, y, w, h, 0);
    }

    private void renderContacts(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "Contatos", GREEN, false);
        if (ui.button(x + w - 40, y + 4, 36, 14, "+ Novo", 0x33FFFFFF, 0xFFFFFFFF)) {
            view = View.ADD;
            ClientState.waSearch = new ArrayList<>();
        }
        List<Map<String, Object>> contacts = new ArrayList<>();
        for (Map<String, Object> c : ClientState.convs) if (Json.bool(c, "contact", false)) contacts.add(c);
        contacts.sort(Comparator.comparing(c -> Json.str(c, "name", "").toLowerCase()));
        int bodyY = y + Common.HEADER_H, bodyH = h - Common.HEADER_H - 16;
        if (contacts.isEmpty()) {
            Common.centeredNote(ui, x, bodyY + 40, w, "Sem contatos. Toque em \"+ Novo\" para procurar um jogador.", ui.th.muted);
        } else {
            final int rowH = 24;
            ui.scrollArea("contacts", x, bodyY, w, bodyH, contacts.size() * rowH, false, () -> {
                for (int i = 0; i < contacts.size(); i++) {
                    Map<String, Object> c = contacts.get(i);
                    int ry = i * rowH;
                    String name = Json.str(c, "name", "?");
                    if (ui.area(0, ry, w, rowH)) { openChat(Json.str(c, "handle", ""), name); return; }
                    Common.avatar(ui, 4, ry + 2, 20, name);
                    ui.text(ui.ellipsize(name, w - 34), 28, ry + 3, ui.th.text);
                    ui.text(Json.str(c, "handle", ""), 28, ry + 13, ui.th.muted);
                }
            });
        }
        Common.banner(ui, x, bodyY, w, "wa.remove");
        tabs(ui, x, y, w, h, 1);
    }

    private void renderAdd(Ui ui, int x, int y, int w, int h) {
        if (Common.header(ui, x, y, w, "Adicionar contato", GREEN, true)) { view = View.CONTACTS; return; }
        ui.field(searchField, x + 6, y + 28, w - 50, 16, GREEN);
        if (ui.button(x + w - 40, y + 28, 34, 16, "Buscar", GREEN, 0xFFFFFFFF)) search();
        ui.text("Só contas existentes podem ser adicionadas.", x + 6, y + 48, ui.th.muted);
        List<Map<String, Object>> res = ClientState.waSearch;
        int ry0 = y + 62;
        for (int i = 0; i < Math.min(res.size(), 9); i++) {
            Map<String, Object> r = res.get(i);
            int ry = ry0 + i * 24;
            String name = Json.str(r, "name", "?");
            Common.avatar(ui, x + 4, ry, 20, name);
            ui.text(ui.ellipsize(name, w - 78), x + 28, ry + 2, ui.th.text);
            ui.text(Json.str(r, "handle", ""), x + 28, ry + 12, ui.th.muted);
            if (Json.bool(r, "contact", false)) {
                if (ui.button(x + w - 44, ry + 2, 40, 14, "Conversar", ui.th.surface2, ui.th.text)) openChat(Json.str(r, "handle", ""), name);
            } else if (ui.button(x + w - 44, ry + 2, 40, 14, "Adicionar", GREEN, 0xFFFFFFFF)) {
                ClientNet.send("wa.add", "handle", Json.str(r, "handle", ""));
                r.put("contact", true);
            }
        }
        Common.banner(ui, x, y + h - 30, w, "wa.add", "wa.search");
    }

    private void renderChat(Ui ui, int x, int y, int w, int h) {
        if (Common.header(ui, x, y, w, chatName, GREEN, true)) { onBack(); return; }
        boolean isContact = false;
        for (Map<String, Object> c : ClientState.convs) if (Json.str(c, "handle", "").equals(chatHandle)) isContact = Json.bool(c, "contact", false);
        if (ui.button(x + w - 36, y + 4, 32, 14, isContact ? "Remover" : "+ Salvar", 0x33FFFFFF, 0xFFFFFFFF)) {
            ClientNet.send(isContact ? "wa.remove" : "wa.add", "handle", chatHandle);
        }
        int inputH = 22;
        int bodyY = y + Common.HEADER_H, bodyH = h - Common.HEADER_H - inputH;
        List<Map<String, Object>> msgs = ClientState.histories.get(chatHandle);
        if (msgs == null) {
            Common.centeredNote(ui, x, bodyY + 50, w, "Carregando conversa...", ui.th.muted);
        } else if (msgs.isEmpty()) {
            Common.centeredNote(ui, x, bodyY + 50, w, "Nenhuma mensagem. Diga olá!", ui.th.muted);
        } else {
            final int maxW = 98;
            int total = 4;
            List<List<String>> wrapped = new ArrayList<>();
            for (Map<String, Object> m : msgs) {
                long id = Json.num(m, "id", 0);
                List<String> lines = wrapCache.computeIfAbsent(id, k -> ui.wrap(Json.str(m, "text", ""), maxW));
                wrapped.add(lines);
                total += lines.size() * 9 + 6 + 10 + 3;
            }
            final int totalH = total;
            ui.scrollArea("chat", x, bodyY, w, bodyH, totalH, true, () -> {
                int cy = 4;
                for (int i = 0; i < msgs.size(); i++) {
                    Map<String, Object> m = msgs.get(i);
                    List<String> lines = wrapped.get(i);
                    boolean mine = Json.bool(m, "mine", false);
                    int tw = 0;
                    for (String l : lines) tw = Math.max(tw, ui.textW(l));
                    tw = Math.max(tw, ui.textW(Ui.time(Json.num(m, "time", 0))));
                    int bw = tw + 8, bh = lines.size() * 9 + 6 + 9;
                    int bx = mine ? w - bw - 5 : 5;
                    boolean share = "share".equals(Json.str(m, "kind", "text"));
                    int bg = mine ? 0xFF1B6B43 : ui.th.surface2;
                    ui.rrect(bx, cy, bw, bh, bg);
                    if (share) ui.rect(bx + 1, cy + 1, 2, bh - 2, 0xFF7A6FF0);
                    for (int k = 0; k < lines.size(); k++) ui.text(lines.get(k), bx + 4, cy + 3 + k * 9, mine ? 0xFFFFFFFF : ui.th.text);
                    ui.textR(Ui.time(Json.num(m, "time", 0)), bx + bw - 3, cy + bh - 9, mine ? 0xFFBFE8D0 : ui.th.muted);
                    cy += bh + 3;
                }
            });
        }
        Common.banner(ui, x, bodyY + 2, w, "wa.send", "wa.add", "wa.remove");
        // barra de digitação
        int iy = y + h - inputH;
        ui.rect(x, iy, w, inputH, ui.th.surface);
        ui.field(msgField, x + 4, iy + 3, w - 34, 16, GREEN);
        if (ui.button(x + w - 28, iy + 3, 24, 16, "Enviar", GREEN, 0xFFFFFFFF)) send();
    }

    private void renderSettings(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "Configurações", GREEN, false);
        Map<String, Object> acc = ClientState.waAccount;
        int cy = y + 28;
        Common.avatar(ui, x + 6, cy, 24, Json.str(acc, "name", "?"));
        ui.text(ui.ellipsize(Json.str(acc, "name", "?"), w - 40), x + 36, cy + 2, ui.th.text);
        ui.text("Número: " + Json.str(acc, "handle", ""), x + 36, cy + 13, ui.th.muted);
        cy += 34;
        ui.text("Alterar nome", x + 6, cy, ui.th.muted);
        ui.field(renameField, x + 6, cy + 11, w - 50, 16, GREEN);
        if (ui.button(x + w - 40, cy + 11, 34, 16, "Salvar", GREEN, 0xFFFFFFFF)) rename();
        cy += 36;
        ui.text("Privacidade", x + 6, cy, ui.th.muted);
        boolean strangers = Json.bool(acc, "strangers", true), searchable = Json.bool(acc, "searchable", true);
        ui.text("Receber de desconhecidos", x + 6, cy + 14, ui.th.text);
        if (ui.toggle(x + w - 28, cy + 13, strangers, GREEN)) ClientNet.send("wa.privacy", "strangers", !strangers, "searchable", searchable);
        ui.text("Aparecer nas buscas", x + 6, cy + 30, ui.th.text);
        if (ui.toggle(x + w - 28, cy + 29, searchable, GREEN)) ClientNet.send("wa.privacy", "strangers", strangers, "searchable", !searchable);
        Common.banner(ui, x, y + h - 52, w, "wa.rename", "wa.privacy");
        tabs(ui, x, y, w, h, 2);
    }
}
