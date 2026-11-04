// bhfx:sprite — built-in material: a texture tinted by p.color. Bind it as "Sprite". Optional
// SpriteGrid = (cols, rows, frames, mask): frames > 1 plays a flipbook over the life; mask = 1 takes coverage from the
// red channel (grayscale images) instead of the alpha. Unset = one image, alpha coverage.

#include "bhfx:particle.glsl"

uniform sampler2D Sprite;
uniform vec4 SpriteGrid;

vec4 material(Particle p) {
    vec4 s;
    if (SpriteGrid.z > 1.5) {
        float cols = max(SpriteGrid.x, 1.0);
        float rows = max(SpriteGrid.y, 1.0);
        s = flipbook(Sprite, p.uv, cols, rows, p.age * min(SpriteGrid.z, cols * rows));
    } else {
        s = texture(Sprite, spriteUv(p.uv));
    }
    float cover = SpriteGrid.w > 0.5 ? s.r : s.a;
    vec3 rgb = SpriteGrid.w > 0.5 ? vec3(1.0) : s.rgb;
    return vec4(rgb * p.color.rgb, clamp(cover * p.color.a, 0.0, 1.0));
}
