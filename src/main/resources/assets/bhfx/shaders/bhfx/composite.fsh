// bhfx composite: the last pass (or the world) to the screen at native resolution, with the frame's screen effects,
// tone-mapped once.

#include "bhfx:light.glsl"

uniform sampler2D SceneLowSampler;
uniform float Upscaled;
uniform vec4 Flash;     // rgb, strength (above 1 washes to white)
uniform vec4 Vignette;  // rgb, strength
uniform float Desaturate;
uniform float Aberration;

vec3 fxAt(vec2 uv) {
    uv = clamp(uv, vec2(0.0005), vec2(0.9995));
    vec3 c = texture(DiffuseSampler, uv).rgb;
    if (Upscaled > 0.5) {
        // passes ran below native resolution: restore the world's fine detail by the ratio of the sharp world to its
        // low-resolution copy (a ratio rather than a difference, so darkened or tinted areas keep their detail)
        vec3 sharp = texture(SceneSampler, uv).rgb;
        vec3 soft = texture(SceneLowSampler, uv).rgb;
        c *= clamp((sharp + 0.04) / (soft + 0.04), 0.6, 1.8);
    }
    return c;
}

void main() {
    vec2 uv = texCoord;
    vec2 fromCentre = (uv - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
    float r2 = dot(fromCentre, fromCentre);

    vec3 col;
    if (Aberration > 0.001) {
        vec2 shift = (uv - 0.5) * r2 * Aberration * 0.03;
        col = vec3(fxAt(uv + shift).r, fxAt(uv).g, fxAt(uv - shift).b);
    } else {
        col = fxAt(uv);
    }

    if (Desaturate > 0.001) {
        col = mix(col, vec3(luma(col)), clamp(Desaturate, 0.0, 1.0));
    }
    if (Vignette.a > 0.001) {
        float edge = smoothstep(0.15, 0.75, r2);
        col = mix(col, col * Vignette.rgb * 1.2 + Vignette.rgb * 0.03, clamp(edge * Vignette.a, 0.0, 1.0));
    }
    if (Flash.a > 0.001) {
        col += Flash.rgb * Flash.a;
        col = mix(col, vec3(1.0), clamp(Flash.a - 1.0, 0.0, 1.0));
    }
    fragColor = vec4(tonemapSoft(col), 1.0);
}
