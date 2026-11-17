// bhfx:shafts (FxShafts): shafts of light through mist, from a point in the world. The air inside a cone (or a whole
// ball) round the source glows, more where a ray of light passes: the rays are noise over the direction from the
// source, so each stays a straight line out of it, and they drift slowly. Marched along the view ray and stopped at
// the scene's depth, so terrain in front hides them; terrain does not cast shadows into them. Pure added light.
// Written as a layer (PassOptions.halfRes); Mode.x = 0 draws it as an ordinary pass instead.

#include "bhfx:scene.glsl"
#include "bhfx:shapes.glsl"
#include "bhfx:noise.glsl"

uniform vec4 Source; // camera-relative source, length (blocks)
uniform vec4 Axis;   // unit direction of the cone, cos of its half angle (-2 = every direction)
uniform vec4 Tint;   // linear rgb * strength, mist share 0..1
uniform vec4 P0;     // seconds, rays per radian, sharpness 0..1, drift
uniform vec4 Mode;   // 1 = layer, fade 0..1, unused, unused

float shaftNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}

void main() {
    Scene s = sceneAt(texCoord);
    vec3 light = vec3(0.0);
    float len = Source.w;
    vec2 span = raySphere(s.origin, s.dir, Source.xyz, len);
    float t0 = max(span.x, 0.0);
    float t1 = min(span.y, s.dist);
    if (span.y > 0.0 && t1 > t0 && len > 0.0) {
        const int STEPS = 32;
        float dt = (t1 - t0) / float(STEPS);
        // a fixed, evenly spread offset per pixel (interleaved gradient noise): no flicker, and finer than white noise
        float t = t0 + dt * fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
        vec3 drift = vec3(0.13, 0.07, -0.1) * P0.x * P0.w;
        float lo = mix(0.3, 0.55, P0.z);
        float hi = lo + mix(0.45, 0.12, P0.z);
        float sum = 0.0;
        for (int i = 0; i < STEPS; i++) {
            vec3 v = s.origin + s.dir * t - Source.xyz;
            float d = max(length(v), 1e-4);
            vec3 u = v / d;
            float cone = Axis.w < -1.5 ? 1.0 : smoothstep(Axis.w, mix(Axis.w, 1.0, 0.35), dot(u, Axis.xyz));
            if (cone > 0.0) {
                float far = clamp(1.0 - d / len, 0.0, 1.0);
                float n = 0.65 * shaftNoise(u * P0.y + drift) + 0.35 * shaftNoise(u * P0.y * 2.3 - drift * 1.7 + 11.0);
                float rays = smoothstep(lo, hi, n);
                sum += cone * far * sqrt(far) * (0.5 + 0.5 * exp(-3.0 * d / len)) * smoothstep(0.0, 0.06, d / len) * mix(rays, 1.0, Tint.a);
            }
            t += dt;
        }
        light = Tint.rgb * sum * dt * Mode.y;
    }
    if (Mode.x > 0.5) {
        fragColor = vec4(light, 0.0);
    } else {
        fragColor = vec4(s.color + light, 1.0);
    }
}
