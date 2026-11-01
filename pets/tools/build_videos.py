#!/usr/bin/env python3
"""Turn the supplied screen recordings into seamless-loop pet videos + manifest for the app.

    python3 tools/build_videos.py --src <dir with mp4s> [--out app/src/main/assets/pets] [--xfade 0.6] [--crf 19]

For every mp4 (sorted by file name = recording time):
  * constant 30 fps (the 60/120 fps files only repeat frames), H.264 High, AAC, faststart
  * loop made seamless by cross-fading the last `xfade` seconds into the first `xfade` seconds
    (video AND song), so the clip's last frame flows straight into its first frame
  * low-res sources (< 900 px wide) get a Lanczos 2x upscale + light sharpen
  * poster.jpg + manifest.json entry
"""
import argparse, json, re, shutil, subprocess
from pathlib import Path

ap = argparse.ArgumentParser()
ap.add_argument("--src", required=True)
ap.add_argument("--out", default=str(Path(__file__).resolve().parent.parent / "app/src/main/assets/pets"))
ap.add_argument("--xfade", type=float, default=0.6)
ap.add_argument("--crf", type=int, default=19)
ap.add_argument("--preset", default="medium")
ap.add_argument("--only", nargs="*")
ap.add_argument("--pack", help="build an importable pet PACK with this name into dist/packs/<name>/ (+ .zip) instead of the app assets")
ap.add_argument("--first-id", type=int, default=1, help="pack mode: number of the first pet in this pack (e.g. 35)")
ap.add_argument("--names", help="pack mode: JSON list, one {name, characters} per video in recording-time order")
ap.add_argument("--crop-bottom", type=float, default=0.0, help="pack mode: crop this fraction off the bottom of every video (watermarks)")
a = ap.parse_args()

def probe(p):
    o = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=codec_type,width,height,duration", "-of", "json", str(p)],
                       capture_output=True, text=True).stdout
    s = json.loads(o)["streams"]
    v = next(x for x in s if x["codec_type"] == "video"); au = next((x for x in s if x["codec_type"] == "audio"), None)
    dur = min(float(v["duration"]), float(au["duration"])) if au else float(v["duration"])
    return int(v["width"]), int(v["height"]), dur, au is not None

_opts = json.load(open(Path(__file__).resolve().parent / "video_opts.json")) if (Path(__file__).resolve().parent / "video_opts.json").exists() else {}
files = sorted(Path(a.src).glob("*.mp4"), key=lambda p: re.search(r"(\d{8}_\d{6})", p.name).group(1))
ROOT = Path(__file__).resolve().parent.parent
if a.pack:
    out = ROOT / "dist" / "packs" / a.pack
    shutil.rmtree(out, ignore_errors=True)
else:
    out = Path(a.out)
out.mkdir(parents=True, exist_ok=True)
_pack_names = json.load(open(a.names)) if a.names else []
man = []
d = a.xfade
for i, f in enumerate(files, 1):
    pid = f"pet{(a.first_id + i - 1) if a.pack else i:02d}"
    w, h, T, has_a = probe(f)
    if a.only and pid not in a.only:
        continue
    ts = re.search(r"(\d{8}_\d{6})", f.name).group(1)
    o = dict(_opts.get(ts, {}))
    if a.pack and a.crop_bottom and "crop_bottom" not in o: o["crop_bottom"] = a.crop_bottom
    crop = ""
    if o.get("crop_bottom"):                         # e.g. a baked-in watermark mosaic along the bottom
        h = int(h * (1 - o["crop_bottom"])) // 2 * 2
        crop = f"crop={w}:{h}:0:0,"
    if o.get("end"):                                 # e.g. cut a trailing end card
        T = min(T, o["end"])
    up = w < 900
    sc = f"scale={w*2}:{h*2}:flags=lanczos,unsharp=5:5:0.6," if up else ""
    ow, oh = (w * 2, h * 2) if up else (w, h)
    ow, oh = ow // 2 * 2, oh // 2 * 2
    T = T - 0.05
    dst = out / pid; dst.mkdir(exist_ok=True)
    lg = o.get("logo")
    pre, srcv = "", "[0:v]"
    if lg:   # cover an animated watermark: soft-blurred copy of the art just above it, feathered in during [t0, t1]
        X, Y, Wd, Hd = lg["x"], lg["y"], lg["w"], lg["h"]
        pre = (f"[0:v]split=2[la][lb];[lb]crop=iw*{Wd}:ih*{Hd}:iw*{X}:ih*({Y}-{Hd}-0.03),gblur=sigma=9[lf];"
               f"[1:v]format=gray[mg];[mg][lf]scale2ref[lm][lf2];[lf2][lm]alphamerge[lfa];"
               f"[la][lfa]overlay=x=main_w*{X}:y=main_h*{Y}:enable='between(t,{lg['t0']},{lg['t1']})'[src];")
        srcv = "[src]"
    base = f"{pre}{srcv}{crop}fps=30,{sc}scale={ow}:{oh},format=yuv420p,setsar=1,split=3[v1][v2][v3];"
    vf = (f"{base}[v1]trim=start={d}:end={T-d},setpts=PTS-STARTPTS[vm];"
          f"[v2]trim=start={T-d}:end={T},setpts=PTS-STARTPTS[vt];"
          f"[v3]trim=start=0:end={d},setpts=PTS-STARTPTS[vh];"
          f"[vt][vh]xfade=transition=fade:duration={d}:offset=0[vx];[vm][vx]concat=n=2:v=1:a=0[vout]")
    af = (f";[0:a]aresample=48000,asplit=3[a1][a2][a3];"
          f"[a1]atrim=start={d}:end={T-d},asetpts=PTS-STARTPTS[am];"
          f"[a2]atrim=start={T-d}:end={T},asetpts=PTS-STARTPTS[at];"
          f"[a3]atrim=start=0:end={d},asetpts=PTS-STARTPTS[ah];"
          f"[at]afade=t=out:st=0:d={d}:curve=qsin[atf];[ah]afade=t=in:st=0:d={d}:curve=qsin[ahf];"
          f"[atf][ahf]amix=inputs=2:duration=longest:normalize=0[ax];[am][ax]concat=n=2:v=0:a=1,loudnorm=I=-18:TP=-1.5:LRA=11[aout]") if has_a else ""
    cmd = ["ffmpeg", "-v", "error", "-y", "-i", str(f)] + (["-loop", "1", "-framerate", "30", "-i", str(Path(__file__).resolve().parent / "logo_mask.png")] if lg else []) + ["-filter_complex", vf + af, "-map", "[vout]"]
    if has_a: cmd += ["-map", "[aout]", "-c:a", "aac", "-b:a", "160k", "-ar", "48000"]
    cmd += ["-c:v", "libx264", "-preset", a.preset, "-crf", str(a.crf), "-profile:v", "high", "-pix_fmt", "yuv420p",
            "-shortest", "-movflags", "+faststart", "-r", "30", str(dst / "loop.mp4")]
    subprocess.run(cmd, check=True)
    L = T - d
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{L*0.62:.2f}", "-i", str(dst / "loop.mp4"), "-frames:v", "1",
                    "-vf", "scale=720:-2", "-q:v", "3", str(dst / "poster.jpg")], check=True)
    man.append({"id": pid, "name": f"Pet {i:02d}", "video": f"{'' if a.pack else 'pets/'}{pid}/loop.mp4", "poster": f"{'' if a.pack else 'pets/'}{pid}/poster.jpg",
                "width": ow, "height": oh, "durationMs": int(L * 1000), "hasAudio": has_a, "source": f.name,
                "bytes": (dst / "loop.mp4").stat().st_size})
    print(pid, f"{ow}x{oh}", f"{L:.2f}s", f"{man[-1]['bytes']/1e6:.1f} MB", flush=True)
mf = out / ("pack.json" if a.pack else "manifest.json")
if a.only and mf.exists():
    old = {m["id"]: m for m in json.load(open(mf))["pets"]}
    old.update({m["id"]: m for m in man}); man = [old[k] for k in sorted(old)]
if a.pack:
    for k, m_ in enumerate(man):
        if k < len(_pack_names):
            m_["name"] = _pack_names[k]["name"]; m_["characters"] = _pack_names[k].get("characters", [])
_nm = Path(__file__).resolve().parent / "pet_names.json"
if _nm.exists() and not a.pack:
    _n = json.load(open(_nm))
    for m_ in man:
        if m_["id"] in _n: m_["name"] = _n[m_["id"]]["name"]; m_["characters"] = _n[m_["id"]]["characters"]
json.dump({"version": 2, "format": "mp4-loop", "crossfadeSec": d, "pets": man}, open(mf, "w"), indent=1)
print(len(man), "pets,", f"{sum(m['bytes'] for m in man)/1e6:.0f} MB")

if a.pack:   # zip with STORE (mp4 is already compressed) and split into <= 380 MB parts so each part can be sent/copied easily
    import zipfile
    LIMIT = 380 * 1024 * 1024
    parts, cur, size = [], [], 0
    pets = [m for m in man]
    groups = []
    for m_ in pets:
        sz = m_["bytes"] + 200_000
        if cur and size + sz > LIMIT: groups.append(cur); cur, size = [], 0
        cur.append(m_); size += sz
    if cur: groups.append(cur)
    for gi, g in enumerate(groups, 1):
        name = f"{a.pack}" if len(groups) == 1 else f"{a.pack}-part{gi}"
        pdir = out.parent / name
        if len(groups) > 1:
            shutil.rmtree(pdir, ignore_errors=True); pdir.mkdir(parents=True)
            for m_ in g: shutil.copytree(out / m_["id"], pdir / m_["id"])
            json.dump({"version": 2, "format": "mp4-loop", "crossfadeSec": d, "pets": g}, open(pdir / "pack.json", "w"), indent=1)
        zp = out.parent / f"{name}.zip"
        with zipfile.ZipFile(zp, "w", zipfile.ZIP_STORED) as z:
            for fpath in sorted((pdir if len(groups) > 1 else out).rglob("*")):
                if fpath.is_file(): z.write(fpath, f"{name}/{fpath.relative_to(pdir if len(groups) > 1 else out)}")
        print("zip", zp.name, f"{zp.stat().st_size/1e6:.0f} MB", len(g), "pets")
    if len(groups) > 1: shutil.rmtree(out, ignore_errors=True)
