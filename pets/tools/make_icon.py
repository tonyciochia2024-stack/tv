#!/usr/bin/env python3
"""Generate Android launcher icons from branding/icon_source.jpg (the supplied chibi image).
Only padding/scaling is applied; the artwork itself is untouched."""
from pathlib import Path
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
src = Image.open(ROOT / "branding" / "icon_source.jpg").convert("RGB")
a = np.asarray(src).astype(int)
edge = np.concatenate([a[:20].reshape(-1, 3), a[:, :20].reshape(-1, 3), a[:, -20:].reshape(-1, 3)])
bg = tuple(int(v) for v in np.median(edge, 0))
print("background colour", "#%02X%02X%02X" % bg)

def canvas(px, art_frac):
    """Square canvas, artwork (whole image) fitted in art_frac of it."""
    c = Image.new("RGB", (px, px), bg)
    s = px * art_frac / max(src.size)
    art = src.resize((round(src.width * s), round(src.height * s)), Image.LANCZOS)
    c.paste(art, ((px - art.width) // 2, (px - art.height) // 2))
    return c

dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, m in dens.items():
    d = RES / f"mipmap-{name}"; d.mkdir(parents=True, exist_ok=True)
    canvas(round(108 * m), 72 / 108).save(d / "ic_launcher_foreground.png")     # adaptive layer (art in 72dp safe zone)
    canvas(round(48 * m), 0.96).save(d / "ic_launcher.png")                      # legacy square
    canvas(round(48 * m), 0.96).save(d / "ic_launcher_round.png")
(RES / "values").mkdir(parents=True, exist_ok=True)
(RES / "values" / "ic_launcher_background.xml").write_text(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#%02X%02X%02X</color>\n</resources>\n' % bg)
(RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
for n in ("ic_launcher", "ic_launcher_round"):
    (RES / "mipmap-anydpi-v26" / f"{n}.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background"/>\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n</adaptive-icon>\n')
# in-app branding image (square, light)
d = RES / "drawable-nodpi"; d.mkdir(parents=True, exist_ok=True)
canvas(512, 1.0).save(d / "brand_chibi.png")
