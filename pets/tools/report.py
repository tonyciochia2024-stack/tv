#!/usr/bin/env python3
"""Writes REPORT.md: which videos/characters were processed and how clean each result is."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
cfg = json.load(open(ROOT / "segments.json"))
notes = json.load(open(ROOT / "qa_notes.json")) if (ROOT / "qa_notes.json").exists() else {}
man = json.load(open(ROOT / "app/src/main/assets/pets/manifest.json"))["characters"]
byid = {c["id"]: c for c in man}
rows, n = [], 0
for v in cfg["videos"]:
    for ci, c in enumerate(v["characters"]):
        n += 1
        cid = f"char{n:02d}"
        m = byid.get(cid)
        pk = ROOT / "app/src/main/assets/pets" / cid / "anim.bin"
        rows.append(f"| {cid} | {Path(v['file']).name} | {c['start']:.2f}–{c['end']:.2f} s | "
                    f"{'ghost-layer' if v['ghost'] else 'standard'} | "
                    + (f"{m['frameCount']} @ {m['fps']:g} fps, {m['width']}×{m['height']}, {pk.stat().st_size/1e6:.1f} MB" if m else "NOT PROCESSED")
                    + f" | {notes.get(cid, 'clean in inspected frames')} |")
out = ["# Processing report", "",
       f"{len(cfg['videos'])} videos → {n} character segments.", "",
       "| id | source video | segment | mode | output | QA note |", "|---|---|---|---|---|---|", *rows]
(ROOT / "REPORT.md").write_text("\n".join(out) + "\n")
print("wrote REPORT.md")
