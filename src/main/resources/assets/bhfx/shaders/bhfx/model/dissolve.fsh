// bhfx:dissolve — the model as painted (like bhfx:textured), burning away by 3D noise in model space.
// MParams.x: 0 whole .. 1 gone. MParams.y: width of the burning edge in noise units (default 0.08 when 0).
// MParams.z: noise scale in waves per block (default 4 when 0). MColor.rgb: the edge's colour (values above 1 glow).

#include "bhfx:noise.glsl"

float bhfxDissolveNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}

vec4 material(Surface s) {
    vec4 t = texture(MTexture, s.uv);
    if (t.a < 0.1) {
        return vec4(0.0);
    }
    float scale = MParams.z > 0.0 ? MParams.z : 4.0;
    vec3 q = s.local * scale;
    float n = 0.6 * bhfxDissolveNoise(q) + 0.3 * bhfxDissolveNoise(q * 2.03 + 17.0) + 0.1 * bhfxDissolveNoise(q * 4.1 + 31.0);
    float w = MParams.y > 0.0 ? MParams.y : 0.08;
    float d = n - MParams.x * (1.0 + w);
    if (d < 0.0) {
        return vec4(0.0);
    }
    float shade = 0.55 + 0.45 * max(dot(s.normal, normalize(vec3(0.3, 1.0, 0.2))), 0.0);
    vec3 lit = t.rgb * shade * mix(0.12, 1.0, s.light);
    float edge = 1.0 - smoothstep(0.0, w, d);
    return vec4(lit + MColor.rgb * edge * 2.5, 1.0);
}
