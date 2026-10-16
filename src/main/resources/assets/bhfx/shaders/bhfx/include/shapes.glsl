// Ray / shape helpers. Rays start at ro with unit direction rd.

#ifndef BHFX_FAR
#define BHFX_FAR 100000.0
#endif

/** Closest distance between the ray and segment ab; tRay = how far along the ray that closest point is. */
float rayLine(vec3 ro, vec3 rd, vec3 a, vec3 b, out float tRay) {
    vec3 ab = b - a;
    vec3 ao = ro - a;
    float abab = dot(ab, ab);
    float abrd = dot(ab, rd);
    float denom = abab - abrd * abrd;
    float s = denom > 1e-6 ? clamp((dot(ao, ab) - dot(ao, rd) * abrd) / denom, 0.0, 1.0) : 0.0;
    vec3 q = a + ab * s;
    tRay = max(dot(q - ro, rd), 0.0);
    return length(ro + rd * tRay - q);
}

/** Entry / exit distances through a sphere, or vec2(-1) when missed. */
vec2 raySphere(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float h = b * b - (dot(oc, oc) - r * r);
    if (h < 0.0) {
        return vec2(-1.0);
    }
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

/** Entry / exit distances through an infinite vertical cylinder around centerXZ, or vec2(-1) when missed. */
vec2 rayCylinderY(vec3 ro, vec3 rd, vec2 centerXZ, float r) {
    vec2 o = ro.xz - centerXZ;
    vec2 d = rd.xz;
    float a = dot(d, d);
    float c = dot(o, o) - r * r;
    if (a < 1e-8) {
        return c <= 0.0 ? vec2(-BHFX_FAR, BHFX_FAR) : vec2(-1.0);
    }
    float b = dot(o, d);
    float h = b * b - a * c;
    if (h < 0.0) {
        return vec2(-1.0);
    }
    h = sqrt(h);
    return vec2((-b - h) / a, (-b + h) / a);
}

/** Distance along the ray to the plane through p0 with normal n, or -1 when parallel or behind. */
float rayPlane(vec3 ro, vec3 rd, vec3 p0, vec3 n) {
    float dn = dot(rd, n);
    if (abs(dn) < 1e-6) {
        return -1.0;
    }
    float t = dot(p0 - ro, n) / dn;
    return t > 0.0 ? t : -1.0;
}

/** Closest horizontal distance between the ray and a vertical axis at centerXZ; t = where along the ray. */
float rayAxisY(vec3 ro, vec3 rd, vec2 centerXZ, out float t) {
    vec2 d = rd.xz;
    float dd = dot(d, d);
    vec2 o = ro.xz - centerXZ;
    t = dd > 1e-8 ? max(-dot(o, d) / dd, 0.0) : 0.0;
    return length(o + d * t);
}
