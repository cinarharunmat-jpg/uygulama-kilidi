# -*- coding: utf-8 -*-
"""Kasa uygulaması için özel ikon üretir (kasa/vault dial motifi, koyu grafit + pirinç renk).
Mevcut Android Studio şablonundan kalma yeşil-ızgara arka plan kullanılmıyordu (foreground webp
zaten kendi arka planını içeriyordu) -- aynı yapı korunuyor: her yoğunlukta tam kare (foreground)
ve dairesel kart (round) webp üretilip üzerine yazılıyor."""
import math
from PIL import Image, ImageDraw, ImageFilter

S = 1728  # süper-örnekleme kanvası (432 xxxhdpi * 4)

def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return m

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def diag_gradient(size, c1, c2):
    base = Image.new("RGB", (size, size))
    px = base.load()
    for y in range(size):
        for x in range(0, size, 4):  # 4'er adımla hesapla, hızlandırmak için bloklarla doldur
            t = (x + y) / (2 * size)
            c = lerp(c1, c2, t)
            for xx in range(x, min(x + 4, size)):
                px[xx, y] = c
    return base

def radial_gradient(size, c1, c2):
    base = Image.new("RGB", (size, size))
    px = base.load()
    cx = cy = size / 2
    maxd = math.hypot(cx, cy)
    for y in range(size):
        for x in range(size):
            t = min(1.0, math.hypot(x - cx, y - cy) / maxd)
            px[x, y] = lerp(c1, c2, t)
    return base

# ---- 1) Amblem: kasa kadranı (dial) + kol (handle), pirinç tonlarında ----
emb = Image.new("RGBA", (S, S), (0, 0, 0, 0))
cx, cy = S / 2, S / 2
R = S * 0.30  # kadran yarıçapı

brass = radial_gradient(S, (247, 214, 140), (170, 122, 46)).convert("RGBA")
brass.putalpha(255)
dial_mask = Image.new("L", (S, S), 0)
dm = ImageDraw.Draw(dial_mask)
dm.ellipse((cx - R, cy - R, cx + R, cy + R), fill=255)
emb = Image.alpha_composite(emb, Image.composite(brass, Image.new("RGBA", (S, S), (0, 0, 0, 0)), dial_mask))

# kadran halka gölgesi (iç kenar koyulaştırma)
ring = Image.new("L", (S, S), 0)
rd = ImageDraw.Draw(ring)
rd.ellipse((cx - R, cy - R, cx + R, cy + R), outline=255, width=int(S * 0.018))
ring = ring.filter(ImageFilter.GaussianBlur(S * 0.004))
dark_ring = Image.new("RGBA", (S, S), (90, 60, 20, 160))
emb = Image.composite(dark_ring, emb, ring)

# tık işaretleri (8 adet, kadran çevresinde)
draw = ImageDraw.Draw(emb)
tick_len = R * 0.22
tick_w = S * 0.018
for i in range(8):
    ang = math.radians(i * 45 - 90)
    x1 = cx + math.cos(ang) * (R - tick_len)
    y1 = cy + math.sin(ang) * (R - tick_len)
    x2 = cx + math.cos(ang) * (R - S * 0.02)
    y2 = cy + math.sin(ang) * (R - S * 0.02)
    draw.line((x1, y1, x2, y2), fill=(58, 40, 15, 255), width=int(tick_w))

# merkez göbek (koyu, kadranı tutan mil)
hub_r = R * 0.30
draw.ellipse((cx - hub_r, cy - hub_r, cx + hub_r, cy + hub_r), fill=(30, 33, 40, 255))
hl_r = hub_r * 0.55
draw.ellipse((cx - hl_r, cy - hl_r * 1.3, cx + hl_r, cy - hl_r * 0.3), fill=(70, 76, 88, 140))

# kol (handle) -- kadranın altından çıkan kısa dikey pirinç çubuk + tutamak
handle_w = S * 0.10
handle_h = S * 0.20
hx0 = cx - handle_w / 2
hy0 = cy + R - S * 0.01
hx1 = cx + handle_w / 2
hy1 = hy0 + handle_h
draw.rounded_rectangle((hx0, hy0, hx1, hy1), radius=int(handle_w * 0.4), fill=(200, 156, 76, 255))
draw.rounded_rectangle((hx0, hy0, hx1, hy0 + handle_h * 0.35), radius=int(handle_w * 0.4), fill=(230, 190, 110, 255))

emb = emb.filter(ImageFilter.GaussianBlur(0))  # no-op, netlik korunuyor

# ---- 2) KARE (foreground) kart: koyu grafit gradyan + amblem ----
card = diag_gradient(S, (58, 63, 71), (24, 27, 32)).convert("RGBA")
card.putalpha(255)
mask = rounded_mask(S, int(S * 0.22))
square_icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
square_icon = Image.composite(card, square_icon, mask)
# hafif üst parlama
sheen = Image.new("L", (S, S), 0)
sd = ImageDraw.Draw(sheen)
sd.ellipse((-S * 0.2, -S * 0.6, S * 1.2, S * 0.5), fill=60)
sheen = sheen.filter(ImageFilter.GaussianBlur(S * 0.08))
square_icon = Image.composite(Image.new("RGBA", (S, S), (255, 255, 255, 255)), square_icon, Image.composite(sheen, Image.new("L", (S, S), 0), mask))
square_icon.alpha_composite(emb)

SIZES_FG = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for name, sz in SIZES_FG.items():
    out = square_icon.resize((sz, sz), Image.LANCZOS)
    out.save(f"app/src/main/res/mipmap-{name}/ic_launcher_foreground.webp", "WEBP", lossless=True)

# ---- 3) YUVARLAK (round) kart: açık gri daire zemin + küçültülmüş kart ----
round_master = Image.new("RGBA", (S, S), (0, 0, 0, 0))
bg = Image.new("RGBA", (S, S), (238, 238, 240, 255))
bg_mask = Image.new("L", (S, S), 0)
bd = ImageDraw.Draw(bg_mask)
bd.ellipse((S * 0.02, S * 0.02, S * 0.98, S * 0.98), fill=255)
round_master = Image.composite(bg, round_master, bg_mask)
inset = square_icon.resize((int(S * 0.62), int(S * 0.62)), Image.LANCZOS)
off = (S - inset.width) // 2
round_master.alpha_composite(inset, (off, off))

SIZES_RD = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, sz in SIZES_RD.items():
    out = round_master.resize((sz, sz), Image.LANCZOS)
    out.save(f"app/src/main/res/mipmap-{name}/ic_launcher_round.webp", "WEBP", lossless=True)

square_icon.resize((512, 512), Image.LANCZOS).save(r"C:\Users\Cnrma\uygulama-kilidi\_onizleme_kare.png")
round_master.resize((512, 512), Image.LANCZOS).save(r"C:\Users\Cnrma\uygulama-kilidi\_onizleme_yuvarlak.png")
print("bitti")
