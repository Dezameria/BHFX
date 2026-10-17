// bhfx bloom, one level down (half size each time). Five taps: the centre plus four diagonal corners one input texel
// out, each of which the linear filter already averages over 2x2 texels. On the first level only the light the
// passes added survives, so the world's own sky and snow never glow.

#include "bhfx:light.glsl"

uniform vec2 TexelSize;   // 1 / input size
uniform float Prefilter;  // 1 on the first level

vec3 lightAt(vec2 uv) {
    vec3 c = texture(DiffuseSampler, uv).rgb;
    if (Prefilter < 0.5) {
        return c;
    }
    vec3 world = texture(SceneSampler, uv).rgb;
    vec3 added = max(c - world, 0.0);
    float over = max(luma(c) - 1.0, 0.0);
    return added + c * min(over, 4.0) * 0.25;
}

void main() {
    vec2 h = TexelSize;
    vec3 sum = lightAt(texCoord) * 4.0;
    sum += lightAt(texCoord + vec2(-h.x, -h.y));
    sum += lightAt(texCoord + vec2(h.x, -h.y));
    sum += lightAt(texCoord + vec2(-h.x, h.y));
    sum += lightAt(texCoord + vec2(h.x, h.y));
    fragColor = vec4(sum / 8.0, 1.0);
}
