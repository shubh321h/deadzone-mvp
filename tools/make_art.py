#!/usr/bin/env python3
"""Generates all HD art assets: launcher icons, material atlas, menu bg."""
import os, math, random
from PIL import Image, ImageDraw, ImageFilter

random.seed(731217)
R = os.path.join(os.path.dirname(__file__), "..", "res")

# ---------------- 1. launcher icons ----------------
src = Image.open(os.path.join(os.path.dirname(__file__), "..", "..", "..", "art", "icon_src.png")).convert("RGBA")
os.makedirs(os.path.join(R, "mipmap-mdpi"), exist_ok=True)
os.makedirs(os.path.join(R, "mipmap-hdpi"), exist_ok=True)
os.makedirs(os.path.join(R, "mipmap-xhdpi"), exist_ok=True)
os.makedirs(os.path.join(R, "mipmap-xxhdpi"), exist_ok=True)
os.makedirs(os.path.join(R, "mipmap-xxxhdpi"), exist_ok=True)
os.makedirs(os.path.join(R, "mipmap-round-xxxhdpi"), exist_ok=True)
def save_icon(size, path, rounded):
    im = src.resize((size, size), Image.LANCZOS)
    if rounded:
        mask = Image.new("L", (size * 4, size * 4), 0)
        d = ImageDraw.Draw(mask)
        d.rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1], radius=int(size * 4 * 0.22), fill=255)
        mask = mask.resize((size, size), Image.LANCZOS)
        im.putalpha(mask)
    im.save(path)
save_icon(48,  f"{R}/mipmap-mdpi/ic_launcher.png", False)
save_icon(72,  f"{R}/mipmap-hdpi/ic_launcher.png", False)
save_icon(96,  f"{R}/mipmap-xhdpi/ic_launcher.png", False)
save_icon(144, f"{R}/mipmap-xxhdpi/ic_launcher.png", False)
save_icon(192, f"{R}/mipmap-xxxhdpi/ic_launcher.png", False)
save_icon(192, f"{R}/mipmap-round-xxxhdpi/ic_launcher_round.png", True)
src.resize((512, 512), Image.LANCZOS).save(os.path.join(os.path.dirname(__file__), "..", "..", "..", "art", "play_icon.png"))

# ---------------- 2. menu background (device-safe 1080p jpeg q82) ----------------
menu = Image.open(os.path.join(os.path.dirname(__file__), "..", "..", "..", "art", "menu_src.jpg")).convert("RGB")
w, h = menu.size
tw, th = 1080, int(1080 * h / w)
menu = menu.resize((tw, th), Image.LANCZOS)
menu.save(os.path.join(os.path.dirname(__file__), "..", "..", "..", "art", "menu_bg.jpg"), quality=82)
print("menu_bg:", menu.size)

# ---------------- 3. material atlas 1024x1024, 2x2 tiles of 512 ----------------
A = Image.new("RGB", (1024, 1024))
px = A.load()

def noise_tile(x0, y0, base, variation, tint=(1, 1, 1)):
    for y in range(512):
        for x in range(512):
            n = random.randint(-variation, variation)
            n2 = random.randint(-variation // 2, variation // 2)
            v = base + n + n2
            r = max(0, min(255, int(v * tint[0])))
            g = max(0, min(255, int(v * tint[1])))
            b = max(0, min(255, int(v * tint[2])))
            px[x0 + x, y0 + y] = (r, g, b)

# tile 0 (0,0): cracked asphalt
noise_tile(0, 0, 46, 7)
d = ImageDraw.Draw(A)
for _ in range(26):  # cracks
    x, y = random.randint(0, 512), random.randint(0, 512)
    for _ in range(random.randint(20, 70)):
        nx, ny = x + random.randint(-9, 9), y + random.randint(-4, 9)
        d.line([x, y, nx, ny], fill=(28, 29, 33), width=1)
        x, y = nx % 512, ny % 512
for _ in range(140):  # aggregate speckle
    x, y = random.randint(0, 511), random.randint(0, 511)
    v = random.randint(60, 95)
    d.point((x, y), fill=(v, v, v - 3))

# tile 1 (512,0): weathered concrete
noise_tile(512, 0, 96, 8, tint=(1.0, 0.98, 0.94))
d = ImageDraw.Draw(A)
for band in range(0, 512, 64):  # formwork seams
    d.line([512, band, 1023, band], fill=(70, 68, 64), width=2)
for _ in range(30):  # stains
    x, y = random.randint(512, 1000), random.randint(0, 500)
    s = random.randint(8, 40)
    d.ellipse([x, y, x + s, y + s], fill=(78, 76, 70))
for _ in range(24):  # rebar rust bleed
    x = random.randint(530, 1000)
    y = random.choice([63, 127, 191, 255, 319, 383, 447])
    d.line([x, y, x + random.randint(10, 50), y + random.randint(4, 12)], fill=(112, 74, 48), width=2)

# tile 2 (0,512): brushed metal
noise_tile(0, 512, 78, 5, tint=(0.94, 0.97, 1.0))
d = ImageDraw.Draw(A)
for _ in range(400):  # brush strokes
    y = random.randint(0, 511)
    x = random.randint(0, 511)
    ln = random.randint(30, 220)
    v = random.randint(60, 100)
    d.line([x, y, min(x + ln, 511), y], fill=(v, v + 3, v + 6), width=1)
for gx in range(32, 512, 96):  # rivet grid
    for gy in range(32, 512, 96):
        d.ellipse([gx - 4, gy - 4, gx + 4, gy + 4], fill=(52, 56, 62))
        d.ellipse([gx - 2, gy - 2, gx + 2, gy + 2], fill=(96, 102, 110))

# tile 3 (512,512): rust & grime
noise_tile(512, 512, 92, 10, tint=(1.0, 0.72, 0.45))
d = ImageDraw.Draw(A)
for _ in range(60):  # corrosion blotches
    x, y = random.randint(512, 1012), random.randint(512, 1012)
    s = random.randint(6, 48)
    c = random.choice([(88, 52, 30), (120, 70, 34), (60, 40, 28), (140, 92, 48)])
    d.ellipse([x, y, x + s, y + int(s * random.uniform(0.5, 1.4))], fill=c)
for _ in range(40):  # dark grime streaks
    x, y = random.randint(512, 1012), random.randint(512, 960)
    d.line([x, y, x + random.randint(-6, 6), y + random.randint(30, 120)], fill=(52, 36, 26), width=2)

A = A.filter(ImageFilter.SMOOTH)
out = os.path.join(os.path.dirname(__file__), "..", "assets")
os.makedirs(out, exist_ok=True)
A.save(os.path.join(out, "atlas.png"))
print("atlas 1024x1024 written")
print("OK")
