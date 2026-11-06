package com.celularmod.client.apps;

import com.celularmod.client.App;
import com.celularmod.client.ClientSettings;
import com.celularmod.client.ClientState;
import com.celularmod.client.ui.Theme;
import com.celularmod.client.ui.Ui;
import com.celularmod.core.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Configurações LOCAIS (ficam em config/celular-client.json) + painel das configurações GLOBAIS do servidor (somente leitura). */
public final class SettingsApp extends App {
    private static final int BLUE = 0xFF3B82F6;
    private boolean dirty, confirmReset;

    @Override public String id() { return "settings"; }
    @Override public String label() { return "Configurações"; }
    @Override public ResourceLocation icon() { return ClientState.icon("configuracoes"); }
    @Override public int accent() { return BLUE; }

    @Override public void onOpen() { confirmReset = false; }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "Configurações", BLUE, false);
        if (dirty && !ui.mouseDown) { ClientSettings.save(); dirty = false; }
        ui.scrollArea("settings", x, y + Common.HEADER_H, w, h - Common.HEADER_H, 376, false, () -> {
            int cy = 4;
            cy = section(ui, "Sons", cy);
            ui.text("Volume: " + ClientSettings.volume + "%", 8, cy, ui.th.text);
            float v = ui.slider("vol", 8, cy + 11, w - 16, ClientSettings.volume / 100f, BLUE);
            int nv = Math.round(v * 100);
            if (nv != ClientSettings.volume) { ClientSettings.volume = nv; dirty = true; }
            cy += 28;
            ui.text("Sons de notificação", 8, cy, ui.th.text);
            if (ui.toggle(w - 30, cy - 1, ClientSettings.sounds, BLUE)) { ClientSettings.sounds = !ClientSettings.sounds; dirty = true; }
            cy += 18;

            cy = section(ui, "Notificações e privacidade", cy);
            ui.text("Mostrar notificações", 8, cy, ui.th.text);
            if (ui.toggle(w - 30, cy - 1, ClientSettings.notifications, BLUE)) { ClientSettings.notifications = !ClientSettings.notifications; dirty = true; }
            cy += 16;
            ui.text("Ocultar texto no aviso", 8, cy, ui.th.text);
            if (ui.toggle(w - 30, cy - 1, ClientSettings.hidePreview, BLUE)) { ClientSettings.hidePreview = !ClientSettings.hidePreview; dirty = true; }
            cy += 18;

            cy = section(ui, "Aparência", cy);
            ui.text("Tema escuro", 8, cy, ui.th.text);
            if (ui.toggle(w - 30, cy - 1, ClientSettings.darkTheme, BLUE)) { ClientSettings.darkTheme = !ClientSettings.darkTheme; dirty = true; }
            cy += 16;
            ui.text("Papel de parede", 8, cy, ui.th.text);
            for (int i = 0; i < 4; i++) {
                int sx = w - 8 - (4 - i) * 14;
                int[] wp = Theme.WALLPAPERS[i];
                ui.rect(sx - 1, cy - 2, 12, 12, i == ClientSettings.wallpaper ? 0xFFFFFFFF : ui.th.line);
                ui.gradient(sx, cy - 1, 10, 10, wp[0], wp[1]);
                if (ui.click(sx, cy - 1, 10, 10)) { ClientSettings.wallpaper = i; dirty = true; }
            }
            cy += 18;
            ui.text("Tamanho: " + ClientSettings.uiScale + "%", 8, cy, ui.th.text);
            float sv = ui.slider("scale", 8, cy + 11, w - 16, (ClientSettings.uiScale - 50) / 100f, BLUE);
            int ns = 50 + Math.round(sv * 100);
            if (ns != ClientSettings.uiScale) { ClientSettings.uiScale = ns; dirty = true; }
            cy += 30;

            cy = section(ui, "Contas", cy);
            Map<String, Object> wa = ClientState.waAccount, th = ClientState.thAccount;
            ui.text("WhatsApp: " + (wa == null ? "sem conta" : Json.str(wa, "name", "?") + " (" + Json.str(wa, "handle", "") + ")"), 8, cy, ui.th.text);
            cy += 11;
            ui.text("Threads: " + (th == null ? "sem conta" : Json.str(th, "name", "?") + " (@" + Json.str(th, "user", "") + ")"), 8, cy, ui.th.text);
            cy += 11;
            ui.text("As contas ficam presas ao seu jogador.", 8, cy, ui.th.muted);
            cy += 16;

            cy = section(ui, "Servidor (global)", cy);
            ui.text("Só administradores alteram.", 8, cy, ui.th.muted);
            cy += 11;
            ui.text("Discord: " + (ClientState.discordUrl.isEmpty() ? "não configurado" : "configurado"), 8, cy, ui.th.text);
            cy += 11;
            ui.text("Sites c/ recursos externos: " + (ClientState.allowExternal ? "sim" : "não"), 8, cy, ui.th.text);
            cy += 11;
            ui.text("Você é admin: " + (ClientState.admin ? "sim" : "não"), 8, cy, ui.th.text);
            cy += 18;

            if (ui.button(8, cy, w - 16, 16, confirmReset ? "Toque de novo para confirmar" : "Restaurar padrões",
                    confirmReset ? ui.th.danger : ui.th.surface2, confirmReset ? 0xFFFFFFFF : ui.th.text)) {
                if (confirmReset) { ClientSettings.reset(); confirmReset = false; } else confirmReset = true;
            }
        });
    }

    private int section(Ui ui, String title, int cy) {
        ui.rect(4, cy + 4, 140 - 8, 1, ui.th.line);
        ui.text(title, 8, cy + 8, BLUE);
        return cy + 22;
    }
}
