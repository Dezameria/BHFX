// bhfx test material: a smoke puff (ALPHA). Shape and dissolve come from a packed noise texture (r = fbm,
// g = cells), so there is no noise maths per pixel. A fake sphere normal lights it from above and lets the fire
// light its underside, which makes a flat sprite read as a volume.

#include "bhfx:particle.glsl"

uniform sampler2D Noise;   // r fbm, g cells
uniform vec4 FireGlow;     // rgb from below, strength

vec4 material(Particle p) {
    vec2 c = p.corner;
    vec2 nuv = p.uv * 0.45 + vec2(p.seed * 7.13, p.seed * 3.71) + vec2(0.0, -p.seconds * 0.05);
    vec4 n = texture(Noise, nuv);
    float fbm = n.r;
    float cells = n.g;
    float bump = cells * 0.7 + fbm * 0.3;
    float shape = radialMask(c + (vec2(fbm, cells) - 0.5) * 0.35, 0.2);
    float dissolve = erode(bump, p.age * 0.85, 0.25);
    vec3 nrm = sphereNormal(c, bump);
    float top = wrapLight(nrm, vec3(0.3, 1.0, 0.6), 0.6);
    float bottom = wrapLight(nrm, vec3(0.0, -1.0, 0.4), 0.3);
    vec3 col = p.color.rgb * (0.35 + 0.65 * top) + FireGlow.rgb * FireGlow.a * bottom * (1.0 - p.age);
    float a = shape * dissolve * p.color.a * fadeInOut(p.age, 0.12, 0.45);
    return vec4(col, a);
}
