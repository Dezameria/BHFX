// bhfx particle material footer: depth test against the scene, soft fade, blending output. Colours go onto bhfx's
// frame as they are: like the world image, it is display-encoded (not linear).
// ADD: rgb * a added. ALPHA: premultiplied (rgb * a, a). A material that does `#define BHFX_PREMULTIPLIED` returns
// (rgb already times coverage, plus added light; a = coverage) instead. DISTORT: material.xy is an offset along the quad's axes,
// material.a its strength; written as a screen-uv offset into the distortion target.

// clip-space w of the scene at uv, the same measure as pViewZ (gl_Position.w): Proj * InvProj = I, so the scene
// point's clip vector is ndc / v.w and its w is 1 / v.w. Exact even with view bobbing folded into the projection.
float bhfxSceneViewZ(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    if (d >= 0.99999) {
        return 1e9;
    }
    vec4 v = InvProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    return 1.0 / v.w;
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / OutSize;
    float gap = bhfxSceneViewZ(screenUv) - pViewZ;
    float soft = PMode.y > 0.0 ? clamp(gap / PMode.y, 0.0, 1.0) : step(0.0, gap);
    soft *= PMode.w; // the effect's presence: it fades in when built late and out when hidden
    // the material runs before any discard: its texture() calls need implicit derivatives, which are undefined
    // in per-pixel control flow (see docs/shaders.md)
    Particle p;
    p.corner = pCorner;
    p.uv = pCorner * 0.5 + 0.5;
    p.age = pAge;
    p.seconds = pLife.y;
    p.seed = pLife.z;
    p.color = pColor;
    p.data = pData;
    p.pos = pPos;
    vec4 c = material(p);
    if (soft <= 0.0) {
        discard;
    }
    if (PMode.x > 1.5) {
        vec2 offset = (c.x * pScreen.xy + c.y * pScreen.zw) * clamp(c.a, 0.0, 1.0) * soft * PMode.z;
        fragColor = vec4(offset, 0.0, 0.0);
        return;
    }
#ifdef BHFX_PREMULTIPLIED
    // the material already multiplied its colour by its coverage and may add light on top: rgb is what it puts on the
    // image, a how much of the image behind it it hides (ALPHA blend). Lets one particle both darken and glow.
    fragColor = vec4(max(c.rgb, 0.0) * soft, PMode.x > 0.5 ? clamp(c.a, 0.0, 1.0) * soft : 0.0);
#else
    float a = clamp(c.a, 0.0, 1.0) * soft;
    if (a <= 0.0) {
        discard;
    }
    fragColor = vec4(max(c.rgb, 0.0) * a, PMode.x > 0.5 ? a : 0.0);
#endif
}
