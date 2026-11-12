# LessScreenDS handoff: debugging on a PC over USB

This file moves the work from a cloud session (which could build but couldn't reach the device) to a Claude Code session on a PC with the Retroid Pocket Mini V2 plugged in by USB. A Claude session on the PC should read all of it first. Sections 1–3 are setup for the person; sections 4 onward are context for whoever picks up the code.

Delete this file before merging into `main`.

---

## 1. Get the code

```sh
git clone --recurse-submodules https://github.com/MarsInsanity/LessScreenDS.git
cd LessScreenDS
git checkout claude/dazzling-hamilton-abadf5
git submodule update --init --recursive
```

- Repository: public fork of `SapphireRhodonite/SeedlessDS`. Default branch `main`.
- Work branch: `claude/dazzling-hamilton-abadf5`, two commits ahead of `main` (focus layout; CI + updater). **Not merged, no pull request, no release yet.**
- `core/` is a git submodule from a different repository (`SapphireRhodonite/SeedlessDS-Core`). Don't change it. Read its state from this repository's JNI code instead (see section 6).

## 2. Build tools (one-time)

| Tool | Version | Notes |
|---|---|---|
| JDK | **25** | Required: `gradle/gradle-daemon-jvm.properties` asks for 25. Bytecode still targets 17. |
| Android SDK | platform 35, build-tools 36.0.0 | Easiest with Android Studio's SDK Manager, or `sdkmanager`. |
| NDK | 25.2.9519653 | SDK Manager → SDK Tools → "Show package details". |
| CMake | 3.22.1 | Same place. |
| platform-tools | latest | Provides `adb`. |

Point Gradle at the SDK in an untracked `local.properties` in the repo root:

```properties
# Windows (escape backslashes and the colon):
sdk.dir=C\:\\Users\\YOU\\AppData\\Local\\Android\\Sdk
# macOS / Linux:
# sdk.dir=/Users/you/Library/Android/sdk
```

Build and test (Windows: `gradlew.bat` instead of `./gradlew`):

```sh
./gradlew testDebugUnitTest      # 20 JVM tests, about a minute
./gradlew assembleDebug          # first build about 4–5 min (native core); later builds are faster
```

The first native build downloads libarchive, unarr and the LZMA SDK (needs internet). The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Device: USB debugging on the RP Mini V2

1. Settings → About device → tap **Build number** 7 times.
2. Settings → System → Developer options → turn on **USB debugging**.
3. Plug in by USB, accept **Allow USB debugging** (tick "Always allow").
4. `adb devices` should list the device as `device` (not `unauthorized`).

Wireless works too (Developer options → Wireless debugging → `adb pair <ip:port>` then `adb connect <ip:port>`), but USB is simpler.

### Everyday commands

The debug build's app ID is `com.lessscreends.app.debug`. It installs separately from any release build and from SeedlessDS.

```sh
# Install / update the build
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Logs from the app only (start the app first; same syntax in bash, zsh and PowerShell)
adb logcat --pid=$(adb shell pidof -s com.lessscreends.app.debug)
# Or by tag: game detection logs under "SeedlessEmu" (from the ROM file) and "lessscreen" (from RAM)
adb logcat -s SeedlessEmu:I lessscreen:I DsGlView:*

# Screenshot. In PowerShell don't use "exec-out ... > file", it corrupts binary output.
adb shell screencap -p /sdcard/shot.png
adb pull /sdcard/shot.png

# Start a game directly
adb shell am start -n com.lessscreends.app.debug/com.seedlessds.app.LaunchGame -e rom_path "/storage/emulated/0/ROMs/nds/Mario Kart DS.nds"

# Wipe all app data (library folders, settings, button mappings, profile flags)
adb shell pm clear com.lessscreends.app.debug
```

A screenshot shows the whole Android display as rendered, with the focus layout composited in. To measure DS pixel positions, use the "Bottom screen is the big one" setting or hold Peek, so the screen you want fills the display, then scale coordinates by `256 / width-on-screen`.

## 4. What exists so far

All on the work branch. The unit tests in `app/src/test` cover the layout maths, game-code detection and update.json parsing.

| Feature | Where |
|---|---|
| **Focus layout**: one DS screen fills the display, the other appears as a framed **small screen** in a corner (size 15–60 %, corner, show/hide). On non-4:3 displays the big screen moves away from the small one into the spare space. | `emu/FocusLayout.kt` (pure geometry), `emu/DsGlView.kt` (`writeFocusGeometry`, `drawInsetFrame`, draw order, touch), `emu/DsSettings.kt` (`focusMode`, `focusInsetSize`, `focusInsetCorner`, `focusInsetVisible`), `emu/SettingsScreen.kt` (Screens → "Single-screen focus (LessScreen)") |
| **Draw order**: when the bottom screen is the big one it is drawn first, so the top screen lands on it. The core's `fxRender(..., flag)` uploads the bottom screen into the *first* texture when the flag is 1. Touches on a top-screen small screen are not sent to the DS. | `DsGlView.draw()`; core `src/frontend/video_out_gl.c: video_out_gl_render_frame` |
| **Hotkeys** (extra functions 10–12): Peek other screen (hold), Show/hide small screen, Focus layout on/off. Mappable in Settings → Physical controller → Map gamepad buttons, or as on-screen special buttons. Peek only rewrites geometry (no shader reload). | `emu/EmulatorScreen.kt` `dispatchExtra`, `emu/KeyMap.kt`, `FocusLayout.peek` |
| **Built-in game profiles**: Mario Kart DS = game code prefix `AMC` (AMCE/AMCP/AMCJ/AMCK, plus any other region letter). The profile is applied once per code as per-game settings: focus layout on, top screen big, small screen 35 % bottom-right. | `emu/GameProfiles.kt` |
| **Game detection**: (1) at launch, read the ROM file header and check its CRC-16/MODBUS at `0x15E`; (2) otherwise, after the first frame, poll the header copy in main RAM at `0x027FFE00` once a second for up to 2 min (archives, firmware boot). Main-RAM offset for `SeedlessBridge.readMemory` is `0x3FFE00`. | `EmulatorActivity.createEmulation`, `EmulatorScreen` (`LaunchedEffect(coreReady)`) |
| **CI**: `.github/workflows/build.yml` builds and tests every push and PR. Pushes to `main`, `v*` tags and manual runs on `main` publish a signed release (APK + `update.json`) when the secrets `RELEASE_KEYSTORE_BASE64` and `RELEASE_KEYSTORE_PASSWORD` exist. Otherwise it builds a debug APK artifact. versionCode = `1000 + run_number`, versionName `1.0.1-ls.<run>`. | workflow + `app/build.gradle.kts` (`LS_BUILD_NUMBER`, `LS_UPDATE_REPO`, `LS_KEYSTORE_*`) |
| **In-app updater** (release builds only): reads `releases/latest/download/update.json`, accepts only APKs from this repository's releases, verifies the SHA-256, opens the installer via a FileProvider. Settings → Advanced → App updates. | `update/Updater.kt`, `update/UpdatePrompt.kt`, manifest provider `${applicationId}.updates` |
| App ID `com.lessscreends.app`, launcher name "LessScreenDS". Kotlin namespace and packages are still `com.seedlessds.app`. | `app/build.gradle.kts`, `strings.xml` `app_name` |

### Confirmed on the device (RP Mini V2, 1240×1080)

- Focus layout, Peek and touch on the small bottom screen all work.
- **Mario Kart DS top-screen HUD during a race:** item box top-left, `LAP n/3` top-right, rank (`1st`) **bottom-left**. Bottom-right is the free corner, so the profile uses it.
- **Bottom screen during a race:** a column of 8 racer portraits (rankings) along the left edge (about x 0–60 of 256, each row about 24 px tall), the course map to the right, `TIME` top-right.
- **Pause** (Start): `PAUSE` appears on the top screen while the 3D race stays there. The **CONTINUE / QUIT** buttons appear on the *bottom* screen, roughly y 72–92 and 104–123, spanning x 10–248. These are estimates from a phone photo; measure properly with `screencap`.
- Debug builds show a "Frame N ‖ +1" bar at the top centre. That's the upstream frame-stepping tool (`BuildConfig.FRAME_CONTROLS`, debug only); CI releases don't show it.

## 5. The current goal

Make Mario Kart DS feel designed for one screen:

1. **Menus and the pause menu on the big screen.** The main menu, character/kart/course select and the pause menu's CONTINUE / QUIT are all on the bottom screen. When one is up, the bottom screen should become the big one automatically, then switch back when racing resumes.
2. **Race:** the race is big, with rankings and map in the bottom-right corner (works today). A possible refinement is cropping just the rankings column and map, so they cover less of the race on a 4:3 screen.

The hard part is telling **menu / race / paused** apart:
- No documented Mario Kart DS "current scene" RAM variable exists for all regions. XorTroll's `mkds-re` only covers the European version.
- During pause the 3D race stays on the top screen, so "follow the 3D picture" alone can't detect it.
- The best lead is the DS **display registers**, which games usually change when a menu layer appears. Hence the probe in section 6.

## 6. Next step: display-register probe (not started)

Add a JNI function in **this repository's** native code (not in `core/`) that returns the display state, log every change, and optionally show it on screen.

Where the values live (core headers, pinned by the submodule commit):

```c
#include "core/nds_state.h"            // needs "${SEEDLESS_CORE_DIR}/src" in recon's include dirs
nds_t *seedless_core(void);            // already declared in app/src/main/cpp/jni/*.c

nds_t *n = seedless_core();
uint16_t powcnt1 = n->bus.io_mirror[0].powcnt1;  // bit 15: 1 = engine A drives the TOP screen
uint32_t dispA   = n->gpu.engine[0].dispcnt;     // engine A (the only one with 3D): bit 3 BG0=3D, bit 8 BG0 on,
                                                 // bits 8–12 BG0–3/OBJ enable, bits 16–17 display mode (0 = off)
uint32_t dispB   = n->gpu.engine[1].dispcnt;     // engine B
uint16_t brightA = n->gpu.engine[0].master_bright, brightB = n->gpu.engine[1].master_bright;
```

Suggested shape:
1. `app/src/main/cpp/CMakeLists.txt`: add `"${SEEDLESS_CORE_DIR}/src"` to `target_include_directories(recon PRIVATE ...)`. Add a new `jni/lessscreen.c` to the `recon` library with `Java_com_seedlessds_core_SeedlessCore_displayRegs(JNIEnv*, jclass, jintArray out)` filling `[powcnt1, dispA, dispB, brightA, brightB]`. Declare `@JvmStatic external fun displayRegs(out: IntArray): Boolean` in `app/src/main/java/com/seedlessds/core/SeedlessCore.kt`. Not compiled yet: check the include chain builds and that `seedless_core()` returns the live machine.
2. Call it every frame from `DsGlView.draw()` (cheap) into a reused `IntArray`. Log with `AppLog.i("lessscreen", ...)` only when the values change, decoding "3D on top/bottom/off".
3. Optional: a small on-screen readout (like `PerfOverlay` in `EmulatorScreen.kt`) behind a setting, so tests also work without USB (photo of the screen).
4. Play Mario Kart DS through: title → main menu → character/kart select → course select → race → **pause → continue** → finish → results → back to menu. Save the log and take a screenshot at each step.
5. Find register patterns that separate menu / race / paused (for example an engine B BG/OBJ enable bit that only turns on for the pause buttons). Implement them as per-game rules in `GameProfiles` with some hysteresis (state must hold for about 10–20 frames), driving the same swap path as Peek (`FocusLayout.swapped`). Put it behind a setting, e.g. "Switch screens automatically".
6. If registers aren't enough, fall back to sampling a few bottom-screen pixels at the pause-button positions (core page buffers in `video_out_gl`), or to per-region RAM addresses.

## 7. Gotchas

- **Line endings:** about 90 tracked files use CRLF (e.g. `AndroidManifest.xml`, `EmulatorActivity.kt`, `KeyMap.kt`, `MainActivity.kt`). Keep each file's existing endings: check with `git ls-files --eol <file>`. A rewrite that switches endings turns a 3-line change into a 300-line diff.
- `gradlew` has its executable bit set in git now. On Windows use `gradlew.bat`.
- The Mario Kart profile applies **once** per game code. Resetting a game's per-game settings doesn't bring it back, because the "applied" flag lives in the `lessscreen` shared prefs. Use `adb shell pm clear ...` to start fresh.
- **Analog triggers** (Settings → Physical controller) map L2/R2 axes to DS L/R. If you map Peek/Swap to L2/R2 and they also fire DS buttons, turn that off.
- `local.properties` is git-ignored, so don't commit it.
- Release signing: the private key went to the owner as a file and nowhere else. CI reads it from the two repository secrets. Never commit it; every release must use it.

## 8. Releasing

1. Add the two secrets (Settings → Secrets and variables → Actions), if not done yet.
2. Merge this branch into `main` (after deleting this file). That push builds and publishes `v1.0.1-ls.<run>` with the APK and `update.json`.
3. Install the release APK once from the GitHub release. Later releases are offered in-app, or through Obtainium.
