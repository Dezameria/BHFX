// 3D signed distance functions: shapes, ways to combine them, and ways to bend space. Standard formulas (as collected
// by Inigo Quilez), written for bhfx. A distance is in blocks: > 0 outside, 0 on the surface, < 0 inside. Shapes sit
// at the origin; move a point into a shape's frame first (p - centre, a rotation, or fxToLocal from data.glsl).
// Draw them with sdf_march.glsl. The 2D shapes are in sdf2d.glsl (sdBox and sdSegment there take vec2; these vec3).

// ---------------------------------------------------------------- shapes

float sdSphere(vec3 p, float r) {
    return length(p) - r;
}

/** Box with half-size b. */
float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

/** Box with half-size b (corners included) and edges rounded by r. */
float sdRoundBox(vec3 p, vec3 b, float r) {
    vec3 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}

/** Distance to the segment a..b (a capsule of radius 0). */
float sdSegment(vec3 p, vec3 a, vec3 b) {
    vec3 pa = p - a;
    vec3 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-8), 0.0, 1.0);
    return length(pa - ba * h);
}

float sdCapsule(vec3 p, vec3 a, vec3 b, float r) {
    return sdSegment(p, a, b) - r;
}

/** A capsule from radius ra at a to rb at b (exact: the two balls joined by the cone tangent to both). */
float sdRoundCone(vec3 p, vec3 a, vec3 b, float ra, float rb) {
    vec3 ba = b - a;
    float l2 = dot(ba, ba);
    float rr = ra - rb;
    float a2 = l2 - rr * rr;
    float il2 = 1.0 / max(l2, 1e-8);
    vec3 pa = p - a;
    float y = dot(pa, ba);
    float z = y - l2;
    vec3 xv = pa * l2 - ba * y;
    float x2 = dot(xv, xv);
    float y2 = y * y * l2;
    float z2 = z * z * l2;
    float k = sign(rr) * rr * rr * x2;
    if (sign(z) * a2 * z2 > k) {
        return sqrt(x2 + z2) * il2 - rb;
    }
    if (sign(y) * a2 * y2 < k) {
        return sqrt(x2 + y2) * il2 - ra;
    }
    return (sqrt(x2 * a2 * il2) + y * rr) * il2 - ra;
}

/** Upright cylinder (along y) of radius r, from y = -h to h. */
float sdCylinder(vec3 p, float h, float r) {
    vec2 d = abs(vec2(length(p.xz), p.y)) - vec2(r, h);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

/** Upright cone cut flat at both ends: radius r1 at y = -h, r2 at y = h (r2 = 0 for a point). */
float sdCappedCone(vec3 p, float h, float r1, float r2) {
    vec2 q = vec2(length(p.xz), p.y);
    vec2 k1 = vec2(r2, h);
    vec2 k2 = vec2(r2 - r1, 2.0 * h);
    vec2 ca = vec2(q.x - min(q.x, q.y < 0.0 ? r1 : r2), abs(q.y) - h);
    vec2 cb = q - k1 + k2 * clamp(dot(k1 - q, k2) / dot(k2, k2), 0.0, 1.0);
    float s = (cb.x < 0.0 && ca.y < 0.0) ? -1.0 : 1.0;
    return s * sqrt(min(dot(ca, ca), dot(cb, cb)));
}

/** Ring lying in the xz plane: R from the centre to the tube's middle, r the tube's radius. */
float sdTorus(vec3 p, float R, float r) {
    return length(vec2(length(p.xz) - R, p.y)) - r;
}

/** Ellipsoid with radii r. Not exact (a bound that is close near the surface); fine for marching. */
float sdEllipsoid(vec3 p, vec3 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / max(k1, 1e-8);
}

/** Octahedron with its tips s from the centre on each axis (a bound, close enough for marching). */
float sdOctahedron(vec3 p, float s) {
    p = abs(p);
    return (p.x + p.y + p.z - s) * 0.57735027;
}

/** Hexagonal prism along z: h.x from the centre to a flat side, half-length h.y. A crystal's body. */
float sdHexPrism(vec3 p, vec2 h) {
    const vec3 k = vec3(-0.8660254, 0.5, 0.57735);
    p = abs(p);
    p.xy -= 2.0 * min(dot(k.xy, p.xy), 0.0) * k.xy;
    vec2 d = vec2(length(p.xy - vec2(clamp(p.x, -k.z * h.x, k.z * h.x), h.x)) * sign(p.y - h.x), p.z - h.y);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

// ---------------------------------------------------------------- combining

/** a with b cut out of it. */
float opSubtract(float a, float b) {
    return max(a, -b);
}

/** Only where both are. */
float opIntersect(float a, float b) {
    return max(a, b);
}

/** Union with a smooth join k blocks wide (k = 0 is a plain min). */
float smin(float a, float b, float k) {
    if (k <= 0.0) {
        return min(a, b);
    }
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

/** smin, plus how much of a the result is (x: distance, y: 1 = all a .. 0 = all b), to blend colours or materials. */
vec2 sminMix(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / max(k, 1e-6), 0.0, 1.0);
    return vec2(mix(b, a, h) - k * h * (1.0 - h), h);
}

/** Intersection with a smooth edge k wide. */
float smax(float a, float b, float k) {
    return -smin(-a, -b, k);
}

/** a with b cut out of it, the cut's edge rounded k wide. */
float smoothSubtract(float a, float b, float k) {
    return smax(a, -b, k);
}

/** A shape made thicker (or rounder) by r. */
float opRound(float d, float r) {
    return d - r;
}

/** Only a shell of thickness 2t round the surface (hollow). */
float opOnion(float d, float t) {
    return abs(d) - t;
}

// ---------------------------------------------------------------- moving and bending space
// These act on the point before a shape is measured: to turn a shape by +a, measure it at the point turned by -a.

mat3 rotX(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c);
}

mat3 rotY(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
}

mat3 rotZ(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0);
}

/** Rotation by angle a round the unit axis k (right-hand rule). */
mat3 rotAxis(vec3 k, float a) {
    float c = cos(a);
    float s = sin(a);
    float t = 1.0 - c;
    return mat3(t * k.x * k.x + c, t * k.x * k.y + s * k.z, t * k.x * k.z - s * k.y,
                t * k.x * k.y - s * k.z, t * k.y * k.y + c, t * k.y * k.z + s * k.x,
                t * k.x * k.z + s * k.y, t * k.y * k.z - s * k.x, t * k.z * k.z + c);
}

/** Repeat round the y axis n times: folds p into the first of n equal slices (the one round +x). */
vec3 opRepeatAround(vec3 p, float n) {
    float slice = 6.2831853 / n;
    float a = atan(p.z, p.x) + slice * 0.5;
    a = mod(a, slice) - slice * 0.5;
    float r = length(p.xz);
    return vec3(cos(a) * r, p.y, sin(a) * r);
}

/** Repeat in a grid of cell size c (centred cells). Shapes must fit inside one cell. */
vec3 opRepeat(vec3 p, vec3 c) {
    return p - c * floor(p / c + 0.5);
}

/** Twist round y by k radians per block. Distances get longer than true: march with a smaller step (SDF_STEP). */
vec3 opTwistY(vec3 p, float k) {
    float a = k * p.y;
    float c = cos(a);
    float s = sin(a);
    return vec3(c * p.x - s * p.z, p.y, s * p.x + c * p.z);
}

/** Bend in the xy plane by k radians per block along x (negative k bends the other way). March with a smaller step. */
vec3 opBendX(vec3 p, float k) {
    float a = k * p.x;
    float c = cos(a);
    float s = sin(a);
    return vec3(c * p.x - s * p.y, s * p.x + c * p.y, p.z);
}
