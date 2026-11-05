# Biscuit Clicker — project memory

## Version
- Current version: **7.43**
- Next build: **7.44**, then 7.45 ... (+0.01 each build)

## Changelog
- 7.43 — Skill Tree lighter rendering (real-phone result unconfirmed).

## Project
Private HTML5 game "Biscuit Clicker" (Android app name "Biscuit Clicker", package `com.drtonytt.biscuitclicker`).
Contents: `index.html` (single big page, ~3.8 MB), `media/` (mp4, webp, jpg, gzipped GLB `.bin`, audio), `android-shell/` (decoded Android wrapper: manifest, icons, WebView code), `build_apk.sh`.
Only the owner plays it, on a phone, so performance matters. Not released yet.

## Rules
- Always build on the newest version; each change builds on the previous one. Don't rebuild features from scratch. Integrate changes into the existing game and don't break anything.
- Bump APK version by 0.01 each time. Keep version line and short changelog here.
- Keep every file under 30 MB. Keep the repo private.
- Never commit the keystore, passwords or built APKs (gitignored).
- Owner sometimes writes Romanian. Reply in the language they use.
- If they ask for a prompt, give it in one code block.

## Design decisions (don't change unless asked)
- 120 FPS Mode toggle stays cosmetic. Max 3 visible biscuits. Phone biscuit size 200px.
- Weather events are a core feature, no on/off setting.
- Main font Times New Roman, basic font as fallback. No look-alike substitutes.
- Prestige available from Level 1,000 (never rises), gives 5 Biscuit Stars per full 1,000 levels. After prestige, menus stay unlocked, but producers and upgrades unlock by level again and purchases reset.
- Skill Tree: 1 skill point per 10 levels; next skill discovered only after previous is maxed; undiscovered skills hidden; purple mystical style; no cosmetic branches.
- Themes in Settings > Theme: Warm bakery and Night bakery (default look, never change), Human Bakery, Carved Oak, and 10 more. Other themes must look human and simple, not AI-made.
- Upgrade Bench sorted cheapest to most expensive.
- Shop music has a 15-second Preview button. Only free-licence music, credits in Settings > Music credits. Stats page mascot is Loafy (owner's own pixel-art loaf).
- Avoid copyrightable content (no copyrighted characters, music, videos). Still to replace later: music Toon and Hoax, Digi Car van branding, golden videos, Minecraft font.
- Open point: Skill Tree lagged on phone. Real-phone result after 7.43 unconfirmed. Prefer lighter rendering.

## Building the APK
Big files can't be attached in Code (30 MB limit), so APK is built from this repo:
`./build_apk.sh <versionName> <keystore> <alias> <storepass> [keypass]`
It combines index.html + media/ + android-shell/, bumps versionCode/versionName, zipaligns, signs with apksigner. Must be signed with the owner's original key so it installs over the current version.
1. Per build the owner attaches only `biscuitclicker-release.keystore` in chat and gives alias + password. If any is missing, ask; never guess.
2. Test game logic first (headless browser, no console errors).
3. Run build_apk.sh, then verify with `apksigner verify` and `aapt dump badging` (signature + version).
4. Be honest about what could not be verified on a real phone.

## Delivery
- When a change is done, commit and push the source (not the APK) with a short message.
- Always send the finished, signed APK in chat, named `BiscuitClicker-7_XX.apk`. Do NOT send HTML files as attachments.
- End each reply with 2-3 lines: what changed, what could not be checked.
