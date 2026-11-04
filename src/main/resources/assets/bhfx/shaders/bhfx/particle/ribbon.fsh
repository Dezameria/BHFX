// bhfx:ribbon — built-in material for RIBBON facing: a soft band across (p.corner.y), brighter in the middle, in
// p.color. Thins out towards the old end (p.age runs along the ribbon) and softens the newest few percent, so the
// head has no square cut; fade it further with alphaOverLife.

#include "bhfx:particle.glsl"

vec4 material(Particle p) {
    float y = p.corner.y;
    float band = exp(-y * y * 2.5) * (1.0 - smoothstep(0.8, 1.0, abs(y)));
    float core = exp(-y * y * 18.0);
    float tail = 1.0 - smoothstep(0.75, 1.0, p.age);
    float head = smoothstep(0.0, 0.04, p.age);
    float a = band * tail * head * p.color.a;
    return vec4(p.color.rgb * (1.0 + core * 1.2), clamp(a, 0.0, 1.0));
}
