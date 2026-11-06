# Navegador (app Google) — como funciona e o que ele exige

O app **Google** renderiza sites HTML/CSS/JavaScript **reais** com um Chromium embutido. O Minecraft não renderiza
HTML sozinho, então o mod usa o **MCEF** (Minecraft Chromium Embedded Framework), um mod separado.

## Requisitos
- Mod **MCEF** para NeoForge 1.21.1 em `mods/` de cada **cliente** (o servidor não precisa dele).
- No primeiro uso o MCEF baixa o Chromium (~100 MB). Antivírus podem bloquear a inicialização.
- Windows/Linux/macOS 64 bits (sem Android).

> O `build.gradle` NÃO depende do MCEF. Toda a integração está em
> `src/main/java/com/celularmod/client/browser/McefBridge.java`, que usa reflexão contra a API
> `com.cinemamod.mcef.MCEF` / `MCEFBrowser` (a API da série 2.x e de forks para 1.21.x).
> Se o seu build do MCEF tiver pacote/métodos diferentes, ajuste só esse arquivo (constante `MCEF_CLASS` e as
> chamadas por nome). Se a imagem sair de cabeça para baixo, troque `FLIP_V`.
> Sem o MCEF o app mostra uma mensagem explicando o que falta — nunca uma tela simulada.

## Fluxo dos sites
1. O autor faz o site (pasta com `index.html` + CSS/JS/imagens) e entrega ao administrador (pasta ou `.zip`).
2. Opcional: o jogador registra o pedido com `/celular site request <id> <título>`; os admins online são avisados.
3. O admin coloca os arquivos em `<mundo>/celular/sites/submissions/<id>/` (ou `<id>.zip`).
4. `/celular site review <id>` valida (extensões, tamanhos, `index.html`, zip-slip, links simbólicos) e avisa sobre
   endereços externos. O admin **lê o código** e então roda `/celular site approve <id>`.
5. O servidor copia para `sites/approved/<id>/`, calcula SHA-256 e avisa todos os clientes.
6. Cada cliente baixa o site em pedaços (cada arquivo é verificado por tamanho e SHA-256) para
   `.minecraft/celular/cache/<servidor>/<id>/` e o serve ao Chromium por um servidor HTTP em `127.0.0.1`.
7. Atualizar um site = remover e aprovar de novo (o hash muda e os clientes baixam a nova versão).

`site.json` opcional na raiz: `{"title": "...", "description": "..."}`.
Links entre sites: `<a href="celular://outro-site/pagina.html">`.

## Segurança (camadas independentes)
- **Só administradores** aprovam/removem (permissão configurável em `server-settings.json`, padrão nível 3).
- Cada site tem **origem própria** (`id.localhost:porta`): sem acesso ao localStorage/cookies de outros.
- A moldura (`celular-host.localhost`) embute o site num **iframe sandbox** sem `allow-top-navigation`,
  `allow-popups`, `allow-downloads`, `allow-modals`, com CSP `frame-src` restrito a `*.localhost:porta`.
- Cada resposta traz **CSP** `default-src 'self'` + `connect-src 'self'`: o site não fala com a internet
  (ligue com `/celular admin external true` se confiar nos seus sites — menos seguro).
- Servidor local: só loopback, só GET/HEAD, valida o cabeçalho `Host`, bloqueia `..`, arquivos ocultos e symlinks.
- Extensões permitidas: html, htm, css, js, mjs, json, txt, svg, png, jpg, jpeg, gif, webp, ico, woff, woff2, ttf, otf, map.
- Limites (padrão): 200 arquivos, 8 MB por site, 2 MB por arquivo.

Importante: JavaScript aprovado roda dentro do Chromium com essas restrições. O Chromium é um software grande;
mantenha o MCEF atualizado e só aprove sites que você revisou.

## O que foi testado e o que não foi
Testado num **Chromium real** (Playwright) contra o MESMO servidor local/CSP/moldura usados no jogo
(`tools/test-browser.py`): HTML, CSS, imagem, JavaScript, `fetch` local, `localStorage`, formulário, links entre
páginas e entre sites, 404, bloqueio de fetch externo, de navegação para a internet, de `top.location`, de
`window.open` e de path traversal.
**Não** testado dentro do Minecraft (veja o README: o ambiente de desenvolvimento não tinha acesso ao Maven do Minecraft).
