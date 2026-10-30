# Pets – offline asset pipeline

Everything here runs **before** the APK is built. The app itself never removes backgrounds and never needs a network.

## Requirements
`ffmpeg`/`ffprobe`, Python 3.10+, `pip install numpy opencv-python-headless onnxruntime pillow scipy`
and the anime segmentation model (176 MB, MIT-licensed *SkyTNT/anime-segmentation* `isnetis.onnx`):

    mkdir -p tools/models
    curl -L -o tools/models/isnetis.onnx https://huggingface.co/skytnt/anime-seg/resolve/main/isnetis.onnx

## Adding / re-processing videos
1. Put videos in `source_videos/` and describe them in `segments.json`:
   `{"key":"c15","file":"source_videos/x.mp4","ghost":false,"bg_colors":null,"characters":[{"start":1.6,"end":10.5}]}`
   * one entry in `characters` per character shown in the file (`start`/`end` in seconds, trim transition wipes out);
   * `ghost: true` for double-exposure clips (a translucent copy of the character behind the sharp one);
   * optional `bg_colors` (`[[r,g,b],...]`) removes border-connected stripe regions of that colour *inside the matting step*.
2. `python3 tools/process_videos.py matte`      (slow: ~1 s per frame per core; resumable; results in `build/cache`)
3. `python3 tools/process_videos.py finalize`   (union-bbox crop, deflicker, wedge clean-up, WebP encode, thumbnails, QA sheets in `build/out/<id>/qa.png`)
4. **Look at every `qa.png`** (checkerboard / dark / yellow backgrounds) before shipping.
5. `python3 tools/build_assets.py`              (writes `app/src/main/assets/pets/manifest.json` + packs)
6. `python3 tools/make_icon.py`                 (launcher icons from `branding/icon_source.jpg`)

## How the background removal works (`pets_matte.py`)
| Step | Why |
|---|---|
| ISNet-IS anime segmentation (1024 px) | Backgrounds are white **and** coloured diagonal stripes; chroma keying is impossible (white hair/dresses). |
| Guided-filter alpha refinement (r=3) | Snaps the soft network mask to the real line-art edges. |
| Alpha curve (0.12 → 0.92) | Removes the faint grey "cloud" around the silhouette. |
| Blur-fusion foreground estimation | Edge pixels get *character* colour, not white/stripe colour → no halos on dark wallpapers. |
| Ghost mode: `1-min(RGB) > 0.6` seeds, close + fill, whiten the rest, 2nd network pass, tight gate | A faded ~40 % copy never gets as inky as the sharp copy; used to isolate the sharp layer. |
| Wedge clean-up (finalize): hue from border + flatness + size + border contact | Stripe wedges whose colour resembles the clothing (e.g. blue on blue). |
| Single-frame alpha-spike removal | Kills one-frame flicker; real motion untouched. |
| One union bounding box per character, premultiplied area resize | Zero jitter, no colour fringes when scaling. |

## Output format
`anim.bin` = concatenated lossy-with-alpha WebP frames, random-accessed by the app using offsets stored in `manifest.json`
(`frameOffsets: [[offset,length],…]`). Frame timing is uniform (`fps`, default 15 = every 2nd frame of the 30 fps source).
