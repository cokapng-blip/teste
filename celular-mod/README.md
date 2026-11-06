# Celular — mod de Minecraft (NeoForge 1.21.1)

Item **Celular**: um smartphone **2D** (sem modelo 3D, sem mão) desenhado no canto inferior direito da tela.
Segure o item, clique com o botão direito para desbloquear e use os apps com o mouse.

| App | O que faz |
|---|---|
| WhatsApp | Conta ligada ao jogador, número único, contatos, busca, conversas em tempo real, não lidas, histórico salvo, privacidade |
| Minecraft | Dados reais do servidor: nome, versão, jogadores online + ping, dimensão, dia/hora, clima, dificuldade |
| Discord | Abre o convite configurado pelo admin (`/celular admin discord <link>`) |
| Google | Navegador Chromium (MCEF) para **sites HTML aprovados** por administradores |
| Configurações (globo) | Volume, sons, notificações, privacidade, tema, papel de parede, tamanho, contas, restaurar padrões |
| Threads | Contas, posts, curtidas sincronizadas, feed "Para você"/"Recentes", perfis, compartilhar pelo WhatsApp, moderação |

Sem chamadas de voz/vídeo (conforme pedido).

## ⚠️ Estado real do projeto (leia)
- **Testado de verdade** (rode: `./tools/run-core-tests.sh` e `python3 tools/test-browser.py`):
  núcleo em Java puro (contas, mensagens, privacidade, posts, curtidas sem duplicação, compartilhamento, persistência,
  aprovação de sites, zip-slip, transferência com SHA-256, servidor HTTP local) — 89 verificações; e os sites de exemplo
  num Chromium real (19 verificações).
- **NÃO compilado/testado dentro do Minecraft.** O ambiente onde o projeto foi escrito bloqueia os repositórios Maven do
  NeoForge/Mojang, então a camada que usa as APIs do Minecraft (`item`, `net`, `server`, `client`) foi escrita com cuidado
  para 1.21.1/NeoForge 21.1 mas **nunca passou pelo compilador do Gradle**. Espere ajustes pequenos de nomes/assinaturas na
  primeira compilação — mande a saída de `./gradlew build` que eu corrijo.
- A integração com o MCEF usa reflexão contra a API da série 2.x e **não foi verificada contra um MCEF real de 1.21.1**.
- Ícones: seus arquivos de WhatsApp, Minecraft, Threads e Google vieram cortados (recortes parciais de uma folha de
  sprites). Usei Discord e globo (limpos) e **redesenhei os outros quatro** no mesmo estilo (`tools/make_textures.py`).
  Se tiver os originais completos, substitua em `src/main/resources/assets/celular/textures/gui/icons/` (PNG 32×32).

## Compilar
Requisitos: **JDK 21**, Gradle 8.10+ (ou gere o wrapper com `gradle wrapper`).
```
gradle build          # gera build/libs/celular-1.0.0.jar
gradle runClient      # abre o Minecraft de teste
gradle runServer
```
Se o Gradle disser que `neo_version` não existe, edite `gradle.properties` e use uma versão `21.1.x` listada em
https://maven.neoforged.net/#/releases/net/neoforged/neoforge . Se o plugin `net.neoforged.moddev` reclamar da versão,
use a versão atual do MDK 1.21.1.

## Instalar
- **Servidor:** `celular-1.0.0.jar` em `mods/`. (MCEF **não** é necessário.)
- **Cada jogador:** `celular-1.0.0.jar` + (para o navegador) o mod **MCEF** em `mods/`. Veja `docs/NAVEGADOR.md`.
- Jogadores sem o mod conseguem entrar (canal opcional), mas não têm o celular.
- Receita: 7 barras de ferro + 1 vidraça + 1 redstone (`IGI / IRI / III`). Também no criativo (Ferramentas) e via
  `/give @s celular:phone`.

## Configuração do servidor
Arquivos em `<mundo>/celular/`: `server-settings.json` (global: convite do Discord, limites de sites, recursos externos,
nível de permissão de admin), `data/*.json` (contas, mensagens, posts), `sites/…`. Local de cada jogador:
`config/celular-client.json` (volume, tema, escala etc.).

## Comandos
Jogadores: `/celular site request <id> <título>`, `/celular site list`.
Admins (permissão ≥ 3): `/celular site pending|review <id>|approve <id>|reject <id> [motivo]|remove <id>|reload`,
`/celular admin discord <url|clear>`, `/celular admin external <true|false>`, `/celular admin posts`,
`/celular admin post-delete <id>`. Admins também veem "Apagar" em todos os posts do Threads.

## Controles
Clique direito com o celular na mão: desbloqueia. Com ele aberto os cliques vão para a tela (não atacam nem usam itens;
você fica parado). Botões inferiores: `<` voltar, quadrado = início, `x` fechar. `Esc` também fecha. Trocar de item fecha.
Digitação: o cursor fica no fim (digitar/apagar/Ctrl+V). No navegador, `+` amplia o celular.

## Arquitetura
```
core/     Java puro (testável): Json, Database, WhatsAppService, ThreadsService, SiteService, ServerSettings, Text, RateLimiter
browser/  Java puro: SiteCache (cache verificado por SHA-256), LocalSiteServer (HTTP 127.0.0.1 + CSP + sandbox)
net/      C2SPacket / S2CPacket (JSON) + registro do canal (opcional, versão "1")
server/   ServerState, ServerHandler (valida TUDO), CelularCommands, ServerEvents
client/   PhoneRenderer/PhoneScreen/PhoneHud, Ui (toolkit), apps/*, ClientState, BrowserManager, browser/McefBridge
```
Multiplayer: o servidor é a fonte da verdade. Identidade = UUID do jogador (o cliente nunca informa "quem sou"),
limites de taxa por ação, textos sanitizados, curtidas por conjunto de UUIDs, compartilhamento montado no servidor a
partir do post real, permissões de admin checadas no servidor. Mensagens chegam ao destinatário em tempo real se
online e ficam salvas se offline.

## Limitações conhecidas
- Sem voz/vídeo; sem imagens nos posts; sem mídia no WhatsApp.
- Navegador depende do MCEF; no navegador o botão direito do mouse/teclas especiais dependem do mapeamento do MCEF.
- Atualizar um site = remover e reaprovar.
- Telas de loading e animações são simples (transição de apps, deslizar do celular).
