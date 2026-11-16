// Drawing a signed distance field by sphere tracing. Write your scene as
//     float map(vec3 p) { ... }      // p camera-relative, distance in blocks (sdf3d.glsl helps)
// ABOVE the line that includes this file: GLSL can't pass functions around, so this file calls the map() it finds.
// Optional, also before the include:
//     #define SDF_STEPS 64           most steps per ray (more: thin or far shapes; fewer: cheaper)
//     #define SDF_STEP 1.0           step scale; lower it (0.6..0.9) after opTwistY / opBendX or inexact shapes
//     #define SDF_SHADOW_STEPS 24    steps of sdfShadow
// Keep it cheap: march only where the shape can be (pass tmin / tmax from raySphere round it), give the pass
// PassOptions.within(...), and stop at the scene (tmax = s.dist) so blocks in front hide it.

#ifndef SDF_STEPS
#define SDF_STEPS 64
#endif
#ifndef SDF_STEP
#define SDF_STEP 1.0
#endif
#ifndef SDF_SHADOW_STEPS
#define SDF_SHADOW_STEPS 24
#endif

struct SdfHit {
    bool hit;       // reached the surface before tmax
    float t;        // distance along the ray to the surface (tmax when missed)
    vec3 pos;       // the point there
    float minDist;  // the nearest the ray came to the surface (for a glow round the shape; 0 on a hit)
    float minT;     // where along the ray that was
};

/**
 * Trace ro + rd * t for t in tmin..tmax. px: the angle one pixel covers (pixelAngle(texCoord)); the surface counts as
 * hit when closer than a pixel, so far shapes cost fewer steps and don't shimmer.
 */
SdfHit sdfMarch(vec3 ro, vec3 rd, float tmin, float tmax, float px) {
    SdfHit h;
    h.hit = false;
    h.minDist = 1e9;
    h.minT = tmin;
    float t = max(tmin, 0.0);
    for (int i = 0; i < SDF_STEPS; i++) {
        if (t > tmax) {
            break;
        }
        float d = map(ro + rd * t);
        if (d < h.minDist) {
            h.minDist = d;
            h.minT = t;
        }
        if (d < max(1e-3, px * t)) {
            h.hit = true;
            h.minDist = 0.0;
            h.minT = t;
            break;
        }
        t += d * SDF_STEP;
    }
    h.t = h.hit ? t : tmax;
    h.pos = ro + rd * h.t;
    h.minDist = max(h.minDist, 0.0);
    return h;
}

/** Surface normal at p (four map() calls). e: the sampling offset, e.g. 0.002 * distance from the camera. */
vec3 sdfNormal(vec3 p, float e) {
    const vec2 k = vec2(1.0, -1.0);
    return normalize(k.xyy * map(p + k.xyy * e) + k.yyx * map(p + k.yyx * e)
                   + k.yxy * map(p + k.yxy * e) + k.xxx * map(p + k.xxx * e));
}

/**
 * Soft shadow of the shape on p from a light in direction l (unit, towards the light): 1 lit .. 0 in shadow.
 * Only the shape itself casts it, not the world. k: hardness (8 soft .. 32 hard).
 */
float sdfShadow(vec3 p, vec3 l, float tmin, float tmax, float k) {
    float lit = 1.0;
    float t = tmin;
    for (int i = 0; i < SDF_SHADOW_STEPS; i++) {
        if (t > tmax) {
            break;
        }
        float d = map(p + l * t);
        lit = min(lit, k * d / t);
        if (lit < 0.01) {
            break;
        }
        t += clamp(d, 0.02, tmax * 0.25);
    }
    return clamp(lit, 0.0, 1.0);
}

/** Ambient occlusion at a surface point p with normal n: 1 open .. 0 in a crease. s: how far it looks (blocks). */
float sdfAO(vec3 p, vec3 n, float s) {
    float occ = 0.0;
    float w = 1.0;
    for (int i = 1; i <= 5; i++) {
        float h = s * float(i) / 5.0;
        occ += (h - map(p + n * h)) * w;
        w *= 0.6;
    }
    return clamp(1.0 - 1.5 * occ / s, 0.0, 1.0);
}
