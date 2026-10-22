#!/usr/bin/env python3
"""Tokkip's own sounds, synthesised here (no samples): soft, short and cute.
Writes shared/src/main/res/raw/*.wav. Run from android/: python3 tools/make_sounds.py"""
import numpy as np, wave, os
SR = 22050
OUT = os.path.join(os.path.dirname(__file__), '..', 'shared', 'src', 'main', 'res', 'raw')
os.makedirs(OUT, exist_ok=True)

def env(n, a=0.006, d=None):
    t = np.arange(n) / SR
    att = np.clip(t / a, 0, 1)
    dec = np.exp(-t / (d if d else (n / SR) / 4))
    return att * dec

def tone(f0, f1, dur, shape="sine", d=None, vib=0.0):
    n = int(SR * dur)
    t = np.arange(n) / SR
    f = f0 * (f1 / f0) ** (t / dur)
    if vib: f = f * (1 + vib * np.sin(2 * np.pi * 9 * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    if shape == "sine": w = np.sin(ph)
    elif shape == "tri": w = 2 / np.pi * np.arcsin(np.sin(ph))
    else: w = 0.6 * np.sin(ph) + 0.25 * np.sin(2 * ph) + 0.1 * np.sin(3 * ph)  # "soft"
    return w * env(n, d=d)

def seq(*parts, gap=0.02):
    out = []
    for p in parts:
        out.append(p); out.append(np.zeros(int(SR * gap)))
    return np.concatenate(out)

def mix(*xs):
    n = max(len(x) for x in xs); o = np.zeros(n)
    for x in xs: o[:len(x)] += x
    return o

def noise(dur, lo=0.0, d=0.03):
    n = int(SR * dur); w = np.random.RandomState(3).uniform(-1, 1, n)
    w = np.convolve(w, np.ones(6) / 6, mode="same")
    return w * env(n, a=0.002, d=d)

def save(name, x, vol=0.55):
    x = x / (np.max(np.abs(x)) + 1e-9) * vol
    tail = np.linspace(1, 0, min(len(x), 200)); x[-len(tail):] *= tail
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())

C5, E5, G5, A5, C6, D6, E6, G6 = 523.3, 659.3, 784.0, 880.0, 1046.5, 1174.7, 1318.5, 1568.0
save("pop", tone(700, 1500, 0.09, "sine", d=0.025))
save("blip", tone(1300, 1100, 0.06, "tri", d=0.02), 0.4)
save("tick", tone(2200, 2000, 0.03, "sine", d=0.008), 0.35)
save("open", seq(tone(G5, G5, 0.07, "soft"), tone(C6, C6, 0.12, "soft"), gap=0.01))
save("close", seq(tone(C6, C6, 0.07, "soft"), tone(G5, G5, 0.12, "soft"), gap=0.01))
save("approve", seq(tone(E5, E5, 0.08, "soft"), tone(G5, G5, 0.08, "soft"), tone(C6, C6, 0.2, "soft", d=0.08), gap=0.005))
save("approval", seq(tone(C6, C6, 0.07, "soft"), tone(E6, E6, 0.18, "soft", d=0.07), gap=0.005))
save("finish", seq(tone(C5, C5, 0.09, "soft"), tone(E5, E5, 0.09, "soft"), tone(G5, G5, 0.09, "soft"), tone(C6, C6, 0.3, "soft", d=0.12), gap=0.01))
save("greet", seq(tone(900, 1500, 0.1, "tri", d=0.05), tone(1100, 1700, 0.14, "tri", d=0.06), gap=0.04))
save("love", seq(tone(G5, G5, 0.12, "sine", d=0.06, vib=0.01), tone(E6, E6, 0.26, "sine", d=0.12, vib=0.012), gap=0.02))
save("proud", seq(tone(C6, C6, 0.06, "soft"), tone(E6, E6, 0.06, "soft"), tone(G6, G6, 0.22, "sine", d=0.1), gap=0.0))
save("wink", tone(1600, 2600, 0.08, "sine", d=0.03))
save("peek", tone(800, 1400, 0.12, "tri", d=0.05))
save("question", tone(700, 1250, 0.2, "tri", d=0.09))
save("annoyed", seq(tone(500, 380, 0.1, "soft", d=0.05), tone(470, 350, 0.14, "soft", d=0.06), gap=0.03))
save("error", seq(tone(420, 400, 0.12, "soft"), tone(320, 300, 0.18, "soft"), gap=0.03))
save("dizzy", tone(1400, 400, 0.6, "tri", d=0.3, vib=0.06))
save("slap", mix(noise(0.08, d=0.02), tone(260, 160, 0.09, "sine", d=0.03)), 0.6)
save("gulp", tone(300, 650, 0.14, "sine", d=0.06))
save("work", seq(tone(A5, A5, 0.06, "soft"), tone(D6, D6, 0.14, "soft", d=0.06), gap=0.02))
save("sleep", tone(600, 300, 0.7, "sine", d=0.35), 0.35)
save("yawn", tone(500, 260, 0.6, "soft", d=0.3, vib=0.02), 0.4)
print("ok")
