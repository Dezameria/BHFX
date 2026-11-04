// bhfx test material: heat haze (DISTORT). Returns an offset along the quad's axes (-1..1) and its strength; bhfx
// bends the image behind it. Grayscale noise texture (stored as one byte per pixel).

#include "bhfx:particle.glsl"

uniform sampler2D Noise;

vec4 material(Particle p) {
    vec2 nuv = p.uv * 0.7 + vec2(p.seed * 5.3, -p.seconds * 0.45);
    float n1 = texture(Noise, nuv).r;
    float n2 = texture(Noise, nuv + vec2(0.37, 0.61)).r;
    vec2 offset = vec2(n1, n2) * 2.0 - 1.0;
    float mask = radialMask(p.corner, 0.1) * fadeInOut(p.age, 0.25, 0.5);
    return vec4(offset, 0.0, mask);
}
