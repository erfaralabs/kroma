# Kroma

An Android **Universal Camera** app implementing **iOS-style Photographic Styles**
(zero-LUT, Oklab shader engine) per `PHOTOGRAPHIC_STYLES_UI_ENGINE_GUIDE.md`.

## Status — Phase 0 (engine core)

| Piece | State |
|---|---|
| Gradle scaffold (`com.kroma.app`, AGP 8.5.2 / Kotlin 2.2.21, Compose) | ✅ |
| GLSL shaders as assets (`photographic_style.frag`, `texture_engine.frag`) | ✅ |
| 15-preset table + Oklab/tone/chroma/skin CPU reference (Kotlin) | ✅ |
| Colour-science validation | ✅ `python3 tools/validate_style.py` |
| JVM unit tests (`app/src/test/.../StyleEngineTest.kt`) | ⏳ run once Gradle deps are available |
| Camera2/CameraX + GLES preview pipeline | ⏳ Phase 1/2 |

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

The Gradle distribution + dependency cache are **not present** on this machine
(empty `~/.gradle`), so the first build downloads ~130 MB (Gradle) + several
hundred MB (AGP/Kotlin/Compose). Until then, validate the engine with:

```
python3 tools/validate_style.py
```

`local.properties` must point at the SDK (`sdk.dir=...`). Use an **arm64** JDK
(e.g. `/Library/Java/JavaVirtualMachines/jdk-21.jdk`).
