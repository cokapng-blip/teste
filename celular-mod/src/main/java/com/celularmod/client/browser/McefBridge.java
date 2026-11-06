package com.celularmod.client.browser;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Adaptador para o Chromium embutido (MCEF — "Minecraft Chromium Embedded Framework").
 *
 * POR QUE REFLEXÃO: o MCEF é um mod separado que baixa o Chromium (CEF) no primeiro uso. Acessá-lo por
 * reflexão mantém este mod compilável sem a dependência e permite mostrar uma mensagem clara quando o MCEF
 * não está instalado — em vez de simular um navegador. TODA a integração com o MCEF está neste arquivo.
 *
 * API esperada (a mesma da série 2.x do MCEF por CinemaMod, usada também por forks para 1.21.x):
 *   com.cinemamod.mcef.MCEF            : static boolean isInitialized(); static MCEFBrowser createBrowser(String url, boolean transparent)
 *   MCEFBrowser                         : resize(int,int) loadURL(String) close() setFocus(boolean) reload() goBack()
 *                                         sendMousePress/Release(int x,int y,int button) sendMouseMove(int,int)
 *                                         sendMouseWheel(int x,int y,double delta,int mods)
 *                                         sendKeyPress/Release(int key,long scan,int mods) sendKeyTyped(char,int)
 *                                         getRenderer().getTextureID()
 * Se o seu build do MCEF usar outros nomes/pacotes, ajuste APENAS as constantes e chamadas abaixo.
 */
public final class McefBridge {
    private McefBridge() {}

    /** Nome da classe principal do MCEF. */
    public static final String MCEF_CLASS = "com.cinemamod.mcef.MCEF";
    /** Se a imagem aparecer de cabeça para baixo no seu build do MCEF, troque para true. */
    public static final boolean FLIP_V = false;

    public static volatile String lastError = "";
    private static final Map<String, Method> CACHE = new HashMap<>();
    private static final List<Browser> OPEN = new ArrayList<>();

    // ------------------------------------------------------------------ disponibilidade
    public static boolean present() {
        try { Class.forName(MCEF_CLASS); return true; } catch (Throwable t) { return false; }
    }

    /** O Chromium já terminou de baixar/iniciar? (no primeiro uso o MCEF baixa ~100 MB) */
    public static boolean ready() {
        try {
            Object r = call(Class.forName(MCEF_CLASS), null, "isInitialized");
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            lastError = describe(t);
            return false;
        }
    }

    public static String status() {
        if (!present()) return "missing";
        if (!ready()) return lastError.isEmpty() ? "loading" : "error";
        return "ready";
    }

    // ------------------------------------------------------------------ navegador
    public static final class Browser {
        private final Object h;
        private int w, hgt;
        private int lastX, lastY;
        boolean closed;

        Browser(Object h) { this.h = h; }

        public void resize(int w, int hgt) {
            if (w == this.w && hgt == this.hgt || w < 16 || hgt < 16) return;
            this.w = w; this.hgt = hgt;
            safe(() -> call(h.getClass(), h, "resize", w, hgt));
        }

        public void load(String url) { safe(() -> call(h.getClass(), h, "loadURL", url)); }

        public void focus(boolean f) { safe(() -> call(h.getClass(), h, "setFocus", f)); }

        public void mouse(int type, int x, int y, int button) {
            lastX = x; lastY = y;
            switch (type) {
                case 0 -> safe(() -> call(h.getClass(), h, "sendMousePress", x, y, button));
                case 1 -> safe(() -> call(h.getClass(), h, "sendMouseRelease", x, y, button));
                default -> safe(() -> call(h.getClass(), h, "sendMouseMove", x, y));
            }
        }

        public void wheel(int x, int y, double delta) { safe(() -> call(h.getClass(), h, "sendMouseWheel", x, y, delta, 0)); }

        public void key(int key, int scan, int mods, boolean down) {
            safe(() -> call(h.getClass(), h, down ? "sendKeyPress" : "sendKeyRelease", key, (long) scan, mods));
        }

        public void typed(char c, int mods) { safe(() -> call(h.getClass(), h, "sendKeyTyped", c, mods)); }

        public void close() {
            if (closed) return;
            closed = true;
            safe(() -> call(h.getClass(), h, "close"));
            OPEN.remove(this);
        }

        public int textureId() {
            try {
                Object r = call(h.getClass(), h, "getRenderer");
                Object id = call(r.getClass(), r, "getTextureID");
                return ((Number) id).intValue();
            } catch (Throwable t) {
                lastError = describe(t);
                return 0;
            }
        }

        /** Desenha a textura do Chromium em (x,y,w,h) (coordenadas do pose atual). */
        public void draw(GuiGraphics g, float x, float y, float w, float hh) {
            int tex = textureId();
            if (tex == 0) return;
            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, tex);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            Matrix4f m = g.pose().last().pose();
            float v0 = FLIP_V ? 1f : 0f, v1 = FLIP_V ? 0f : 1f;
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            b.addVertex(m, x, y + hh, 0).setUv(0f, v1).setColor(255, 255, 255, 255);
            b.addVertex(m, x + w, y + hh, 0).setUv(1f, v1).setColor(255, 255, 255, 255);
            b.addVertex(m, x + w, y, 0).setUv(1f, v0).setColor(255, 255, 255, 255);
            b.addVertex(m, x, y, 0).setUv(0f, v0).setColor(255, 255, 255, 255);
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
    }

    /** Cria um navegador apontando para {@code url}. Retorna null (e define lastError) em caso de falha. */
    public static Browser create(String url, int w, int h) {
        try {
            Class<?> mcef = Class.forName(MCEF_CLASS);
            Object b = call(mcef, null, "createBrowser", url, false);
            Browser br = new Browser(b);
            br.resize(w, h);
            br.focus(true);
            OPEN.add(br);
            lastError = "";
            return br;
        } catch (Throwable t) {
            lastError = describe(t);
            return null;
        }
    }

    public static void closeAll() {
        for (Browser b : new ArrayList<>(OPEN)) b.close();
    }

    // ------------------------------------------------------------------ reflexão
    private interface Thrower { void run() throws Throwable; }

    private static void safe(Thrower t) {
        try { t.run(); } catch (Throwable e) { lastError = describe(e); }
    }

    private static String describe(Throwable t) {
        Throwable c = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }

    /** Chama método público por nome, convertendo números/boolean/char conforme os tipos dos parâmetros. */
    private static Object call(Class<?> cls, Object target, String name, Object... args) throws Throwable {
        String key = cls.getName() + "#" + name + "/" + args.length;
        Method m = CACHE.get(key);
        Object[] conv = null;
        if (m != null) conv = convert(m.getParameterTypes(), args);
        if (m == null || conv == null) {
            m = null;
            for (Method c : cls.getMethods()) {
                if (!c.getName().equals(name) || c.getParameterCount() != args.length) continue;
                Object[] cv = convert(c.getParameterTypes(), args);
                if (cv != null) { m = c; conv = cv; break; }
            }
            if (m == null) throw new NoSuchMethodException(cls.getName() + "." + name + "(" + args.length + " args)");
            CACHE.put(key, m);
        }
        try {
            return m.invoke(target, conv);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private static Object[] convert(Class<?>[] types, Object[] args) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Class<?> t = types[i];
            Object a = args[i];
            if (t == int.class || t == Integer.class) { if (!(a instanceof Number)) return null; out[i] = ((Number) a).intValue(); }
            else if (t == long.class || t == Long.class) { if (!(a instanceof Number)) return null; out[i] = ((Number) a).longValue(); }
            else if (t == double.class || t == Double.class) { if (!(a instanceof Number)) return null; out[i] = ((Number) a).doubleValue(); }
            else if (t == float.class || t == Float.class) { if (!(a instanceof Number)) return null; out[i] = ((Number) a).floatValue(); }
            else if (t == boolean.class || t == Boolean.class) { if (!(a instanceof Boolean)) return null; out[i] = a; }
            else if (t == char.class || t == Character.class) { if (!(a instanceof Character)) return null; out[i] = a; }
            else if (t == String.class) { if (!(a instanceof String)) return null; out[i] = a; }
            else return null;
        }
        return out;
    }
}
