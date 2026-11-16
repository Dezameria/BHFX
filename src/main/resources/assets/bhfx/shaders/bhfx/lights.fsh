// bhfx built-in: point lights from every effect (PassList.light), all in this one pass, drawn before the other passes.
// Each light brightens the world surfaces inside its radius, from the depth buffer: the surface colour is multiplied
// up (so textures stay readable) with a little of the light's own colour added (so it shows on dark blocks at night).

#include "bhfx:scene.glsl"
#include "bhfx:fog.glsl"

#define BHFX_MAX_LIGHTS 64

// two entries per light: (centre camera-relative, radius), (linear rgb * strength, unused)
uniform vec4 Lights[BHFX_MAX_LIGHTS * 2];
uniform float LightCount;

void main() {
    Scene s = sceneAt(texCoord);
    if (s.sky) {
        fragColor = vec4(s.color, 1.0);
        return;
    }
    // face normal from the depth buffer (flat per block face), turned toward the eye
    vec3 n = normalize(cross(dFdx(s.pos), dFdy(s.pos)));
    if (dot(n, s.dir) > 0.0) {
        n = -n;
    }
    vec3 light = vec3(0.0);
    int count = min(int(LightCount + 0.5), BHFX_MAX_LIGHTS);
    for (int i = 0; i < count; i++) {
        vec4 a = Lights[i * 2];
        vec3 d = a.xyz - s.pos;
        float d2 = dot(d, d);
        float r2 = a.w * a.w;
        if (d2 >= r2) {
            continue;
        }
        float dist = sqrt(d2);
        // smooth window to zero at the radius, brighter toward the centre
        float w = 1.0 - d2 / r2;
        float atten = w * w / (1.0 + 4.0 * d2 / r2);
        // wrapped: faces turned away still catch a little, so a light inside a room has no black walls
        float facing = 0.3 + 0.7 * max(dot(n, d / max(dist, 1e-4)), 0.0);
        light += Lights[i * 2 + 1].rgb * (atten * facing);
    }
    // less gain on surfaces that are already bright, so a light in daylight tints without burning out
    float bright = clamp(dot(s.color, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
    vec3 lit = s.color + (s.color * (2.5 * (1.0 - 0.7 * bright)) + 0.10) * light;
    fragColor = vec4(mix(lit, s.color, fogAmount(s)), 1.0);
}
