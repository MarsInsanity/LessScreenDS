# SeedlessDS

A Nintendo DS emulator for Android handhelds with physical controls, including devices with two displays.

SeedlessDS is the smaller sibling of WatermelonDS: the same watermelon idea, with the seeds taken out.

<p align="center">
<img width="350" height="300" alt="SeedlessDS library" src="https://github.com/user-attachments/assets/9e9fbcee-28d5-4938-8b81-bbf5eb30fc36" />
<img width="350" height="300" alt="SeedlessDS interface" src="https://github.com/user-attachments/assets/74c48268-3037-4c81-9abe-400f0b790b9f" />
<img width="350" height="300" alt="SeedlessDS settings" src="https://github.com/user-attachments/assets/b23fb273-9a10-4244-b2b5-f05003c48c4d" />
</p>

> **Current status:** `1.0.1`. The Android app uses [SeedlessCoreDS](core/README.md), a Nintendo DS emulation library reconstructed from DraStic r2.6.0.4a and built from source. The original DraStic APK and prebuilt core are not required to build or run SeedlessDS.

## LessScreenDS: single-screen focus

LessScreenDS is a fork for handhelds with one display, typically a 4:3 panel. Showing both DS screens at once on such a display leaves each one at half size or less.

- **Focus layout** (Settings → Screens → Single-screen focus): one DS screen fills the display and the other appears as a **small screen** in a corner, on top of it. You can set its size and corner, or hide it. On a 4:3 display the big screen fills the panel, so the small screen covers that corner. On displays with spare room, such as 1240×1080, the big screen moves away from the small one so it sits mostly in the spare space.
- **Hotkeys**: *Peek other screen (hold)* swaps the screens only while held. *Show/hide small screen* and *Focus layout on/off* are toggles. Map them to physical buttons in Settings → Physical controller → Map gamepad buttons, or put them on the special on-screen buttons. The existing *Swap screens* hotkey chooses which screen is big.
- **Built-in game profiles**: games are identified by the 4-letter code in the cartridge header. The first three letters name the game and the last one is the region, so one profile covers every region. The code is read from the ROM file when the game starts (and checked against the header's checksum), or from RAM once it is running, which covers archives and firmware boots. The first time a known game runs, its profile is saved as that game's per-game settings, which you can then edit or reset. Currently only Mario Kart DS has a profile (AMCE, AMCP, AMCJ, AMCK): the top screen is big and the map sits bottom-right, away from the item slot (top-left) and lap counter (top-right).

### Releases and updates

Every push to `main`, every `v*` tag and every manual run of the **Build** workflow on `main` publishes a signed APK and an `update.json` to [GitHub Releases](../../releases). Other branches and pull requests are built and tested, and their debug APK is attached to the workflow run.

- **In-app updates:** release builds check the latest release when the library opens (at most every 6 hours). If it is newer, they offer to download it, verify its SHA-256 and open Android's installer. Turn this off or check manually in Settings → Advanced → App updates.
- **Obtainium** works too: add this repository's URL.
- **Signing:** releases are signed with a key stored in two repository secrets, `RELEASE_KEYSTORE_BASE64` (the PKCS#12 keystore, base64-encoded, key alias `lessscreen`) and `RELEASE_KEYSTORE_PASSWORD`. Without them, the workflow builds a debug APK and publishes nothing. Keep the key: Android only installs an update signed with the same key.
- The app ID is `com.lessscreends.app`, so LessScreenDS installs alongside SeedlessDS.

## What it is

SeedlessDS combines a game library, settings, input mapping, save states, cheats and RetroAchievements with a renderer for one or two physical displays. The Android frontend and reconstructed core communicate through JNI and the core's C API.

## Why SeedlessDS exists

The project focuses on Android handhelds where emulation performance and physical controls matter. It complements the melonDS-based WatermelonDS project with a different core. Performance depends on the game, device and internal resolution; higher resolutions require more processing power.

## The differentiator

### DualScreen Presets

Presets coordinate scale, aspect ratio, alignment, position and touch mapping across both displays. Layouts can also be adjusted for a single display.

### Dual-panel rendering

The app coordinates each emulated frame and its presentation through EGL/OpenGL ES, with a separate presentation thread for the external display. This helps games that connect the two DS screens into one scene. Behavior still depends on the device's display implementation.

### Controller-first interface

The interface supports D-pad navigation, shoulder-button tab switching and configurable emulator shortcuts. On supported dual-display layouts, the second panel can show information about the selected item.

## Features

### Library

- User-selected game folders, favorites and accent-insensitive search.
- Library filters, a Continue Playing section and per-game save slots.
- Box art from libretro thumbnails, with a bundled index and disk cache.

### Emulation

- Save states with screenshots, quick save and quick load.
- Bundled cheat database and custom cheat codes.
- Global settings, per-game overrides and profiles.
- Editable on-screen controls, screen layouts, image filters and integer scaling.
- Internal resolution settings from 1x to 8x.

### RetroAchievements

The integration uses [`rcheevos`](https://github.com/RetroAchievements/rcheevos), pinned to v12.3.0. It provides achievements, leaderboards and profile information, including casual and hardcore score displays. Compatibility is still being tested; a score display alone does not imply that every Hardcore feature is supported.

### Built for a gamepad

Map DS buttons and emulator actions to physical controls: fast-forward, quick save/load, swapping screens, cycling layouts, microphone input and turbo.

### Front-end friendly

Launchers can start a game through the `LaunchGame` activity alias:

```sh
am start -n com.lessscreends.app/com.seedlessds.app.LaunchGame \
    -e rom_path /storage/emulated/0/roms/nds/game.nds \
    --ei load_slot 1
```

The app recognizes `.nds`, `.dsi`, `.srl` and `.ids` game files and includes archive handling for ZIP, 7z and RAR. File recognition does not guarantee that a game or every archive layout is compatible. Device testing of the 7z/RAR paths remains in progress.

## How it is built

| Layer | Implementation |
|---|---|
| Android app | Kotlin, Jetpack Compose, Material 3 and Android Views |
| Emulation | [SeedlessCoreDS](core/README.md), in the `core/` submodule |
| Core library | `librecon_fn.so`, compiled from C and AArch64 assembly |
| JNI | `librecon.so`, connecting Android to the core |
| App helpers and achievements | `libseedless_bridge.so` and rcheevos |
| Display and audio | EGL/OpenGL ES and OpenSL ES |
| Supported ABI | `arm64-v8a` |
| Android | API 21 minimum; API 35 compile/target SDK |
| Version | `1.0.1`, version code 111 |

### Build from source

Initialize the submodules from the repository root:

```sh
git submodule update --init --recursive
```

Use JDK 25, Android SDK 35 with Build Tools 36.0.0, NDK `25.2.9519653` and CMake `3.22.1`. The Gradle wrapper selects Gradle `9.7.0`, with Android Gradle Plugin `9.3.1` and Kotlin/Compose compiler `2.4.21`. Set the SDK location through `local.properties` or your Android SDK environment. The Gradle daemon requires a locally installed JDK 25. Java/Kotlin bytecode remains targeted to Java 17 and is converted to Android DEX; the minimum Android API level remains 21. The native build downloads pinned libarchive, unarr and LZMA SDK sources with SHA-256 verification; see [third-party licenses](core/third_party/LICENSES.md).

Build the debug app (`com.lessscreends.app.debug`):

```sh
./gradlew assembleDebug
```

For the signed release (`com.lessscreends.app`), configure `MELONDS_KEYSTORE`, `MELONDS_KEYSTORE_PASSWORD`, `MELONDS_KEY_ALIAS` and `MELONDS_KEY_PASSWORD` in your untracked `local.properties`, then run:

```sh
./gradlew assembleRelease
```

On Windows, use `gradlew.bat` with the same arguments. APKs are written under `app/build/outputs/apk/`. Keep signing credentials and the release keystore outside version control. The tracked debug keystore is only for debug builds.

## Roadmap

The reconstructed core is now built from source. Future work includes broader compatibility testing, performance improvements and continued refinement of the Android interface. A Linux frontend remains a future direction; there is no released Linux application in this repository.

## Credits and distribution

DraStic was created by its original authors, including Exophase. SeedlessCoreDS is reconstructed from DraStic r2.6.0.4a; this is not a claim of an independently designed core. SeedlessDS is a separate project and is not an official DraStic release.

Games and original Nintendo BIOS/firmware images are not included. The app includes replacement BIOS assets. Third-party source and assets retain their respective attribution and license terms. Nintendo and game names belong to their respective owners; the project is not affiliated with Nintendo.

## Current known notes

- This release supports ARM64 Android devices.
- Game compatibility, performance and dual-display behavior vary by device.
- RetroAchievements and archive handling need further compatibility testing.
- The interface is still being refined.

## Reporting issues

Include the device model, Android version, game, internal resolution, display layout, input method and whether RetroAchievements was enabled. Add reproduction steps and, where useful, screenshots or logs. Do not attach games, BIOS images, firmware or signing credentials.

## License

Original or authorized project code is licensed under GPL-3.0-or-later; see [LICENSE](LICENSE) and [LICENSING.md](LICENSING.md). Bundled dependencies and resources retain their individual terms, including the DeadSkullzJr cheat catalog's AGPL-3.0 and XML source.

Release source archives include exact app/core revisions, dependency sources, resource sources, generators and build instructions. The checked-out Android product builds directly with Gradle as described above.
