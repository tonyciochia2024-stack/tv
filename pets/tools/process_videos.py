#!/usr/bin/env python3
"""Offline video -> transparent animation pipeline for Pets.

Usage (from the pets/ directory):

    python3 tools/process_videos.py matte    --config segments.json --cache build/cache [--only c01:0 ...] [--workers 2]
    python3 tools/process_videos.py finalize --config segments.json --cache build/cache --out build/out [--height 360]

`matte`    decodes each character segment at the configured fps, removes the
           background (see pets_matte.py) and stores full-resolution RGBA PNGs in the cache.
`finalize` removes single-frame alpha flicker, crops every frame of a character to the
           *same* union bounding box (so nothing jitters), resizes with premultiplied alpha,
           and writes lossy-with-alpha WebP frames plus thumbnail / preview / QA sheets.

Both stages are resumable: finished frames / characters are skipped.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pets_matte as pm  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
MODEL = os.environ.get("PETS_SEG_MODEL", str(ROOT / "tools" / "models" / "isnetis.onnx"))
WORK_W = 1080  # working width for matting (never upscaled)


def char_id(n: int) -> str:
    return f"char{n:02d}"


def enumerate_characters(cfg):
    n = 0
    for v in cfg["videos"]:
        for ci, c in enumerate(v["characters"]):
            n += 1
            yield n, v, ci, c


def probe_size(path: Path):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
                          "stream=width,height", "-of", "csv=p=0", str(path)], capture_output=True, text=True).stdout
    w, h = out.strip().split(",")
    return int(w), int(h)


def decode(path: Path, start: float, end: float, fps: float, width: int, height: int):
    """Yield RGB uint8 frames of [start, end) resampled to `fps`."""
    cmd = ["ffmpeg", "-v", "error", "-ss", f"{start:.3f}", "-t", f"{end - start:.3f}", "-i", str(path),
           "-vf", f"fps={fps},scale={width}:{height}:flags=lanczos", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    n = len(raw) // (width * height * 3)
    return np.frombuffer(raw, np.uint8)[: n * width * height * 3].reshape(n, height, width, 3)


# --------------------------------------------------------------------------- stage 1
def matte_character(job):
    n, vfile, ghost, start, end, fps, cache_dir, threads, bgc = job
    cid = char_id(n)
    d = Path(cache_dir) / cid
    d.mkdir(parents=True, exist_ok=True)
    sw, sh = probe_size(ROOT / vfile)
    w = min(WORK_W, sw)
    h = int(round(sh * w / sw / 2) * 2)
    frames = decode(ROOT / vfile, start, end, fps, w, h)
    seg = pm.Segmenter(MODEL, 1024, threads=threads)
    done = 0
    for i, rgb in enumerate(frames):
        out = d / f"{i:04d}.png"
        if out.exists():
            continue
        a0f = d / f"a0_{i:04d}.png"
        if a0f.exists():
            a0 = cv2.imread(str(a0f), cv2.IMREAD_GRAYSCALE).astype(np.float32) / 255.0
        else:
            a0 = pm.model_alpha(seg, rgb, ghost=ghost, bg_colors=bgc)
            cv2.imwrite(str(a0f), (a0 * 255 + 0.5).astype(np.uint8))
        rgba = pm.finish_alpha(rgb, a0, fill_holes=ghost)
        cv2.imwrite(str(out), cv2.cvtColor(rgba, cv2.COLOR_RGBA2BGRA), [cv2.IMWRITE_PNG_COMPRESSION, 1])
        done += 1
    (d / "meta.json").write_text(json.dumps({"frames": len(frames), "width": w, "height": h, "fps": fps}))
    return cid, len(frames), done


def cmd_matte(a):
    cfg = json.load(open(a.config))
    fps = cfg["fps"]
    jobs = []
    only = set(a.only or [])
    for n, v, ci, c in enumerate_characters(cfg):
        tag = f"{v['key']}:{ci}"
        if only and tag not in only and char_id(n) not in only:
            continue
        jobs.append((n, v["file"], v["ghost"], c["start"], c["end"], fps, a.cache, max(1, 4 // a.workers), v.get("bg_colors")))
    with ProcessPoolExecutor(a.workers) as ex:
        for cid, total, done in ex.map(matte_character, jobs):
            print(f"{cid}: {total} frames ({done} new)", flush=True)


# --------------------------------------------------------------------------- stage 2
def load_cached(d: Path):
    meta = json.loads((d / "meta.json").read_text())
    arr = [cv2.cvtColor(cv2.imread(str(d / f"{i:04d}.png"), cv2.IMREAD_UNCHANGED), cv2.COLOR_BGRA2RGBA)
           for i in range(meta["frames"])]
    return np.stack(arr), meta


def remove_wedges(frames: np.ndarray, min_area_frac: float = 0.03):
    """Remove flat coloured stripe wedges the network kept because they resemble the clothing.

    Wedge hues are estimated per character from saturated, flat, opaque pixels on the frame border.
    A frame then loses every region that (a) has such a hue, (b) is saturated and locally *flat*
    (smooth gradients allowed), (c) touches the frame border and (d) is large. Hair and clothing are
    fragmented into small flat patches by strand/outline edges, so they are protected."""
    n, h, w = frames.shape[:3]
    ring = np.zeros((h, w), bool)
    ring[:5] = ring[-5:] = True
    ring[:, :5] = ring[:, -5:] = True

    def analyse(f):
        rgb = np.ascontiguousarray(f[..., :3])
        hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)
        g = cv2.GaussianBlur(cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY), (0, 0), 1.0).astype(np.float32) / 255.0
        grad = np.hypot(cv2.Sobel(g, cv2.CV_32F, 1, 0), cv2.Sobel(g, cv2.CV_32F, 0, 1))
        base = (f[..., 3] > 128) & (hsv[..., 1] > 80) & (hsv[..., 2] > 60) & (grad < 0.10)
        return hsv[..., 0], base

    hist = np.zeros(180)
    for f in frames[:: max(1, n // 40)]:
        hue, base = analyse(f)
        hist += np.bincount(hue[base & ring], minlength=180)[:180]
    if hist.sum() < 400:
        return frames, 0
    sm = np.convolve(np.concatenate([hist[-8:], hist, hist[:8]]), np.ones(9), "same")[8:-8]
    good = np.zeros(180, bool)
    for _ in range(2):                         # up to two dominant stripe hues
        c = int(sm.argmax())
        if sm[c] < 0.10 * hist.sum():
            break
        for d in range(-10, 11):
            good[(c + d) % 180] = True
        for d in range(-14, 15):
            sm[(c + d) % 180] = 0
    out = frames.copy()
    removed = 0
    for t in range(n):
        hue, base = analyse(frames[t])
        m = (base & good[hue]).astype(np.uint8)
        m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
        cnt, lab, st, _ = cv2.connectedComponentsWithStats(m, connectivity=4)
        kill = np.zeros((h, w), np.uint8)
        taken = set()
        for i in range(1, cnt):
            x, y, bw, bh, area = st[i]
            if area >= min_area_frac * h * w * 0.4 and (x == 0 or y == 0 or x + bw >= w or y + bh >= h):
                kill[lab == i] = 1
                taken.add(i)
        if kill.any():
            # propagate to neighbouring same-hue patches that the character's line-art cut off from the border
            for _ in range(3):
                near = cv2.dilate(kill, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (21, 21)))
                grew = False
                for i in range(1, cnt):
                    if i in taken or st[i][4] < 0.004 * h * w or st[i][4] > 0.022 * h * w:
                        continue
                    sel = lab == i
                    if near[sel].mean() > 0.05:
                        kill[sel] = 1; taken.add(i); grew = True
                if not grew:
                    break
            kill = cv2.dilate(kill, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
            removed += int(kill.sum())
            a = out[t][..., 3].astype(np.float32) * (1 - cv2.GaussianBlur(kill.astype(np.float32), (0, 0), 1.0))
            out[t][..., 3] = a.astype(np.uint8)
    return out, removed


def remove_dust(frames: np.ndarray, min_frac: float = 0.0002):
    """Delete isolated specks of alpha (stripe crumbs, matting noise) smaller than min_frac of the frame."""
    n, h, w = frames.shape[:3]
    min_area = max(20, int(min_frac * h * w))
    removed = 0
    out = frames.copy()
    for t in range(n):
        m = (frames[t][..., 3] > 12).astype(np.uint8)
        cnt, lab, st, _ = cv2.connectedComponentsWithStats(m, connectivity=8)
        small = np.zeros(cnt, bool)
        small[1:] = st[1:, cv2.CC_STAT_AREA] < min_area
        kill = small[lab]
        if kill.any():
            removed += int(kill.sum())
            out[t][..., 3][kill] = 0
    return out, removed


def despark(frames: np.ndarray, radius: int = 9, thr: float = 0.16):
    """Remove small bright glow / sparkle blobs painted over the character (flagged clips only):
    pixels much brighter than their local median and nearly white-hot are replaced by that median."""
    out = frames.copy()
    k = 2 * radius + 1
    for t in range(len(frames)):
        rgb = np.ascontiguousarray(frames[t][..., :3])
        a = frames[t][..., 3] > 128
        med = cv2.medianBlur(rgb, k if k % 2 else k + 1)
        v = rgb.max(2).astype(np.float32) / 255
        vm = med.max(2).astype(np.float32) / 255
        glow = (v - vm > thr) & (v > 0.85) & a
        glow = cv2.dilate(glow.astype(np.uint8), np.ones((5, 5), np.uint8)) > 0
        glow &= a
        out[t][..., :3][glow] = med[glow]
    return out


def feather_cut_edges(crop: np.ndarray, frac: float = 0.06):
    """Per frame: where the character touches the crop border (cut by the video frame), fade alpha out
    over `frac` of the size so the pet never ends in a hard straight line."""
    n, h, w = crop.shape[:3]
    out = crop
    def ramp(L):
        r = max(4, int(L * frac))
        t = np.clip(np.arange(r, dtype=np.float32) / r, 0, 1)
        return t * t * (3 - 2 * t), r
    fb, rb = ramp(h); fw, rw = ramp(w)
    for i in range(n):
        al = out[i][..., 3]
        a = al.astype(np.float32)
        if (al[-2:, :] > 200).mean() > 0.04:
            a[h - rb:, :] *= fb[::-1][:, None]
        if (al[:, :2] > 200).mean() > 0.04:
            a[:, :rw] *= fw[None, :]
        if (al[:, -2:] > 200).mean() > 0.04:
            a[:, w - rw:] *= fw[::-1][None, :]
        out[i][..., 3] = a.astype(np.uint8)
    return out


def despark(frames: np.ndarray, radius: int = 9, thr: float = 0.16):
    """Remove small bright glow / sparkle blobs painted over the character (flagged clips only):
    pixels much brighter than their local median and nearly white-hot are replaced by that median."""
    out = frames.copy()
    k = 2 * radius + 1
    for t in range(len(frames)):
        rgb = np.ascontiguousarray(frames[t][..., :3])
        a = frames[t][..., 3] > 128
        med = cv2.medianBlur(rgb, k if k % 2 else k + 1)
        v = rgb.max(2).astype(np.float32) / 255
        vm = med.max(2).astype(np.float32) / 255
        glow = (v - vm > thr) & (v > 0.85) & a
        glow = cv2.dilate(glow.astype(np.uint8), np.ones((5, 5), np.uint8)) > 0
        glow &= a
        out[t][..., :3][glow] = med[glow]
    return out


def feather_cut_edges(crop: np.ndarray, frac: float = 0.07):
    """Where the character is cut off by the video frame (opaque pixels on the crop border in most frames),
    fade alpha out over `frac` of the size so the pet does not end in a hard straight line."""
    n, h, w = crop.shape[:3]
    out = crop
    def ramp(L):
        r = max(4, int(L * frac))
        t = np.clip(np.arange(r, dtype=np.float32) / r, 0, 1)
        return t * t * (3 - 2 * t), r
    cuts = {"bottom": (crop[:, -3:, :, 3] > 200).mean(), "left": (crop[:, :, :3, 3] > 200).mean(),
            "right": (crop[:, :, -3:, 3] > 200).mean()}
    a = out[..., 3].astype(np.float32)
    if cuts["bottom"] > 0.15:
        f, r = ramp(h); a[:, h - r:, :] *= f[::-1][None, :, None]
    if cuts["left"] > 0.15:
        f, r = ramp(w); a[:, :, :r] *= f[None, None, :]
    if cuts["right"] > 0.15:
        f, r = ramp(w); a[:, :, w - r:] *= f[::-1][None, None, :]
    out[..., 3] = a.astype(np.uint8)
    return out


def deflicker(rgba: np.ndarray) -> np.ndarray:
    """Remove single-frame alpha spikes: a pixel whose neighbours (t-1, t+1) agree but
    which itself deviates strongly is replaced by their mean. Real motion is untouched."""
    a = rgba[..., 3].astype(np.int16)
    out = rgba.copy()
    fixed = 0
    for t in range(1, len(a) - 1):
        p, c, nx = a[t - 1], a[t], a[t + 1]
        agree = np.abs(p - nx) < 40
        mean = (p + nx) // 2
        bad = agree & (np.abs(c - mean) > 90)
        if bad.any():
            fixed += int(bad.sum())
            out[t][..., 3][bad] = mean[bad].astype(np.uint8)
            # take colours from the neighbour that is more opaque where a hole was filled
            src = rgba[t - 1] if p.mean() >= nx.mean() else rgba[t + 1]
            fill = bad & (mean > c)
            out[t][..., :3][fill] = src[..., :3][fill]
    return out, fixed


def resize_premult(rgba: np.ndarray, size) -> np.ndarray:
    f = rgba.astype(np.float32)
    a = f[..., 3:4] / 255.0
    pm_ = np.dstack([f[..., :3] * a, f[..., 3:4]])
    r = cv2.resize(pm_, size, interpolation=cv2.INTER_AREA)
    ra = r[..., 3:4] / 255.0
    rgb = np.where(ra > 1e-3, r[..., :3] / np.maximum(ra, 1e-3), 0)
    return np.dstack([np.clip(rgb, 0, 255), r[..., 3:4]]).astype(np.uint8)


def encode_webp(rgba: np.ndarray, quality: int, alpha_quality: int = int(os.environ.get('PETS_ALPHA_Q', '60'))) -> bytes:
    from PIL import Image
    import io
    buf = io.BytesIO()
    Image.fromarray(rgba, "RGBA").save(buf, "WEBP", quality=quality, alpha_quality=alpha_quality, method=4, exact=False)
    return buf.getvalue()


def checker(h, w, s=12):
    yy, xx = np.indices((h, w))
    c = ((yy // s + xx // s) % 2) * 40 + 205
    return np.dstack([c, c, c]).astype(np.float32)


def over(rgba, bg):
    a = rgba[..., 3:4].astype(np.float32) / 255.0
    return (rgba[..., :3] * a + bg * (1 - a)).astype(np.uint8)


def finalize_character(job):
    n, cfgc, cache, out_root, height, quality, fps = job
    cid = char_id(n)
    out = Path(out_root) / cid
    if (out / "info.json").exists():
        return cid, "skip"
    frames, meta = load_cached(Path(cache) / cid)
    if cfgc.get("despark"):
        frames = despark(frames)
    frames, wedge_px = remove_wedges(frames) if cfgc.get("wedge_cleanup") else (frames, 0)
    frames, dust_px = remove_dust(frames)
    frames, fixed = deflicker(frames)
    alpha = frames[..., 3]
    # Union bounding box over the whole animation -> identical canvas for every frame.
    occ = (alpha > 12).any(axis=0)
    ys, xs = np.where(occ)
    pad = 3
    y0, y1 = max(0, ys.min() - pad), min(alpha.shape[1], ys.max() + 1 + pad)
    x0, x1 = max(0, xs.min() - pad), min(alpha.shape[2], xs.max() + 1 + pad)
    crop = frames[:, y0:y1, x0:x1].copy()
    crop = feather_cut_edges(crop)
    ch, cw = crop.shape[1:3]
    scale = height / ch
    size = (max(2, int(round(cw * scale / 2) * 2)), height)
    out.mkdir(parents=True, exist_ok=True)
    blobs, small = [], []
    for fr in crop:
        s = resize_premult(fr, size)
        small.append(s)
        blobs.append(encode_webp(s, quality))
    small = np.stack(small)
    with open(out / "anim.bin", "wb") as f:
        offsets, pos = [], 0
        for b in blobs:
            offsets.append([pos, len(b)])
            f.write(b)
            pos += len(b)
    # thumbnail: a frame from the 2nd half (close-up pose) rendered with transparency
    from PIL import Image
    ti = min(len(small) - 1, int(len(small) * 0.8))
    thumb = small[ti]
    th = 288
    tw = int(round(thumb.shape[1] * th / thumb.shape[0]))
    Image.fromarray(resize_premult(thumb, (tw, th)), "RGBA").save(out / "thumb.webp", quality=88, alpha_quality=95, method=4)
    # animated transparent preview (all frames, 2x subsampled in size for QA)
    pv = [Image.fromarray(resize_premult(s, (size[0] * 2 // 3, size[1] * 2 // 3)), "RGBA") for s in small[::1]]
    pv[0].save(out / "preview.webp", save_all=True, append_images=pv[1:], duration=int(round(1000 / fps)), loop=0,
               quality=70, alpha_quality=90, method=3)
    # QA sheet: 8 evenly spaced frames on checkerboard, dark and yellow backgrounds
    idx = np.linspace(0, len(small) - 1, 8).astype(int)
    qh = 200
    qw = int(round(size[0] * qh / size[1]))
    tiles = []
    for bgname, bg in (("chk", None), ("dark", (28, 28, 60)), ("yel", (250, 230, 60))):
        row = []
        for i in idx:
            r = resize_premult(small[i], (qw, qh))
            b = checker(qh, qw) if bg is None else np.full((qh, qw, 3), bg, np.float32)
            row.append(over(r, b))
        tiles.append(np.hstack(row))
    cv2.imwrite(str(out / "qa.png"), cv2.cvtColor(np.vstack(tiles), cv2.COLOR_RGB2BGR))
    total = sum(len(b) for b in blobs)
    info = {"id": cid, "frames": len(blobs), "width": size[0], "height": size[1], "fps": fps,
            "crop": [int(x0), int(y0), int(x1), int(y1)], "source_size": [meta["width"], meta["height"]],
            "bytes": total, "flicker_pixels_fixed": fixed, "dust_pixels_removed": dust_px, "wedge_pixels_removed": wedge_px, "offsets": offsets, "thumb_index": ti}
    (out / "info.json").write_text(json.dumps(info))
    return cid, f"{len(blobs)} frames {size[0]}x{size[1]} {total / 1e6:.2f} MB fixed={fixed}"


def cmd_finalize(a):
    cfg = json.load(open(a.config))
    only = set(a.only or [])
    jobs = []
    for n, v, ci, c in enumerate_characters(cfg):
        tag = f"{v['key']}:{ci}"
        if only and tag not in only and char_id(n) not in only:
            continue
        jobs.append((n, {**c, "wedge_cleanup": v.get("wedge_cleanup", False), "despark": v.get("despark", False)}, a.cache, a.out, a.height, a.quality, cfg["fps"]))
    with ProcessPoolExecutor(a.workers) as ex:
        for cid, msg in ex.map(finalize_character, jobs):
            print(cid, msg, flush=True)


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    m = sub.add_parser("matte")
    m.add_argument("--config", default=str(ROOT / "segments.json"))
    m.add_argument("--cache", default=str(ROOT / "build" / "cache"))
    m.add_argument("--only", nargs="*")
    m.add_argument("--workers", type=int, default=2)
    f = sub.add_parser("finalize")
    f.add_argument("--config", default=str(ROOT / "segments.json"))
    f.add_argument("--cache", default=str(ROOT / "build" / "cache"))
    f.add_argument("--out", default=str(ROOT / "build" / "out"))
    f.add_argument("--only", nargs="*")
    f.add_argument("--height", type=int, default=480)
    f.add_argument("--quality", type=int, default=72)
    f.add_argument("--workers", type=int, default=2)
    a = ap.parse_args()
    {"matte": cmd_matte, "finalize": cmd_finalize}[a.cmd](a)


if __name__ == "__main__":
    main()
