// bhfx test material (bhfx:test_particles): a glowing ember. Colour from a 256x1 ramp texture (read as stored),
// cooling along the ramp as it ages; the bright core goes above 1 so it blooms.

#include "bhfx:particle.glsl"

uniform sampler2D Ramp;

vec4 material(Particle p) {
    float r = length(p.corner);
    float core = exp(-r * r * 9.0);
    float halo = exp(-r * r * 2.5) * 0.35;
    float flicker = 0.7 + 0.3 * sin(p.seconds * 23.0 + p.seed * 61.0);
    float heat = clamp((1.0 - p.age) * (0.75 + 0.25 * p.seed), 0.0, 1.0);
    vec3 col = texture(Ramp, vec2(0.25 + 0.75 * heat, 0.5)).rgb * p.color.rgb;
    float a = (core + halo) * flicker * fadeInOut(p.age, 0.1, 0.5) * p.color.a;
    return vec4(col * (1.0 + 3.0 * core), clamp(a, 0.0, 1.0));
}
