// bhfx built-in test (bhfx:test_volume / test_volume_full): a ball of glowing, drifting mist marched through with 40
// steps per pixel, hidden behind terrain by the depth buffer. Written as a layer (PassOptions.halfRes): it outputs
// premultiplied light and coverage, not the finished image, so the same shader runs at half or at full resolution.
// Mode.x = 1 when drawn as a layer, 0 as an ordinary pass (then it lays itself over the image).

#include "bhfx:scene.glsl"
#include "bhfx:shapes.glsl"
#include "bhfx:noise.glsl"

uniform sampler2D Noise;
uniform vec4 Bound;  // centre camera-relative, radius
uniform vec4 P0;     // seconds, fade 0..1, unused, unused
uniform vec4 Tint;   // linear rgb (Uniforms.color), unused
uniform vec4 Mode;

float density(vec3 p, float t) {
    vec3 q = (p - Bound.xyz) / Bound.w;
    float ball = 1.0 - smoothstep(0.35, 1.0, length(q));
    // three slices of one 2D noise stand in for 3D noise
    float a = texture(Noise, q.xz * 0.9 + vec2(t * 0.05, q.y * 0.6)).r;
    float b = texture(Noise, q.xy * 1.7 - vec2(q.z * 0.5, t * 0.08)).r;
    float c = texture(Noise, q.zy * 3.1 + vec2(t * 0.11, 0.0)).r;
    float n = a * 0.55 + b * 0.3 + c * 0.15;
    return ball * smoothstep(0.38, 0.7, n);
}

void main() {
    Scene s = sceneAt(texCoord);
    vec4 layer = vec4(0.0);
    vec2 span = raySphere(s.origin, s.dir, Bound.xyz, Bound.w);
    float t0 = max(span.x, 0.0);
    float t1 = min(span.y, s.dist);
    if (span.y > 0.0 && t1 > t0) {
        const int STEPS = 40;
        float dt = (t1 - t0) / float(STEPS);
        float t = t0 + dt * hash12(gl_FragCoord.xy + fract(P0.x) * 61.0);
        float through = 1.0;
        vec3 light = vec3(0.0);
        for (int i = 0; i < STEPS; i++) {
            vec3 p = s.origin + s.dir * t;
            float d = density(p, P0.x);
            if (d > 0.001) {
                float a = 1.0 - exp(-d * dt * 2.2);
                // brighter and whiter toward the middle of the ball
                float core = 1.0 - clamp(length(p - Bound.xyz) / Bound.w, 0.0, 1.0);
                vec3 c = mix(Tint.rgb, vec3(1.0), core * core * 0.7) * (0.6 + 1.6 * core);
                light += c * a * through;
                through *= 1.0 - a;
            }
            t += dt;
        }
        layer = vec4(light, 1.0 - through) * P0.y;
    }
    if (Mode.x > 0.5) {
        fragColor = layer;
    } else {
        fragColor = vec4(s.color * (1.0 - clamp(layer.a, 0.0, 1.0)) + layer.rgb, 1.0);
    }
}
