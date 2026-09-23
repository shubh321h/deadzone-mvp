#!/usr/bin/env python3
"""DEAD ZONE — original procedural audio.

Generates all 23 WAVs bundled in assets/audio/ (names must match
AudioMgr.NAMES). Pure stdlib; 22 kHz mono 16-bit to stay small on
mid-range phones. Loops (rain, drone) are crossfaded for seamless repeat.
Usage: python3 tools/make_audio.py [out_dir]
"""
import math
import os
import random
import struct
import sys
import wave

SR = 22050
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(__file__), "..", "assets", "audio")


# ---------- primitives ----------
def noise(n, seed):
    rnd = random.Random(seed)
    return [rnd.uniform(-1.0, 1.0) for _ in range(n)]


def sine(n, f, ph=0.0, f1=None):
    out = [0.0] * n
    ph2 = ph
    df = (f1 - f) / max(1, n - 1) if f1 else 0.0
    cur = f
    for i in range(n):
        ph2 += 2.0 * math.pi * cur / SR
        out[i] = math.sin(ph2)
        cur += df
    return out


def exp(n, k):
    return [math.exp(-k * i / SR) for i in range(n)]


def lp(x, fc):
    a = 1.0 - math.exp(-2.0 * math.pi * fc / SR)
    y, out = 0.0, []
    for v in x:
        y += a * (v - y)
        out.append(y)
    return out


def hp(x, fc):
    a = 1.0 - math.exp(-2.0 * math.pi * fc / SR)
    y, out, last = 0.0, [], 0.0
    for v in x:
        y = a * (last + v - y - last)
        last = v
        out.append(v - y)
    return out


def bp(x, f1, f2):
    return [a - b for a, b in zip(lp(x, f2), lp(x, f1))]


def scale(x, g):
    return [v * g for v in x]


def add(*xs):
    n = max(len(x) for x in xs)
    out = [0.0] * n
    for x in xs:
        for i, v in enumerate(x):
            out[i] += v
    return out


def pad(x, n):
    if len(x) >= n:
        return x[:n]
    return x + [0.0] * (n - len(x))


def norm(x, peak=0.85):
    m = max(abs(v) for v in x) if x else 1.0
    if m < 1e-6:
        return x
    return [v * (peak / m) for v in x]


def crossfade_loop(x, fade_s=0.4):
    f = int(fade_s * SR)
    if f < 8 or len(x) <= 2 * f:
        return x
    out = list(x)
    for i in range(f):
        t = i / f
        out[f + i] = x[f + i] * t + x[i] * (1.0 - t)  # blend head into tail
        out[i] = x[i] * (1.0 - 0.5 * t)
    return out


def write_wav(name, x, loop=False):
    if loop:
        x = crossfade_loop(x)
    x = norm(x)
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = b"".join(struct.pack("<h", max(-32767, min(32767, int(v * 32767)))) for v in x)
        w.writeframes(frames)
    print("  %-14s %6.1f KB  %.3fs" % (name, os.path.getsize(path) / 1024.0, len(x) / SR))


def tick(n, t0, gain=0.9, fc=1800):
    if t0 < 0 or t0 >= n:
        return [0.0] * n
    L = int(0.008 * SR)
    burst = pad(noise(L, 11), 0)
    burst = scale(bp(burst, fc * 0.6, fc * 1.6), gain)
    e = exp(L, 1 / 0.004)
    burst = [a * b for a, b in zip(burst, e)]
    out = [0.0] * n
    out[t0:t0 + L] = [a + b for a, b in zip(out[t0:t0 + L], burst)]
    return out


def gun(seed, dur, body_hz, crack_hz, g1, g2, tail=None):
    n = int(dur * SR)
    nz = noise(n, seed)
    crack = scale(bp(nz, crack_hz * 0.7, crack_hz * 1.8), g1)
    crack = [a * b for a, b in zip(crack, exp(n, 1 / (dur * 0.28)))]
    body = scale(sine(n, body_hz), g2)
    body = [a * b for a, b in zip(body, exp(n, 1 / (dur * 0.35)))]
    out = add(crack, body)
    if tail:
        tn = int(tail * SR)
        tnz = noise(tn, seed + 1)
        ttail = scale(lp(tnz, 500), g1 * 0.35)
        ttail = [a * b for a, b in zip(ttail, exp(tn, 1 / (tail * 0.5)))]
        out = add(out, pad(ttail, n + tn)[:n] if len(out) >= n else out)
    # hard attack
    out = [v * min(1.0, i / 4.0) for i, v in enumerate(out)]
    return out


def build():
    os.makedirs(OUT, exist_ok=True)
    print("DEAD ZONE audio ->", os.path.abspath(OUT))

    # --- guns ---
    write_wav("shot_rifle", gun(1, 0.075, 120, 2400, 1.0, 0.8))
    write_wav("shot_smg", gun(2, 0.055, 160, 3000, 0.8, 0.5))
    write_wav("shot_shotgun", gun(3, 0.16, 70, 1500, 1.2, 1.1, tail=0.12))
    write_wav("shot_sniper", gun(4, 0.22, 55, 3500, 1.3, 1.2, tail=0.18))

    # --- mechanical ---
    n = int(0.6 * SR)
    reload = add(tick(n, 0, 1.0, 2200), tick(n, int(0.2 * SR), 0.8, 1800),
                 tick(n, int(0.42 * SR), 0.9, 2600), tick(n, int(0.55 * SR), 1.1, 1500))
    write_wav("reload", reload)

    n = int(0.09 * SR)
    write_wav("empty", tick(n, 0, 1.0, 2800))

    n = int(0.36 * SR)
    swap = add(tick(n, 0, 0.9, 1200), tick(n, int(0.16 * SR), 1.0, 2000))
    blip = scale(sine(int(0.1 * SR), 340, f1=190), 0.35)
    blip = [a * b for a, b in zip(blip, exp(len(blip), 1 / 0.03))]
    swap = add(swap, pad(blip, int(0.28 * SR)))
    write_wav("swap", swap)

    # --- creatures ---
    n = int(0.32 * SR)
    growl = sine(n, 170, f1=52)
    gnz = lp(noise(n, 21), 700)
    zdie = add(scale(growl, 0.9), scale(gnz, 0.55))
    e = exp(n, 1 / 0.16)
    zdie = [a * b for a, b in zip(zdie, e)]
    write_wav("zdie", zdie)

    n = int(0.72 * SR)
    s1 = sine(n, 880, f1=1350)
    s2 = sine(n, 892, f1=1361)  # beat = roughness
    fall = [math.exp(-4.5 * (i / n)) for i in range(n)]
    vib = [1.0 + 0.25 * math.sin(2 * math.pi * 26 * i / SR + 3 * i / n) for i in range(n)]
    sc = add(scale(s1, 0.5), scale(s2, 0.4))
    ramp = [min(1.0, i / 60.0) for i in range(n)]
    sc = [a * b * c * d for a, b, c, d in zip(sc, fall, vib, ramp)]
    sc = add(sc, scale(bp(noise(n, 31), 900, 2600), 0.28))
    write_wav("scream", sc)

    n = int(1.5 * SR)
    r = sine(n, 62, f1=38)
    r2 = sine(n, 62.7, f1=37)
    gr = lp(noise(n, 41), 300)
    am = [1.0 + 0.5 * math.sin(2 * math.pi * 24 * i / SR) for i in range(n)]
    roar = add(scale(r, 1.0), scale(r2, 0.8), scale(gr, 0.9))
    roar = [a * b for a, b in zip(roar, am)]
    e = [min(1.0, i / (0.03 * SR)) * math.exp(-2.2 * (i / n)) for i in range(n)]
    write_wav("boss_roar", [a * b for a, b in zip(roar, e)])

    n = int(0.5 * SR)
    slam = scale(sine(n, 38), 1.2)
    imp = scale(lp(noise(n, 51), 250), 0.9)
    e = exp(n, 1 / 0.22)
    write_wav("boss_slam", [a * b for a, b in zip(add(slam, imp), e)])

    n = int(0.7 * SR)
    chnz = noise(n, 61)
    # bandpass sweep via per-block filtering
    block = 512
    out = [0.0] * n
    for b0 in range(0, n - block, block):
        seg = chnz[b0:b0 + block]
        fc = 200 + 1300 * ((b0 + block / 2) / n)
        f = scale(lp(seg, fc), 0.9)
        out[b0:b0 + block] = f
    whoosh = sine(n, 80, f1=210)
    e = [min(1.0, (i / n) ** 0.7) * math.exp(-1.5 * (1 - i / n)) for i in range(n)]
    write_wav("boss_charge", [a * b for a, b in zip(add(scale(whoosh, 0.7), scale(out, 0.8)), e)])

    n = int(0.8 * SR)
    wind = sine(n, 110, f1=320)
    vib = [1.0 + 0.3 * math.sin(2 * math.pi * 30 * i / SR) for i in range(n)]
    br = scale(lp(noise(n, 71), 1200), 0.35)
    e = [min(1.0, i / (0.05 * SR)) * math.exp(-1.2 * (1 - i / n)) for i in range(n)]
    write_wav("boss_wind", [a * b * c + d * e2 for a, b, c, d, e2 in zip(wind, vib, e, br, e)])

    # --- feedback ---
    n = int(0.2 * SR)
    hurt = add(scale(sine(n, 95), 0.9), scale(lp(noise(n, 81), 500), 0.6))
    e = exp(n, 1 / 0.06)
    write_wav("hurt", [a * b for a, b in zip(hurt, e)])

    n = int(0.95 * SR)
    die = sine(n, 190, f1=36)
    die2 = sine(n, 95, f1=18)
    dn = scale(lp(noise(n, 91), 400), 0.4)
    e = [min(1.0, i / (0.02 * SR)) * math.exp(-1.6 * (i / n)) for i in range(n)]
    write_wav("die", [a * b for a, b in zip(add(scale(die, 0.8), scale(die2, 0.7), dn), e)])

    def blipchain(notes, dur, g=0.6):
        n = int(dur * SR)
        out = [0.0] * n
        for i, f in enumerate(notes):
            t0 = int(i * dur / len(notes) * SR)
            seg = scale(sine(int(0.09 * SR), f), g)
            e = exp(len(seg), 1 / 0.035)
            seg = [a * b for a, b in zip(seg, e)]
            for j, v in enumerate(seg):
                if t0 + j < n:
                    out[t0 + j] += v
        return out

    write_wav("med", blipchain([660, 880], 0.34))
    write_wav("pickup", blipchain([520, 784], 0.24))
    write_wav("interact", blipchain([600], 0.16, 0.5))
    write_wav("ui", blipchain([880], 0.1, 0.5))

    n = int(0.26 * SR)
    deny = [0.0] * n
    for k in range(2):
        t0 = int(k * 0.11 * SR)
        seg = sine(int(0.09 * SR), 130)
        seg = [v * 0.7 * (1.0 if (int(v * 10) % 2) else -0.2) for v in seg]  # harsh buzz
        e = exp(len(seg), 1 / 0.03)
        for j, v in enumerate(seg):
            if t0 + j < n:
                deny[t0 + j] += v * e[j] * 0.8
    write_wav("deny", deny)

    # wave horn: low dissonant brass-ish swell
    n = int(1.2 * SR)
    h1 = sine(n, 98)
    h2 = sine(n, 147)
    h3 = sine(n, 196)
    h4 = sine(n, 245)
    horn = add(scale(h1, 1.0), scale(h2, 0.55), scale(h3, 0.3), scale(h4, 0.18))
    br = scale(lp(noise(n, 101), 900), 0.12)
    e = [min(1.0, i / (0.25 * SR)) * math.exp(-1.8 * (i / n) ** 1.5) for i in range(n)]
    write_wav("wave", [a * b + c * d for a, b, c, d in zip(horn, e, br, e)])

    # --- loops ---
    n = int(3.0 * SR)
    rn = noise(n, 121)
    rain = lp(rn, 5000)
    rain = [a * 0.7 + b * 0.3 for a, b in zip(rain, lp(rn, 900))]
    # soft droplet pings
    rnd = random.Random(13)
    for _ in range(14):
        t0 = rnd.randint(0, n - int(0.03 * SR))
        d = scale(sine(int(0.03 * SR), rnd.randint(1800, 3400)), 0.12)
        e = exp(len(d), 1 / 0.012)
        for j, v in enumerate(d):
            rain[t0 + j] += v * e[j]
    # gentle steady-level LFO
    lfo = [1.0 + 0.12 * math.sin(2 * math.pi * 0.4 * i / SR) for i in range(n)]
    write_wav("rain", [a * b for a, b in zip(rain, lfo)], loop=True)

    n = int(6.0 * SR)
    d1 = sine(n, 55)
    d2 = sine(n, 55.6)
    d3 = sine(n, 82.4)
    d4 = sine(n, 110.3)
    wind = scale(lp(noise(n, 131), 350), 0.5)
    drone = add(scale(d1, 0.9), scale(d2, 0.8), scale(d3, 0.35), scale(d4, 0.15), wind)
    lfo = [1.0 + 0.25 * math.sin(2 * math.pi * 0.05 * i / SR) +
               0.1 * math.sin(2 * math.pi * 0.11 * i / SR + 1.7) for i in range(n)]
    write_wav("drone", [a * b for a, b in zip(drone, lfo)], loop=True)

    total = 0
    for f in os.listdir(OUT):
        total += os.path.getsize(os.path.join(OUT, f))
    print("TOTAL %d files, %.1f KB" % (len(os.listdir(OUT)), total / 1024.0))


if __name__ == "__main__":
    build()
