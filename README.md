# Kroma

An Android **Universal Camera** app implementing **iOS-style Photographic Styles**
(zero-LUT, Oklab shader engine) per `PHOTOGRAPHIC_STYLES_UI_ENGINE_GUIDE.md`.

## Status — Phase 0 (engine core)

| Piece | State |
|---|---|
| Gradle scaffold (`com.kroma.app`, AGP 8.5.2 / Kotlin 2.2.21, Compose) | ✅ |
| GLSL shaders as assets (`photographic_style.frag`, `texture_engine.frag`) | ✅ |
| 15-preset table + Oklab/tone/chroma/skin CPU reference (Kotlin) | ✅ |
| **Debug build (`:app:assembleDebug`)** | ✅ **BUILD SUCCESSFUL** |
| JVM unit tests (`app/src/test/.../StyleEngineTest.kt`) | ✅ **8/8 passing** |
| Colour-science validation | ✅ `python3 tools/validate_style.py` |
| Camera2/CameraX + GLES preview pipeline | ⏳ Phase 1/2 |
| iOS-style UI (sheet, 2D pad, texture tab) | ⏳ see `UI_PLAN.md` |

## Layout

```
app/src/main/assets/shaders/        GLSL (from the spec)
app/src/main/java/com/kroma/app/
  engine/Presets.kt                 15 presets (id, anchor, beta, hue, toe)
  engine/Oklab.kt                   colour-science CPU reference
  engine/StyleEngine.kt             style pass + StyleState
  engine/ColorChart.kt              Phase-0 preview swatches
  MainActivity.kt                   Compose shell: selection strip + HUD
tools/validate_style.py             zero-dependency maths validator
references/                         iOS Photographic Styles screenshots
```

## Building

Gradle's cache lives **outside the repo** to spare the internal disk:

```
./build.sh :app:assembleDebug          # build
./build.sh :app:testDebugUnitTest      # tests
./eject.sh                             # release the drive (detach image)
./eject.sh --drive                     # ...and eject the physical drive
```

`build.sh` forces the **arm64 JDK** (`jdk-21.jdk`) and points `GRADLE_USER_HOME` at
a clean **APFS sparse image** on the external drive (`/Volumes/GradleHome`),
auto-attaching it if needed. **Always build through `build.sh`**, not bare `./gradlew`.

Purely-maths validation (no build needed): `python3 tools/validate_style.py`.

`local.properties` must point at the SDK (`sdk.dir=...`).
