#!/usr/bin/env python3
"""Write tools/pet_names.json names into app/src/main/assets/pets/manifest.json (no re-encoding needed)."""
import json
from pathlib import Path
R = Path(__file__).resolve().parent.parent
names = json.load(open(R / "tools/pet_names.json"))
mf = R / "app/src/main/assets/pets/manifest.json"
m = json.load(open(mf))
for p in m["pets"]:
    n = names.get(p["id"])
    if n:
        p["name"] = n["name"]; p["characters"] = n["characters"]
json.dump(m, open(mf, "w"), indent=1)
print("renamed", sum(1 for p in m["pets"] if p["id"] in names), "pets")
