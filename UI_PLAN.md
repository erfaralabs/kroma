# Kroma — UI Build Plan (iOS Photographic Styles parity)

**Goal:** replicate the iOS 18 "Photographic Styles" UI, driven by our screenshots
(`references/`) + online references.

**Status:** Phase 0 has the *Selection Strip* + *HUD* + *intensity slider* already
(`MainActivity.kt`). This doc covers the rest.

---

## 1. Reference inventory

| Ref | Shows | Maps to |
|---|---|---|
| `references/01-texture-tab.jpeg` | Bottom sheet, `PALETTE \| TEXTURE` tabs, 5 texture modes, strength pips, caption *"Texture is applied after taking a photo."* | spec §2 State 3, §5 |
| `references/02-viewfinder-photo.jpeg` | Viewfinder, lenses, `f`, `VIDEO/PHOTO/PORTRAIT` | spec §2 State 1 |
| `references/03-pro-controls.jpeg` | Pro controls (`ƒ2.8 · EV · AF · RAW 48`, AE/AF LOCK) | spec §2 |
| **MISSING** | **PALETTE tab** — 2D pad + puck + `Tone/Color/Intensity` HUD + reset | spec §1 |

---

## 2. Target layout (bottom-up)

```
┌────────────────────────────────────────────┐
│                VIEWFINDER                   │  (full-bleed, later = camera)
│                                             │
│        ┌──────────────────────────────┐     │
│        │  ‹   PALETTE │ TEXTURE   ↺  │     │  ← segmented tabs, sheet
│        │                              │     │
│        │      [ tab content ]         │     │
│        │                              │     │
│        │      caption line            │     │
│        └──────────────────────────────┘     │
│   [X]                       [◉]      [1x]   │  ← controls row
│   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓   │  ← intensity slider
│   [Blush][Balance][Champagne] ... carousel   │  ← Selection Strip (State 1)
└────────────────────────────────────────────┘
```

### PALETTE tab (§1)
- **2D pad**: square boundary, cross-hair, draggable **puck** resting at the preset anchor.
- **HUD**: `Tone: ±N   Color: ±N   Intensity: N%`.
- **Reset ↺** appears only when the puck deviates from the anchor.

### TEXTURE tab (§5)
- Row of 5 modes: `STANDARD ⊘ · SOFT SKIN 👤 · GLOW ◌ · FILM ▤ · GRAIN ▦`.
- **Strength pips** (~11 segments) for the selected mode.
- Caption: *"Texture is applied after taking a photo."* (post-capture pass).

---

## 3. Visual language (from the screenshot)

| Token | Value |
|---|---|
| Accent (active tab / selection) | warm yellow ≈ `#FFD84D` |
| Sheet background | near-black translucent, `#1C1C1E` @ ~0.85, **rounded top corners ~28dp** |
| Primary text | `#F2F2F2` |
| Secondary text | `#9A9A9A` |
| Mode icon idle / active | outline circle → filled white circle |
| Type | sans, letter-spaced small caps for labels |

---

## 4. Compose components to build

| Component | Notes |
|---|---|
| `KromaTheme` | dark scheme + accent token |
| `StyleCarousel` | ✅ exists (upgrade visuals) |
| `CalibrationSheet` | host for the two tabs + back/reset |
| `SegmentedTabs` | `PALETTE \| TEXTURE` |
| `PalettePad` | `Canvas` + `pointerInput` drag; clamps to `[-100,100]`; writes `StyleState.padColor/padTone` |
| `HudReadout` | ✅ exists |
| `IntensitySlider` | ✅ exists |
| `TextureRow` | 5 icon buttons → `textureMode` |
| `TexturePips` | segmented strength → `textureIntensity` |
| `ResetButton` | visible when `!isAtAnchor` |

---

## 5. State changes needed

`StyleState` currently has `presetId, padColor, padTone, intensity, skinAlpha`.
**Add** the texture block (spec §5) so the Texture tab is wired:

```kotlin
enum class TextureMode { STANDARD, SOFT_SKIN, GLOW, FILM }
// + filmGrainEnabled: Boolean   (the GRAIN binary toggle)
// + textureIntensity: Float     (0..1)
```

---

## 6. Execution order (tomorrow)

1. `StyleState` texture fields + `TextureMode` enum (engine).
2. `KromaTheme` tokens.
3. `SegmentedTabs` + `CalibrationSheet` shell (static layout first).
4. `PalettePad` (the centrepiece) → wires live into the CPU render.
5. `TextureRow` + `TexturePips` + caption.
6. Polish to match the screenshot (spacing, translucency, accent).

> Everything here is CPU-driven and works **without** the Gradle build or GPU —
> it updates the existing colour chart live. GPU/camera slots in later untouched.

---

## 7. Interaction map — **what opens what** (spec §2 state machine)

### 7.1 Screen flow

```
┌──────────────────────────────────────────────────────────────┐
│ VIEWFINDER (State 0)                                          │
│   • tap the yellow 3×3-dots button (refs 02 / 03, bottom-right)│
└───────────────────────────┬──────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────┐
│ STATE 1 — Selection Strip                                     │
│   horizontal style carousel over the viewfinder, live preview  │
│   • tap a style → it becomes "focused"                         │
└───────────────────────────┬──────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────┐
│ STATE 2 — "Customize" indicator fades in                      │
│   • tap Customize                                             │
└───────────────────────────┬──────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────┐
│ STATE 3 — Calibration Workspace (bottom sheet, ref 01)         │
│   ├─ [PALETTE] tab → 2D pad + HUD + intensity + reset          │
│   └─ [TEXTURE] tab → STANDARD / SOFT SKIN / GLOW / FILM │ GRAIN│
│   • tap ‹ (back) → collapse to STATE 1                         │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 Button-by-button

| Control | Where (ref) | Tap / gesture → action | Result |
|---|---|---|---|
| **Yellow 3×3-dots button** | Viewfinder bottom-right (`02`,`03`) | Tap | Opens Photographic Styles → **State 1** |
| **Style thumbnail** | Selection Strip (`02`) | Tap | Focuses style, live preview updates; reveals **Customize** (State 2) |
| **Customize** button | Over viewfinder (State 2) | Tap | Opens **Calibration Workspace** (State 3) |
| **`PALETTE`** tab | Sheet header (`01`) | Tap | Swaps sheet content to the 2D pad + HUD |
| **`TEXTURE`** tab | Sheet header (`01`) | Tap | Swaps sheet content to the texture row |
| **2D pad** | Palette tab (§1) | Drag puck | Sets `padColor` (x) / `padTone` (y), clamped `[-100,+100]`; HUD updates live |
| **`↺` reset** | Sheet header right (`01`) | Tap | Re-anchors puck/params to the preset baseline; **hidden when already at anchor** |
| **`‹` back** | Sheet header left (`01`) | Tap | Collapse sheet → **State 1** |
| **`⊘ STANDARD`** | Texture tab (`01`) | Tap | `textureMode = STANDARD` (no texture); hide pips |
| **`👤 SOFT SKIN`** | Texture tab (`01`) | Tap | `textureMode = SOFT_SKIN`; reveal strength pips |
| **`◌ GLOW`** | Texture tab (`01`) | Tap | `textureMode = GLOW`; reveal strength pips |
| **`▤ FILM`** | Texture tab (`01`) | Tap | `textureMode = FILM`; reveal strength pips |
| **`▦ GRAIN`** | Texture tab, right of the `│` divider (`01`) | Tap (toggle) | `filmGrainEnabled = !filmGrainEnabled` — **binary**, not a slider |
| **Strength pips** | Texture tab (`01`) | Drag / tap segments | `textureIntensity` `0…100` for the selected mode |
| **Intensity slider** | Palette tab (§1) | Drag | `intensity` `0.0…1.0` (master blend) |
| **Shutter** | Viewfinder centre (`01`,`02`,`03`) | Tap / **light press** | Light press → quick-scrub overlay; hard press → capture |
| **`f`** | Viewfinder left (`02`,`03`) | Tap | Aperture control |
| **Lens picker `.5 1x 2 4 8`** | Above shutter (`02`,`03`) | Tap | Switch lens / zoom |
| **`VIDEO / PHOTO / PORTRAIT`** | Bottom-centre (`02`,`03`) | Tap | Switch capture mode |
| **Photo thumbnail** | Bottom-left (`02`,`03`) | Tap | Open gallery |
| **Flip `⟳`** | Bottom-right (`02`,`03`) | Tap | Toggle front / rear camera |
| **`1/2 2.8 -1.0 EV AF RAW48`** | Pro bar (top, `03`) | Tap each | Shutter · aperture · EV · AF · ProRAW toggles |
| **`AE/AF LOCK`** | Viewfinder (`03`) | Tap | Lock focus + exposure |
| **Focus square + sun slider** | Viewfinder (`03`) | Drag | Set focus point; drag the sun to change EV |

### 7.3 Behaviour rules to encode in state

- **Reset visibility:** `↺` renders only when `!StyleState.isAtAnchor` (spec §1).
- **Puck rests at the anchor**, not the origin — opening a style pinches the puck to its `(anchorX, anchorY)`.
- **Pips vs toggle:** `STANDARD/SOFT_SKIN/GLOW/FILM` → `textureIntensity` slider; `GRAIN` → boolean (matches the `│` divider in ref `01` and spec §5).
- **Sheet is non-modal over the viewfinder** — live preview keeps running behind it.
- **Back (`‹`) ≠ Reset (`↺`):** `‹` closes the sheet; `↺` clears the adjustments but stays in the sheet.

> **Out-of-scope for the UI-only milestone:** camera/optics controls (lens picker, `f`, EV, RAW, flip,
> capture modes) are stubbed as visual placeholders until the Camera2/CameraX phase — but they are
> listed here so the layout and hit-areas are final from the start.

