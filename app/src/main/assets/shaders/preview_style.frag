#version 300 es
// Preview variant of photographic_style.frag: the camera feed arrives sRGB-encoded, so this
// wrapper converts sRGB -> linear, runs the SAME Oklab style maths, then converts back.
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

in vec2 v_TexCoord;
out vec4 out_FragColor;

uniform samplerExternalOES u_LinearP3Texture;
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

vec3 srgb_to_linear(vec3 c) {
  return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

vec3 linear_to_srgb(vec3 c) {
  c = clamp(c, 0.0, 1.0);
  return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

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

  vec3 lin = srgb_to_linear(baseColor.rgb);

  if (u_PresetId == 5 && length(u_PadCoordinates) < 0.001) {
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
  float sCurve = pow(max(L, 0.0), gamma) / (pow(max(L, 0.0), gamma) + pow(max(1.0 - L, 0.0001), gamma));
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
  vec3 finalRgb = mix(lin, styledRgb, u_Intensity);
  out_FragColor = vec4(linear_to_srgb(finalRgb), baseColor.a);
}
