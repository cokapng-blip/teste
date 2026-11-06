package com.celularmod.client.ui;

import com.celularmod.client.ClientSettings;

/** Cores da interface (ARGB). Claro/escuro vêm das configurações locais. */
public final class Theme {
    public int bg, surface, surface2, text, muted, line, hover, danger = 0xFFE5484D, ok = 0xFF3DD16B;

    private static final Theme DARK = new Theme(0xFF14151A, 0xFF1F2128, 0xFF2A2D36, 0xFFECEDF2, 0xFF9094A3, 0xFF33363F, 0x22FFFFFF);
    private static final Theme LIGHT = new Theme(0xFFF2F4F8, 0xFFFFFFFF, 0xFFE6E9F0, 0xFF16181F, 0xFF667085, 0xFFD3D8E2, 0x18000000);

    private Theme(int bg, int surface, int surface2, int text, int muted, int line, int hover) {
        this.bg = bg; this.surface = surface; this.surface2 = surface2; this.text = text; this.muted = muted; this.line = line; this.hover = hover;
    }

    public static Theme current() { return ClientSettings.darkTheme ? DARK : LIGHT; }

    /** Papéis de parede (gradiente de cima para baixo). */
    public static final int[][] WALLPAPERS = {
            {0xFF1B2A49, 0xFF4A2C6B}, {0xFF0E5E4F, 0xFF1B2A29}, {0xFFB5452B, 0xFF2B1B3D}, {0xFF2F3340, 0xFF0F1014}
    };
}
