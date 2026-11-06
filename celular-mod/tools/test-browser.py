#!/usr/bin/env python3
"""Teste end-to-end num Chromium REAL: sobe o servidor local do mod com os sites de exemplo
e verifica renderização, JavaScript, fetch, formulário, links, navegação entre sites e bloqueios."""
import subprocess, sys, os, time, re, tempfile
from playwright.sync_api import sync_playwright

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out = tempfile.mkdtemp()
env = dict(os.environ, JAVA_TOOL_OPTIONS="")
subprocess.check_call(["javac", "-encoding", "UTF-8", "--release", "21", "-d", out] +
    [os.path.join(dp, f) for d in ("src/main/java/com/celularmod/core", "src/main/java/com/celularmod/browser", "src/test/java/com/celularmod/core")
     for dp, _, fs in os.walk(os.path.join(root, d)) for f in fs if f.endswith(".java")], env=env)

def run(allow_external):
    p = subprocess.Popen(["java", "-cp", out, "com.celularmod.core.BrowserDemo", os.path.join(root, "sample-sites"), str(allow_external).lower()],
                         stdout=subprocess.PIPE, text=True, env=env)
    port = None
    navs = []
    while port is None:
        line = p.stdout.readline()
        m = re.match(r"PORT (\d+)", line)
        if m: port = int(m.group(1))
    return p, port

fails = []
def check(c, what):
    print(("  ok   " if c else "  FALHOU ") + what)
    if not c: fails.append(what)

def frame_of(page):
    for _ in range(50):
        for f in page.frames:
            if f != page.main_frame and f.url.startswith("http://exemplo"):
                return f
        time.sleep(0.1)
    return None

for allow_external in (False,):
    proc, port = run(allow_external)
    host = f"http://celular-host.localhost:{port}/?u="
    with sync_playwright() as pw:
        b = pw.chromium.launch(args=["--proxy-server=direct://", "--proxy-bypass-list=*"])
        ctx = b.new_context(viewport={"width": 360, "height": 640}, device_scale_factor=2)
        page = ctx.new_page()
        console = []
        page.on("console", lambda m: console.append(m.text))
        page.goto(host + "exemplo-loja/")
        f = frame_of(page)
        check(f is not None, "moldura abre o site dentro do iframe")
        f.wait_for_selector("#titulo")
        check(f.inner_text("#titulo") == "Loja do Exemplo", "HTML renderizado (título)")
        check(f.evaluate("getComputedStyle(document.body).backgroundColor") == "rgb(15, 23, 42)", "CSS externo aplicado")
        check(f.evaluate("document.querySelector('header img').naturalWidth") > 0, "imagem SVG carregada")
        f.click("#btn"); f.click("#btn"); f.click("#btn")
        check(f.inner_text("#contador") == "3", "JavaScript executa e responde a cliques")
        f.wait_for_function("document.querySelectorAll('#produtos li').length==3")
        check("Elytra" in f.inner_text("#produtos"), "fetch('data.json') funciona (mesma origem)")
        check(f.evaluate("localStorage.getItem('visitas')") == "1", "localStorage disponível por site")
        f.click("#externo")
        f.wait_for_function("document.getElementById('status-externo').textContent!=='tentando…'")
        check(f.inner_text("#status-externo") == "bloqueado", "fetch para a internet é BLOQUEADO pela CSP")
        page.screenshot(path="/tmp/celular-browser-home.png")
        # formulário
        f.fill("#nome", "Steve"); f.click("#enviar")
        f.wait_for_function("document.getElementById('ola') && document.getElementById('ola').textContent.includes('Steve')")
        check("Olá, Steve!" in f.inner_text("#ola"), "formulário HTML enviado e processado com JS")
        f.click("#voltar")
        f.wait_for_selector("#link-sobre")
        # navegação entre páginas do mesmo site
        f.click("#link-sobre")
        f.wait_for_function("document.getElementById('titulo').textContent==='Sobre a loja'")
        check(f.url.endswith("/sobre.html"), "link relativo navega para outra página do site")
        # "Voltar" no jogo usa uma pilha de histórico própria (recarrega a moldura com a URL anterior)
        page.goto(host + "exemplo-loja/index.html")
        f2 = frame_of(page)
        f2.wait_for_selector("#link-sobre")
        check(f2.url.endswith("/index.html"), "voltar (pilha própria) recarrega a página anterior")
        # link entre sites via celular://
        f2.wait_for_selector("#link-noticias")
        f2.click("#link-noticias")
        f2.wait_for_function("document.title==='Notícias do Servidor'")
        check(f2.url.startswith(f"http://exemplo-noticias.localhost:{port}"), "link celular:// abre outro site aprovado")
        # tentativa de escapar: o iframe navega para a internet?
        f2.evaluate("location.href='https://example.com/'")
        time.sleep(1.5)
        f3 = frame_of(page)
        check(all("example.com" not in fr.url for fr in page.frames), "iframe NÃO navega para a internet (frame-src)")
        # script do site tentando navegar a página de cima
        page.goto(host + "exemplo-loja/")
        f4 = frame_of(page); f4.wait_for_selector("#titulo")
        top_before = page.url
        f4.evaluate("try{top.location.href='https://example.com/'}catch(e){}")
        time.sleep(1.0)
        check(page.url == top_before, "site não consegue navegar a página de cima (sandbox)")
        # popups/alert
        f4.evaluate("window.open('https://example.com/')")
        time.sleep(0.8)
        check(len(ctx.pages) == 1, "window.open bloqueado")
        # site inexistente
        page.goto(host + "nao-existe/")
        check("indisponível" in page.inner_text("body").lower(), "site inexistente mostra erro amigável")
        # páginas inexistentes dentro de um site
        page.goto(f"http://exemplo-loja.localhost:{port}/nao-existe.html")
        check("não encontrada" in page.inner_text("body").lower(), "404 amigável dentro do site")
        # arquivo oculto / traversal
        r = page.goto(f"http://exemplo-loja.localhost:{port}/.celular-site.json")
        check(r.status == 404, "metadados ocultos não são servidos")
        r = page.goto(f"http://exemplo-loja.localhost:{port}/..%2f..%2fexemplo-noticias%2findex.html")
        check(r.status == 404, "traversal entre sites bloqueado")
        b.close()
    proc.kill()

print("\nRESULTADO:", "TUDO OK" if not fails else f"{len(fails)} falha(s): {fails}")
sys.exit(1 if fails else 0)
