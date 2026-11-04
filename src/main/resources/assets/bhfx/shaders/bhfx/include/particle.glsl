// Helpers for particle materials (assets/<ns>/shaders/bhfx/particle/<name>.fsh). The Particle struct and the inputs
// come from the particle header; only use this file in particle materials.

/** 0 → 1 over the first `fadeIn` of the life, 1 → 0 over the last `fadeOut` (both 0..1 shares of the life). */
float fadeInOut(float age, float fadeIn, float fadeOut) {
    float a = fadeIn > 0.0 ? clamp(age / fadeIn, 0.0, 1.0) : 1.0;
    float b = fadeOut > 0.0 ? clamp((1.0 - age) / fadeOut, 0.0, 1.0) : 1.0;
    return a * b;
}

/** Round falloff, 1 at the centre to 0 at the quad's inscribed circle. */
float radialMask(vec2 corner, float hardness) {
    return 1.0 - smoothstep(clamp(hardness, 0.0, 0.999), 1.0, length(corner)); // edges in order (reversed is undefined)
}

/**
 * Normal of a sphere (or a bumpy one, with `bump` from a noise texture) seen face on, in the quad's frame: x right,
 * y up, z towards the camera. Light it with a direction in the same frame to make a flat sprite read as a volume.
 */
vec3 sphereNormal(vec2 corner, float bump) {
    vec2 c = corner * (1.0 + (bump - 0.5) * 0.4);
    float z = sqrt(max(1.0 - dot(c, c), 0.0));
    return normalize(vec3(c, z + 0.15));
}

/** Simple wrap lighting for sphereNormal: `wrap` 0 = hard terminator, 1 = very soft. */
float wrapLight(vec3 n, vec3 lightDir, float wrap) {
    return clamp((dot(n, normalize(lightDir)) + wrap) / (1.0 + wrap), 0.0, 1.0);
}

/**
 * FxTexture images are stored like Minecraft's: the image's top row at v = 0. p.uv has y up, so sample a sprite or
 * a picture upright with this.
 */
vec2 spriteUv(vec2 uv) {
    return vec2(uv.x, 1.0 - uv.y);
}

/**
 * Flipbook lookup: the texture holds cols × rows frames, left to right then top to bottom. `frame` is 0..frames
 * (fractional), e.g. age * frames. Returns the uv of frame floor(frame) in xy and of the next one in zw; mix the two
 * samples by fract(frame) for smooth playback.
 */
vec4 flipbookUv(vec2 uv, float cols, float rows, float frame) {
    float count = cols * rows;
    float f0 = clamp(floor(frame), 0.0, count - 1.0);
    float f1 = min(f0 + 1.0, count - 1.0);
    vec2 cell = vec2(1.0 / cols, 1.0 / rows);
    vec2 inner = spriteUv(uv) * cell;
    // row 0 is the image's top row, which sits at v = 0 (see spriteUv)
    vec2 a = vec2(mod(f0, cols), floor(f0 / cols)) * cell + inner;
    vec2 b = vec2(mod(f1, cols), floor(f1 / cols)) * cell + inner;
    return vec4(a, b);
}

/** Sample a flipbook smoothly between frames. */
vec4 flipbook(sampler2D tex, vec2 uv, float cols, float rows, float frame) {
    vec4 f = flipbookUv(uv, cols, rows, frame);
    return mix(texture(tex, f.xy), texture(tex, f.zw), fract(frame));
}

/** Dissolve by a noise value: 1 while noise is above the threshold that rises with `progress` 0..1, soft by `edge`. */
float erode(float noise, float progress, float edge) {
    float e = max(edge, 1e-4);
    return smoothstep(progress, progress + e, noise * (1.0 + e));
}
