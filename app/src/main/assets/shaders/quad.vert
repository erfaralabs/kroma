#version 300 es
// Full-screen quad vertex shader for the Kroma preview style pass.
// Applies the SurfaceTexture transform, then a centre-crop scale so the camera image keeps its
// aspect ratio instead of being squeezed to the viewport (PreviewView FILL_CENTER behaviour).

in vec2 a_Position;   // -1..1 clip space
in vec2 a_TexCoord;   // 0..1
out vec2 v_TexCoord;

uniform mat4 u_TexMatrix;   // SurfaceTexture transform
uniform vec2 u_CropScale;   // uv crop (<=1) per axis

void main() {
  vec2 cropped = vec2(0.5) + (a_TexCoord - vec2(0.5)) * u_CropScale;
  v_TexCoord = (u_TexMatrix * vec4(cropped, 0.0, 1.0)).xy;
  gl_Position = vec4(a_Position, 0.0, 1.0);
}
