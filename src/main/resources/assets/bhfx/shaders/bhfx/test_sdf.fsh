// bhfx built-in test (bhfx:test_sdf): a hand of light, ray-marched from signed distances (sdf3d.glsl, sdf_march.glsl),
// its bones written into an FxData table by the Fx every frame (FxFrame, data.glsl). On a followed entity it sits
// over the right hand (body parts); otherwise it stands at the target, turning. It opens and closes.

#include "bhfx:scene.glsl"
#include "bhfx:shapes.glsl"
#include "bhfx:data.glsl"
#include "bhfx:sdf3d.glsl"

// entries 0..2: the hand's frame (x to the thumb, y its back, z to the fingers, origin at the wrist)
// entries 3 + 2i, 4 + 2i: bone i of the fingers and thumb, (start, radius), (end, radius)
uniform samplerBuffer Hand;
uniform vec4 P0;     // bone count, size (blocks per unit), fade 0..1, unused
uniform vec4 Bound;  // a ball round the hand: centre (camera-relative), radius
uniform vec3 Tint;

const int MAX_BONES = 15;
FxFrame gHand;
vec4 gA[MAX_BONES];
vec4 gB[MAX_BONES];
int gBones;
float gS;

float map(vec3 p) {
    vec3 l = fxToLocal(gHand, p);
    float d = sdRoundBox(l - vec3(0.0, 0.0, 0.45) * gS, vec3(0.36, 0.08, 0.42) * gS, 0.08 * gS);
    for (int i = 0; i < MAX_BONES; i++) {
        if (i >= gBones) {
            break;
        }
        d = smin(d, sdRoundCone(p, gA[i].xyz, gB[i].xyz, gA[i].w, gB[i].w), 0.05 * gS);
    }
    // a ring round the wrist: the torus lies in xz, so swap the hand's z (along the arm) into y
    float ring = sdTorus((l - vec3(0.0, 0.0, -0.08 * gS)).xzy, 0.3 * gS, 0.035 * gS);
    return min(d, ring);
}

#include "bhfx:sdf_march.glsl"

void main() {
    Scene s = sceneAt(texCoord);
    vec3 col = s.color;
    vec2 span = raySphere(s.origin, s.dir, Bound.xyz, Bound.w);
    float tmax = min(span.y, s.dist);
    if (span.y > 0.0 && tmax > max(span.x, 0.0)) {
        gHand = fxFrame(Hand, 0);
        gBones = min(int(P0.x), MAX_BONES);
        gS = P0.y;
        for (int i = 0; i < MAX_BONES; i++) {
            gA[i] = fxData(Hand, 3 + i * 2);
            gB[i] = fxData(Hand, 4 + i * 2);
        }
        SdfHit h = sdfMarch(s.origin, s.dir, max(span.x, 0.0), tmax, pixelAngle(texCoord));
        vec3 glow = Tint * exp(-h.minDist / (0.12 * gS)) * 0.6;
        if (h.hit) {
            vec3 n = sdfNormal(h.pos, max(0.002, 0.001 * h.t));
            float facing = abs(dot(n, s.dir));
            float rim = pow(1.0 - facing, 3.0);
            float ao = sdfAO(h.pos, n, 0.15 * gS);
            vec3 body = Tint * (0.15 + 0.45 * ao) + mix(Tint, vec3(1.0), 0.5) * rim * 2.2;
            col = mix(col, body, 0.8);
        }
        col += glow;
        col = mix(s.color, col, P0.z);
    }
    fragColor = vec4(col, 1.0);
}
