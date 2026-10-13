# STORAGE.md — Kroma disk & build environment

> **Read this before downloading anything or running a build.**
> Measured 2026-10-13. The internal disk is *critically* tight.

## TL;DR — rules for any agent

1. **Never download to the internal disk.** `~/.gradle` must stay near-empty (≈2 MB). Always build via `./build.sh`.
2. **Never run bare `./gradlew`.** It uses the default `GRADLE_USER_HOME` (`~/.gradle`) and will pull ~1.5 GB onto internal.
3. **Internal free space: ~10 GB.** Do not add SDKs, emulators, system images, big models or dependency trees to internal.
4. All caches live on the **external SSD**: `/Volumes/GradleHome` — an **APFS sparse image** sitting inside `/Volumes/ERFARA/dev/`.
5. If the external drive is not mounted, **mount it first**; never fall back to internal silently.
6. **Ask before downloading anything > ~50 MB.**

## Disk map (measured)

| Location | What it is | Size |
|---|---|---|
| internal `/` | macOS + user data | **≈10 GB free** of 228 GB |
| `~/.bubblewrap/android_sdk` | Android SDK (platforms 35/36/37, build-tools 34–36, NDK 27.1.12297006) | **3.7 GB** |
| `/Volumes/ERFARA` | external SSD, **exFAT** | 1.4 TB, **259 GB free** |
| `/Volumes/ERFARA/dev/GradleHome.sparseimage` | the APFS image file itself | ~1.9 GB on disk |
| `/Volumes/GradleHome` (mounted) | Gradle user home: wrapper dist + all dependencies | **1.8 GB** |
| `~/.gradle` | default Gradle home — must stay tiny | **2 MB** |
| `~/kroma/app/build` | build outputs (gitignored, regenerable) | **151 MB** |

## Toolchain paths

- **Android SDK:** `/Users/erfaanshamsuthin/.bubblewrap/android_sdk` (also set in `kroma/local.properties` as `sdk.dir=`)
- **arm64 JDK — use this:** `/Library/Java/JavaVirtualMachines/jdk-21.jdk`
- ⛔ **Do NOT use** `/Library/Java/JavaVirtualMachines/temurin-21.jdk` — it is **x86_64** and dies with *“Bad CPU type in executable”* on this Apple-silicon Mac. The bare `java` on `PATH` resolves to it.
- Spare arm64 JDK: `~/.bubblewrap/jdk/jdk-17.0.11+9`
- **Gradle 8.7** (wrapper) — already cached under `/Volumes/GradleHome/wrapper/dists`
- **adb device (Kroma test phone):** serial `00196654A001190`, model `A001` (`adb -s 00196654A001190 ...`)

## Build / eject workflow

```bash
cd ~/kroma

./build.sh :app:assembleDebug        # BUILD  (auto-attaches image, forces arm64 JDK,
./build.sh :app:testDebugUnitTest    #        sets GRADLE_USER_HOME=/Volumes/GradleHome)
./eject.sh                           # stop Gradle daemons + detach the APFS image
./eject.sh --drive                   # ...and eject the physical drive

# If you background a build you MUST redirect stdin, or the job is SIGTTIN-suspended:
nohup ./build.sh :app:assembleDebug < /dev/null > /tmp/build.log 2>&1 &

adb -s 00196654A001190 install -r app/build/outputs/apk/debug/app-debug.apk
```

`build.sh` is the ONLY supported entry point. It does three things: forces the arm64 JDK, points
`GRADLE_USER_HOME` at the external APFS image (attaching it if needed), then runs the wrapper.

## Traps already hit here (don't repeat)

| Symptom | Cause | Fix |
|---|---|---|
| Build silently stops | Background job suspended reading the tty | always `< /dev/null` |
| `Could not find EOCD in …R.jar / …app-debug.apk` | corrupt leftovers from a suspended/killed build | `rm -rf app/build/outputs` (or `app/build`) |
| Gradle suddenly downloads 130 MB | the IDE's Gradle extension auto-synced the project | `kroma/.vscode/settings.json` disables it (`gradle.autoDetect: off`) |
| `Bad CPU type in executable` | x86 JDK on arm64 | use `jdk-21.jdk` (build.sh does this) |
| `._*` junk files everywhere | exFAT AppleDouble files | why the cache is an APFS **sparse image**, not a plain folder |
| Eject fails “in use” | Gradle daemons hold the image | `./eject.sh` (stops daemons first) |
| Can't read a pasted image | per-conversation media budget | analyse on disk instead — see below |

### Reading images without the image channel
`tesseract` (`/opt/homebrew/bin/tesseract`) and Python **PIL 12.3** are installed. You can OCR and
sample pixels from the CLI, e.g.:
```bash
tesseract '/path/pic.jpeg' stdout          # text
tesseract '/path/pic.jpeg' stdout tsv      # words + bounding boxes
python3 -c "from PIL import Image; im=Image.open('/path/pic.jpeg'); print(im.size)"
```
Useful because screenshots of the device land in `~/Downloads/` (e.g. `grid ui.jpeg`).

## Reclaiming space (safe)

- `rm -rf ~/kroma/app/build` → frees ~151 MB; it regenerates on the next build.
- `/Volumes/GradleHome` holds 1.8 GB of dependencies — only delete if you accept a re-download.
- ⛔ **Do not delete** `~/.bubblewrap/android_sdk` (3.7 GB) — other projects (e.g. Vanta) share it.

## Provenance of the caches

- Gradle 8.7 dist + all AGP/Kotlin/Compose/CameraX artefacts were downloaded **once** (~1.5 GB) into
  the external APFS image. Subsequent builds are offline-fast.
- Nothing was ever written into `~/.gradle` beyond ~2 MB of wrapper metadata.
