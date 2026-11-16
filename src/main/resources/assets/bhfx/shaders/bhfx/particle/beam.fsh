// bhfx:beam — built-in material for RIBBON facing: a texture laid along the strip, tinted by p.color. Bind it as
// "Sprite" (a repeating FxTexture, not clamped). Optional BeamUv = (repeats per block along, scroll in repeats per
// second toward the newest point, mask, core): mask = 1 takes coverage from the red channel (grayscale images) and the
// colour from p.color alone; core adds a bright line down the middle (0 = none, 1 = strong). Unset = one repeat per
// block, no scroll, alpha coverage, no core. Soft at both edges across the strip.

#include "bhfx:particle.glsl"

uniform sampler2D Sprite;
uniform vec4 BeamUv;

vec4 material(Particle p) {
    float repeats = BeamUv.x > 0.0 ? BeamUv.x : 1.0;
    float y = p.corner.y;
    vec2 uv = vec2(p.along * repeats - PTime * BeamUv.y, y * 0.5 + 0.5);
    vec4 s = texture(Sprite, uv);
    bool mask = BeamUv.z > 0.5;
    float cover = mask ? s.r : s.a;
    vec3 rgb = mask ? p.color.rgb : s.rgb * p.color.rgb;
    float edge = 1.0 - smoothstep(0.55, 1.0, abs(y));
    float core = BeamUv.w * exp(-y * y * 22.0);
    float a = (cover * edge + core) * p.color.a;
    return vec4(rgb * (1.0 + core * 1.5), clamp(a, 0.0, 1.0));
}
