// bhfx:test_swirl — an anime wind / smoke wisp along a RIBBON drawn all at once: one shared noise texture stretched
// along the strip by p.along (0 at the tail .. 1 at the head here), swept in from the tail and off the head over the
// life, tapered towards the tail, lighter where a strip lying in a plane is seen edge-on (p.facing).
// p.data: x = streaks across (texture repeats over the width), y = scroll along per second, z = share of the strip
// visible at once (the sweep window), w = unused.

#include "bhfx:particle.glsl"

uniform sampler2D Noise;

vec4 material(Particle p) {
    float u = p.along;
    float v = p.corner.y;
    float span = max(p.data.z, 0.2);
    // the window's head runs from near the tail to past the head over the life
    float headAt = mix(0.2, 1.0 + span * 0.6, p.age);
    float win = smoothstep(headAt - span, headAt - span * 0.45, u) * (1.0 - smoothstep(headAt - 0.06, headAt, u));
    // thin at the tail, full near the head
    float width = mix(0.3, 1.0, smoothstep(headAt - span, headAt - 0.12, u));
    float across = 1.0 - smoothstep(width * 0.45, width, abs(v));

    // streaks: the texture stretched along the strip, fine across it, scrolling along
    vec2 tuv = vec2(u * 0.55 - p.seconds * p.data.y + p.seed, v * max(p.data.x, 0.1) * 0.25 + p.seed * 3.1);
    float n1 = texture(Noise, tuv).r;
    float n2 = texture(Noise, tuv * vec2(1.7, 2.3) + vec2(0.31, 0.57)).r;
    float streak = smoothstep(0.38, 0.82, n1 * 0.62 + n2 * 0.55);
    float core = exp(-v * v / max(width * width * 0.06, 1e-3));

    float density = (streak * 0.8 + core * 0.45) * across * win;
    density *= mix(0.5, 1.0, p.facing);
    vec3 col = mix(vec3(0.68, 0.76, 0.9), vec3(1.0), clamp(core + streak * 0.5, 0.0, 1.0));
    return vec4(col * p.color.rgb, clamp(density * p.color.a, 0.0, 1.0));
}
