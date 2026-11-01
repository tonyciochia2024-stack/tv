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
a = ap.parse_args()

def probe(p):
    o = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=codec_type,width,height,duration", "-of", "json", str(p)],
                       capture_output=True, text=True).stdout
    s = json.loads(o)["streams"]
    v = next(x for x in s if x["codec_type"] == "video"); au = next((x for x in s if x["codec_type"] == "audio"), None)
    dur = min(float(v["duration"]), float(au["duration"])) if au else float(v["duration"])
    return int(v["width"]), int(v["height"]), dur, au is not None

files = sorted(Path(a.src).glob("*.mp4"), key=lambda p: re.search(r"(\d{8}_\d{6})", p.name).group(1))
out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
man = []
d = a.xfade
for i, f in enumerate(files, 1):
    pid = f"pet{i:02d}"
    w, h, T, has_a = probe(f)
    if a.only and pid not in a.only:
        continue
    up = w < 900
    sc = f"scale={w*2}:{h*2}:flags=lanczos,unsharp=5:5:0.6," if up else ""
    ow, oh = (w * 2, h * 2) if up else (w, h)
    ow, oh = ow // 2 * 2, oh // 2 * 2
    T = T - 0.05
    dst = out / pid; dst.mkdir(exist_ok=True)
    base = f"[0:v]fps=30,{sc}scale={ow}:{oh},format=yuv420p,setsar=1,split=3[v1][v2][v3];"
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
    cmd = ["ffmpeg", "-v", "error", "-y", "-i", str(f), "-filter_complex", vf + af, "-map", "[vout]"]
    if has_a: cmd += ["-map", "[aout]", "-c:a", "aac", "-b:a", "160k", "-ar", "48000"]
    cmd += ["-c:v", "libx264", "-preset", a.preset, "-crf", str(a.crf), "-profile:v", "high", "-pix_fmt", "yuv420p",
            "-shortest", "-movflags", "+faststart", "-r", "30", str(dst / "loop.mp4")]
    subprocess.run(cmd, check=True)
    L = T - d
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{L*0.62:.2f}", "-i", str(dst / "loop.mp4"), "-frames:v", "1",
                    "-vf", "scale=720:-2", "-q:v", "3", str(dst / "poster.jpg")], check=True)
    man.append({"id": pid, "name": f"Pet {i:02d}", "video": f"pets/{pid}/loop.mp4", "poster": f"pets/{pid}/poster.jpg",
                "width": ow, "height": oh, "durationMs": int(L * 1000), "hasAudio": has_a, "source": f.name,
                "bytes": (dst / "loop.mp4").stat().st_size})
    print(pid, f"{ow}x{oh}", f"{L:.2f}s", f"{man[-1]['bytes']/1e6:.1f} MB", flush=True)
mf = out / "manifest.json"
if a.only and mf.exists():
    old = {m["id"]: m for m in json.load(open(mf))["pets"]}
    old.update({m["id"]: m for m in man}); man = [old[k] for k in sorted(old)]
json.dump({"version": 2, "format": "mp4-loop", "crossfadeSec": d, "pets": man}, open(mf, "w"), indent=1)
print(len(man), "pets,", f"{sum(m['bytes'] for m in man)/1e6:.0f} MB")
