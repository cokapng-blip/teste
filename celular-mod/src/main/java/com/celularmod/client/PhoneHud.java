package com.celularmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Camada de HUD: o celular BLOQUEADO no canto inferior direito (enquanto segurado) e os avisos. */
public final class PhoneHud {
    private PhoneHud() {}

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (!(mc.screen instanceof PhoneScreen)) PhoneRenderer.draw(g, mc.font, 0, 0, false);
        drawToasts(g, mc);
    }

    private static void drawToasts(GuiGraphics g, Minecraft mc) {
        List<Toasts.Toast> list = Toasts.ACTIVE;
        if (list.isEmpty()) return;
        int w = mc.getWindow().getGuiScaledWidth();
        long now = System.currentTimeMillis();
        int y = 6;
        for (Toasts.Toast t : list) {
            long age = now - t.created;
            float in = Math.min(1f, age / 220f);
            float out = Math.min(1f, (Toasts.LIFETIME_MS - age) / 300f);
            float e = PhoneRenderer.ease(Math.min(in, out));
            int tw = 132, th = 26;
            int x = (int) (w - tw - 6 + (1f - e) * (tw + 10));
            g.fill(x, y, x + tw, y + th, 0xEE1B1D24);
            g.fill(x, y, x + 2, y + th, 0xFF3DD16B);
            g.blit(t.icon, x + 5, y + 5, 16, 16, 0, 0, 32, 32, 32, 32);
            String title = mc.font.plainSubstrByWidth(t.title, tw - 30);
            String text = mc.font.plainSubstrByWidth(t.text, tw - 30);
            g.drawString(mc.font, title, x + 26, y + 4, 0xFFFFFFFF, false);
            g.drawString(mc.font, text, x + 26, y + 15, 0xFFB8BDCC, false);
            y += th + 4;
        }
    }
}
