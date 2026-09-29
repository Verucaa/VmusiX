#!/usr/bin/env python3
"""
Generator ikon VmusiX — PNG murni tanpa dependensi (zlib+struct stdlib).

Kenapa script ini ikut disimpan: ikon adalah file gambar biner, dan file
biner tidak bisa direview lewat diff._sources. Menyimpan generator-nya
membuat ikon reproducible: ubah parameter di bawah, jalankan ulang, dapat
PNG yang identik.

    python3 tools/make_icons.py

Output:
  app/src/main/res/mipmap-*/ic_launcher.png          (legacy, 48..192)
  app/src/main/res/mipmap-*/ic_launcher_round.png    (legacy, bulat)
  app/src/main/res/mipmap-*/ic_launcher_foreground.png (adaptive, 108..432)
  app/src/main/res/mipmap-*/ic_launcher_background.png (adaptive, gradient)
  app/src/main/res/mipmap-*/ic_launcher_mono.png       (themed icon, alpha)
  docs/icon-512.png                                  (preview README)
"""

import os
import struct
import zlib

# --- Palet: mengikuti ui/theme/Theme.kt (Accent #FA233B) -----------------------
ACCENT = (0xFA, 0x23, 0x3B)
ACCENT_2 = (0xFF, 0x5C, 0x7D)
VIOLET = (0x7B, 0x4D, 0xFF)
INK = (0x0A, 0x0A, 0x0C)
WHITE = (0xFF, 0xFF, 0xFF)

# Equalizer: 5 bar, semua koordinat dalam satuan 1.0 dari lebar kanvas.
# Batas aman adaptive icon = lingkaran 66,7% di tengah (72dp dari 108dp).
# Bar sengaja dikecilkan (0.29..0.71 x, 0.23..0.77 y) supaya ujung bar tidak
# pernah terpakai oleh mask launcher berbentuk lingkaran/persegi membulat.
BARS = [
    # (center_x, half_width, half_height)
    (0.290, 0.038, 0.115),
    (0.390, 0.038, 0.205),
    (0.490, 0.038, 0.270),
    (0.590, 0.038, 0.180),
    (0.690, 0.038, 0.090),
]
BAR_RADIUS = 0.036  # rounded cap, satuan 1.0

SS = 3  # supersampling


def write_png(path, w, h, rgba_rows):
    raw = b"".join(b"\x00" + bytes(row) for row in rgba_rows)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def lerp(a, b, t):
    return a + (b - a) * t


def gradient(t):
    """Diagonal 3 titik: accent -> accent2 (t=0..0.55) -> violet."""
    t = max(0.0, min(1.0, t))
    if t < 0.55:
        u = t / 0.55
        return tuple(int(round(lerp(ACCENT[i], ACCENT_2[i], u))) for i in range(3))
    u = (t - 0.55) / 0.45
    return tuple(int(round(lerp(ACCENT_2[i], VIOLET[i], u))) for i in range(3))


def in_round_rect(x, y, radius):
    """Persegi 0..1 dengan sudut membulat, di luar = 0."""
    cx = min(max(x, radius), 1.0 - radius)
    cy = min(max(y, radius), 1.0 - radius)
    return (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius


def in_circle(x, y):
    dx, dy = x - 0.5, y - 0.5
    return dx * dx + dy * dy <= 0.25


def in_bar(x, y, cx, hw, hh, r):
    """Bar vertikal dengan rounded cap, center di (cx, 0.5)."""
    left, right = cx - hw, cx + hw
    if not (left <= x <= right):
        return False
    top, bottom = 0.5 - hh, 0.5 + hh
    if top <= y <= bottom:
        return True
    # tutup membulat di atas / bawah
    cap_y = top if y < top else bottom
    dx, dy = x - cx, y - cap_y
    return dx * dx + dy * dy <= r * r


def bar_cov(x, y):
    for cx, hw, hh in BARS:
        if in_bar(x, y, cx, hw, hh, BAR_RADIUS):
            return True
    return False


def render(size, shape, with_bg, with_bars, bar_rgb, radius=0.225):
    """Render RGBA pada ukuran `size`, SSx oversampling per piksel."""
    n = size * SS
    inv = 1.0 / n
    # bucket sample: (r,g,b,a) -> count
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            acc_r = acc_g = acc_b = acc_a = 0
            for sy in range(SS):
                y = (py * SS + sy + 0.5) * inv
                for sx in range(SS):
                    x = (px * SS + sx + 0.5) * inv
                    inside = in_round_rect(x, y, radius) if shape == "squircle" else in_circle(x, y)
                    if not inside:
                        continue
                    if with_bg:
                        r, g, b = gradient((x + y) / 2.0)
                    else:
                        r, g, b = 0, 0, 0
                    if with_bars and bar_cov(x, y):
                        r, g, b = bar_rgb
                    acc_r += r
                    acc_g += g
                    acc_b += b
                    acc_a += 255
            total = SS * SS
            # average hanya sample yang di dalam shape (anti-alias tepi)
            hits = acc_a // 255
            if hits == 0:
                row += b"\x00\x00\x00\x00"
            else:
                row += bytes(
                    (
                        int(round(acc_r / hits)),
                        int(round(acc_g / hits)),
                        int(round(acc_b / hits)),
                        int(round(acc_a / total)),
                    )
                )
        rows.append(row)
    return rows


def emit(rel_dir, base, size, shape, with_bg, with_bars, bar_rgb):
    out = os.path.join(ROOT, "app/src/main/res", rel_dir, base + ".png")
    write_png(out, size, size, render(size, shape, with_bg, with_bars, bar_rgb))
    return out


DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    made = []
    for dens, mult in DENSITIES:
        d = "mipmap-" + dens
        # Legacy launcher: 48dp
        legacy = int(round(48 * mult))
        made.append(emit(d, "ic_launcher", legacy, "squircle", True, True, WHITE))
        made.append(emit(d, "ic_launcher_round", legacy, "circle", True, True, WHITE))
        # Adaptive: 108dp, foreground hanya bar (aman 66%), background gradient penuh
        adaptive = int(round(108 * mult))
        made.append(emit(d, "ic_launcher_background", adaptive, "squircle", True, False, WHITE))
        made.append(emit(d, "ic_launcher_foreground", adaptive, "squircle", False, True, WHITE))
        # Themed icon (Android 13+): alpha dipakai launchers sebagai mask
        made.append(emit(d, "ic_launcher_mono", adaptive, "squircle", False, True, INK))

    preview = os.path.join(ROOT, "docs/icon-512.png")
    write_png(preview, 512, 512, render(512, "squircle", True, True, WHITE))
    made.append(preview)

    for p in made:
        print("%8d  %s" % (os.path.getsize(p), os.path.relpath(p, ROOT)))


if __name__ == "__main__":
    main()
