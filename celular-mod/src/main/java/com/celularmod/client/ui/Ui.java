package com.celularmod.client.ui;

import com.celularmod.client.Sfx;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Mini-toolkit de interface "imediata" para a tela do celular. As coordenadas são LÓGICAS:
 * a tela do celular tem {@link #W}×{@link #H} unidades, escalada para a tela do jogo.
 * Cliques chegam como evento pendente e são consumidos pelo primeiro botão/área sob o cursor.
 */
public final class Ui {
    public static final int W = 144, H = 268;

    public GuiGraphics g;
    public Font font;
    public Theme th = Theme.current();
    private float s = 1, ox, oy, tx, ty;

    // entrada
    private boolean clickPending;
    private double cx, cy;
    public double mx = -1000, my = -1000;
    public boolean mouseDown;
    private boolean wheelPending;
    private double wheelDelta;
    public TextField focus;
    private String dragId;

    private final Map<String, Float> scroll = new HashMap<>();
    private final Map<String, Float> scrollMax = new HashMap<>();
    private final ArrayList<float[]> clips = new ArrayList<>();

    // ------------------------------------------------------------------ ciclo
    public void begin(GuiGraphics g, Font font, float scale, float ox, float oy) {
        this.g = g; this.font = font; this.s = scale; this.ox = ox; this.oy = oy; tx = 0; ty = 0;
        th = Theme.current();
        clips.clear();
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(scale, scale, 1f);
    }

    public void end() {
        g.pose().popPose();
        if (clickPending && focus != null) focus = null;   // clique "no vazio" tira o foco do campo
        clickPending = false;
        wheelPending = false;
    }

    // eventos (coordenadas locais)
    public void pressed(double lx, double ly) { clickPending = true; cx = lx; cy = ly; mouseDown = true; moved(lx, ly); }
    public void released() { mouseDown = false; dragId = null; }
    public void moved(double lx, double ly) { mx = lx; my = ly; }
    public void scrolled(double lx, double ly, double delta) { wheelPending = true; wheelDelta = delta; moved(lx, ly); }

    // ------------------------------------------------------------------ hit-test
    private boolean inClips(double px, double py) {
        for (float[] c : clips) if (px < c[0] || py < c[1] || px >= c[0] + c[2] || py >= c[1] + c[3]) return false;
        return true;
    }

    public boolean hover(float x, float y, float w, float h) {
        double lx = mx - tx, ly = my - ty;
        return lx >= x && lx < x + w && ly >= y && ly < y + h && inClips(mx, my);
    }

    /** Consome o clique pendente se ele caiu dentro do retângulo. */
    public boolean click(float x, float y, float w, float h) {
        if (!clickPending) return false;
        double lx = cx - tx, ly = cy - ty;
        if (lx >= x && lx < x + w && ly >= y && ly < y + h && inClips(cx, cy)) {
            clickPending = false;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ desenho básico
    public void rect(float x, float y, float w, float h, int color) { g.fill((int) x, (int) y, (int) (x + w), (int) (y + h), color); }

    /** Retângulo com cantos "chanfrados" de 1 unidade (estilo pixel art). */
    public void rrect(float x, float y, float w, float h, int color) {
        int ix = (int) x, iy = (int) y, iw = (int) w, ih = (int) h;
        g.fill(ix + 1, iy, ix + iw - 1, iy + ih, color);
        g.fill(ix, iy + 1, ix + iw, iy + ih - 1, color);
    }

    public void gradient(float x, float y, float w, float h, int top, int bottom) {
        g.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), top, bottom);
    }

    public void text(String t, float x, float y, int color) { g.drawString(font, t, (int) x, (int) y, color, false); }

    public void textBold(String t, float x, float y, int color) {
        g.drawString(font, net.minecraft.network.chat.Component.literal(t).withStyle(net.minecraft.ChatFormatting.BOLD), (int) x, (int) y, color, false);
    }

    public void textC(String t, float cxm, float y, int color) { text(t, cxm - font.width(t) / 2f, y, color); }

    public void textR(String t, float right, float y, int color) { text(t, right - font.width(t), y, color); }

    /** Texto reduzido (ex.: rótulos de ícones). */
    public void textScaled(String t, float x, float y, int color, float k) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1f);
        g.drawString(font, t, 0, 0, color, false);
        g.pose().popPose();
    }

    public void textScaledC(String t, float cxm, float y, int color, float k) {
        textScaled(t, cxm - font.width(t) * k / 2f, y, color, k);
    }

    public int textW(String t) { return font.width(t); }

    public String ellipsize(String t, int w) {
        if (font.width(t) <= w) return t;
        return font.plainSubstrByWidth(t, w - font.width("…")) + "…";
    }

    public List<String> wrap(String t, int w) {
        List<String> out = new ArrayList<>();
        for (FormattedText ft : font.getSplitter().splitLines(t, w, Style.EMPTY)) out.add(ft.getString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    public void icon(ResourceLocation tex, float x, float y, int size) {
        g.blit(tex, (int) x, (int) y, size, size, 0, 0, 32, 32, 32, 32);
    }

    public void pushClip(float x, float y, float w, float h) {
        float bx = x + tx, by = y + ty;
        float[] pc = clips.isEmpty() ? null : clips.get(clips.size() - 1);
        clips.add(new float[]{bx, by, w, h});
        g.enableScissor((int) Math.floor(ox + bx * s), (int) Math.floor(oy + by * s),
                (int) Math.ceil(ox + (bx + w) * s), (int) Math.ceil(oy + (by + h) * s));
    }

    public void popClip() {
        clips.remove(clips.size() - 1);
        g.disableScissor();
    }

    // ------------------------------------------------------------------ componentes
    public boolean button(float x, float y, float w, float h, String label, int bg, int fg) {
        boolean hov = hover(x, y, w, h);
        rrect(x, y, w, h, bg);
        if (hov) rrect(x, y, w, h, th.hover);
        text(ellipsize(label, (int) w - 4), x + (w - Math.min(textW(label), w - 4)) / 2f, y + (h - 8) / 2f, fg);
        boolean c = click(x, y, w, h);
        if (c) Sfx.click();
        return c;
    }

    /** Área clicável invisível (com realce ao passar o mouse). */
    public boolean area(float x, float y, float w, float h) {
        if (hover(x, y, w, h)) rect(x, y, w, h, th.hover);
        boolean c = click(x, y, w, h);
        if (c) Sfx.click();
        return c;
    }

    public boolean toggle(float x, float y, boolean on, int accent) {
        rrect(x, y, 20, 10, on ? accent : th.surface2);
        rect(on ? x + 11 : x + 1, y + 1, 8, 8, 0xFFFFFFFF);
        boolean c = click(x - 2, y - 2, 24, 14);
        if (c) Sfx.click();
        return c;
    }

    public float slider(String id, float x, float y, float w, float v, int accent) {
        rect(x, y + 3, w, 3, th.surface2);
        rect(x, y + 3, w * v, 3, accent);
        rect(x + w * v - 2, y, 4, 9, 0xFFFFFFFF);
        if (click(x - 3, y - 3, w + 6, 15)) dragId = id;
        if (id.equals(dragId) && mouseDown) v = (float) Math.max(0, Math.min(1, (mx - tx - x) / w));
        return v;
    }

    public void field(TextField f, float x, float y, float w, float h, int accent) {
        boolean focused = f == focus;
        rrect(x, y, w, h, focused ? accent : th.line);
        rrect(x + 1, y + 1, w - 2, h - 2, th.surface);
        boolean blink = focused && (System.currentTimeMillis() / 500) % 2 == 0;
        if (f.multiline) {
            String shown = f.text + (blink ? "_" : "");
            List<String> lines = wrap(shown.isEmpty() ? " " : shown, (int) w - 6);
            int maxLines = Math.max(1, (int) ((h - 4) / 9));
            int from = Math.max(0, lines.size() - maxLines);
            if (f.text.isEmpty() && !focused) text(ellipsize(f.hint, (int) w - 6), x + 3, y + 3, th.muted);
            else for (int i = from; i < lines.size(); i++) text(lines.get(i), x + 3, y + 3 + (i - from) * 9, th.text);
        } else {
            if (f.text.isEmpty() && !focused) {
                text(ellipsize(f.hint, (int) w - 6), x + 3, y + (h - 8) / 2f, th.muted);
            } else {
                String tail = font.plainSubstrByWidth(f.text, (int) w - 10, true);
                text(tail + (blink ? "_" : ""), x + 3, y + (h - 8) / 2f, th.text);
            }
        }
        if (click(x, y, w, h)) focus = f;
    }

    /** Área rolável. Dentro de {@code content}, as coordenadas começam em (0,0) no canto da área. */
    public void scrollArea(String id, float x, float y, float w, float h, float contentH, boolean stickBottom, Runnable content) {
        float max = Math.max(0, contentH - h);
        float prevMax = scrollMax.getOrDefault(id, 0f);
        float cur = scroll.getOrDefault(id, stickBottom ? max : 0f);
        if (stickBottom && cur >= prevMax - 1) cur = max;
        if (wheelPending && hover(x, y, w, h)) {
            cur -= (float) wheelDelta * 16f;
            wheelPending = false;
        }
        cur = Math.max(0, Math.min(max, cur));
        scroll.put(id, cur);
        scrollMax.put(id, max);

        pushClip(x, y, w, h);
        g.pose().pushPose();
        g.pose().translate(x, y - cur, 0);
        float otx = tx, oty = ty;
        tx += x; ty += y - cur;
        content.run();
        tx = otx; ty = oty;
        g.pose().popPose();
        popClip();
        if (max > 0) {
            float bh = Math.max(10, h * h / contentH);
            float by = y + (h - bh) * (cur / max);
            rect(x + w - 2, by, 2, bh, th.muted);
        }
    }

    public void resetScroll(String id) { scroll.remove(id); scrollMax.remove(id); }

    // ------------------------------------------------------------------ formatação
    public static String time(long ms) {
        return DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    /** HH:mm se for hoje, senão dd/MM. */
    public static String shortDate(long ms) {
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        if (z.toLocalDate().equals(LocalDate.now())) return DateTimeFormatter.ofPattern("HH:mm").format(z);
        return DateTimeFormatter.ofPattern("dd/MM").format(z);
    }
}
