// bhfx built-in test (bhfx:test): a warning ring on the ground, then a column of light hidden correctly by terrain,
// lighting the land, a shock ring and cooling heat at its foot.

#include "bhfx:scene.glsl"
#include "bhfx:shapes.glsl"
#include "bhfx:light.glsl"
#include "bhfx:noise.glsl"
#include "bhfx:ground.glsl"
#include "bhfx:sdf2d.glsl"

uniform vec3 Target;  // ground point, camera-relative
uniform vec4 P0;      // radius, warning 0..1, column 0..1, seconds since impact (< 0 before)
uniform vec4 P1;      // seed 0..1, fade 0..1, heat 0..1, unused

const vec3 WARN = vec3(1.0, 0.32, 0.08);
const vec3 HOT = vec3(1.0, 0.85, 0.6);
const float TAU = 6.2831853;

void main() {
    Scene s = sceneAt(texCoord);
    vec3 base = s.color;
    vec3 glow = vec3(0.0);
    float R = P0.x;
    float beamR = R * 0.3;
    vec2 axis = Target.xz;
    float aa = groundAA(s, texCoord);

    if (!s.sky) {
        float hd = groundDistance(s, Target);
        float nearGround = groundBand(s, Target.y, 16.0);
        // never thinner than about one pixel, so the ring stays solid far away
        float w = max(0.12, aa * 1.5);

        // warning: a dashed rim turning, pulses closing on the centre, a faint fill
        if (P0.y > 0.001) {
            float ang = atan(s.pos.z - axis.y, s.pos.x - axis.x);
            float rim = groundRing(s, Target, R, w, aa);
            float dash = step(0.4, fract(ang / TAU * floor(R * 1.5) - GameTime * 0.6));
            float k = fract(GameTime * 0.9);
            float pulse = (1.0 - smoothstep(0.0, w * 2.0 + 0.3, abs(hd - R * (1.0 - k)))) * (1.0 - k);
            float fill = (1.0 - smoothstep(0.0, R, hd)) * 0.08;
            // a turning five-pointed glyph inside the ring
            vec3 dec = groundDecal(s, Target, R * 0.7, GameTime * 0.4);
            float glyph = aaLine(sdStar(dec.xy, 0.95, 5, 0.42), 0.035, aa / (R * 0.7)) * dec.z;
            glow += WARN * (rim * dash * 2.2 + pulse * 1.4 + fill + glyph * 1.2) * P0.y * nearGround;
        }

        // the column lighting the land round it: 1/d from a long light, cut off further out
        if (P0.z > 0.001) {
            float wall = max(hd - beamR, 0.0);
            float lit = lineLight(wall, beamR) * glowExp(wall, R * 1.5);
            base *= mix(vec3(1.0), vec3(1.8, 0.75, 0.45), clamp(lit, 0.0, 1.0) * P0.z * nearGround);
        }

        // shock ring rolling out for two seconds after impact
        if (P0.w >= 0.0 && P0.w < 2.0) {
            float front = beamR + P0.w * 45.0;
            float ring = shockRing(s, Target, front, 1.0 + P0.w * 3.0) * (1.0 - P0.w / 2.0);
            glow += mix(HOT, WARN, P0.w / 2.0) * ring * 2.0 * nearGround;
        }

        // scorched ground glowing, then cooling
        if (P1.z > 0.001) {
            float heat = P1.z * glowGauss(hd, beamR * 1.2) * (0.7 + 0.3 * vnoise2(s.pos.xz * 0.6 + P1.x * 50.0));
            glow += blackbody(heat) * heat * 1.6 * nearGround;
            base *= mix(1.0, 0.45, clamp(P1.z * glowGauss(hd, beamR), 0.0, 1.0));
        }
    }

    // the column: an opaque glowing cylinder from the ground up, cut off by whatever is in front of it
    if (P0.z > 0.001) {
        float top = Target.y + 320.0;
        vec3 ro = s.origin;
        vec2 hit = rayCylinderY(ro, s.dir, axis, beamR);
        float t0 = max(hit.x, 0.0);
        float t1 = min(hit.y, s.dist);
        if (abs(s.dir.y) > 1e-5) {
            float ta = (Target.y - ro.y) / s.dir.y;
            float tb = (top - ro.y) / s.dir.y;
            t0 = max(t0, min(ta, tb));
            t1 = min(t1, max(ta, tb));
        } else if (Target.y > ro.y || top < ro.y) {
            t1 = -1.0;
        }
        float tAxis;
        float b = rayAxisY(ro, s.dir, axis, tAxis);
        if (hit.y > 0.0 && t1 > t0) {
            float x = clamp(b / beamR, 0.0, 1.0);
            vec3 enter = ro + s.dir * t0;
            float ang = atan(enter.z - axis.y, enter.x - axis.x);
            float flow = vnoise2(vec2(ang * 3.0, (enter.y - Target.y - GameTime * 60.0) * 0.05));
            vec3 body = mix(vec3(1.0, 0.45, 0.12), vec3(0.75, 0.12, 0.03), smoothstep(0.3, 1.0, x));
            body *= 0.8 + 0.4 * flow;
            body = mix(body, HOT * 1.6, glowGauss(x, 0.18));
            base = mix(base, body, P0.z);
        } else if (tAxis < s.dist) {
            // the air round it glowing, only beside the column's height
            float y = ro.y + s.dir.y * tAxis;
            float beside = step(Target.y - 2.0, y) * step(y, top);
            float out_ = max(b - beamR, 0.0);
            glow += WARN * glowExp2(out_, beamR * 0.25, beamR * 2.0, 0.15) * 0.6 * P0.z * beside;
        }
    }

    float fade = P1.y;
    fragColor = vec4(mix(s.color, base, fade) + glow * fade, 1.0);
}
