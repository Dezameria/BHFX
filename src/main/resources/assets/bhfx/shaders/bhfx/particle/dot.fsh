// bhfx:dot — built-in material: a soft round dot in p.color. ADD: a glowing core; ALPHA: a soft puff. Shape it from
// Java with colorOverLife / alphaOverLife / sizeOverLife; no uniforms needed.

#include "bhfx:particle.glsl"

vec4 material(Particle p) {
    float r2 = dot(p.corner, p.corner);
    float edge = 1.0 - smoothstep(0.6, 1.0, r2);
    float body = exp(-r2 * 3.0) * edge;
    float core = PMode.x < 0.5 ? exp(-r2 * 14.0) * 1.5 : 0.0;
    float a = body * p.color.a * fadeInOut(p.age, 0.04, 0.2);
    return vec4(p.color.rgb * (1.0 + core), clamp(a, 0.0, 1.0));
}
