// bhfx built-in test (bhfx:test_data): a swarm of glowing orbs whose places come from an FxData table that the Fx
// rewrites every frame. Checks that a pass reads a table, that only changed entries upload, and that an orb behind
// terrain is hidden (the glow only counts up to the visible surface).

#include "bhfx:scene.glsl"
#include "bhfx:light.glsl"
#include "bhfx:data.glsl"

// two entries per orb: (centre camera-relative, radius), (rgb, unused)
uniform samplerBuffer Orbs;
uniform vec4 P0; // orb count, fade 0..1, unused, unused

void main() {
    Scene s = sceneAt(texCoord);
    vec3 glow = vec3(0.0);
    int n = min(int(P0.x), fxDataSize(Orbs) / 2);
    for (int i = 0; i < n; i++) {
        vec4 a = fxData(Orbs, i * 2);
        vec4 b = fxData(Orbs, i * 2 + 1);
        vec3 oc = a.xyz - s.origin;
        float t = dot(oc, s.dir);
        if (t < 0.0) {
            continue;
        }
        // closest approach, but nothing past the visible surface lights the pixel
        float d = length(oc - s.dir * min(t, s.dist));
        float hidden = step(s.dist, t - a.w);
        glow += b.rgb * glowExp2(d, a.w * 0.6, a.w * 3.0, 0.15) * (1.0 - hidden);
    }
    fragColor = vec4(s.color + glow * P0.y, 1.0);
}
