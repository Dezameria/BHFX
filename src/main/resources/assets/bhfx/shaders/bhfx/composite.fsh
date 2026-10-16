// bhfx composite: the last pass (or the world) to the screen at native resolution with the frame's screen effects.
// Order: ripples → aberration / zoom blur → bloom → grade → tone-map → impact frame → speed lines → afterimage →
// flash → grain. Everything after the tone-map works in display space.

#include "bhfx:light.glsl"
#include "bhfx:noise.glsl"

uniform sampler2D SceneLowSampler;
uniform sampler2D BloomSampler;
uniform sampler2D HoldSampler;
uniform float Upscaled;     // 0..1 how much detail to restore
uniform float Bloom;
uniform vec4 Flash;         // rgb, strength (above 1 washes to white)
uniform vec4 Vignette;      // rgb, strength
uniform float Desaturate;
uniform float Aberration;
uniform float ZoomBlur;
uniform float SpeedLines;
uniform vec4 Grade;         // contrast, grain, afterimage, unused
uniform vec4 ImpactA;       // strength, kind, focus uv
uniform vec4 ImpactB;       // dark rgb, focus-line density
uniform vec4 ImpactC;       // light rgb, kind parameter (dot size px / slash angle deg)
uniform vec4 RippleA[8];    // centre uv, radius, strength (radius in screen heights)
uniform vec4 RippleB[8];    // width (screen heights), unused
uniform float RippleCount;

const float TAU = 6.2831853;

vec2 aspectScale() {
    return vec2(OutSize.x / OutSize.y, 1.0);
}

vec3 fxAt(vec2 uv) {
    uv = clamp(uv, vec2(0.0005), vec2(0.9995));
    vec3 c = texture(DiffuseSampler, uv).rgb;
    if (Upscaled > 0.001) {
        // passes ran below native resolution: restore the world's fine detail by the ratio of the sharp world to its
        // low-resolution copy (a ratio rather than a difference, so darkened or tinted areas keep their detail)
        vec3 sharp = texture(SceneSampler, uv).rgb;
        vec3 soft = texture(SceneLowSampler, uv).rgb;
        c *= mix(vec3(1.0), clamp((sharp + 0.04) / (soft + 0.04), 0.6, 1.8), Upscaled);
    }
    return c;
}

vec3 fetch(vec2 uv) {
    if (Aberration < 0.001) {
        return fxAt(uv);
    }
    vec2 fromCentre = (uv - 0.5) * aspectScale();
    vec2 shift = (uv - 0.5) * dot(fromCentre, fromCentre) * Aberration * 0.03;
    return vec3(fxAt(uv + shift).r, fxAt(uv).g, fxAt(uv - shift).b);
}

vec2 rippled(vec2 uv) {
    vec2 a = aspectScale();
    for (int i = 0; i < 8; i++) {
        if (float(i) >= RippleCount) {
            break;
        }
        vec4 r = RippleA[i];
        float width = max(RippleB[i].x, 1e-4);
        vec2 d = (uv - r.xy) * a;
        float dist = length(d);
        float x = (dist - r.z) / width;
        float wave = exp(-x * x) * sin(x * 3.14159);
        vec2 dir = dist > 1e-5 ? d / dist : vec2(0.0);
        uv -= dir / a * wave * r.w * width * 0.6;
    }
    return uv;
}

// ---------------------------------------------------------------- impact frames

float inkLuma(vec2 uv) {
    return luma(clamp(fxAt(uv), 0.0, 1.0));
}

float edgeAt(vec2 uv) {
    vec2 px = 1.0 / OutSize;
    float tl = inkLuma(uv + px * vec2(-1.0, 1.0));
    float t = inkLuma(uv + px * vec2(0.0, 1.0));
    float tr = inkLuma(uv + px * vec2(1.0, 1.0));
    float l = inkLuma(uv + px * vec2(-1.0, 0.0));
    float r = inkLuma(uv + px * vec2(1.0, 0.0));
    float bl = inkLuma(uv + px * vec2(-1.0, -1.0));
    float b = inkLuma(uv + px * vec2(0.0, -1.0));
    float br = inkLuma(uv + px * vec2(1.0, -1.0));
    float gx = (tr + 2.0 * r + br) - (tl + 2.0 * l + bl);
    float gy = (tl + 2.0 * t + tr) - (bl + 2.0 * b + br);
    return length(vec2(gx, gy));
}

vec3 impactFrame(vec2 uv, vec3 col) {
    int kind = int(ImpactA.y + 0.5);
    vec3 dark = ImpactB.rgb;
    vec3 light = ImpactC.rgb;
    vec2 d = (uv - ImpactA.zw) * aspectScale();
    float l = luma(col);
    vec3 result;
    bool inkLight = false; // which colour focus lines use

    if (kind == 2) {
        // negative with hard contrast
        result = clamp((0.5 - col) * 1.8 + 0.5, 0.0, 1.0);
    } else if (kind == 3) {
        // rotated dot screen; dots grow where the picture is dark
        float cell = max(ImpactC.a, 3.0);
        mat2 turn = mat2(0.866, 0.5, -0.5, 0.866);
        vec2 f = fract(turn * (uv * OutSize) / cell) - 0.5;
        float radius = sqrt(clamp(1.0 - l, 0.0, 1.0)) * 0.62;
        float dotMask = 1.0 - smoothstep(radius - 0.06, radius + 0.06, length(f));
        result = mix(light, dark, dotMask);
    } else if (kind == 4) {
        // rows torn sideways, colour split, scanlines, tinted
        float row = floor(uv.y * OutSize.y / 14.0);
        float tick = floor(GameTime * 24.0);
        float torn = step(0.55, hash12(vec2(row * 1.7, tick + 3.0)));
        vec2 g = vec2(uv.x + (hash12(vec2(row, tick)) - 0.5) * 0.12 * torn, uv.y);
        vec3 split = tonemapSoft(vec3(fxAt(g + vec2(0.006, 0.0)).r, fxAt(g).g, fxAt(g - vec2(0.006, 0.0)).b));
        float scan = 0.85 + 0.15 * step(0.5, fract(uv.y * OutSize.y * 0.25));
        result = mix(split, split * dark * 1.5 + dark * 0.2, 0.45) * scan;
    } else {
        // ink: edges drawn dark, the rest split by brightness
        float paper = step(0.42, l) * (1.0 - smoothstep(0.18, 0.4, edgeAt(uv)));
        bool inverted = kind == 1;
        float seam = 0.0;
        if (kind == 5) {
            float angle = radians(ImpactC.a);
            float side = dot(d, vec2(-sin(angle), cos(angle)));
            inverted = side < 0.0;
            seam = exp(-abs(side) * 160.0);
        }
        result = inverted ? mix(light, dark, paper) : mix(dark, light, paper);
        result = mix(result, light * 1.2 + 0.2, seam);
        inkLight = inverted;
    }

    if (ImpactB.a > 0.0) {
        float ang = atan(d.y, d.x);
        float lines = step(0.8, hash11(floor((ang / TAU + 0.5) * ImpactB.a * 40.0)));
        float reach = smoothstep(0.12, 0.6, length(d));
        result = mix(result, inkLight ? light : dark, lines * reach);
    }
    return mix(col, result, clamp(ImpactA.x, 0.0, 1.0));
}

// ---------------------------------------------------------------- main

void main() {
    vec2 uv = RippleCount > 0.5 ? rippled(texCoord) : texCoord;

    vec3 col = fetch(uv);
    if (ZoomBlur > 0.001) {
        vec3 acc = col;
        float total = 1.0;
        for (int i = 1; i < 8; i++) {
            float k = float(i) / 8.0;
            float w = 1.0 - k * 0.5;
            acc += fetch(mix(uv, vec2(0.5), k * ZoomBlur * 0.12)) * w;
            total += w;
        }
        col = acc / total;
    }

    if (Bloom > 0.001) {
        vec3 b = texture(BloomSampler, uv).rgb * Bloom * 0.35;
        col += b * (1.0 - clamp(luma(col), 0.0, 1.0) * 0.4);
    }

    vec2 fromCentre = (uv - 0.5) * aspectScale();
    float r2 = dot(fromCentre, fromCentre);
    if (Desaturate > 0.001) {
        col = mix(col, vec3(luma(col)), clamp(Desaturate, 0.0, 1.0));
    }
    if (Grade.x > 0.001) {
        col = max((col - 0.42) * (1.0 + Grade.x) + 0.42, 0.0);
    }
    if (Vignette.a > 0.001) {
        float edge = smoothstep(0.15, 0.75, r2);
        col = mix(col, col * Vignette.rgb * 1.2 + Vignette.rgb * 0.03, clamp(edge * Vignette.a, 0.0, 1.0));
    }

    col = tonemapSoft(col);

    if (ImpactA.x > 0.001) {
        col = impactFrame(uv, col);
    }
    if (SpeedLines > 0.001) {
        float ang = atan(fromCentre.y, fromCentre.x);
        float id = floor((ang / TAU + 0.5) * 140.0);
        float on = step(0.86, hash12(vec2(id, floor(GameTime * 18.0))));
        col = mix(col, vec3(1.0), on * smoothstep(0.35, 0.8, sqrt(r2)) * SpeedLines * 0.7);
    }
    if (Grade.z > 0.001) {
        vec2 px = 3.0 / OutSize;
        vec3 held = (texture(HoldSampler, uv).rgb * 2.0
                + texture(HoldSampler, uv + vec2(px.x, 0.0)).rgb + texture(HoldSampler, uv - vec2(px.x, 0.0)).rgb
                + texture(HoldSampler, uv + vec2(0.0, px.y)).rgb + texture(HoldSampler, uv - vec2(0.0, px.y)).rgb) / 6.0;
        vec3 ghost = clamp(held * smoothstep(0.1, 0.9, luma(held)) * Grade.z, 0.0, 1.0);
        col = 1.0 - (1.0 - col) * (1.0 - ghost);
    }
    if (Flash.a > 0.001) {
        col += Flash.rgb * Flash.a;
        col = mix(col, vec3(1.0), clamp(Flash.a - 1.0, 0.0, 1.0));
    }
    if (Grade.y > 0.001) {
        col += (hash12(uv * OutSize + fract(GameTime * 7.13) * 419.0) - 0.5) * Grade.y * 0.18;
    }
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
