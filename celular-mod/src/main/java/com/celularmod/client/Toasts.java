package com.celularmod.client;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Avisos (notificações) mostrados no canto da tela por alguns segundos. */
public final class Toasts {
    public static final class Toast {
        public final ResourceLocation icon; public final String title, text; public final long created;
        Toast(ResourceLocation i, String t, String x) { icon = i; title = t; text = x; created = System.currentTimeMillis(); }
    }

    public static final long LIFETIME_MS = 4500;
    public static final List<Toast> ACTIVE = new ArrayList<>();

    public static void push(ResourceLocation icon, String title, String text) {
        if (!ClientSettings.notifications) return;
        ACTIVE.add(new Toast(icon, title, text));
        while (ACTIVE.size() > 3) ACTIVE.remove(0);
    }

    public static void prune() {
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(t -> now - t.created > LIFETIME_MS);
    }
}
