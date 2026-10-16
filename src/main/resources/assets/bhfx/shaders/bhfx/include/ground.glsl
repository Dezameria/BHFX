// Shapes painted onto whatever surface is visible near a point: rings, discs, decals, shock fronts.
// Needs scene.glsl (for Scene); include it first. Every function measures distance in the horizontal plane through
// center (camera-relative, blocks) and returns 0 for sky. Multiply by groundBand() to keep marks off far-away walls
// and cliffs above or below the effect.

/** World size of one screen pixel at this surface; compute once per fragment and pass as aa. */
float groundAA(Scene s, vec2 uv) {
    return max(s.dist * pixelAngle(uv), 1e-4);
}

/** 1 on surfaces within range blocks above or below y, fading out over another half range. */
float groundBand(Scene s, float y, float range) {
    if (s.sky) {
        return 0.0;
    }
    return 1.0 - smoothstep(range, range * 1.5, abs(s.pos.y - y));
}

/** Horizontal distance from center to the visible surface. */
float groundDistance(Scene s, vec3 center) {
    return length(s.pos.xz - center.xz);
}

/** A solid ring of the given width, anti-aliased, at least about a pixel wide however far away. */
float groundRing(Scene s, vec3 center, float radius, float width, float aa) {
    if (s.sky) {
        return 0.0;
    }
    float d = abs(groundDistance(s, center) - radius);
    float w = max(width, aa * 1.5);
    return clamp(0.5 - (d - w * 0.5) / aa, 0.0, 1.0);
}

/** A filled disc with an anti-aliased edge. */
float groundDisc(Scene s, vec3 center, float radius, float aa) {
    if (s.sky) {
        return 0.0;
    }
    return clamp(0.5 - (groundDistance(s, center) - radius) / aa, 0.0, 1.0);
}

/**
 * Square decal of half size halfSize turned by angle radians: xy = position inside it (-1..1, x along the turned +x,
 * y along the turned +z), z = 1 inside and 0 outside. Sample a texture at xy * 0.5 + 0.5, or feed xy to sdf2d.glsl.
 */
vec3 groundDecal(Scene s, vec3 center, float halfSize, float angle) {
    if (s.sky) {
        return vec3(0.0);
    }
    vec2 d = s.pos.xz - center.xz;
    float c = cos(-angle);
    float n = sin(-angle);
    vec2 local = vec2(c * d.x - n * d.y, n * d.x + c * d.y) / max(halfSize, 1e-4);
    float inside = step(max(abs(local.x), abs(local.y)), 1.0);
    return vec3(local, inside);
}

/**
 * A soft shock front at radius: a bright leading edge with a gentler tail inside it. thickness sets the edge width;
 * returns 0..1, peaking at the front.
 */
float shockRing(Scene s, vec3 center, float radius, float thickness) {
    if (s.sky) {
        return 0.0;
    }
    float x = (groundDistance(s, center) - radius) / max(thickness, 1e-4);
    // gaussian in front of the edge, slower exponential behind it
    return x > 0.0 ? exp(-x * x * 2.0) : exp(x * 0.6);
}
