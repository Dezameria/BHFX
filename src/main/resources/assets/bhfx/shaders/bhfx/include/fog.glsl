// Minecraft's terrain fog, the way the level shaders apply it. FogColor and FogRange (start, end, shape: 0 sphere,
// 1 cylinder) come from the header and are captured after the solid blocks. Needs scene.glsl (for Scene); include it
// first. Use it to swap the fog on far terrain for a sky a pass draws instead.

/** The distance the fog is measured over: the camera-relative position, spherical or cylindrical. */
float fogDistance(vec3 pos) {
    return FogRange.z > 0.5 ? max(length(pos.xz), abs(pos.y)) : length(pos);
}

/** How much of the visible surface is fog, 0..1. 0 for sky, which the game draws with its own fog. */
float fogAmount(Scene s) {
    if (s.sky) {
        return 0.0;
    }
    float d = fogDistance(s.pos);
    if (d <= FogRange.x) {
        return 0.0;
    }
    float k = d < FogRange.y && FogRange.y > FogRange.x ? smoothstep(FogRange.x, FogRange.y, d) : 1.0;
    return k * FogColor.a;
}

/** The surface colour with Minecraft's fog swapped for another colour (amount from fogAmount). */
vec3 refog(vec3 color, float amount, vec3 fog) {
    return color + amount * (fog - FogColor.rgb);
}
