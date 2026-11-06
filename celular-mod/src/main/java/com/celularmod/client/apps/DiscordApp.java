package com.celularmod.client.apps;

import com.celularmod.client.App;
import com.celularmod.client.ClientState;
import com.celularmod.client.ui.Ui;
import com.celularmod.core.Res;
import com.celularmod.core.Text;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

/** Abre o convite do Discord configurado pelo administrador (nunca um link inventado). */
public final class DiscordApp extends App {
    private static final int BLURPLE = 0xFF5865F2;
    private String status = "";

    @Override public String id() { return "discord"; }
    @Override public String label() { return "Discord"; }
    @Override public ResourceLocation icon() { return ClientState.icon("discord"); }
    @Override public int accent() { return BLURPLE; }

    @Override public void onOpen() { status = ""; }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "Discord", BLURPLE, false);
        ui.icon(icon(), x + (w - 48) / 2f, y + 40, 48);
        ui.textC("Comunidade do servidor", x + w / 2f, y + 98, ui.th.text);
        String url = ClientState.discordUrl;
        if (!ClientState.hello) {
            Common.centeredNote(ui, x, y + 118, w, "Sem conexão com o servidor.", ui.th.muted);
            return;
        }
        if (url.isEmpty()) {
            Common.centeredNote(ui, x, y + 118, w, "O administrador ainda não configurou o convite.", ui.th.muted);
            if (ClientState.admin) Common.centeredNote(ui, x, y + 150, w, "Use: /celular admin discord <link>", ui.th.text);
            return;
        }
        ui.textC(ui.ellipsize(url, w - 12), x + w / 2f, y + 114, ui.th.muted);
        if (ui.button(x + 14, y + 134, w - 28, 20, "Abrir Discord", BLURPLE, 0xFFFFFFFF)) {
            Res<String> ok = Text.discordUrl(url);   // revalida no cliente antes de abrir qualquer coisa
            if (ok.ok && !ok.value.isEmpty()) {
                Util.getPlatform().openUri(ok.value);
                status = "Abrindo o link... O Minecraft continua aberto.";
            } else status = "Link inválido.";
        }
        if (!status.isEmpty()) Common.centeredNote(ui, x, y + 162, w, status, ui.th.muted);
        Common.centeredNote(ui, x, y + 190, w, "Se o Discord não estiver instalado, o link abre no navegador.", ui.th.muted);
    }
}
