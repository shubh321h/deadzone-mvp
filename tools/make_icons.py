#!/usr/bin/env python3
"""Generate DEAD ZONE launcher icons (pure stdlib PNG writer).
Original art: stylized zombie skull with glowing eyes on dark green ground."""
import struct, zlib, math, os

def make_image(size):
    # normalized-space skull: returns list of rows of (r,g,b,a)
    img = [[(0, 0, 0, 0)] * size for _ in range(size)]
    for j in range(size):
        for i in range(size):
            u = i / (size - 1)
            v = j / (size - 1)
            # background: dark post-apocalyptic green with subtle vignette
            bg = (22 + 14 * (1 - abs(u - 0.5)), 34 + 10 * (1 - abs(u - 0.5)) + 8 * (1 - abs(v - 0.5)), 26, 255)
            px = bg
            # skull head: ellipse center (0.5, 0.44), rx 0.30 ry 0.34
            dx = (u - 0.5) / 0.30
            dy = (v - 0.44) / 0.36
            dhead = dx * dx + dy * dy
            if dhead <= 1.0:
                shade = 205 - 35 * dhead
                px = (shade, shade + 4, shade - 6, 255)
                # jaw: smaller ellipse lower
            # jaw
            dx = (u - 0.5) / 0.19
            dy = (v - 0.70) / 0.16
            if dx * dx + dy * dy <= 1.0 and v > 0.56:
                shade = 185
                px = (shade, shade, shade - 8, 255)
            # eyes: glowing green
            for ex in (0.375, 0.625):
                dx = (u - ex) / 0.075
                dy = (v - 0.40) / 0.075
                if dx * dx + dy * dy <= 1.0:
                    px = (140, 255, 120, 255)
            # nose: dark triangle-ish
            dx = (u - 0.5) / 0.05
            dy = (v - 0.545) / 0.045
            if dx * dx + dy * dy <= 1.0:
                px = (30, 30, 28, 255)
            # teeth gaps on jaw
            if v > 0.62 and v < 0.76:
                for tx in (0.42, 0.5, 0.58):
                    dx = (u - tx) / 0.028
                    dy = (v - 0.69) / 0.075
                    if dx * dx + dy * dy <= 1.0:
                        px = (35, 35, 32, 255)
            # crack line down forehead
            if 0.42 < v < 0.52 and abs(u - (0.53 + 0.02 * (v - 0.42) * 8)) < 0.008 and dhead <= 1:
                px = (60, 62, 55, 255)
            img[j][i] = px
    return img

def write_png(path, img):
    size = len(img)
    raw = b""
    for row in img:
        raw += b"\x00" + b"".join(
            struct.pack("4B", *tuple(max(0, min(255, int(round(c)))) for c in p)) for p in row)
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        c += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return c
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)

base = os.path.join(os.path.dirname(__file__), "..", "res")
base = os.path.normpath(base)
for dpi, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    img = make_image(size)
    p = os.path.join(base, f"mipmap-{dpi}", "ic_launcher.png")
    write_png(p, img)
    print("wrote", p, size)
print("ICONS_OK")
