// 2D signed distances (negative inside) and anti-aliased fills, for shapes drawn in a plane: on the ground (with
// ground.glsl), on a billboard, or in screen space. Units are whatever p is in; pass aa in the same units.

/** Distance to a circle of radius r at the origin. */
float sdCircle(vec2 p, float r) {
    return length(p) - r;
}

/** Distance to an axis-aligned box with half extents b at the origin. */
float sdBox(vec2 p, vec2 b) {
    vec2 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
}

/** Unsigned distance to the segment ab. */
float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-8), 0.0, 1.0);
    return length(p - a - ab * t);
}

/** Distance to a closed polygon with up to 8 corners in v[0..n-1] (either winding; even-odd inside test). */
float sdPolygon(vec2 p, vec2 v[8], int n) {
    float best = 1e20;
    bool inside = false;
    for (int i = 0; i < 8; i++) {
        if (i >= n) {
            break;
        }
        vec2 a = v[i];
        vec2 b = v[i + 1 < n ? i + 1 : 0];
        best = min(best, sdSegment(p, a, b));
        // a horizontal ray from p to +x crosses this edge
        if ((a.y > p.y) != (b.y > p.y)) {
            float x = a.x + (p.y - a.y) / (b.y - a.y) * (b.x - a.x);
            if (p.x < x) {
                inside = !inside;
            }
        }
    }
    return inside ? -best : best;
}

/** Regular star / polygon: k points, outer radius r, inner radius ratio m (m = cos(pi / k) gives a plain polygon). */
float sdStar(vec2 p, float r, int k, float m) {
    float sector = 6.2831853 / float(k);
    float a = mod(atan(p.y, p.x) + sector * 0.5, sector) - sector * 0.5;
    vec2 q = length(p) * vec2(cos(a), abs(sin(a)));
    vec2 tip = vec2(r, 0.0);
    vec2 valley = r * m * vec2(cos(sector * 0.5), sin(sector * 0.5));
    float d = sdSegment(q, tip, valley);
    // inside when q is on the origin side of the tip-valley edge
    vec2 e = valley - tip;
    float side = e.x * (q.y - tip.y) - e.y * (q.x - tip.x);
    return side > 0.0 ? -d : d;
}

/** Coverage 0..1 of a filled shape from its distance; aa = size of one pixel in the same units. */
float aaFill(float d, float aa) {
    return clamp(0.5 - d / max(aa, 1e-6), 0.0, 1.0);
}

/** Coverage of a line of the given width along the shape's edge, never thinner than about a pixel. */
float aaLine(float d, float width, float aa) {
    float w = max(width, aa);
    return aaFill(abs(d) - w * 0.5, aa) * min(width / max(aa, 1e-6), 1.0);
}

/** Rotate p by angle radians (counter-clockwise). */
vec2 rotate2(vec2 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}
