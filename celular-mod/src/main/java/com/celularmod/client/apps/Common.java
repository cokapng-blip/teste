package com.celularmod.client.apps;

import com.celularmod.client.ClientState;
import com.celularmod.client.ui.Ui;

/** Pedaços de interface compartilhados pelos apps. */
final class Common {
    private Common() {}

    static final int HEADER_H = 22;

    /** Cabeçalho colorido com título. Retorna true se o botão "voltar" foi clicado. */
    static boolean header(Ui ui, int x, int y, int w, String title, int accent, boolean back) {
        ui.rect(x, y, w, HEADER_H, accent);
        boolean clicked = false;
        int tx = x + 6;
        if (back) {
            clicked = ui.area(x, y, 18, HEADER_H);
            ui.text("<", x + 7, y + 7, 0xFFFFFFFF);
            tx = x + 20;
        }
        ui.textBold(ui.ellipsize(title, w - (tx - x) - 4), tx, y + 7, 0xFFFFFFFF);
        return clicked;
    }

    static int hashColor(String s) {
        int[] pal = {0xFF5B8DEF, 0xFFE0773B, 0xFF9B59D0, 0xFF2BB673, 0xFFD6477A, 0xFF3AA3C9, 0xFFB8A12D, 0xFF7A6FF0};
        return pal[Math.floorMod(s.hashCode(), pal.length)];
    }

    static void avatar(Ui ui, float x, float y, int size, String name) {
        ui.rrect(x, y, size, size, hashColor(name));
        String ini = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        ui.text(ini, x + (size - ui.textW(ini)) / 2f, y + (size - 8) / 2f, 0xFFFFFFFF);
    }

    /** Barra de abas inferior. Retorna o índice clicado ou -1. */
    static int tabBar(Ui ui, int x, int y, int w, String[] labels, int selected, int accent, int[] badges) {
        ui.rect(x, y, w, 16, ui.th.surface);
        ui.rect(x, y, w, 1, ui.th.line);
        int clicked = -1;
        float tw = w / (float) labels.length;
        for (int i = 0; i < labels.length; i++) {
            boolean sel = i == selected;
            if (ui.area(x + i * tw, y + 1, tw, 15)) clicked = i;
            ui.textC(labels[i], x + i * tw + tw / 2, y + 5, sel ? accent : ui.th.muted);
            if (sel) ui.rect(x + i * tw + 6, y + 14, tw - 12, 2, accent);
            if (badges != null && badges[i] > 0) {
                String b = String.valueOf(Math.min(99, badges[i]));
                float bx = x + i * tw + tw / 2 + ui.textW(labels[i]) / 2f + 1;
                ui.rrect(bx, y + 2, ui.textW(b) + 4, 9, 0xFFE5484D);
                ui.text(b, bx + 2, y + 3, 0xFFFFFFFF);
            }
        }
        return clicked;
    }

    /** Resposta recente do servidor (erro em vermelho / sucesso em verde) para uma das ações dadas. */
    static void banner(Ui ui, int x, int y, int w, String... actions) {
        if (System.currentTimeMillis() - ClientState.lastResultAt > 4000 || ClientState.lastMsg.isEmpty()) return;
        boolean match = actions.length == 0;
        for (String a : actions) if (a.equals(ClientState.lastAction)) match = true;
        if (!match) return;
        java.util.List<String> lines = ui.wrap(ClientState.lastMsg, w - 8);
        int h = lines.size() * 9 + 6;
        ui.rrect(x + 2, y, w - 4, h, ClientState.lastOk ? 0xEE1F7A3F : 0xEEB3262B);
        for (int i = 0; i < lines.size(); i++) ui.text(lines.get(i), x + 6, y + 3 + i * 9, 0xFFFFFFFF);
    }

    static void centeredNote(Ui ui, int x, int y, int w, String text, int color) {
        java.util.List<String> lines = ui.wrap(text, w - 16);
        for (int i = 0; i < lines.size(); i++) ui.textC(lines.get(i), x + w / 2f, y + i * 10, color);
    }
}
