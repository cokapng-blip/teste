package com.celularmod.client.apps;

import com.celularmod.client.App;
import com.celularmod.client.ClientNet;
import com.celularmod.client.ClientState;
import com.celularmod.client.ui.Ui;
import com.celularmod.core.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Informações REAIS do servidor atual (enviadas pelo servidor; nada é inventado). */
public final class MinecraftApp extends App {
    private static final int GREEN = 0xFF4C9A2A;
    private int ticks;

    @Override public String id() { return "minecraft"; }
    @Override public String label() { return "Minecraft"; }
    @Override public ResourceLocation icon() { return ClientState.icon("minecraft"); }
    @Override public int accent() { return GREEN; }

    @Override
    public void onOpen() { ticks = 0; ClientNet.send("mc.info"); }

    @Override
    public void tick() { if (++ticks % 100 == 0) ClientNet.send("mc.info"); }

    private static String difficulty(String k) {
        return switch (k) { case "peaceful" -> "Pacífico"; case "easy" -> "Fácil"; case "normal" -> "Normal"; case "hard" -> "Difícil"; default -> k; };
    }

    private static String gamemode(String k) {
        return switch (k) { case "survival" -> "Sobrevivência"; case "creative" -> "Criativo"; case "adventure" -> "Aventura"; case "spectator" -> "Espectador"; default -> k; };
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "Servidor", GREEN, false);
        int by = y + Common.HEADER_H;
        Map<String, Object> m = ClientState.mcInfo;
        if (!ClientState.hello) {
            Common.centeredNote(ui, x, by + 50, w, "Sem conexão com o mod no servidor. As informações só existem quando o servidor tem o mod Celular instalado.", ui.th.muted);
            return;
        }
        if (m == null) { Common.centeredNote(ui, x, by + 60, w, "Carregando informações...", ui.th.muted); return; }
        List<Map<String, Object>> players = ClientState.mapList(m.get("players"));
        final int rowH = 10;
        int contentH = 170 + players.size() * 12;
        ui.scrollArea("mc", x, by, w, h - Common.HEADER_H, contentH, false, () -> {
            int cy = 4;
            cy = card(ui, w, cy, "Servidor", new String[][]{
                    {"Nome", Json.str(m, "name", "?")},
                    {"Versão", Json.str(m, "version", "?")},
                    {"Tipo", Json.bool(m, "dedicated", false) ? "Dedicado" : "Mundo local"}});
            cy = card(ui, w, cy, "Você", new String[][]{
                    {"Jogador", Json.str(m, "me", "?")},
                    {"Ping", Json.num(m, "ping", 0) + " ms"},
                    {"Modo", gamemode(Json.str(m, "gamemode", "?"))}});
            long tod = Json.num(m, "timeOfDay", 0);
            int hour = (int) ((tod / 1000 + 6) % 24), min = (int) ((tod % 1000) * 60 / 1000);
            String weather = Json.bool(m, "thundering", false) ? "Tempestade" : Json.bool(m, "raining", false) ? "Chuva" : "Limpo";
            cy = card(ui, w, cy, "Mundo", new String[][]{
                    {"Dimensão", Json.str(m, "dimension", "?").replace("minecraft:", "")},
                    {"Dia", String.valueOf(Json.num(m, "day", 0) + 1)},
                    {"Hora", String.format("%02d:%02d", hour, min)},
                    {"Clima", weather},
                    {"Dificuldade", difficulty(Json.str(m, "difficulty", "?"))}});
            ui.text("Online: " + Json.num(m, "online", 0) + " / " + Json.num(m, "max", 0), 6, cy, ui.th.text);
            cy += 12;
            for (Map<String, Object> p : players) {
                boolean me = Json.bool(p, "me", false);
                ui.rect(4, cy, 3, 8, ui.th.ok);
                ui.text(ui.ellipsize(Json.str(p, "name", "?"), w - 60), 11, cy, me ? 0xFFFFD866 : ui.th.text);
                ui.textR(Json.num(p, "ping", 0) + " ms", w - 6, cy, ui.th.muted);
                cy += 12;
            }
        });
        long age = (System.currentTimeMillis() - ClientState.mcInfoAt) / 1000;
        ui.textR("atualizado há " + age + "s", x + w - 4, y + h - 10, ui.th.muted);
    }

    private int card(Ui ui, int w, int cy, String title, String[][] rows) {
        int ch = 14 + rows.length * 10;
        ui.rrect(4, cy, w - 8, ch, ui.th.surface);
        ui.text(title, 8, cy + 3, GREEN);
        for (int i = 0; i < rows.length; i++) {
            ui.text(rows[i][0], 8, cy + 14 + i * 10, ui.th.muted);
            ui.textR(ui.ellipsize(rows[i][1], w - 70), w - 8, cy + 14 + i * 10, ui.th.text);
        }
        return cy + ch + 4;
    }
}
