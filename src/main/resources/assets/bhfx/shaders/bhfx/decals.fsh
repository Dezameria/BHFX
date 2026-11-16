// bhfx built-in: textured marks from every effect (PassList.decal), all in this one pass, drawn after the lights pass
// and before the other passes. Each mark is a square in a plane; a surface point takes it when it lies inside the
// square, close to the plane, and faces the same way.

#include "bhfx:scene.glsl"
#include "bhfx:fog.glsl"

#define BHFX_MAX_DECALS 32

// five entries per mark:
//   (centre camera-relative, half size)
//   (unit u axis, reach in front of / behind the plane)
//   (unit v axis, code = texture 0..7 + 8 when glowing + 16 when a mask)
//   (linear rgb * brightness, opacity)
//   (uv offset, uv scale): the cell of the texture shown
uniform vec4 Decals[BHFX_MAX_DECALS * 5];
uniform float DecalCount;
uniform sampler2D DecalTex0;
uniform sampler2D DecalTex1;
uniform sampler2D DecalTex2;
uniform sampler2D DecalTex3;
uniform sampler2D DecalTex4;
uniform sampler2D DecalTex5;
uniform sampler2D DecalTex6;
uniform sampler2D DecalTex7;

// explicit gradients: the lookup sits inside a loop and branches, where implicit ones are undefined
vec4 decalTexel(int index, vec2 uv, vec2 dx, vec2 dy) {
    if (index == 0) return textureGrad(DecalTex0, uv, dx, dy);
    if (index == 1) return textureGrad(DecalTex1, uv, dx, dy);
    if (index == 2) return textureGrad(DecalTex2, uv, dx, dy);
    if (index == 3) return textureGrad(DecalTex3, uv, dx, dy);
    if (index == 4) return textureGrad(DecalTex4, uv, dx, dy);
    if (index == 5) return textureGrad(DecalTex5, uv, dx, dy);
    if (index == 6) return textureGrad(DecalTex6, uv, dx, dy);
    return textureGrad(DecalTex7, uv, dx, dy);
}

void main() {
    Scene s = sceneAt(texCoord);
    if (s.sky) {
        fragColor = vec4(s.color, 1.0);
        return;
    }
    vec3 px = dFdx(s.pos);
    vec3 py = dFdy(s.pos);
    vec3 n = normalize(cross(px, py));
    if (dot(n, s.dir) > 0.0) {
        n = -n;
    }
    vec3 color = s.color;
    float fog = 1.0 - fogAmount(s);
    int count = min(int(DecalCount + 0.5), BHFX_MAX_DECALS);
    for (int i = 0; i < count; i++) {
        vec4 a = Decals[i * 5];
        vec4 b = Decals[i * 5 + 1];
        vec4 c = Decals[i * 5 + 2];
        vec3 d = s.pos - a.xyz;
        vec2 local = vec2(dot(d, b.xyz), dot(d, c.xyz)) / a.w;
        if (max(abs(local.x), abs(local.y)) >= 1.0) {
            continue;
        }
        vec3 normal = cross(b.xyz, c.xyz);
        float off = abs(dot(d, normal));
        if (off >= b.w) {
            continue;
        }
        float facing = smoothstep(0.35, 0.6, dot(n, normal));
        float near = 1.0 - smoothstep(0.6, 1.0, off / b.w);
        float weight = facing * near * fog;
        if (weight <= 0.0) {
            continue;
        }
        vec4 cell = Decals[i * 5 + 4];
        vec4 tint = Decals[i * 5 + 3];
        int code = int(c.w + 0.5);
        // v runs down the picture: local.y = +1 is its top edge
        vec2 unit = vec2(local.x, -local.y) * 0.5 + 0.5;
        vec2 uv = cell.xy + unit * cell.zw;
        vec2 scale = cell.zw * 0.5 / a.w;
        vec2 dx = vec2(dot(px, b.xyz), -dot(px, c.xyz)) * scale;
        vec2 dy = vec2(dot(py, b.xyz), -dot(py, c.xyz)) * scale;
        // a discontinuity in depth gives huge gradients: cap them so the edge reads a coarse mip, not the 1x1 one
        dx = clamp(dx, vec2(-0.25), vec2(0.25));
        dy = clamp(dy, vec2(-0.25), vec2(0.25));
        vec4 t = decalTexel(code & 7, uv, dx, dy);
        bool mask = (code & 16) != 0;
        vec3 rgb = mask ? tint.rgb : t.rgb * tint.rgb;
        float cover = (mask ? t.r : t.a) * tint.a * weight;
        if ((code & 8) != 0) {
            color += rgb * cover;
        } else {
            color = mix(color, rgb, clamp(cover, 0.0, 1.0));
        }
    }
    fragColor = vec4(color, 1.0);
}
