# Kroma — Photographic Styles & UI Engine Guide

> **Source of truth:** `~/Documents/Photographic Styles & UI Engine Specification.pdf` (11 pages)
> **Target architecture:** Android Universal Camera App Pipeline — *Zero-LUT Oklab Shader Execution Model*
> **Status:** Reference/implementation guide derived from the specification. Reviewed and transcribed verbatim; inferred items are explicitly flagged.

---

## 0. Acknowledgement / What this document specifies

This guide captures the complete *Photographic Styles & UI Engine Specification*. It describes a
**preset-driven photographic styling engine** that runs entirely as GPU shader math (**no 3D LUTs**),
operating in **perceptual Oklab/Oklch color space** over **16-bit linear Display P3** buffers. The
engine is composed of eight parts:

| # | Section | Purpose |
|---|---------|---------|
| 1 | 2D Palette Control Pad & Intensity | Unified touch coordinate model + master blend |
| 2 | App Layout Hierarchy & State Machine | Camera UI flow (Selection → Customize → Calibration) |
| 3 | 15 Presets Reference Matrix | Canonical preset parameter table |
| 4 | Color Science & Transfer Functions | Oklab conversion, tone curve, chroma/hue, skin protection |
| 5 | Advanced Texture Overlays | Soft Skin, Glow, Film, Film Grain |
| 6 | Production GLSL Shaders | `photographic_style.frag`, `texture_engine.frag` |
| 7 | Camera2 & EGL Multi-Pass Engine | 4-pass async render graph |
| 8 | NDK Post-Capture Serialization | HEIF container + XMP metadata + re-edit workflow |

### Core idea in one paragraph

Instead of exposing dozens of independent linear sliders, a single **2D touch coordinate**
(`x = Color`, `y = Tone`, both in `[-100, +100]`) drives *all* structural color variables at once,
offset by a **per-preset anchor point**. A horizontal **master intensity** slider (`0..100 → 0.0..1.0`)
linearly blends the styled result against the untouched native ISP baseline. Every preset is therefore
fully described by four scalars: **anchor `(X, Y)`**, **beta factor**, **hue bias angle**, and
**shadow toe offset**.

---

## 1. 2D Palette Control Pad & Intensity Specification

Replaces traditional independent linear sliders with a unified touch-coordinate system targeting
structural variables simultaneously, augmented with master intensity blending and preset-specific
anchor points.

### 1.1 Control Uniform Mappings

| UI Control Input | Mathematical Variable | Scale Range | Shader Uniform Target Mapping |
|---|---|---|---|
| Vertical Motion (Y-Axis) | Tone (Shadow Contrast / Black Point) | `-100` to `+100` | `u_Tone` (Modifies gamma / S-curve point matrices) |
| Horizontal Motion (X-Axis) | Color (Local Saturation / Hue Shift) | `-100` to `+100` | `u_Color` (Selective multi-channel saturation clamp) |
| Horizontal Intensity Slider | Master Style Intensity | `0` to `100` (`0.0` to `1.0`) | `u_Intensity` (Linear blend weight vs. baseline native ISP) |
| Reset Action (↺) | Position Reset | Binary Trigger | Restores puck to preset default anchor `u_PresetAnchor` |

### 1.2 Interaction Mechanics

- **Dynamic Puck Anchors:** Unlike generic color pickers where the center is always `(0, 0)`, each
  preset features a **unique default anchor coordinate** — e.g. **Cinema** defaults to a negative
  Y-bias; **Hearth** defaults to positive X and negative Y.
- **HUD Readout:** A persistent top-level HUD displays real-time integer readouts
  (**Tone: ±N, Color: ±N, Intensity: N%**). A **Reset** button dynamically appears whenever the
  current coordinates deviate from the active preset's baseline anchor.

---

## 2. App Layout Hierarchy & State Machine

Replicates a multi-tiered camera workflow stacked directly over the viewfinder canvas.

- **State 1 — Selection Strip:** A lightweight horizontal `RecyclerView` scrolling through the
  **15 base titles**. Live preview processes base constants in real time.
- **State 2 — Customize Option:** Fades in a **"Customize"** indicator button when a style selection
  is focused. Tapping transitions the interface to calibration mode.
- **State 3 — Calibration Workspace:** Replaces the carousel with a dedicated bottom sheet containing
  two segmented sub-tabs:
  - **Palette Tab:** Houses the 2D touch boundary box, real-time puck, dynamic HUD readout, and
    master intensity slider.
  - **Texture Tab:** Houses structural rendering adjustments (Soft Skin, Glow, Film, and Film Grain).
- **Hardware Camera Control Binding:** Supports capacitive or two-stage physical shutter rails. A
  **light press** opens a quick-scrub overlay to cycle presets or switch between *Tone*, *Color*, and
  *Intensity* without obscuring the viewfinder.

---

## 3. Comprehensive 15 Presets Reference Matrix

Each preset is defined by its **default anchor `(X, Y)`**, **Beta Factor** (chroma gain multiplier),
**Hue Bias Angle**, and **Shadow Toe Offset**.

| Category | Style Name | Apple Reference | Anchor (X, Y) | Beta Factor | Hue Bias Angle | Shadow Toe Offset |
|---|---|---|---|---|---|---|
| Undertones | Blush | Cool Rose | `(+20, 0)` | 0.85 | +18.0 deg (Pink/Blue) | 0.00 |
| Undertones | Balance | Neutral | `(0, 0)` | 0.00 | 0.0 deg | 0.00 |
| Undertones | Champagne | Rose Gold | `(+35, +15)` | 0.90 | +8.5 deg (Warm Pink) | +0.04 |
| Undertones | Gilded | Gold | `(+40, +10)` | 1.10 | −12.0 deg (Amber Gold) | +0.02 |
| Undertones | Bronze | Amber | `(+50, −25)` | 1.25 | −24.0 deg (Copper) | −0.06 |
| Default | Native | Standard | `(0, 0)` | 0.00 | 0.0 deg (Bypass) | 0.00 |
| Moods | Vivid | Vibrant | `(+30, +10)` | 1.40 | 0.0 deg | +0.03 |
| Organic | Natural | Natural | `(+10, 0)` | 0.40 | −4.0 deg (Foliage Bias) | +0.01 |
| Radiance | Luminous | Luminous | `(0, +35)` | 0.60 | +2.0 deg | +0.08 |
| Cinema | Dramatic | Dramatic | `(0, −45)` | 0.75 | −5.0 deg (Teal/Orange) | −0.12 |
| Hush | Quiet | Quiet | `(−30, 0)` | −0.60 | 0.0 deg | 0.00 |
| Hearth | Cozy | Cozy | `(+35, −30)` | 1.15 | −18.0 deg (Warm Red) | −0.08 |
| Airy | Ethereal | Ethereal | `(+15, +40)` | 0.50 | +6.0 deg (Pastel) | +0.12 |
| Silver | Muted B&W | Muted B&W | `(−100, −10)` | −1.00 | Pure Monochrome | −0.02 |
| Graphite | Stark B&W | Stark B&W | `(−100, −50)` | −1.00 | Pure Monochrome | −0.15 |

### 3.1 Inferred preset-ID map (`u_PresetId`)

> **Note (inferred):** The spec fixes `u_PresetId == 5` as the **Bypass/Standard** preset, and
> `u_PresetId == 13 || u_PresetId == 14` as the two **monochrome** presets (the XMP example also sets
> `<style:PresetId>9</style:PresetId>` for **Cinema**). Anchoring on those three data points gives the
> natural 0-based index order below. Confirm against firmware before relying on it.

| ID | Preset | ID | Preset | ID | Preset |
|---|---|---|---|---|---|
| 0 | Blush | 5 | Native (Bypass) | 10 | Hush (Quiet) |
| 1 | Balance | 6 | Vivid | 11 | Hearth (Cozy) |
| 2 | Champagne | 7 | Natural | 12 | Airy |
| 3 | Gilded | 8 | Luminous (Radiance) | 13 | Silver (Muted B&W) |
| 4 | Bronze | 9 | Cinema | 14 | Graphite (Stark B&W) |


---

## 4. Color Science & Mathematical Transfer Functions

To avoid hue distortions and shadow posterization, all transforms execute in **Oklab / Oklch
perceptual color space** over **16-bit linear Display P3** buffers.

### 4.1 Linear Display P3 → Oklab Conversion

```
LMS       = M1 * RGB_linear
LMS_cubic = LMS ^ (1/3)
Lab       = M2 * LMS_cubic
```

**Conversion matrices (row-major as printed in the spec):**

```
M1 = [[0.41222147, 0.53633254, 0.05144599],
      [0.21190350, 0.68069955, 0.10739696],
      [0.08830246, 0.28171884, 0.62997870]]

M2 = [[0.21045426,  0.79361779, -0.00407205],
      [1.97799850, -2.42859221,  0.45059371],
      [0.02590404,  0.78277177, -0.80867577]]
```

**Inverse matrices (row-major — required by any CPU port):**

```
M2_INV = [[1.00000000,  0.39633778,  0.21580376],
          [1.00000000, -0.10556135, -0.06385417],
          [1.00000000, -0.08948418, -1.29148555]]

M1_INV = [[ 4.07674166, -3.30771159,  0.23096993],
          [-1.26843800,  2.60975740, -0.34131940],
          [-0.00419609, -0.70341861,  1.70761470]]
```

> ⚠️ **Column-major trap.** The `mat3(...)` literals in the shaders (§6.1) are **column-major**, so
> the matrices above are their **transposes**. A CPU port that copies the GLSL argument order
> straight into a row-major array silently breaks `oklab_to_rgb` (the Oklab round-trip fails and all
> output collapses toward red). This exact bug was caught by `tools/validate_style.py`.

### 4.2 Parametric Tone Curve (`u_Tone`)

Tone adjustments modify perceptual lightness `L` in range `[0.0, 1.0]` using a midtone-anchored
rational sigmoid curve:

```
gamma     = exp(-u_Tone / 50.0)
S(L,gamma) = (L^gamma) / ((L^gamma) + ((1.0 - L)^gamma))
L_styled  = L + (S(L,gamma) - L) * (4.0 * L * (1.0 - L))
```

### 4.3 Parametric Chroma & Hue Mapping (`u_Color`)

```
Chroma C   = sqrt(a^2 + b^2)
Hue angle h = atan2(b, a)
k_C        = 1.0 + (u_Color / 100.0) * Beta_Preset
C_styled   = C * k_C
```

### 4.4 Skin Tone Hue Protection

Human skin forms a localized cluster in Oklab space around `(a = 0.092, b = 0.061)`. Protection
weighting decouples skin from global color distortion:

```
d_skin    = sqrt( ((a - 0.092) / 0.040)^2 + ((b - 0.061) / 0.030)^2 )
W_skin    = clamp(1.0 - d_skin, 0.0, 1.0) * Skin_Mask_Alpha
C_final   = (1.0 - W_skin) * C_styled + W_skin * ( C * (1.0 + (u_Color/100.0) * 0.15) )
L_final   = (1.0 - W_skin) * L_styled + W_skin * ( 0.85 * L + 0.15 * L_styled )
```


---

## 5. Advanced Texture Overlays

Exclusive structural rendering passes running parallel to the core color-matrix math, layered in the
**Texture** sub-tab:

| Texture Target | UI Control Interface | Scale | Operational Behavior |
|---|---|---|---|
| Standard | Default State Button | Fixed | Passthrough full hardware sensor detail with zero post-blur. |
| Soft Skin | Horizontal Tab & Slider | 0 to 100 | Selective bilateral frequency facial feature smoothing targeting skin mask confidence zones. |
| Glow | Horizontal Tab & Slider | 0 to 100 | Highlight diffusion bleed pass (Halation simulation) using separable Gaussian bloom over high-luminance thresholds. |
| Film | Horizontal Tab & Slider | 0 to 100 | Logarithmic edge compression curve formatting, gently rolling off extreme dynamic range highlights. |
| Film Grain | Binary Toggle Button | On/Off | Procedural Perlin noise vector pattern addition modulated by pixel luminance. |

---

## 6. Production GLSL Shaders

### 6.1 Core Style Fragment Shader (`photographic_style.frag`)

```glsl
#version 300 es
precision highp float;

in vec2 v_TexCoord;
out vec4 out_FragColor;

uniform sampler2D u_LinearP3Texture;
uniform sampler2D u_SkinMaskTexture;
uniform vec2      u_PadCoordinates;   // x: Color [-100, 100], y: Tone [-100, 100]
uniform float     u_Intensity;        // Master blend [0.0, 1.0]
uniform vec2      u_PresetAnchor;     // (AnchorX, AnchorY)
uniform int       u_PresetId;
uniform float     u_BetaPreset;
uniform float     u_HueBiasAngle;     // In radians

const mat3 M1 = mat3(
   0.41222147, 0.21190350, 0.08830246,
   0.53633254, 0.68069955, 0.28171884,
   0.05144599, 0.10739696, 0.62997870
);

const mat3 M2 = mat3(
   0.21045426,  1.97799850,  0.02590404,
   0.79361779, -2.42859221,  0.78277177,
  -0.00407205,  0.45059371, -0.80867577
);

const mat3 M2_INV = mat3(
   1.00000000,  1.00000000,  1.00000000,
   0.39633778, -0.10556135, -0.08948418,
   0.21580376, -0.06385417, -1.29148555
);

const mat3 M1_INV = mat3(
   4.07674166, -1.26843800, -0.00419609,
  -3.30771159,  2.60975740, -0.70341861,
   0.23096993, -0.34131940,  1.70761470
);

vec3 rgb_to_oklab(vec3 rgb) {
  vec3 lms = M1 * rgb;
  vec3 lms_cube = pow(max(lms, vec3(0.0)), vec3(1.0 / 3.0));
  return M2 * lms_cube;
}

vec3 oklab_to_rgb(vec3 lab) {
  vec3 lms_cube = M2_INV * lab;
  vec3 lms = lms_cube * lms_cube * lms_cube;
  return max(M1_INV * lms, vec3(0.0));
}

void main() {
  vec4 baseColor = texture(u_LinearP3Texture, v_TexCoord);
  float skinAlpha = texture(u_SkinMaskTexture, v_TexCoord).r;

  if (u_PresetId == 5 && length(u_PadCoordinates) < 0.001) {
    out_FragColor = baseColor;
    return;
  }

  vec3 lab = rgb_to_oklab(baseColor.rgb);
  float L = lab.x;
  float a = lab.y;
  float b = lab.z;

  vec2 effectiveCoords = clamp(u_PadCoordinates + u_PresetAnchor, vec2(-100.0), vec2(100.0));
  float effColor = effectiveCoords.x;
  float effTone  = effectiveCoords.y;

  // 1. Parametric Tone Curve
  float gamma = exp(-effTone / 50.0);
  float sCurve = pow(L, gamma) / (pow(L, gamma) + pow(max(1.0 - L, 0.0001), gamma));
  float L_styled = L + (sCurve - L) * (4.0 * L * (1.0 - L));

  // 2. Parametric Chroma & Hue Mapping
  float chroma = sqrt(a * a + b * b);
  float hue = atan(b, a);

  float k_C = max(0.0, 1.0 + (effColor / 100.0) * u_BetaPreset);
  float chroma_styled = chroma * k_C;
  float hue_styled = hue + u_HueBiasAngle * (effColor / 100.0);

  if (u_PresetId == 13 || u_PresetId == 14) {
    chroma_styled = 0.0;
  }

  // 3. Skin Tone Hue Protection
  float d_skin = sqrt(pow((a - 0.092) / 0.040, 2.0) + pow((b - 0.061) / 0.030, 2.0));
  float w_skin = clamp(1.0 - d_skin, 0.0, 1.0) * skinAlpha;

  float L_protected = mix(L_styled, mix(L, L_styled, 0.25), w_skin);
  float C_protected = mix(chroma_styled, chroma * (1.0 + (effColor / 100.0) * 0.15), w_skin);
  float h_protected = mix(hue_styled, hue, w_skin);

  vec3 lab_processed = vec3(
    L_protected,
    C_protected * cos(h_protected),
    C_protected * sin(h_protected)
  );

  vec3 styledRgb = oklab_to_rgb(lab_processed);
  vec3 finalRgb = mix(baseColor.rgb, styledRgb, u_Intensity);
  out_FragColor = vec4(finalRgb, baseColor.a);
}
```


### 6.2 Structural Texture Fragment Shader (`texture_engine.frag`)

```glsl
#version 300 es
precision highp float;

in vec2 v_TexCoord;
out vec4 out_FragColor;

uniform sampler2D u_InputTexture;
uniform sampler2D u_SkinMaskTexture;
uniform sampler2D u_BloomTexture;
uniform int       u_TextureMode;        // 0: Standard, 1: SoftSkin, 2: Glow, 3: Film
uniform float     u_TextureIntensity;   // [0.0, 1.0]
uniform bool      u_FilmGrainEnabled;
uniform float     u_Time;

float procedural_noise(vec2 p) {
  vec2 k = vec2(23.14069263277926, 2.665144142690225);
  return fract(cos(dot(p, k)) * 12345.6789);
}

void main() {
  vec4 base = texture(u_InputTexture, v_TexCoord);
  vec3 color = base.rgb;
  float skinAlpha = texture(u_SkinMaskTexture, v_TexCoord).r;

  // Soft Skin: Bilateral filter gated by skin confidence
  if (u_TextureMode == 1 && u_TextureIntensity > 0.0) {
    vec2 texelSize = 1.0 / vec2(textureSize(u_InputTexture, 0));
    vec3 sum = vec3(0.0);
    float norm = 0.0;
    float sigma_r = 0.08;

    for (int x = -2; x <= 2; ++x) {
      for (int y = -2; y <= 2; ++y) {
        vec2 offset = vec2(float(x), float(y)) * texelSize * 1.5;
        vec3 sampleColor = texture(u_InputTexture, v_TexCoord + offset).rgb;
        float dist_s = dot(offset, offset);
        float dist_r = dot(sampleColor - color, sampleColor - color);
        float weight = exp(-dist_s / 0.0001 - dist_r / (2.0 * sigma_r * sigma_r));
        sum += sampleColor * weight;
        norm += weight;
      }
    }
    vec3 smoothed = sum / norm;
    color = mix(color, smoothed, skinAlpha * u_TextureIntensity);
  }

  // Glow: Highlight diffusion bloom bleed
  if (u_TextureMode == 2 && u_TextureIntensity > 0.0) {
    vec3 bloom = texture(u_BloomTexture, v_TexCoord).rgb;
    color = color + bloom * (u_TextureIntensity * 0.45);
  }

  // Film: Logarithmic highlight shoulder compression
  if (u_TextureMode == 3 && u_TextureIntensity > 0.0) {
    vec3 filmColor = 1.0 - exp(-color * 1.25);
    color = mix(color, filmColor, u_TextureIntensity);
  }

  // Procedural Film Grain: Luminance-modulated
  if (u_FilmGrainEnabled) {
    float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float midtoneMod = sqrt(max(0.0, lum * (1.0 - lum))) * 2.0;
    float noise = (procedural_noise(v_TexCoord * 1024.0 + fract(u_Time)) - 0.5) * 0.06;
    color += noise * midtoneMod;
  }

  out_FragColor = vec4(clamp(color, 0.0, 1.0), base.a);
}
```


---

## 7. Android Camera2 & EGL Multi-Pass Engine

To maintain **60 FPS** preview rendering, the processing graph executes asynchronously:

| Pass | Name | Description |
|---|---|---|
| **Pass 1** | OES Linearization | Reads `GL_TEXTURE_EXTERNAL_OES` from Camera2, applies un-gamma and sensor color correction matrix, writing to `FBO_LinearP3` (RGBA16F, half-float). |
| **Pass 2** | Neural Inference & Upsampling | Downsampled frame (`256×256`) is fed to a **TFLite / QNN NPU** runtime generating the skin mask. A high-resolution **bilateral guided filter** upsamples the mask using linear P3 luminance as reference, eliminating boundary fringing. |
| **Pass 3** | Core Shader Pass | Executes `photographic_style.frag` in Oklab space using pad offsets, intensity, and upsampled alpha mask. |
| **Pass 4** | Texture & Grain Pass | Applies bilateral facial smoothing, glow bleed, film curve, and procedural grain. |

**Output Dispatch:** Blits to the viewport `SurfaceView` (sRGB encoded) and parallelizes to the NDK
capture queue on shutter trigger.

---

## 8. NDK Post-Capture Serialization & Metadata Schema

Captures are serialized as **High Efficiency Image Files (`.heic`)** containing raw auxiliary buffers
to support complete post-capture re-editability in the gallery.

### 8.1 HEIF Container Track Layout

| Track | Content |
|---|---|
| Primary Item (`0x0001`) | 10-bit HDR / Display P3 encoded final styled image. |
| Auxiliary Track 1 | Single-channel ISO Gain Map (ISO 21496-1) for HDR display expansion. |
| Auxiliary Track 2 | 16-bit linear P3 un-stylized sensor base capture. |
| Auxiliary Track 3 | Packed 8-bit R8 semantic segmentation mask. |

*(Plus an XML XMP Metadata Block carrying the complete parameter state payload.)*

### 8.2 XMP Serialization Schema

```xml
<x:xmpmeta>
  <rdf:RDF>
    <rdf:Description xmlns:style="android:camera:photographicstyle">
      <style:Version>1.0.0</style:Version>
      <style:PresetId>9</style:PresetId>
      <style:PresetName>Cinema</style:PresetName>
      <style:ToneCoord>-45.0</style:ToneCoord>
      <style:ColorCoord>0.0</style:ColorCoord>
      <style:Intensity>1.00</style:Intensity>
      <style:AnchorX>0.0</style:AnchorX>
      <style:AnchorY>-45.0</style:AnchorY>
      <style:TextureMode>0</style:TextureMode>
      <style:TextureIntensity>0.0</style:TextureIntensity>
      <style:FilmGrainEnabled>false</style:FilmGrainEnabled>
      <style:ColorSpace>Display_P3_Linear</style:ColorSpace>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
```

### 8.3 Gallery Non-Destructive Re-Editing Workflow

1. The Gallery app checks for the `style:Version` XMP packet upon opening a photo.
2. If the user taps **"Edit Style"**, `libheif` decodes the linear base capture (**Track 2**) and
   segmentation mask (**Track 3**) into an offscreen OpenGL ES context.
3. The UI initializes the 2D Palette puck and intensity slider from the XMP coordinates.
4. User adjustments invoke `photographic_style.frag` against the cached linear buffer in real time with
   **zero generational quality loss**.

---

## 9. Implementer's Checklist & Gotchas

- [ ] **Uniform contract locked.** `u_PadCoordinates` (`vec2`, x=Color, y=Tone), `u_Intensity`
      (`float`), `u_PresetAnchor` (`vec2`), `u_PresetId` (`int`), `u_BetaPreset` + `u_HueBiasAngle`
      (`float`, radians) must match between Kotlin/JNI and both shaders.
- [ ] **Anchor is additive, pad is the gesture delta.** Effective coordinate =
      `clamp(u_PadCoordinates + u_PresetAnchor, -100, +100)`. The puck *rests* at the preset anchor,
      not at the origin.
- [ ] **Bypass fast-path.** When `u_PresetId == 5` (Native) and the pad is at origin
      (`length < 0.001`), return the base color untouched for a zero-cost passthrough.
- [ ] **Monochrome override.** Preset IDs 13/14 zero out `chroma_styled` *after* hue/sat mapping.
- [ ] **Degrees → radians.** The presets table (Section 3) is in degrees; feed radians to the shader.
- [ ] **Working space.** All math is on **16-bit linear Display P3**; only encode to sRGB at output.
- [ ] **Alpha.** `out_FragColor` preserves `baseColor.a` / `base.a` — never overwrite alpha.
- [ ] **Skin mask gating.** Skin protection also multiplies by `skinAlpha`; without the mask the effect
      is global.
- [ ] **60 FPS budget.** Keep texture work to 4 passes; the Soft Skin bilateral is a `5×5` kernel
      gated by skin confidence — do not widen the kernel on the UI thread.
- [ ] **XMP round-trip.** Persist `ToneCoord`, `ColorCoord`, `Intensity`, `AnchorX/Y`, `TextureMode`,
      `TextureIntensity`, `FilmGrainEnabled` so the gallery can fully restore state.

### Quick uniform/parameter cheat-sheet

| Symbol | Range | Units | Notes |
|---|---|---|---|
| `u_PadCoordinates.x` (Color) | `-100 … +100` | integer steps | local saturation / hue shift |
| `u_PadCoordinates.y` (Tone) | `-100 … +100` | integer steps | shadow contrast / black point |
| `u_Intensity` | `0.0 … 1.0` | normalised | linear blend vs. native ISP baseline |
| `u_BetaPreset` | e.g. `-1.00 … 1.40` | scalar | chroma gain multiplier |
| `u_HueBiasAngle` | degrees→radians | radians | per-preset hue rotation |
| `u_TextureIntensity` | `0.0 … 1.0` | normalised | Soft Skin / Glow / Film strength |
| Shadow Toe Offset | e.g. `-0.15 … +0.12` | scalar | record-only param in current shader |

---

*Guide generated from the 11-page specification PDF. All formulas, matrices, shader source, preset
values, and container/XMP schemas are transcribed verbatim; the preset-ID map in §3.1 is inferred and
flagged as such.*

