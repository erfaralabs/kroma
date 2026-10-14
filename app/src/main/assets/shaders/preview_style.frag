#version 300 es
// Preview variant of photographic_style.frag: the camera feed arrives sRGB-encoded, so this
// wrapper converts sRGB -> linear, runs the SAME Oklab style maths, then converts back.
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

in vec2 v_TexCoord;
in vec2 v_ScreenCoord;
out vec4 out_FragColor;

uniform samplerExternalOES u_LinearP3Texture;
uniform sampler2D u_SkinMaskTexture;
uniform vec2      u_PadCoordinates;   // x: Color [-100, 100], y: Tone [-100, 100]
uniform float     u_Intensity;        // Master blend [0.0, 1.0]
uniform vec2      u_PresetAnchor;     // (AnchorX, AnchorY)
uniform int       u_PresetId;
uniform float     u_BetaPreset;
uniform float     u_HueBiasAngle;     // In radians
uniform float     u_ShadowToe;
uniform int       u_TextureMode;      // 0: Standard, 1: SoftSkin, 2: Glow, 3: Film
uniform float     u_TextureIntensity; // [0.0, 1.0]
uniform bool      u_FilmGrainEnabled;
uniform float     u_Time;

float procedural_noise(vec2 p) {
  vec2 k = vec2(23.14069263277926, 2.665144142690225);
  return fract(cos(dot(p, k)) * 12345.6789);
}

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

vec3 srgb_to_linear(vec3 c) {
  return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

vec3 linear_to_srgb(vec3 c) {
  c = clamp(c, 0.0, 1.0);
  return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

vec3 rgb_to_oklab(vec3 rgb) {
  vec3 lms = M1 * rgb;
  vec3 lms_cube = pow(max(lms, vec3(1e-6)), vec3(1.0 / 3.0));
  return M2 * lms_cube;
}

vec3 oklab_to_rgb(vec3 lab) {
  vec3 lms_cube = M2_INV * lab;
  vec3 lms = lms_cube * lms_cube * lms_cube;
  return max(M1_INV * lms, vec3(0.0));
}

void main() {
  vec4 baseColor = texture(u_LinearP3Texture, v_TexCoord);
  float skinAlpha = texture(u_SkinMaskTexture, v_ScreenCoord).r;

  vec3 lin = srgb_to_linear(baseColor.rgb);

  if (u_PresetId == 5 && length(u_PadCoordinates) < 0.001 && u_TextureMode == 0 && !u_FilmGrainEnabled) {
    out_FragColor = vec4(baseColor.rgb, baseColor.a);
    return;
  }

  vec3 lab = rgb_to_oklab(lin);
  float L = lab.x;
  float a = lab.y;
  float b = lab.z;

  vec2 effectiveCoords = clamp(u_PadCoordinates + u_PresetAnchor, vec2(-100.0), vec2(100.0));
  float effColor = effectiveCoords.x;
  float effTone  = effectiveCoords.y;

  // 1. Parametric Tone Curve
  float gamma = exp(-effTone / 50.0);
  float sCurve = pow(max(L, 1e-6), gamma) / (pow(max(L, 1e-6), gamma) + pow(max(1.0 - L, 0.0001), gamma));
  float L_styled = L + (sCurve - L) * (4.0 * L * (1.0 - L));
  float shadowWeight = pow(1.0 - clamp(L_styled, 0.0, 1.0), 2.0);
  L_styled = clamp(L_styled + u_ShadowToe * shadowWeight * (1.0 - abs(effTone) / 200.0), 0.0, 1.0);

  // 2. Parametric Chroma & Hue Mapping
  float chroma = sqrt(a * a + b * b);
  float hue = (chroma > 1e-5) ? atan(b, a) : 0.0;

  float k_C = max(0.0, 1.0 + (effColor / 100.0) * u_BetaPreset);
  float chroma_styled = chroma * k_C;
  float hue_styled = hue + u_HueBiasAngle * (effColor / 100.0);

  if (u_PresetId == 13 || u_PresetId == 14) {
    chroma_styled = 0.0;
  }

  // 3. Semantic Spatial Awareness & Skin Tone Protection (Spec §4.4)
  float d_skin = sqrt(pow((a - 0.092) / 0.040, 2.0) + pow((b - 0.061) / 0.030, 2.0));
  float lum_gate = smoothstep(0.12, 0.26, L) * (1.0 - smoothstep(0.85, 0.96, L));
  float sat_gate = 1.0 - smoothstep(0.18, 0.25, chroma);
  vec2 frame_offset = (v_ScreenCoord - vec2(0.5, 0.48)) * vec2(1.0, 0.85);
  float spatial_weight = 1.0 - smoothstep(0.35, 0.75, length(frame_offset));

  float w_skin = clamp(1.0 - d_skin, 0.0, 1.0) * lum_gate * sat_gate * mix(0.75, 1.0, spatial_weight) * skinAlpha;

  float L_protected = mix(L_styled, 0.85 * L + 0.15 * L_styled, w_skin);
  float C_protected = mix(chroma_styled, chroma * (1.0 + (effColor / 100.0) * 0.15), w_skin);
  float h_protected = mix(hue_styled, hue, w_skin);

  vec3 lab_processed = vec3(
    L_protected,
    C_protected * cos(h_protected),
    C_protected * sin(h_protected)
  );

  vec3 styledRgb = oklab_to_rgb(lab_processed);
  vec3 finalRgb = mix(lin, styledRgb, u_Intensity);

  // 4. Texture Overlays (Spec §5)
  // Soft Skin: Smooth skin regions gated by skin tone confidence
  if (u_TextureMode == 1 && u_TextureIntensity > 0.0) {
    vec2 stepUV = vec2(1.5 / 1080.0, 1.5 / 1920.0);
    vec3 c0 = srgb_to_linear(texture(u_LinearP3Texture, v_TexCoord + vec2(-stepUV.x, 0.0)).rgb);
    vec3 c1 = srgb_to_linear(texture(u_LinearP3Texture, v_TexCoord + vec2(stepUV.x, 0.0)).rgb);
    vec3 c2 = srgb_to_linear(texture(u_LinearP3Texture, v_TexCoord + vec2(0.0, -stepUV.y)).rgb);
    vec3 c3 = srgb_to_linear(texture(u_LinearP3Texture, v_TexCoord + vec2(0.0, stepUV.y)).rgb);
    vec3 smoothed = (finalRgb * 2.0 + c0 + c1 + c2 + c3) / 6.0;
    finalRgb = mix(finalRgb, smoothed, clamp(w_skin * u_TextureIntensity * 1.5, 0.0, 1.0));
  }

  // Glow: Highlight diffusion bloom bleed
  if (u_TextureMode == 2 && u_TextureIntensity > 0.0) {
    vec2 stepG = vec2(3.0 / 1080.0, 3.0 / 1920.0);
    vec3 g0 = srgb_to_linear(texture(u_LinearP3Texture, v_TexCoord + vec2(-stepG.x, -stepG.y)).rgb);
    vec3 g1 = srgb_to_linear(texture(u_LinearP3Texture, v_TexCoord + vec2(stepG.x, -stepG.y)).rgb);
    vec3 g2 = srgb_to_linear(texture(u_LinearP3Texture, v_TexCoord + vec2(-stepG.x, stepG.y)).rgb);
    vec3 g3 = srgb_to_linear(texture(u_LinearP3Texture, v_TexCoord + vec2(stepG.x, stepG.y)).rgb);
    vec3 glowColor = (g0 + g1 + g2 + g3) * 0.25;
    float glowLum = dot(glowColor, vec3(0.2126, 0.7152, 0.0722));
    float highlightWeight = smoothstep(0.4, 0.9, glowLum);
    finalRgb += glowColor * (highlightWeight * u_TextureIntensity * 0.45);
  }

  // Film: Logarithmic highlight compression and subtle black lift
  if (u_TextureMode == 3 && u_TextureIntensity > 0.0) {
    vec3 filmColor = vec3(1.0) - exp(-finalRgb * 1.35) + vec3(0.015);
    finalRgb = mix(finalRgb, filmColor, u_TextureIntensity * 0.65);
  }

  // 5. Film Grain: Luminance-modulated procedural noise
  vec3 srgbOut = linear_to_srgb(finalRgb);
  if (u_FilmGrainEnabled) {
    float lum = dot(srgbOut, vec3(0.2126, 0.7152, 0.0722));
    float midtoneMod = sqrt(max(0.0, lum * (1.0 - lum))) * 2.0;
    float noise = (procedural_noise(v_ScreenCoord * 1024.0 + fract(u_Time)) - 0.5) * 0.07;
    srgbOut = clamp(srgbOut + vec3(noise * midtoneMod), 0.0, 1.0);
  }

  out_FragColor = vec4(srgbOut, baseColor.a);
}
