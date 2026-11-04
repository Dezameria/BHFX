// bhfx test material: a shock ring lying on the ground (FLAT facing), broken up by noise.

#include "bhfx:particle.glsl"

uniform sampler2D Ramp;
uniform sampler2D Noise;

vec4 material(Particle p) {
    float r = length(p.corner);
    float grow = 1.0 - pow(1.0 - p.age, 3.0);
    float radius = mix(0.1, 0.92, grow);
    float width = mix(0.03, 0.09, p.age);
    float x = (r - radius) / width;
    float ring = exp(-x * x);
    float inner = (1.0 - smoothstep(0.0, radius, r)) * 0.25 * (1.0 - p.age);
    float ang = atan(p.corner.y, p.corner.x) / 6.2831853 + 0.5;
    float n = texture(Noise, vec2(ang * 3.0, r * 0.5 - p.seconds * 0.2)).r;
    float heat = 1.0 - p.age;
    vec3 col = texture(Ramp, vec2(0.4 + 0.6 * heat, 0.5)).rgb * p.color.rgb * (1.5 + 3.0 * heat);
    float a = (ring * (0.5 + n) + inner * n) * (1.0 - smoothstep(0.92, 1.0, r)) * fadeInOut(p.age, 0.0, 0.6);
    return vec4(col, clamp(a, 0.0, 1.0));
}
