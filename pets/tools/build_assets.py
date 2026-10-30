#!/usr/bin/env python3
"""Package finalized characters (build/out/*) into the Android assets + manifest.

    python3 tools/build_assets.py [--out build/out] [--assets app/src/main/assets/pets] [--previews previews]

Writes   <assets>/manifest.json   and   <assets>/<id>/{anim.bin,thumb.webp}
         <previews>/<id>.webp / <id>_qa.png   (transparent previews + QA sheets for humans)
Characters are listed in config order; names are neutral ("Character 01") because official names are unknown.
"""
import argparse, json, shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ap = argparse.ArgumentParser()
ap.add_argument("--config", default=str(ROOT / "segments.json"))
ap.add_argument("--out", default=str(ROOT / "build" / "out"))
ap.add_argument("--assets", default=str(ROOT / "app" / "src" / "main" / "assets" / "pets"))
ap.add_argument("--previews", default=str(ROOT / "previews"))
a = ap.parse_args()

cfg = json.load(open(a.config))
assets, previews, out = Path(a.assets), Path(a.previews), Path(a.out)
if assets.exists():
    shutil.rmtree(assets)
assets.mkdir(parents=True)
previews.mkdir(exist_ok=True)

chars, n = [], 0
for v in cfg["videos"]:
    for ci, c in enumerate(v["characters"]):
        n += 1
        cid = f"char{n:02d}"
        d = out / cid
        if not (d / "info.json").exists():
            print("MISSING (not processed yet):", cid); continue
        info = json.loads((d / "info.json").read_text())
        (assets / cid).mkdir()
        shutil.copy(d / "anim.bin", assets / cid / "anim.bin")
        shutil.copy(d / "thumb.webp", assets / cid / "thumb.webp")
        # compact transparent animated preview, rebuilt from the packed frames
        from PIL import Image
        import io
        blob = (d / "anim.bin").read_bytes()
        frames = []
        for off, ln in info["offsets"]:
            im = Image.open(io.BytesIO(blob[off:off + ln])).convert("RGBA")
            frames.append(im.resize((round(im.width * 240 / im.height), 240), Image.LANCZOS))
        frames[0].save(previews / f"{cid}.webp", save_all=True, append_images=frames[1:], duration=round(1000 / info["fps"]),
                       loop=0, quality=45, alpha_quality=60, method=3)
        from PIL import Image as _I
        _I.open(d / "qa.png").convert("RGB").save(previews / f"{cid}_qa.jpg", quality=86)
        chars.append({
            "id": cid, "name": f"Character {n:02d}",
            "width": info["width"], "height": info["height"], "fps": info["fps"],
            "frameCount": info["frames"], "frameDurationMs": round(1000 / info["fps"], 2),
            "anim": f"pets/{cid}/anim.bin", "thumb": f"pets/{cid}/thumb.webp",
            "frameOffsets": info["offsets"],
            "source": {"video": Path(v["file"]).name, "start": c["start"], "end": c["end"], "ghostLayerRemoved": v["ghost"]},
        })
json.dump({"version": 1, "format": "webp-rgba-frames-in-pack", "characters": chars}, open(assets / "manifest.json", "w"), separators=(",", ":"))
print(f"{len(chars)} characters, {sum(c['frameCount'] for c in chars)} frames, "
      f"{sum((assets / c['id'] / 'anim.bin').stat().st_size for c in chars) / 1e6:.1f} MB packed")
