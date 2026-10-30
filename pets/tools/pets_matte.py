"""Offline background removal for the Pets project.

Pipeline for one RGB frame:
  1. Anime-specific segmentation network (ISNet-IS, ONNX, runs on CPU) -> soft alpha.
  2. Guided-filter refinement so the alpha follows the real line-art edges.
  3. Optional "ghost suppression" for double-exposure clips (see ghost_mask()).
  4. Alpha contrast curve + small defringe.
  5. Foreground colour estimation (blur-fusion) so edge pixels do not carry the
     original white / coloured background (no halos on dark wallpapers).

Everything here is pure numpy / OpenCV / onnxruntime, no network access needed
once the model file has been downloaded (see tools/README.md).
"""
from __future__ import annotations

import cv2
import numpy as np
import onnxruntime as ort

MODEL_URL = "https://huggingface.co/skytnt/anime-seg/resolve/main/isnetis.onnx"


class Segmenter:
    def __init__(self, model_path: str, size: int = 1024, threads: int = 0):
        so = ort.SessionOptions()
        if threads:
            so.intra_op_num_threads = threads
        self.sess = ort.InferenceSession(model_path, so, providers=["CPUExecutionProvider"])
        self.inp = self.sess.get_inputs()[0].name
        self.size = size

    def __call__(self, rgb: np.ndarray) -> np.ndarray:
        """rgb uint8 HxWx3 -> float32 alpha HxW in [0,1]."""
        h, w = rgb.shape[:2]
        s = self.size
        r = s / max(h, w)
        nh, nw = int(round(h * r)), int(round(w * r))
        x = np.zeros((s, s, 3), np.float32)
        y0, x0 = (s - nh) // 2, (s - nw) // 2
        x[y0:y0 + nh, x0:x0 + nw] = cv2.resize(rgb, (nw, nh), interpolation=cv2.INTER_AREA).astype(np.float32) / 255.0
        out = self.sess.run(None, {self.inp: x.transpose(2, 0, 1)[None]})[0][0, 0]
        a = out[y0:y0 + nh, x0:x0 + nw]
        return np.clip(cv2.resize(a, (w, h), interpolation=cv2.INTER_CUBIC), 0, 1).astype(np.float32)


def guided_filter(guide: np.ndarray, src: np.ndarray, r: int, eps: float) -> np.ndarray:
    """Gray-guide guided filter (He et al.). guide/src float32 HxW in [0,1]."""
    k = (2 * r + 1, 2 * r + 1)
    mean = lambda x: cv2.boxFilter(x, -1, k, normalize=True, borderType=cv2.BORDER_REPLICATE)
    mi, mp = mean(guide), mean(src)
    cov = mean(guide * src) - mi * mp
    var = mean(guide * guide) - mi * mi
    a = cov / (var + eps)
    b = mp - a * mi
    return mean(a) * guide + mean(b)


def estimate_foreground(rgb: np.ndarray, alpha: np.ndarray, r1: int = 41, r2: int = 6) -> np.ndarray:
    """Blur-fusion foreground estimation (Germer et al. 2020). Returns float32 RGB in [0,1]."""
    img = rgb.astype(np.float32) / 255.0
    a = alpha[..., None]

    def blur(x, r):
        return cv2.blur(x, (2 * r + 1, 2 * r + 1))

    def one_pass(F_in, B_in, r):
        a_ = np.maximum(blur(alpha, r)[..., None], 1e-4)
        F = blur(F_in * a, r) / a_
        ia = np.maximum(blur(1 - alpha, r)[..., None], 1e-4)
        B = blur(B_in * (1 - a), r) / ia
        return F, B

    F, B = one_pass(img, img, r1)
    F, B = one_pass(F, B, r2)
    F = F + a * (img - a * F - (1 - a) * B)
    # Where we are (almost) certain, trust the original pixel.
    F = np.where(a > 0.985, img, F)
    return np.clip(F, 0, 1)


def ghost_mask(rgb: np.ndarray, alpha: np.ndarray, ink_thr: float = 0.60, close: int = 14,
               min_area: int = 120, grow: int = 1) -> np.ndarray:
    """Mask of the *sharp* (opaque) layer for double-exposure clips.

    Those clips overlay a large ~40 % opacity copy of the character behind the
    sharp one. Distance-from-white ("ink" = 1 - min(R,G,B)) of the faded copy is
    bounded by its opacity (<~0.5) while the sharp copy reaches 0.6-1.0, so
    ink > 0.6 seeds only the sharp layer. Pale interiors (skin, white ribbons) are
    recovered by closing the seed blob and filling enclosed holes.
    Returns float32 HxW in {0,1}.
    """
    f = rgb.astype(np.float32) / 255.0
    seed = (((1.0 - f.min(axis=2)) > ink_thr) & (alpha > 0.5)).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(seed, connectivity=8)
    keep = np.zeros(n, bool)
    keep[1:] = stats[1:, cv2.CC_STAT_AREA] >= min_area
    # Coloured wedge stripes are big *flat* blobs with no internal line-art; characters are not.
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY).astype(np.float32) / 255.0
    gx, gy = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3), cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    edge = (np.hypot(gx, gy) > 0.35).astype(np.float32)
    for i in range(1, n):
        if keep[i]:
            m = lab == i
            area = float(stats[i, cv2.CC_STAT_AREA])
            if area > 2500 and edge[m].sum() / area < 0.02:
                keep[i] = False
    seed = keep[lab].astype(np.uint8)
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * close + 1, 2 * close + 1))
    closed = cv2.morphologyEx(cv2.copyMakeBorder(seed, close, close, close, close, cv2.BORDER_CONSTANT, value=0),
                              cv2.MORPH_CLOSE, k)
    # Re-attach the frame border so holes opening towards the (cropped) edge count as enclosed
    closed = closed[close:-close, close:-close]
    h, w = closed.shape
    inv = np.pad(1 - closed, 1, constant_values=1).astype(np.uint8)
    cv2.floodFill(inv, np.zeros((h + 4, w + 4), np.uint8), (0, 0), 2)
    filled = (inv[1:-1, 1:-1] != 2).astype(np.uint8)
    if grow:
        filled = cv2.dilate(filled, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * grow + 1, 2 * grow + 1)))
    return filled.astype(np.float32)


def wedge_mask(rgb: np.ndarray, colors, tol: float = 55.0, min_area: int = 1200) -> np.ndarray:
    """Regions of the known flat stripe colours that touch the frame border (uint8 0/1).
    Used for clips whose stripe colour is too close to the character's clothing for the
    network to reject it."""
    img = cv2.GaussianBlur(rgb, (0, 0), 1.0).astype(np.float32)
    m = np.zeros(rgb.shape[:2], bool)
    for c in colors:
        m |= np.linalg.norm(img - np.array(c, np.float32), axis=2) < tol
    m = cv2.morphologyEx(m.astype(np.uint8), cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    n, lab, st, _ = cv2.connectedComponentsWithStats(m, connectivity=4)
    h, w = m.shape
    out = np.zeros_like(m)
    for i in range(1, n):
        x, y, bw, bh, a = st[i]
        touches = x == 0 or y == 0 or x + bw >= w or y + bh >= h
        if touches and a >= min_area:
            out[lab == i] = 1
    return cv2.dilate(out, np.ones((5, 5), np.uint8))


def matte_frame(seg: Segmenter, rgb: np.ndarray, ghost: bool = False, refine_r: int = 3, gate: int = 3,
                lo: float = 0.12, hi: float = 0.92, bg_colors=None) -> np.ndarray:
    """Return RGBA uint8 (straight alpha) for one frame."""
    a0 = seg(rgb)
    if ghost:
        # Pass 1 found the sharp layer's region; whiten everything farther than
        # `reach` px from it (ghost copy, coloured wedges) and segment again so the
        # network sees an ordinary character-on-white shot and recovers pale hands.
        reach = max(6, int(round(rgb.shape[1] * 0.017)))
        region = ghost_mask(rgb, a0)
        near = cv2.dilate(region, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * reach + 1, 2 * reach + 1)))
        near = cv2.GaussianBlur(near, (0, 0), 2.0)[..., None]
        clean = (rgb * near + 255.0 * (1 - near)).astype(np.uint8)
        # Final gate hugs the sharp layer so faint drop-shadow / ghost residue forms no pale fringe.
        tight = cv2.dilate(region, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * gate + 1, 2 * gate + 1)))
        a0 = seg(clean) * cv2.GaussianBlur(tight, (0, 0), 1.2)
    if bg_colors:
        a0 = a0 * (1.0 - cv2.GaussianBlur(wedge_mask(rgb, bg_colors).astype(np.float32), (0, 0), 1.0))
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY).astype(np.float32) / 255.0
    a1 = np.clip(guided_filter(gray, a0, refine_r, 1e-3), 0, 1)
    a = np.clip((a1 - lo) / (hi - lo), 0, 1)
    F = estimate_foreground(rgb, a)
    out = np.dstack([(F * 255 + 0.5).astype(np.uint8), (a * 255 + 0.5).astype(np.uint8)])
    out[out[..., 3] == 0, :3] = 0
    return out


def composite(rgba: np.ndarray, bg=(40, 40, 60)) -> np.ndarray:
    a = rgba[..., 3:4].astype(np.float32) / 255.0
    return (rgba[..., :3] * a + np.array(bg, np.float32) * (1 - a)).astype(np.uint8)
