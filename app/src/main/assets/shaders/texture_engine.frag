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
