package com.celularmod.client;

import com.celularmod.client.ui.Ui;
import net.minecraft.resources.ResourceLocation;

/** Um aplicativo do celular. Desenha na área (x,y,w,h) da tela e trata a navegação interna. */
public abstract class App {
    public abstract String id();

    public abstract String label();

    public abstract ResourceLocation icon();

    /** Cor de destaque do app (ARGB). */
    public abstract int accent();

    public void onOpen() {}

    public void onClose() {}

    public void tick() {}

    public abstract void render(Ui ui, int x, int y, int w, int h);

    /** Voltar uma tela dentro do app. Retorna false se já está na raiz (aí o celular volta ao início). */
    public boolean onBack() { return false; }

    // ---- entrada "crua" (usada pelo navegador para repassar mouse/teclado ao Chromium) ----
    /** type: 0 = pressionou, 1 = soltou, 2 = moveu, 3 = arrastou. Retorna true se consumiu. */
    public boolean rawMouse(int type, double lx, double ly, int button) { return false; }

    public boolean rawScroll(double lx, double ly, double delta) { return false; }

    public boolean captureKeyboard() { return false; }

    public boolean rawKey(int key, int scan, int mods, boolean down) { return false; }

    public boolean rawChar(char c, int mods) { return false; }

    /** O app quer o celular ampliado (ex.: navegador)? */
    public boolean wantsBigPhone() { return false; }
}
