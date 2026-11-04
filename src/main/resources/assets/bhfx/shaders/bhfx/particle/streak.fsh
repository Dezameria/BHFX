// bhfx:streak — built-in material: a soft streak along the quad's x (VELOCITY: the motion, front at +1; AXIS: the
// axis), in p.color, brighter towards the front.

#include "bhfx:particle.glsl"

vec4 material(Particle p) {
    float across = exp(-p.corner.y * p.corner.y * 9.0) * (1.0 - smoothstep(0.85, 1.0, abs(p.corner.y)));
    float along = 1.0 - smoothstep(0.35, 1.0, abs(p.corner.x));
    float head = smoothstep(-1.0, 1.0, p.corner.x);
    float a = across * along * (0.35 + 0.65 * head) * p.color.a * fadeInOut(p.age, 0.0, 0.25);
    return vec4(p.color.rgb * (1.0 + across * head), clamp(a, 0.0, 1.0));
}
