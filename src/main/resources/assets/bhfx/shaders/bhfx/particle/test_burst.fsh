// bhfx test material: an explosion puff played from a 4x4 flipbook (16 frames over the life), coloured by the ramp.

#include "bhfx:particle.glsl"

uniform sampler2D Book;
uniform sampler2D Ramp;

vec4 material(Particle p) {
    float v = flipbook(Book, p.uv, 4.0, 4.0, p.age * 15.0).r;
    vec3 col = texture(Ramp, vec2(clamp(v * 1.1, 0.0, 1.0), 0.5)).rgb * p.color.rgb * 3.0;
    return vec4(col, clamp(v * 1.4, 0.0, 1.0));
}
