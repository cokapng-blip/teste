#!/usr/bin/env python3
"""Gera as texturas do mod a partir dos recursos fornecidos pelo usuário.

- phone_frame.png: a imagem do celular (pixel art) reduzida à grade nativa 50x92, com a TELA vazada
  (alpha 0) para que a interface seja desenhada por baixo da moldura.
- icons/discord.png e icons/configuracoes.png: ícones do usuário, limpos (fragmentos de ícones vizinhos
  removidos) e reamostrados para 32x32.
- icons/whatsapp|minecraft|threads|google.png: os arquivos enviados estavam cortados (recortes parciais de
  uma folha de sprites), então foram redesenhados no mesmo estilo (borda preta, cantos chanfrados, cores chapadas).
"""
import sys, os, math
from PIL import Image
import numpy as np
from scipy import ndimage

PHONE_SRC, ICON_SRC, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(os.path.join(OUT, "textures/gui/icons"), exist_ok=True)
os.makedirs(os.path.join(OUT, "textures/item"), exist_ok=True)

# ---------------------------------------------------------------- moldura
im = Image.open(PHONE_SRC).convert("RGBA")
small = im.resize((50, 92), Image.BOX)
a = np.array(small)
a[..., 3] = np.where(a[..., 3] > 100, 255, 0)
SCREEN = (7, 11, 36, 67)  # x, y, w, h (em pixels nativos)
sx, sy, sw, sh = SCREEN
frame = a.copy()
frame[sy:sy + sh, sx:sx + sw] = (0, 0, 0, 0)
Image.fromarray(frame).save(os.path.join(OUT, "textures/gui/phone_frame.png"))

# ícone do item (16x16): o celular com a tela azulada
item = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
ph = Image.fromarray(a).resize((9, 16), Image.BOX)
pa = np.array(ph)
pa[..., 3] = np.where(pa[..., 3] > 100, 255, 0)
for y in range(16):
    for x in range(9):
        r, g, b, al = pa[y, x]
        if al and r > 170 and g > 170 and b > 170:
            pa[y, x] = (96, 170, 235, 255)
item.paste(Image.fromarray(pa), (3, 0))
item.save(os.path.join(OUT, "textures/item/phone.png"))

# ---------------------------------------------------------------- ícones do usuário (limpos)
def clean(path, grid=32):
    img = Image.open(path).convert("RGBA")
    arr = np.array(img)
    mask = arr[..., 3] > 128
    opened = ndimage.binary_opening(mask, structure=np.ones((9, 9)))
    lab, n = ndimage.label(opened)
    sizes = ndimage.sum(opened, lab, range(1, n + 1))
    keep = lab == (1 + int(np.argmax(sizes)))
    keep = ndimage.binary_dilation(keep, iterations=6) & mask
    arr[~keep] = 0
    o = Image.fromarray(arr)
    o = o.crop(o.getchannel("A").getbbox())
    side = max(o.size)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.paste(o, ((side - o.size[0]) // 2, (side - o.size[1]) // 2))
    s = np.array(sq.resize((grid, grid), Image.BOX))
    s[..., 3] = np.where(s[..., 3] > 110, 255, 0)
    return Image.fromarray(s)

clean(os.path.join(ICON_SRC, "discord.png")).save(os.path.join(OUT, "textures/gui/icons/discord.png"))
clean(os.path.join(ICON_SRC, "configuracoes_globo.png")).save(os.path.join(OUT, "textures/gui/icons/configuracoes.png"))

# ---------------------------------------------------------------- ícones redesenhados (32x32)
N = 32
BLACK = (14, 14, 18, 255)

def tile(fill, hi=None, lo=None):
    """quadrado arredondado com borda preta de 2px e cantos chanfrados"""
    t = np.zeros((N, N, 4), np.uint8)
    for y in range(N):
        for x in range(N):
            cx = min(x, N - 1 - x); cy = min(y, N - 1 - y)
            if cx + cy < 3 and not (cx >= 2 and cy >= 2 and False): 
                if cx + cy < 2: continue
            inner = cx >= 2 and cy >= 2 and not (cx + cy < 5)
            t[y, x] = fill if inner else BLACK
    if hi:
        for x in range(4, N - 4): t[2, x] = hi
        for y in range(4, N - 4): t[y, 2] = hi
    if lo:
        for x in range(4, N - 4): t[N - 3, x] = lo
        for y in range(4, N - 4): t[y, N - 3] = lo
    return t

def put(t, x, y, c):
    if 0 <= x < N and 0 <= y < N: t[y, x] = c

def disc(t, cx, cy, r0, r1, color, a0=None, a1=None):
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + .5 - cx, y + .5 - cy)
            if r0 <= d <= r1:
                if a0 is not None:
                    ang = (math.degrees(math.atan2(y + .5 - cy, x + .5 - cx)) + 360) % 360
                    lo, hi = a0 % 360, a1 % 360
                    ok = (lo <= ang <= hi) if lo <= hi else (ang >= lo or ang <= hi)
                    if not ok: continue
                put(t, x, y, color)

WHITE = (255, 255, 255, 255)

def save(name, t): Image.fromarray(t).save(os.path.join(OUT, f"textures/gui/icons/{name}.png"))

# WhatsApp
t = tile((34, 214, 60, 255), (110, 240, 120, 255), (20, 150, 40, 255))
disc(t, 16, 15, 7.2, 9.6, WHITE)
for i in range(4): put(t, 8 + i, 22 - i + 0, WHITE); put(t, 7 + i, 22 - i, WHITE)   # cauda do balão
for x, y in [(6, 24), (7, 24), (8, 23), (6, 23), (7, 23)]: put(t, x, y, WHITE)
for (x, y) in [(13, 12), (14, 13), (15, 14), (16, 15), (17, 16), (18, 17), (13, 13), (14, 14), (15, 15), (16, 16), (17, 17)]:
    put(t, x, y, WHITE)                                   # fone
put(t, 12, 12, WHITE); put(t, 12, 13, WHITE); put(t, 18, 18, WHITE); put(t, 19, 18, WHITE); put(t, 19, 17, WHITE)
save("whatsapp", t)

# Minecraft (bloco de grama em fundo de céu)
t = tile((106, 170, 240, 255), (160, 205, 250, 255), (70, 120, 200, 255))
def poly(t, pts, color):
    from PIL import ImageDraw
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.polygon(pts, fill=color)
    arr = np.array(img)
    m = arr[..., 3] > 0
    t[m] = arr[m]
poly(t, [(16, 6), (26, 11), (16, 16), (6, 11)], (96, 190, 60, 255))       # topo
poly(t, [(6, 11), (16, 16), (16, 27), (6, 22)], (134, 96, 67, 255))        # lado esquerdo
poly(t, [(26, 11), (16, 16), (16, 27), (26, 22)], (104, 72, 50, 255))      # lado direito
poly(t, [(6, 11), (16, 16), (16, 18), (6, 13)], (70, 150, 45, 255))        # franja de grama esq
poly(t, [(26, 11), (16, 16), (16, 18), (26, 13)], (58, 125, 38, 255))
for (x, y) in [(9, 17), (12, 21), (11, 15), (20, 19), (22, 16), (19, 23)]: put(t, x, y, (90, 62, 44, 255))
for (x, y) in [(14, 9), (18, 9), (16, 12), (11, 10), (21, 10)]: put(t, x, y, (130, 215, 90, 255))
save("minecraft", t)

# Threads (fundo escuro, "@" branco)
t = tile((38, 38, 44, 255), (70, 70, 80, 255), (22, 22, 28, 255))
disc(t, 16, 16, 8.2, 10.4, WHITE, 40, 320)          # anel aberto
disc(t, 16.5, 16.5, 3.0, 5.0, WHITE)                  # miolo
for y in range(14, 24):                               # haste
    put(t, 21, y, WHITE); put(t, 22, y, WHITE)
for x in range(21, 26): put(t, x, 22, WHITE)
for x in range(23, 26): put(t, x, 21, WHITE)
save("threads", t)

# Google ("G" colorido sobre branco)
t = tile((246, 248, 252, 255), (255, 255, 255, 255), (200, 208, 220, 255))
R, Y, G, B = (234, 67, 53, 255), (251, 188, 5, 255), (52, 168, 83, 255), (66, 133, 244, 255)
disc(t, 16, 16, 6.2, 10, R, 205, 320)
disc(t, 16, 16, 6.2, 10, Y, 130, 205)
disc(t, 16, 16, 6.2, 10, G, 50, 130)
disc(t, 16, 16, 6.2, 10, B, 330, 50)
for y in range(14, 18):
    for x in range(16, 26): put(t, x, y, B)
save("google", t)
print("texturas geradas em", OUT)
