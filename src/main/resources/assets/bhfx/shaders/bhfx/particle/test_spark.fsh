// bhfx test material: a spark streak (VELOCITY facing: corner.x runs along the motion, the front at +1).

#include "bhfx:particle.glsl"

uniform sampler2D Ramp;

vec4 material(Particle p) {
    float across = exp(-p.corner.y * p.corner.y * 10.0);
    float along = 1.0 - smoothstep(0.3, 1.0, abs(p.corner.x));
    float head = smoothstep(-1.0, 1.0, p.corner.x);
    float heat = 1.0 - p.age;
    vec3 col = texture(Ramp, vec2(0.45 + 0.55 * heat, 0.5)).rgb * p.color.rgb * (2.0 + 4.0 * heat);
    return vec4(col, across * along * (0.35 + 0.65 * head) * fadeInOut(p.age, 0.0, 0.35));
}
