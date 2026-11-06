package com.celularmod.client;

import com.celularmod.core.Json;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Configurações LOCAIS do jogador (não afetam outros). Arquivo: config/celular-client.json */
public final class ClientSettings {
    public static int volume = 70;               // 0..100
    public static boolean sounds = true;         // sons de notificação
    public static boolean notifications = true;  // avisos na tela
    public static boolean hidePreview = false;   // privacidade: não mostra o texto da mensagem no aviso
    public static int uiScale = 85;              // 50..150 (%)
    public static boolean darkTheme = true;
    public static int wallpaper = 0;             // 0..3

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("celular-client.json"); }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Map<String, Object> m = Json.asMap(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
            volume = clamp((int) Json.num(m, "volume", volume), 0, 100);
            sounds = Json.bool(m, "sounds", sounds);
            notifications = Json.bool(m, "notifications", notifications);
            hidePreview = Json.bool(m, "hidePreview", hidePreview);
            uiScale = clamp((int) Json.num(m, "uiScale", uiScale), 50, 150);
            darkTheme = Json.bool(m, "darkTheme", darkTheme);
            wallpaper = clamp((int) Json.num(m, "wallpaper", wallpaper), 0, 3);
        } catch (IOException | IllegalArgumentException ignored) {
            // arquivo ilegível: usa os padrões
        }
    }

    public static void save() {
        try {
            Files.writeString(file(), Json.stringify(Json.obj("volume", volume, "sounds", sounds, "notifications", notifications,
                    "hidePreview", hidePreview, "uiScale", uiScale, "darkTheme", darkTheme, "wallpaper", wallpaper)), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    public static void reset() {
        volume = 70; sounds = true; notifications = true; hidePreview = false; uiScale = 85; darkTheme = true; wallpaper = 0;
        save();
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
