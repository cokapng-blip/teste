package com.celularmod.client.apps;

import com.celularmod.client.*;
import com.celularmod.client.browser.McefBridge;
import com.celularmod.client.ui.TextField;
import com.celularmod.client.ui.Ui;
import com.celularmod.core.Json;
import com.celularmod.core.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * "Google": navegador de sites aprovados. Páginas HTML/CSS/JS REAIS renderizadas por um Chromium (MCEF).
 * Só abre sites que o administrador aprovou (celular://id-do-site); não há acesso à internet aberta.
 * Se o Chromium não estiver disponível, o app diz isso claramente — não há "navegador falso".
 */
public final class GoogleApp extends App {
    private enum View { HOME, BROWSE }

    private static final int BLUE = 0xFF4285F4;
    private View view = View.HOME;
    private final TextField searchField = new TextField("Pesquisar ou digitar endereço", 100, false);
    private final TextField addrField = new TextField("celular://site", 200, false);
    private McefBridge.Browser browser;
    private String cur = "";               // "id/caminho" mostrado
    private String seen = "";              // último "id/caminho" informado pelo servidor local
    private String expected = "";          // navegação iniciada por nós (não empilha no histórico)
    private int seenEvents;
    private String error = "";
    private boolean big;
    private int ticks;
    // retângulo do navegador na tela do celular (coordenadas locais)
    private float vx, vy, vw, vh;

    public GoogleApp() {
        searchField.onEnter = this::submitSearch;
        addrField.onEnter = () -> go(addrField.take());
    }

    @Override public String id() { return "google"; }
    @Override public String label() { return "Google"; }
    @Override public ResourceLocation icon() { return ClientState.icon("google"); }
    @Override public int accent() { return BLUE; }
    @Override public boolean wantsBigPhone() { return view == View.BROWSE && big; }

    @Override
    public void onOpen() { view = View.HOME; error = ""; big = false; ClientNet.send("site.list"); }

    @Override
    public void onClose() { closeBrowser(); view = View.HOME; }

    private void closeBrowser() {
        if (browser != null) browser.close();
        browser = null;
        cur = ""; seen = ""; expected = "";
        BrowserManager.history.clear();
    }

    @Override
    public boolean onBack() {
        if (view == View.BROWSE) { back(); return true; }
        return false;
    }

    @Override
    public void tick() { ticks++; }

    // ------------------------------------------------------------------ navegação
    private void submitSearch() { go(searchField.text.trim()); }

    /** Aceita "celular://id/caminho", "id/caminho" ou "id". Qualquer outra coisa vira pesquisa na lista. */
    private void go(String raw) {
        error = "";
        String a = raw == null ? "" : raw.trim();
        if (a.isEmpty()) return;
        String lower = a.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.contains("://") && !lower.startsWith("celular://")) {
            error = "Sites externos não são permitidos. Só sites aprovados pelo servidor (celular://nome-do-site).";
            return;
        }
        if (lower.startsWith("celular://")) a = a.substring("celular://".length());
        int sl = a.indexOf('/');
        String id = (sl < 0 ? a : a.substring(0, sl)).toLowerCase(Locale.ROOT);
        String path = sl < 0 ? "/" : a.substring(sl);
        if (!Text.validSiteId(id) || a.contains(" ") && sl < 0) {
            // não parece um endereço: é uma pesquisa — mantém na lista filtrada
            view = View.HOME;
            return;
        }
        if (siteMeta(id) == null) { error = "Site \"" + id + "\" não encontrado entre os aprovados."; view = View.HOME; return; }
        if (!BrowserManager.isReady(id)) { error = "O site \"" + id + "\" ainda está sendo baixado. Tente de novo em instantes."; view = View.HOME; return; }
        if (path.contains("..") || path.contains("\\")) { error = "Endereço inválido."; return; }
        open(id, path, true);
    }

    private Map<String, Object> siteMeta(String id) {
        for (Map<String, Object> s : ClientState.sites) if (Json.str(s, "id", "").equals(id)) return s;
        return null;
    }

    private void open(String id, String path, boolean pushHistory) {
        String status = McefBridge.status();
        view = View.BROWSE;
        if (!status.equals("ready") || !BrowserManager.ready()) return;   // a tela de BROWSE explica o motivo
        String target = id + path;
        if (pushHistory && !cur.isEmpty() && !cur.equals(target)) BrowserManager.history.push(cur);
        expected = target;
        cur = target;
        String url = BrowserManager.server().frameUrl(id, path);
        if (browser == null) {
            float s = PhoneRenderer.layout()[0];
            int gs = (int) Minecraft.getInstance().getWindow().getGuiScale();
            browser = McefBridge.create(url, Math.max(64, Math.round(Ui.W * s * gs)), Math.max(64, Math.round(200 * s * gs)));
            if (browser == null) error = "Falha ao criar o navegador: " + McefBridge.lastError;
        } else browser.load(url);
        addrField.text = "celular://" + cur;
    }

    private void back() {
        Deque<String> h = BrowserManager.history;
        if (h.isEmpty()) { closeBrowser(); view = View.HOME; return; }
        String prev = h.pop();
        int sl = prev.indexOf('/');
        open(prev.substring(0, sl), prev.substring(sl), false);
    }

    private void reload() {
        if (cur.isEmpty() || browser == null) return;
        int sl = cur.indexOf('/');
        expected = cur;
        browser.load(BrowserManager.server().frameUrl(cur.substring(0, sl), cur.substring(sl)));
    }

    /** O servidor local avisa quando o iframe carrega outro documento (clique em link). */
    private void syncFromServer() {
        int ev = BrowserManager.navEvents;
        if (ev == seenEvents) return;
        seenEvents = ev;
        String now = BrowserManager.current;
        if (now.isEmpty() || now.equals(seen)) return;
        seen = now;
        if (now.equals(expected)) { expected = ""; cur = now; }
        else {
            if (!cur.isEmpty() && !cur.equals(now)) BrowserManager.history.push(cur);
            cur = now;
        }
        if (ui().focus != addrField) addrField.text = "celular://" + cur;
    }

    private Ui ui() { return PhoneRenderer.ui; }

    // ------------------------------------------------------------------ desenho
    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        if (view == View.HOME) home(ui, x, y, w, h); else browse(ui, x, y, w, h);
    }

    private void home(Ui ui, int x, int y, int w, int h) {
        Common.header(ui, x, y, w, "Google", BLUE, false);
        ui.field(searchField, x + 6, y + 28, w - 36, 16, BLUE);
        if (ui.button(x + w - 28, y + 28, 22, 16, "Ir", BLUE, 0xFFFFFFFF)) submitSearch();
        if (!error.isEmpty()) {
            List<String> l = ui.wrap(error, w - 12);
            for (int i = 0; i < l.size(); i++) ui.text(l.get(i), x + 6, y + 48 + i * 9, ui.th.danger);
        }
        String q = searchField.text.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> shown = new ArrayList<>();
        for (Map<String, Object> s : ClientState.sites) {
            String hay = (Json.str(s, "id", "") + " " + Json.str(s, "title", "") + " " + Json.str(s, "description", "")).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || hay.contains(q)) shown.add(s);
        }
        int top = y + (error.isEmpty() ? 50 : 50 + ui.wrap(error, w - 12).size() * 9 + 4);
        ui.text("Sites aprovados", x + 6, top, ui.th.muted);
        top += 12;
        int listH = h - (top - y) - 30;
        if (!ClientState.hello) {
            Common.centeredNote(ui, x, top + 10, w, "Sem conexão com o servidor.", ui.th.muted);
        } else if (shown.isEmpty()) {
            Common.centeredNote(ui, x, top + 10, w, ClientState.sites.isEmpty() ? "Nenhum site aprovado ainda." : "Nenhum site encontrado para essa pesquisa.", ui.th.muted);
        } else {
            final int rowH = 28;
            ui.scrollArea("sites", x, top, w, listH, shown.size() * rowH, false, () -> {
                for (int i = 0; i < shown.size(); i++) {
                    Map<String, Object> s = shown.get(i);
                    int ry = i * rowH;
                    String id = Json.str(s, "id", "");
                    boolean ready = BrowserManager.isReady(id);
                    String err = ClientState.siteErrors.get(id);
                    if (ui.area(0, ry, w, rowH - 2)) { go(id); if (view == View.BROWSE) return; }
                    ui.rrect(4, ry + 2, 20, 20, BLUE);
                    String t = Json.str(s, "title", id);
                    ui.text(t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase(), 11, ry + 8, 0xFFFFFFFF);
                    ui.text(ui.ellipsize(t, w - 34), 28, ry + 3, ui.th.text);
                    String st = err != null ? "erro: " + err : ready ? "celular://" + id : "baixando...";
                    ui.text(ui.ellipsize(st, w - 34), 28, ry + 13, err != null ? ui.th.danger : ui.th.muted);
                    ui.rect(28, ry + rowH - 2, w - 28, 1, ui.th.line);
                }
            });
        }
        String foot = "Publicar um site: /celular site request <id> <título>";
        List<String> fl = ui.wrap(foot, w - 12);
        for (int i = 0; i < fl.size(); i++) ui.text(fl.get(i), x + 6, y + h - 8 - (fl.size() - i) * 9 + 8, ui.th.muted);
        if (ClientState.admin && ClientState.pendingSites > 0)
            ui.textR(ClientState.pendingSites + " pendente(s)", x + w - 4, y + h - 8 - fl.size() * 9, 0xFFE5A04D);
    }

    private void browse(Ui ui, int x, int y, int w, int h) {
        syncFromServer();
        // barra de ferramentas
        ui.rect(x, y, w, 20, ui.th.surface);
        if (ui.button(x + 2, y + 3, 14, 14, "<", ui.th.surface2, ui.th.text)) back();
        if (view != View.BROWSE) return;
        if (ui.button(x + 18, y + 3, 14, 14, "R", ui.th.surface2, ui.th.text)) reload();
        ui.field(addrField, x + 34, y + 3, w - 34 - 32, 14, BLUE);
        if (ui.button(x + w - 30, y + 3, 14, 14, big ? "-" : "+", ui.th.surface2, ui.th.text)) big = !big;
        if (ui.button(x + w - 15, y + 3, 13, 14, "H", ui.th.surface2, ui.th.text)) { closeBrowser(); view = View.HOME; return; }

        vx = x; vy = y + 20; vw = w; vh = h - 20;
        String status = McefBridge.status();
        ui.rect(vx, vy, vw, vh, 0xFFFFFFFF);
        if (!status.equals("ready")) { explain(ui, status); return; }
        if (!BrowserManager.ready()) { explainText(ui, "O servidor local de sites não iniciou: " + ClientState.siteErrors.getOrDefault("*", "erro desconhecido")); return; }
        if (browser == null) { explainText(ui, error.isEmpty() ? "Abrindo..." : error); return; }
        float s = PhoneRenderer.layout()[0];
        int gs = (int) Minecraft.getInstance().getWindow().getGuiScale();
        browser.resize(Math.max(64, Math.round(vw * s * gs)), Math.max(64, Math.round(vh * s * gs)));
        browser.draw(ui.g, vx, vy, vw, vh);
        if (!McefBridge.lastError.isEmpty()) {
            ui.rrect(x + 2, y + h - 12, w - 4, 10, 0xDDB3262B);
            ui.text(ui.ellipsize(McefBridge.lastError, w - 10), x + 5, y + h - 11, 0xFFFFFFFF);
        }
    }

    private void explain(Ui ui, String status) {
        String msg = switch (status) {
            case "missing" -> "O navegador precisa do mod MCEF (Chromium para Minecraft). Instale-o em mods/ e reinicie o jogo. Veja docs/NAVEGADOR.md.";
            case "loading" -> "O Chromium está sendo iniciado pelo MCEF (no primeiro uso ele baixa ~100 MB). Aguarde e abra o site de novo.";
            default -> "Erro no MCEF: " + McefBridge.lastError;
        };
        explainText(ui, msg);
    }

    private void explainText(Ui ui, String msg) {
        List<String> l = ui.wrap(msg, (int) vw - 14);
        for (int i = 0; i < l.size(); i++) ui.text(l.get(i), vx + 7, vy + 20 + i * 9, 0xFF333844);
    }

    // ------------------------------------------------------------------ entrada para o Chromium
    private boolean inView(double lx, double ly) {
        return view == View.BROWSE && browser != null && lx >= vx && ly >= vy && lx < vx + vw && ly < vy + vh;
    }

    private int bx(double lx) { return (int) ((lx - vx) * PhoneRenderer.layout()[0] * Minecraft.getInstance().getWindow().getGuiScale()); }

    private int by(double ly) { return (int) ((ly - vy) * PhoneRenderer.layout()[0] * Minecraft.getInstance().getWindow().getGuiScale()); }

    @Override
    public boolean rawMouse(int type, double lx, double ly, int button) {
        if (!inView(lx, ly)) {
            if (type == 1 && browser != null && view == View.BROWSE) browser.mouse(1, bx(lx), by(ly), Math.max(0, button));
            return false;
        }
        if (type == 0) PhoneRenderer.ui.focus = null;   // clicar na página tira o foco da barra de endereço
        browser.mouse(type, bx(lx), by(ly), Math.max(0, button));
        return true;
    }

    @Override
    public boolean rawScroll(double lx, double ly, double delta) {
        if (!inView(lx, ly)) return false;
        browser.wheel(bx(lx), by(ly), delta * 3);
        return true;
    }

    @Override
    public boolean captureKeyboard() {
        return view == View.BROWSE && browser != null && PhoneRenderer.ui.focus != addrField;
    }

    @Override
    public boolean rawKey(int key, int scan, int mods, boolean down) {
        if (browser == null) return false;
        browser.key(key, scan, mods, down);
        return true;
    }

    @Override
    public boolean rawChar(char c, int mods) {
        if (browser == null) return false;
        browser.typed(c, mods);
        return true;
    }
}
