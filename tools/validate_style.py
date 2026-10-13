#!/usr/bin/env python3
"""
Zero-dependency validator for Kroma's colour science.

Mirrors the exact maths of:
  * app/src/main/assets/shaders/photographic_style.frag
  * app/src/main/java/com/kroma/app/engine/StyleEngine.kt   (CPU reference)
  * app/src/main/java/com/kroma/app/engine/Oklab.kt

We use this because a full Gradle build currently requires downloading the Gradle
distribution plus several hundred MB of dependencies (see the repo guide). This
script needs only the Python standard library.

Run:  python3 tools/validate_style.py
"""
import math
import sys

# --- Matrices (row-major; transposes of the column-major mat3() literals) -------
M1 = [[0.41222147, 0.53633254, 0.05144599],
      [0.21190350, 0.68069955, 0.10739696],
      [0.08830246, 0.28171884, 0.62997870]]
M2 = [[0.21045426, 0.79361779, -0.00407205],
      [1.97799850, -2.42859221, 0.45059371],
      [0.02590404, 0.78277177, -0.80867577]]
M2_INV = [[1.00000000, 0.39633778, 0.21580376],
          [1.00000000, -0.10556135, -0.06385417],
          [1.00000000, -0.08948418, -1.29148555]]
M1_INV = [[4.07674166, -3.30771159, 0.23096993],
          [-1.26843800, 2.60975740, -0.34131940],
          [-0.00419609, -0.70341861, 1.70761470]]


def mv(m, v):
    return [sum(m[i][j] * v[j] for j in range(3)) for i in range(3)]


def rgb_to_oklab(rgb):
    lms = mv(M1, rgb)
    cube = [math.pow(max(x, 0.0), 1.0 / 3.0) for x in lms]
    return mv(M2, cube)


def oklab_to_rgb(lab):
    cube = mv(M2_INV, lab)
    lms = [c * c * c for c in cube]
    return [max(x, 0.0) for x in mv(M1_INV, lms)]


def srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else math.pow((c + 0.055) / 1.055, 2.4)


def linear_to_srgb(c):
    x = min(max(c, 0.0), 1.0)
    return x * 12.92 if x <= 0.0031308 else 1.055 * math.pow(x, 1.0 / 2.4) - 0.055


def mix(a, b, t):
    return a + (b - a) * t


# --- Preset table (id, name, anchorX, anchorY, beta, hueDeg, toe, mono) ---------
PRESETS = {
    0:  ("Blush",      20.0,   0.0,  0.85,  18.0,  0.00, False),
    1:  ("Balance",     0.0,   0.0,  0.00,   0.0,  0.00, False),
    2:  ("Champagne",  35.0,  15.0,  0.90,   8.5,  0.04, False),
    3:  ("Gilded",     40.0,  10.0,  1.10, -12.0,  0.02, False),
    4:  ("Bronze",     50.0, -25.0,  1.25, -24.0, -0.06, False),
    5:  ("Native",      0.0,   0.0,  0.00,   0.0,  0.00, False),
    6:  ("Vivid",      30.0,  10.0,  1.40,   0.0,  0.03, False),
    7:  ("Natural",    10.0,   0.0,  0.40,  -4.0,  0.01, False),
    8:  ("Luminous",    0.0,  35.0,  0.60,   2.0,  0.08, False),
    9:  ("Dramatic",    0.0, -45.0,  0.75,  -5.0, -0.12, False),
    10: ("Quiet",     -30.0,   0.0, -0.60,   0.0,  0.00, False),
    11: ("Cozy",       35.0, -30.0,  1.15, -18.0, -0.08, False),
    12: ("Ethereal",   15.0,  40.0,  0.50,   6.0,  0.12, False),
    13: ("Muted B&W", -100.0, -10.0, -1.00,   0.0, -0.02, True),
    14: ("Stark B&W", -100.0, -50.0, -1.00,   0.0, -0.15, True),
}
NATIVE_ID = 5


def apply_style(base, pad_color, pad_tone, preset_id, intensity=1.0, skin_alpha=1.0):
    _name, ax, ay, beta, hue_deg, _toe, mono = PRESETS[preset_id]

    if preset_id == NATIVE_ID and math.hypot(pad_color, pad_tone) < 0.001:
        return list(base)

    lab = rgb_to_oklab(base)
    l, a, b = lab[0], lab[1], lab[2]

    eff_color = min(max(pad_color + ax, -100.0), 100.0)
    eff_tone = min(max(pad_tone + ay, -100.0), 100.0)

    # 1. Parametric tone curve
    gamma = math.exp(-eff_tone / 50.0)
    lg = math.pow(max(l, 0.0), gamma)
    om = math.pow(max(1.0 - l, 0.0001), gamma)
    s_curve = 0.0 if (lg + om) == 0.0 else lg / (lg + om)
    l_styled = l + (s_curve - l) * (4.0 * l * (1.0 - l))

    # 2. Parametric chroma & hue
    chroma = math.hypot(a, b)
    hue = math.atan2(b, a)
    k_c = max(0.0, 1.0 + (eff_color / 100.0) * beta)
    chroma_styled = chroma * k_c
    hue_styled = hue + math.radians(hue_deg) * (eff_color / 100.0)
    if mono:
        chroma_styled = 0.0

    # 3. Skin tone hue protection
    d_skin = math.sqrt(((a - 0.092) / 0.040) ** 2 + ((b - 0.061) / 0.030) ** 2)
    w_skin = min(max(1.0 - d_skin, 0.0), 1.0) * skin_alpha

    l_prot = mix(l_styled, mix(l, l_styled, 0.25), w_skin)
    c_prot = mix(chroma_styled, chroma * (1.0 + (eff_color / 100.0) * 0.15), w_skin)
    h_prot = mix(hue_styled, hue, w_skin)

    lab_p = [l_prot, c_prot * math.cos(h_prot), c_prot * math.sin(h_prot)]
    styled = oklab_to_rgb(lab_p)
    return [mix(base[i], styled[i], intensity) for i in range(3)]


def render_srgb(srgb, preset_id, pad_color=0.0, pad_tone=0.0, intensity=1.0, skin_alpha=1.0):
    lin = [srgb_to_linear(c) for c in srgb]
    out = apply_style(lin, pad_color, pad_tone, preset_id, intensity, skin_alpha)
    return [linear_to_srgb(c) for c in out]



# --- Checks --------------------------------------------------------------------
FAILS = []


def check(name, cond, detail=""):
    if not cond:
        FAILS.append(name)
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}{('  -> ' + detail) if detail else ''}")


def close(a, b, tol=1e-3):
    return all(abs(a[i] - b[i]) <= tol for i in range(3))


def main():
    print("Kroma colour-science validation (mirrors StyleEngine.kt)\n")

    print("1. Oklab round-trip identity")
    for rgb in ([0, 0, 0], [1, 1, 1], [0.5, 0.5, 0.5], [0.8, 0.2, 0.1], [0.1, 0.4, 0.9]):
        rt = oklab_to_rgb(rgb_to_oklab(rgb))
        check(f"round-trip {rgb}", close(rgb, rt, 1e-3), f"{[round(x, 4) for x in rt]}")

    print("\n2. Preset table")
    check("15 presets", len(PRESETS) == 15)
    check("ids 0..14", sorted(PRESETS) == list(range(15)))
    check("Native anchor (0,0)", PRESETS[5][1] == 0 and PRESETS[5][2] == 0)
    check("Cinema anchor Y = -45", PRESETS[9][2] == -45)
    check("13 & 14 monochrome", PRESETS[13][6] and PRESETS[14][6])

    print("\n3. Neutral preset at its anchor is a no-op")
    for srgb in ([0.85, 0.68, 0.56], [0.45, 0.66, 0.90], [0.5, 0.5, 0.5]):
        out = render_srgb(srgb, 1)
        check(f"identity {srgb}", close(srgb, out, 1e-3), f"{[round(x, 4) for x in out]}")

    print("\n4. Native preset at origin is an exact bypass")
    base = [0.42, 0.51, 0.63]
    check("exact bypass", apply_style(base, 0.0, 0.0, NATIVE_ID) == base)

    print("\n5. Zero intensity returns the base for every preset")
    ok = all(close([0.6, 0.45, 0.35],
                   apply_style([0.6, 0.45, 0.35], 40.0, -25.0, pid, intensity=0.0), 1e-4)
             for pid in PRESETS)
    check("all 15 presets", ok)

    print("\n6. Monochrome presets remove chroma")
    out = render_srgb([0.80, 0.20, 0.10], 14)
    check("channels equalise (grey)",
          abs(out[0] - out[1]) < 5e-3 and abs(out[1] - out[2]) < 5e-3,
          f"{[round(x, 4) for x in out]}")

    print("\n7. Tone curve: upward Tone compresses contrast & lifts shadows")
    dark = render_srgb([0.25, 0.25, 0.25], 1)[1]
    bright = render_srgb([0.75, 0.75, 0.75], 1)[1]
    dark_t = render_srgb([0.25, 0.25, 0.25], 1, pad_tone=60.0)[1]
    bright_t = render_srgb([0.75, 0.75, 0.75], 1, pad_tone=60.0)[1]
    check("contrast compressed", (bright_t - dark_t) < (bright - dark),
          f"{bright - dark:.4f} -> {bright_t - dark_t:.4f}")
    check("shadows lifted", dark_t > dark, f"{dark:.4f} -> {dark_t:.4f}")

    print("\n8. Vivid widens channel spread")
    spread = lambda c: max(c) - min(c)  # noqa: E731
    n = render_srgb([0.45, 0.66, 0.90], 1)
    v = render_srgb([0.45, 0.66, 0.90], 6)
    check("spread grows", spread(v) > spread(n), f"{spread(n):.4f} -> {spread(v):.4f}")

    print("\n9. Sample render — skyline blue through warm 'Cozy'")
    print(f"   base  {[round(x, 3) for x in [0.30, 0.45, 0.72]]}")
    print(f"   Cozy  {[round(x, 3) for x in render_srgb([0.30, 0.45, 0.72], 11)]}")

    print("\n" + ("ALL CHECKS PASSED" if not FAILS else f"FAILURES: {FAILS}"))
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())

