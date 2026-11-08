#!/usr/bin/env python3
"""Generates the launcher icons: one per card type, each a miniature of that card's face.

Needs Pillow (pip install pillow). Run from the repo root after editing ICONS:

    python3 tools/gen_icons.py

Writes into app/src/main:
  res/mipmap-<density>/ic_launcher_{bg,fg,mono}_<id>.png  adaptive-icon layers
  res/mipmap-anydpi/ic_launcher_<id>.xml                  adaptive icon
  java/.../icon/IconResources.kt                          id -> resources/alias map
and rewrites the <activity-alias> block in AndroidManifest.xml between the ICON-ALIASES markers.

Layouts follow ui/CardDesigns.kt. Coordinates below are in the icon's visible square (u, v in
0..1 covers the central 72 of 108 dp), and may run past it to bleed under the mask.
"""
import math
import pathlib
import re

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main"
PKG = "io.github.grillza.icreader"
FONT = pathlib.Path(__file__).resolve().parent / "fonts" / "OpenSans-Bold.ttf"
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
S = 864  # render size (px for 108 dp), downsampled per density

PEARL = ("#F7F8F9", "#E2E5E8")
SILVER = ("#E7E9EC", "#C5C9CE")

# id, label, base gradient, brand text, text colour, text centre (u, v), text width (fraction), shapes
ICONS = [
    ("default", "IC Reader", ("#26324F", "#1A2238"), None, None, None, 0, "default"),
    ("suica", "Suica", PEARL, "Suica", "#FFFFFF", (0.45, 0.68), 0.62, "suica"),
    ("pasmo", "PASMO", SILVER, "PASMO", "#EA5A9C", (0.5, 0.74), 0.74, "pasmo"),
    ("icoca", "ICOCA", ("#2A66C9", "#4F92E6"), "ICOCA", "#E3EEFF", (0.5, 0.5), 0.74, None),
    ("toica", "TOICA", SILVER, "toica", "#FFFFFF", (0.5, 0.76), 0.62, "toica"),
    ("manaca", "manaca", ("#D3D6DF", "#BBC0CC"), "manaca", "#8E95A6", (0.5, 0.5), 0.70, "manaca"),
    ("kitaca", "Kitaca", PEARL, "Kitaca", "#6FA82B", (0.5, 0.36), 0.66, "kitaca"),
    ("sugoca", "SUGOCA", PEARL, "SUGOCA", "#E4462E", (0.5, 0.72), 0.78, "sugoca"),
    ("nimoca", "nimoca", PEARL, "nimoca", "#FFFFFF", (0.5, 0.5), 0.66, "nimoca"),
    ("hayakaken", "Hayakaken", PEARL, "Hayakaken", "#2E86C1", (0.5, 0.34), 0.84, "hayakaken"),
    ("pitapa", "PiTaPa", SILVER, "PiTaPa", "#E6E8EC", (0.62, 0.62), 0.58, "pitapa"),
    ("transit", "Transit IC", SILVER, "IC", "#FFFFFF", (0.5, 0.76), 0.34, "transit"),
    ("edy", "Rakuten Edy", ("#FFFFFF", "#EDEEF0"), "Edy", "#D0000F", (0.42, 0.40), 0.52, "edy"),
    ("nanaco", "nanaco", ("#FFF8E7", "#F7EBCB"), "nanaco", "#E38D00", (0.5, 0.38), 0.70, "nanaco"),
    ("waon", "WAON", ("#CDEBF8", "#86CFEF"), "WAON", "#1C6FD0", (0.5, 0.5), 0.72, "waon"),
]


def px(u: float) -> float:
    """Visible-square coordinate to render pixels (visible square is 18..90 of 108 dp)."""
    return S * (18 + 72 * u) / 108


def hex_rgba(c: str, a: int = 255):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def gradient(c1: str, c2: str) -> Image.Image:
    a, b = hex_rgba(c1), hex_rgba(c2)
    img = Image.new("RGBA", (S, S))
    d = ImageDraw.Draw(img)
    for i in range(2 * S):  # diagonal, top-left to bottom-right
        t = i / (2 * S - 1)
        d.line([(i, 0), (0, i)], fill=tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(4)), width=2)
    return img


def P(u, v):
    return (px(u), px(v))


def R(u0, v0, u1, v1):
    return [px(u0), px(v0), px(u1), px(v1)]


def draw_shapes(img: Image.Image, kind: str):
    d = ImageDraw.Draw(img)
    if kind == "default":
        w = S / 108 * 0.74  # scaled about the centre to stay inside the mask
        o = S * 0.13
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([o + 30 * w, o + 38 * w, o + 78 * w, o + 70 * w], radius=6 * w, outline="#FFFFFF", width=int(4.5 * w))
        d.rectangle([o + 34 * w, o + 46 * w, o + 44 * w, o + 54 * w], fill="#FFFFFF")
        for r in (8, 14, 20):
            d.arc([o + 52 * w - r * w, o + 54 * w - r * w, o + 52 * w + r * w, o + 54 * w + r * w], -38, 38, fill="#FFFFFF", width=int(4 * w))
    elif kind == "suica":
        # Green panel on the left; its right edge slants down to the bottom-right.
        d.polygon([P(-0.4, 0.30), P(0.62, 0.30), P(1.30, 1.25), P(-0.4, 1.25)], fill="#62BD2F")
    elif kind == "pasmo":
        for v in (0.10, 0.34):
            d.rounded_rectangle(R(0.10, v, 0.66, v + 0.17), radius=(px(0.06) - px(0)), fill="#EA5A9C")
            d.ellipse(R(0.73, v + 0.02, 0.87, v + 0.16), fill="#EA5A9C")
    elif kind == "toica":
        pts = []
        for i in range(61):
            u = -0.4 + 1.8 * i / 60
            pts.append(P(u, 0.56 + 0.11 * math.sin((u + 0.1) * 2 * math.pi)))
        d.polygon(pts + [P(1.4, 1.4), P(-0.4, 1.4)], fill="#17A9E1")
    elif kind == "manaca":
        d.ellipse(R(0.02, 0.02, 0.98, 0.98), fill="#F1E63B")
    elif kind in ("kitaca", "hayakaken", "edy"):
        a, b = {"kitaca": ("#9CCB3B", "#7AB52E"), "hayakaken": ("#7FC3E8", "#3E9ED6"), "edy": ("#E60012", "#BF0000")}[kind]
        d.polygon([P(0.25, 1.3), P(1.3, 0.40), P(1.3, 1.3)], fill=a)
        d.polygon([P(0.0, 1.25), P(0.12, 1.25), P(1.25, 0.28), P(1.25, 0.17)], fill=b)
    elif kind == "sugoca":
        d.ellipse(R(0.45, -0.25, 1.25, 0.55), fill="#B7CFA2")
    elif kind == "nimoca":
        d.ellipse(R(0.03, 0.03, 0.97, 0.97), fill="#45A2CB")
    elif kind == "pitapa":
        d.rectangle(R(0.30, -0.4, 1.4, 1.4), fill="#8D8EC8")
        d.ellipse(R(-0.12, 0.10, 0.36, 0.58), fill="#8D8EC8")
    elif kind == "transit":
        d.polygon([P(-0.4, 0.62), P(0.2, 0.44), P(0.6, 0.66), P(1.4, 0.48), P(1.4, 1.4), P(-0.4, 1.4)], fill="#607D8B")
    elif kind == "nanaco":
        d.ellipse(R(0.30, 0.55, 1.50, 1.75), fill="#6CC3E8")
        d.ellipse(R(-0.10, 0.62, 0.90, 1.62), fill="#F7A800")
    elif kind == "waon":
        for (u, v, r) in ((0.82, 0.22, 0.22), (0.18, 0.80, 0.16), (0.88, 0.86, 0.12), (0.2, 0.15, 0.08)):
            d.ellipse(R(u - r, v - r, u + r, v + r), fill=(255, 255, 255, 120))


def wordmark(text: str, color: str, center, width_frac: float) -> Image.Image:
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    target = (px(1) - px(0)) * width_frac
    size = 10
    while ImageFont.truetype(str(FONT), size + 4).getlength(text) < target:
        size += 4
    font = ImageFont.truetype(str(FONT), size)
    ImageDraw.Draw(img).text((px(center[0]), px(center[1])), text, font=font, fill=color, anchor="mm")
    return img


def save(img: Image.Image, name: str):
    for dens, size in DENSITIES.items():
        out = ROOT / f"res/mipmap-{dens}/{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        img.resize((size, size), Image.LANCZOS).save(out, optimize=True)


def adaptive(ident: str) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/gen_icons.py -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_bg_{ident}" />
    <foreground android:drawable="@mipmap/ic_launcher_fg_{ident}" />
    <monochrome android:drawable="@mipmap/ic_launcher_mono_{ident}" />
</adaptive-icon>
"""


def camel(ident: str) -> str:
    return ident[0].upper() + ident[1:]


def main():
    # Remove outputs of the old vector-based generator.
    for old in list((ROOT / "res/drawable").glob("ic_launcher_fg_*.xml")) + [ROOT / "res/values/icon_colors.xml"]:
        old.unlink(missing_ok=True)

    for ident, _label, base, text, color, center, width, shapes in ICONS:
        bg = gradient(*base)
        if shapes and shapes != "default":
            draw_shapes(bg, shapes)
        if shapes == "default":
            fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
            draw_shapes(fg, "default")
        else:
            fg = wordmark(text, color, center, width)
        # Themed icons: the system tints this single-colour layer.
        mono = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        mono.paste((0, 0, 0, 255), mask=fg.split()[3])
        save(bg.convert("RGB"), f"ic_launcher_bg_{ident}")
        save(fg, f"ic_launcher_fg_{ident}")
        save(mono, f"ic_launcher_mono_{ident}")
        (ROOT / "res/mipmap-anydpi").mkdir(parents=True, exist_ok=True)
        (ROOT / f"res/mipmap-anydpi/ic_launcher_{ident}.xml").write_text(adaptive(ident))

    entries = ",\n".join(
        f'    AppIcon("{i[0]}", "{i[1]}", "{PKG}.Launcher{camel(i[0])}", '
        f"R.mipmap.ic_launcher_fg_{i[0]}, R.mipmap.ic_launcher_bg_{i[0]})"
        for i in ICONS
    )
    kt = f"""// Generated by tools/gen_icons.py - do not edit.
package {PKG}.icon

import {PKG}.R

val APP_ICONS: List<AppIcon> = listOf(
{entries},
)
"""
    (ROOT / f"java/{PKG.replace('.', '/')}/icon/IconResources.kt").write_text(kt)

    aliases = []
    for i in ICONS:
        enabled = "true" if i[0] == "default" else "false"
        aliases.append(
            f"""        <activity-alias
            android:name=".Launcher{camel(i[0])}"
            android:enabled="{enabled}"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_{i[0]}"
            android:roundIcon="@mipmap/ic_launcher_{i[0]}"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>"""
        )
    block = "\n".join(aliases)
    manifest = ROOT / "AndroidManifest.xml"
    text = manifest.read_text()
    text = re.sub(
        r"(<!-- ICON-ALIASES-START -->).*?(\n\s*<!-- ICON-ALIASES-END -->)",
        lambda m: m.group(1) + "\n" + block + m.group(2),
        text,
        flags=re.S,
    )
    manifest.write_text(text)
    print(f"Generated {len(ICONS)} icons")


if __name__ == "__main__":
    main()
