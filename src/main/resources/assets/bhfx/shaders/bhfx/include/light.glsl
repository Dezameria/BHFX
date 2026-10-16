// Light falloffs and colour helpers. Colours are linear and may exceed 1 until the composite tone-maps.

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

/** Tight core: exp(-(d/w)^2). */
float glowGauss(float d, float w) {
    float x = d / max(w, 1e-4);
    return exp(-x * x);
}

/** Soft halo: exp(-d/w). */
float glowExp(float d, float w) {
    return exp(-d / max(w, 1e-4));
}

/** Two-layer haze: a bright near layer plus a faint wide one, the way glow scatters in air. */
float glowExp2(float d, float wNear, float wFar, float farWeight) {
    return glowExp(d, wNear) + farWeight * glowExp(d, wFar);
}

/** Light thrown by a long line or column falls off as 1/d; r keeps it finite at the surface. */
float lineLight(float d, float r) {
    return r / (max(d, 0.0) + r);
}

/** Temperature 0..1.2 to an incandescent colour: dull red, orange, amber, warm white. */
vec3 blackbody(float x) {
    vec3 c = mix(vec3(0.20, 0.015, 0.0), vec3(0.95, 0.20, 0.03), smoothstep(0.0, 0.4, x));
    c = mix(c, vec3(1.0, 0.60, 0.17), smoothstep(0.35, 0.8, x));
    return mix(c, vec3(1.0, 0.93, 0.83), smoothstep(0.8, 1.2, x));
}

vec3 toLinear(vec3 c) {
    return pow(max(c, 0.0), vec3(2.2));
}

/**
 * HDR to display. Unchanged below the knee so untouched world pixels pass through, a soft shoulder above it, and
 * channels past 1.0 bleed into the others so intense coloured light turns white instead of clipping flat.
 */
vec3 tonemapSoft(vec3 c) {
    float peak = max(c.r, max(c.g, c.b));
    c += max(peak - 1.0, 0.0) * 0.4;
    const float knee = 0.9;
    vec3 over = max(c - knee, 0.0);
    return min(c, vec3(knee)) + (1.0 - knee) * over / (over + (1.0 - knee));
}
