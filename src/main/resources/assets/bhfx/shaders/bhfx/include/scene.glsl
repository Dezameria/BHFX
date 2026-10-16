// bhfx scene reconstruction. Camera at the origin, world axes, units in blocks (camera-relative world space).
// Rays start at Eye, not the origin: view bobbing moves the real eye a little while walking. Positions from the depth
// buffer are exact either way; only analytic shapes need the right origin.
// DiffuseSampler = what earlier passes drew (or the world), SceneSampler = the untouched world, DepthSampler = depth.

#ifndef BHFX_FAR
#define BHFX_FAR 100000.0
#endif

float sceneDepth(vec2 uv) {
    return texture(DepthSampler, uv).r;
}

bool depthIsSky(float depth) {
    return depth >= 0.99999;
}

/** Camera-relative position of the point at screen uv and depth (0..1). */
vec3 unprojectAt(vec2 uv, float depth) {
    vec4 p = InvProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    p /= p.w;
    return (InvView * vec4(p.xyz, 1.0)).xyz;
}

/** Unit view ray through screen uv, starting at Eye. */
vec3 viewDir(vec2 uv) {
    return normalize(unprojectAt(uv, 0.5) - Eye);
}

struct Scene {
    vec3 color;   // current colour (earlier passes included)
    vec3 origin;  // where the ray starts (Eye)
    vec3 dir;     // unit view ray
    vec3 pos;     // visible surface, camera-relative; far along dir for sky
    float dist;   // distance from origin to pos (pos = origin + dir * dist)
    bool sky;
};

Scene sceneAt(vec2 uv) {
    Scene s;
    s.color = texture(DiffuseSampler, uv).rgb;
    s.origin = Eye;
    s.dir = viewDir(uv);
    float d = sceneDepth(uv);
    s.sky = depthIsSky(d);
    s.pos = s.sky ? Eye + s.dir * BHFX_FAR : unprojectAt(uv, d);
    s.dist = length(s.pos - Eye);
    return s;
}

/** Screen uv (xy) and view depth (z) of a camera-relative point; z < 0 when behind the camera. */
vec3 projectUv(vec3 p) {
    vec4 c = ViewProj * vec4(p, 1.0);
    if (c.w <= 0.0) {
        return vec3(-1.0, -1.0, -1.0);
    }
    return vec3(c.xy / c.w * 0.5 + 0.5, c.w);
}

/** Angle one output pixel covers along this ray. A line thinner than distance * pixelAngle would alias. */
float pixelAngle(vec2 uv) {
    return length(viewDir(uv + vec2(0.0, 1.0 / OutSize.y)) - viewDir(uv));
}
