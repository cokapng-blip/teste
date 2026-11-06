package com.celularmod.client;

import com.celularmod.client.ui.TextField;
import com.celularmod.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * O celular DESBLOQUEADO. Enquanto esta tela está aberta, o Minecraft não processa ataque, quebrar blocos
 * ou uso de itens: todos os cliques vão para a interface do celular. Ao fechar, o controle normal volta.
 * Não pausa o jogo (importante em multiplayer) e não escurece o fundo.
 */
public class PhoneScreen extends Screen {
    public PhoneScreen() { super(Component.literal("Celular")); }

    @Override
    protected void init() { PhoneRenderer.onScreenOpened(); }

    @Override
    public void removed() {
        PhoneRenderer.onScreenClosed();
        ClientEvents.lastClose = System.currentTimeMillis();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { /* sem fundo escurecido nem desfoque */ }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        PhoneRenderer.draw(g, this.font, mx, my, true);
    }

    @Override
    public void tick() { PhoneRenderer.tick(); }

    // ------------------------------------------------------------------ mouse
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double[] l = PhoneRenderer.toLocal(mx, my);
        if (!PhoneRenderer.insideScreen(l[0], l[1])) return true;        // clique fora da tela: ignora (e não vaza p/ o jogo)
        App a = PhoneRenderer.currentApp();
        if (a != null && a.rawMouse(0, l[0], l[1], button)) return true;
        if (button == 0) PhoneRenderer.ui.pressed(l[0], l[1]);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        double[] l = PhoneRenderer.toLocal(mx, my);
        App a = PhoneRenderer.currentApp();
        if (a != null) a.rawMouse(1, l[0], l[1], button);
        PhoneRenderer.ui.released();
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        double[] l = PhoneRenderer.toLocal(mx, my);
        PhoneRenderer.ui.moved(l[0], l[1]);
        App a = PhoneRenderer.currentApp();
        if (a != null) a.rawMouse(3, l[0], l[1], button);
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        double[] l = PhoneRenderer.toLocal(mx, my);
        App a = PhoneRenderer.currentApp();
        if (a != null) a.rawMouse(2, l[0], l[1], -1);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        double[] l = PhoneRenderer.toLocal(mx, my);
        if (!PhoneRenderer.insideScreen(l[0], l[1])) return true;
        App a = PhoneRenderer.currentApp();
        if (a != null && a.rawScroll(l[0], l[1], sy)) return true;
        PhoneRenderer.ui.scrolled(l[0], l[1], sy);
        return true;
    }

    // ------------------------------------------------------------------ teclado
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        App a = PhoneRenderer.currentApp();
        Ui ui = PhoneRenderer.ui;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (ui.focus != null) ui.focus = null;
            else onClose();
            return true;
        }
        if (a != null && a.captureKeyboard()) return a.rawKey(key, scan, mods, true);
        TextField f = ui.focus;
        if (f != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) { f.backspace(); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { if (f.onEnter != null) f.onEnter.run(); return true; }
            if (key == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                f.paste(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
            return true;   // com um campo focado, nenhuma outra tecla vaza para o jogo
        }
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        App a = PhoneRenderer.currentApp();
        if (a != null && a.captureKeyboard()) return a.rawKey(key, scan, mods, false);
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        App a = PhoneRenderer.currentApp();
        if (a != null && a.captureKeyboard()) return a.rawChar(c, mods);
        TextField f = PhoneRenderer.ui.focus;
        if (f != null) f.type(c);
        return true;
    }
}
