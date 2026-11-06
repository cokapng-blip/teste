package com.celularmod.client;

import com.celularmod.CelularMod;
import com.celularmod.client.apps.*;
import com.celularmod.client.ui.Theme;
import com.celularmod.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Desenha o celular 2D (moldura de pixel art + tela) e guarda o estado de navegação.
 * É usado tanto pelo HUD (celular bloqueado, só exibição) quanto pela {@link PhoneScreen} (desbloqueado, interativo).
 * A tela do aparelho é desenhada POR BAIXO da moldura, cuja área da tela é transparente na textura.
 */
public final class PhoneRenderer {
    private PhoneRenderer() {}

    // geometria da textura (50x92 px nativos, 4x): moldura 200x368, tela em (28,44) com 144x268
    public static final int FW = 200, FH = 368, SX = 28, SY = 44;
    public static final int STATUS_H = 12, NAV_H = 16;
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(CelularMod.MODID, "textures/gui/phone_frame.png");

    public static final Ui ui = new Ui();
    public static final List<App> APPS = List.of(new WhatsAppApp(), new MinecraftApp(), new DiscordApp(),
            new GoogleApp(), new SettingsApp(), new ThreadsApp());

    // estado
    private static boolean unlocked;       // PhoneScreen aberto
    private static boolean holding;        // item na mão
    private static float show, unlock, appFade = 1f;
    private static App current;            // null = tela inicial
    private static long lastFrame = System.nanoTime();

    public static boolean isOpen() { return unlocked; }

    public static App currentApp() { return current; }

    public static void setHolding(boolean h) { holding = h; }

    // ------------------------------------------------------------------ geometria
    /** Retorna {escala, x0, y0} do celular na tela, no estado de animação atual. */
    public static float[] layout() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float fit = Math.min((h - 8f) / FH, (w - 8f) / FW);
        float user = ClientSettings.uiScale / 100f;
        boolean big = current != null && current.wantsBigPhone();
        float full = big ? fit : Math.min(user, fit);
        float lock = full * 0.55f;
        float s = lock + (full - lock) * ease(unlock);
        float x0 = w - FW * s - 6;
        float y0 = h - FH * s - 6 + (1f - ease(show)) * (FH * s + 10);
        return new float[]{s, x0, y0};
    }

    public static float ease(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    /** Converte a posição do mouse (GUI) para a tela do celular (0..144, 0..268). */
    public static double[] toLocal(double mx, double my) {
        float[] l = layout();
        return new double[]{(mx - (l[1] + SX * l[0])) / l[0], (my - (l[2] + SY * l[0])) / l[0]};
    }

    public static boolean insideScreen(double lx, double ly) { return lx >= 0 && ly >= 0 && lx < Ui.W && ly < Ui.H; }

    // ------------------------------------------------------------------ navegação
    public static void onScreenOpened() {
        unlocked = true;
        current = null;
        appFade = 1f;
        ClientState.openChat = null;
    }

    public static void onScreenClosed() {
        unlocked = false;
        if (current != null) current.onClose();
        current = null;
        ClientState.openChat = null;
        ui.focus = null;
    }

    public static void openApp(App a) {
        if (current != null) current.onClose();
        current = a;
        appFade = 0f;
        a.onOpen();
    }

    public static void goHome() {
        if (current != null) { current.onClose(); current = null; appFade = 0f; }
        ui.focus = null;
    }

    public static void back() {
        if (current == null) return;
        ui.focus = null;
        if (!current.onBack()) goHome();
    }

    public static void tick() {
        if (current != null) current.tick();
    }

    // ------------------------------------------------------------------ desenho
    public static void draw(GuiGraphics g, Font font, int mouseX, int mouseY, boolean interactive) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float k = 1f - (float) Math.exp(-14 * dt);
        show += ((holding || unlocked ? 1f : 0f) - show) * k;
        unlock += ((unlocked ? 1f : 0f) - unlock) * k;
        appFade += (1f - appFade) * (1f - (float) Math.exp(-18 * dt));
        if (show < 0.01f && !holding && !unlocked) return;

        float[] l = layout();
        float s = l[0], x0 = l[1], y0 = l[2];
        float ox = x0 + SX * s, oy = y0 + SY * s;

        if (interactive) {
            double[] m = toLocal(mouseX, mouseY);
            ui.moved(m[0], m[1]);
        } else {
            ui.moved(-1000, -1000);
        }
        ui.begin(g, font, s, ox, oy);
        ui.pushClip(0, 0, Ui.W, Ui.H);
        Theme th = ui.th;
        if (unlock < 0.55f || !unlocked) {
            drawLock(ui);
        } else if (current == null) {
            drawHome(ui);
        } else {
            ui.rect(0, 0, Ui.W, Ui.H, th.bg);
            current.render(ui, 0, STATUS_H, Ui.W, Ui.H - STATUS_H - NAV_H);
            if (appFade < 0.98f) ui.rect(0, 0, Ui.W, Ui.H, ((int) ((1f - appFade) * 255) << 24) | (th.bg & 0xFFFFFF));
        }
        if (unlocked && unlock >= 0.55f) drawChrome(ui);
        ui.popClip();
        ui.end();

        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().scale(s, s, 1f);
        g.blit(FRAME, 0, 0, FW, FH, 0, 0, 50, 92, 50, 92);
        g.pose().popPose();
    }

    private static void drawWallpaper(Ui ui) {
        int[] w = Theme.WALLPAPERS[Math.max(0, Math.min(3, ClientSettings.wallpaper))];
        ui.gradient(0, 0, Ui.W, Ui.H, w[0], w[1]);
    }

    private static void drawLock(Ui ui) {
        drawWallpaper(ui);
        String t = Ui.time(System.currentTimeMillis());
        ui.textScaledC(t, Ui.W / 2f, 40, 0xFFFFFFFF, 3.2f);
        String date = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", new Locale("pt", "BR")).format(LocalDate.now());
        ui.textC(ui.ellipsize(date, Ui.W - 8), Ui.W / 2f, 78, 0xFFDDE3F0);
        int y = 110;
        if (ClientState.waUnread > 0) {
            ui.rrect(10, y, Ui.W - 20, 24, 0x66000000);
            ui.icon(ClientState.icon("whatsapp"), 13, y + 4, 16);
            ui.text("WhatsApp", 34, y + 4, 0xFFFFFFFF);
            ui.text(ClientState.waUnread + (ClientState.waUnread == 1 ? " mensagem nova" : " mensagens novas"), 34, y + 14, 0xFFCCD3E0);
        }
        if (unlocked) ui.textC("Desbloqueando...", Ui.W / 2f, Ui.H - 30, 0xFFFFFFFF);
        else {
            ui.textC("Botão direito:", Ui.W / 2f, Ui.H - 38, 0xFFFFFFFF);
            ui.textC("desbloquear", Ui.W / 2f, Ui.H - 28, 0xFFFFFFFF);
        }
    }

    private static void drawHome(Ui ui) {
        drawWallpaper(ui);
        ui.textScaledC(Ui.time(System.currentTimeMillis()), Ui.W / 2f, 22, 0xFFFFFFFF, 2.2f);
        int cols = 3, cellW = Ui.W / cols, y0 = 62, cellH = 66;
        for (int i = 0; i < APPS.size(); i++) {
            App a = APPS.get(i);
            int cx = (i % cols) * cellW, cy = y0 + (i / cols) * cellH;
            boolean hov = ui.hover(cx, cy, cellW, cellH - 6);
            float iconY = cy + (hov ? 1 : 3);
            ui.icon(a.icon(), cx + (cellW - 32) / 2f, iconY, 32);
            int badge = a.id().equals("whatsapp") ? ClientState.waUnread : 0;
            if (badge > 0) {
                String b = badge > 99 ? "99+" : String.valueOf(badge);
                int bw = Math.max(9, ui.textW(b) + 4);
                float bx = cx + (cellW + 32) / 2f - bw + 3;
                ui.rrect(bx, iconY - 3, bw, 10, 0xFFE5484D);
                ui.text(b, bx + (bw - ui.textW(b)) / 2f, iconY - 2, 0xFFFFFFFF);
            }
            ui.textScaledC(a.label(), cx + cellW / 2f, cy + 40, 0xFFFFFFFF, 0.75f);
            if (ui.click(cx, cy, cellW, cellH - 6)) { Sfx.click(); openApp(a); return; }
        }
    }

    /** Barra de status (relógio) e barra de navegação (voltar / início / fechar). */
    private static void drawChrome(Ui ui) {
        Theme th = ui.th;
        boolean home = current == null;
        int bar = home ? 0x55000000 : th.surface;
        int fg = home ? 0xFFFFFFFF : th.text;
        ui.rect(0, 0, Ui.W, STATUS_H, bar);
        ui.text(Ui.time(System.currentTimeMillis()), 4, 2, fg);
        if (!ClientState.hello) ui.textR("offline", Ui.W - 4, 2, 0xFFE5A04D);
        else ui.textR("Celular", Ui.W - 4, 2, home ? 0xFFCCD3E0 : th.muted);

        int y = Ui.H - NAV_H;
        ui.rect(0, y, Ui.W, NAV_H, home ? 0x55000000 : th.surface);
        float third = Ui.W / 3f;
        if (ui.area(0, y, third, NAV_H) && !home) back();
        ui.textC("<", third / 2, y + 4, home ? 0x66FFFFFF : fg);
        if (ui.area(third, y, third, NAV_H)) goHome();
        ui.rrect(third + third / 2 - 4, y + 4, 8, 8, fg);
        if (ui.area(2 * third, y, third, NAV_H)) closePhone();
        ui.textC("x", 2 * third + third / 2, y + 4, fg);
    }

    public static void closePhone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneScreen) mc.setScreen(null);
    }
}
