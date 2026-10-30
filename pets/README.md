# Pets – animated desktop pets for Android

Pick an anime character, tap **Show Pet**, go home: the character keeps dancing on your home screen and over other
apps as a **transparent, draggable** overlay. 25 characters, fully offline, no ads, no analytics, no `INTERNET` permission.

| | |
|---|---|
| Package | `com.pets.desktoppets` · name **Pets** · v1.2.0 |
| SDK | minSdk 26 (Android 8.0) · target/compile 35 |
| Stack | Kotlin 2.0.21, Jetpack Compose (Material 3), DataStore, a plain `View` + `WindowManager` overlay |
| APKs | `dist/Pets-1.2.0-release.apk` (132 MB, not committed: build it or get it from the chat) |

## Install
```
adb install -r dist/Pets-1.2.0-release.apk        # or copy the APK to the phone and open it
```
First start: open **Pets → Show Pet**; if the overlay permission is missing the app explains it and opens the system
screen *Display over other apps*. Return to the app (it re-checks on resume) and press **Show Pet** again.

## Behaviour (as specified)
* Movement: **drag only** – no wandering. Interaction: **none** – a tap on the pet does nothing (verified: position unchanged).
* Default size **160 dp tall** (aspect ratio kept), adjustable 80–320 dp in *Settings* – applied live. *Reset position* in Settings.
* Selection, size, position, restore preference are stored in DataStore (position as fractions, so rotation / resolution changes re-clamp).
* **Restore after reboot**: `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) restarts the foreground service if the user wanted the pet, the
  restore switch is on and the overlay permission is still granted. If Android refuses a background start, a notification offers a one-tap resume.
  A pet you **Hide** yourself is never restarted (`wantActive=false`). The service is `START_STICKY` and recovers from process death.
* UI state reflects the real service/overlay (`PetState`), never a saved flag alone: *Active / Starting / Hidden / Pet stopped*.
* Revoking the overlay permission stops the service cleanly (AppOps watcher); the rest of the app keeps working.
* Rendering stops while the screen is off or the pet is hidden; frames are decoded one at a time into two reusable bitmaps.

## New in 1.2.0
* Re-matted at full 1080 px source resolution with a new outline-bounded matte (no more soft glow halo around characters), foreground colours re-estimated, floating specks removed.
* Output 480 px high (was 360) at **30 fps** (was 15); cut-off frame edges are feathered so the pet fades out instead of ending in a hard line.
* Glow hearts on Characters 24/25 reduced (not fully removed). Not run on a device in this session; verified by inspecting the QA sheets.

## New in 1.1.0
* **Pet never blocks the app**: while the Pets UI is open the overlay lets touches through (`FLAG_NOT_TOUCHABLE`), so a big pet can no longer swallow taps meant for the controls under it. It is draggable again as soon as you leave the app.
* **Opacity** slider (30–100 %) and live size slider (writes are debounced instead of one per slider tick).
* **Surprise me**: optional random character each time you tap *Show Pet*.
* **Favourites** (heart on each card) with an *All / Favourites* filter.
* **Quick Settings tile** "Desktop pet": show/hide from the notification shade (same rules as the buttons; opens the app if the overlay permission is missing).
* **Dark theme** (follows the system), a dismissible first-run guide, content descriptions for the animated previews.
* Geometry/placement maths extracted into pure functions with **JVM unit tests** (`./gradlew :app:testDebugUnitTest`, 6 tests).
* Matte pipeline: isolated specks (stripe crumbs / noise) are removed in the finalize stage.

## Permissions
| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | draw the pet over the home screen / other apps (user grants it in system settings) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | keeps the overlay alive; Android requires a visible notification (has a *Hide pet* action) |
| `RECEIVE_BOOT_COMPLETED` | restore the pet after restart |
| `POST_NOTIFICATIONS` | Android 13+: shows the service notification (the pet works even if you deny it) |

## Build from source
Needs JDK 17+ and the Android SDK (platform 35, build-tools 35). Put the SDK path in `local.properties` (`sdk.dir=…`).
```
./gradlew :app:assembleDebug            # app/build/outputs/apk/debug
./gradlew :app:assembleRelease          # needs ../pets-release.jks (see app/build.gradle.kts); generate your own:
keytool -genkeypair -keystore pets-release.jks -alias pets -keyalg RSA -keysize 2048 -validity 10000
```
`settings.gradle.kts` lists Google's Maven-Central mirror first because Maven Central itself rate-limited the build machine (HTTP 429).
Processed characters are already in `app/src/main/assets/pets`. To (re)create them see **`tools/README.md`**
(background removal pipeline, adding more videos). Overview: `segments.json` → `process_videos.py matte/finalize` → `build_assets.py`.

## Troubleshooting (also shown in-app, Settings → Troubleshooting)
* Pet gone after reboot: allow *Run in background / Unrestricted battery*, enable *Autostart* (Xiaomi/Huawei/OPPO/Vivo/OnePlus), remove from Samsung “Sleeping apps”, allow notifications. Manual **Show Pet** always works.
* “Pet stopped” on the Home tab = you wanted it on but Android killed it; press **Show Pet**.
* A very large pet can cover controls; drag it away or lower the size with the slider.

## What is in the box
`app/` Android project · `tools/` offline pipeline · `segments.json` segment definitions · `previews/` transparent animated previews + QA sheets ·
`REPORT.md` per-character processing report · `branding/icon_source.jpg` the supplied chibi image (launcher icon source).

## Verification status – read this
**v1.1.0 additions** (touch pass-through while the app is open, opacity, shuffle, favourites, Quick Settings tile, dark theme, onboarding, monochrome icon):
verified by **compilation, 6 JVM unit tests (placement maths) and Android lint (0 errors)** only. I tried to re-run the emulator checks, but the freshly wiped
no-KVM emulator kept restarting its system server during boot (StorageStatsService race) and never accepted an install, so **these features have not been run on a device**.
Treat the table below as results for the **v1.0.0 build** – the drag/size/boot/kill/revocation code paths are unchanged in 1.1.0 except for the additions above.

Tested (v1.0.0 build) on an **Android 11 (API 30) x86 emulator running without hardware acceleration** (very slow; the system restarted a few times), release APK:

| Check | Result |
|---|---|
| Project compiles; debug + release APK build; release signature verifies (v2) | ✅ built here |
| App launches; Home / Characters / Settings render; supplied icon shown in-app | ✅ screenshots |
| Pet floats transparently over the launcher wallpaper **and over another app's UI** | ✅ screenshots |
| Default height = 160 dp (window 584×440 px at 440 dpi) | ✅ measured via `dumpsys window` |
| Drag moves the window; tap without drag does not | ✅ measured |
| Size slider resizes live (599×440 → 999×734 → 648×476) | ✅ measured |
| Selection persists across process kill; position/size stored | ✅ DataStore inspected |
| Hide stops service + overlay; simulated reboot does *not* revive a hidden pet | ✅ |
| Simulated `BOOT_COMPLETED` restores pet at saved position | ✅ (broadcast sent as root; **a real reboot was not performed**) |
| `kill -9` → service restarts and pet returns | ✅ |
| Overlay permission revoked while running → service stops, UI honest | ✅ (found & fixed a bug during this test) |
| Rotation → placement recomputed | ✅ window coordinates changed and returned (launcher itself did not rotate) |
| No INTERNET permission, no ad/analytics classes | ✅ manifest + dex inspected |
| Memory: PSS flat at ~36 MB over 8 min of animation | ✅ emulator numbers only |
| Launcher icon visually in the app drawer | ⚠️ not captured; resources inspected (adaptive icon built from the supplied image, plus a monochrome themed-icon layer in 1.1.0) |
| Real devices, OEM battery managers, real reboot, Android 12–15 foreground-start rules, CPU/battery draw, frame-rate smoothness | ❌ **untested** |

## Known limitations
* **Source material**: the 14 files are TikTok screen recordings of a dance edit. Each contains *two* characters (a ~0.5 s flash of the next one, a wipe, then the second) except three single-character clips with a translucent “ghost” double exposure → **25 characters**. They are named *Character 01…25* (official names unknown). Close-ups are cut off by the video frame; that cannot be recovered.
* Of the 3 attached images, one was a plain black screenshot (ignored) and two were identical copies of the red-haired chibi, used as the launcher icon.
* The source videos are not committed (`source_videos/` is git-ignored); put them there to re-run the pipeline.
* Camera cuts between close-up and full shot are part of the original animation, so the character changes size within a loop.
* Background removal is very good but **not perfect**: see `REPORT.md` for per-character notes (thin stripe slivers, ghost-clip fingertips, blue-on-blue hair). 15 fps (every 2nd source frame).
* The overlay window is rectangular (the character's union bounding box); touches on transparent corners of that rectangle are swallowed by the pet (Android offers no per-pixel pass-through). Everything outside it is untouched.
* Android may hide overlays on secure screens (banking apps, system dialogs) or while the keyguard is shown.
* The release APK uses a throw-away signing key included in the project; use your own key for distribution.
