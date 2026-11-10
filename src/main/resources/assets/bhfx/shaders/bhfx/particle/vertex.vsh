#version 150
// bhfx particle vertex shader, shared by every particle material. One instance per ring slot, four vertices each
// (triangle strip). The particle's data lives in a buffer texture, 7 texels per slot:
//   0: pos.xyz (from the batch origin), birth (s)   1: vel.xyz (blocks/s), life (s)
//   2: size at birth, size at death, rotation, spin 3: rgba (display-encoded, like the frame)   4: free data
//   5: axis.xyz, length (AXIS facing; length 0 = 2 x size; RIBBON: x = ribbon id, y = along, zw = own strip
//      normal, octahedral, w + 3; w = 0: none)
//   6: orbit (rad/s), spin-up (rad/s^2), pull (blocks/s), link (RIBBON: previous point's slot + 1, 0 = none)
// Motion is analytic: p(t) = p0 + v0 (1 - e^-kt) / k + g t^2 / 2, plus a turbulence offset that is 0 at birth. An
// orbiting particle (orbit or pull set) is stored relative to the orbit centre, moves that way in a frame turning round
// the batch's orbit axis by w t + a t^2 / 2, pulled towards the axis along its spawn radius by pull (1 - e^-kt) / k
// (stopping on the axis), and is placed round the centre as it is now: it moves with the axis. Over-life curves scale size here and
// colour / alpha into pColor, so every material gets them. Slots not alive at PTime collapse to nothing.
// RIBBON: instance i draws the segment from its previous point to itself; each end is turned by its own point's
// backward tangent, so neighbouring segments share their edge exactly. The strip turns to the camera, or lies in the
// plane of the point's normal (its own, else PRibbonNormal), which turns with an orbiting point.

uniform samplerBuffer PData;
uniform mat4 InvProj;
uniform mat4 InvView;
uniform mat4 ViewProj;
uniform vec3 Eye;
uniform vec3 POrigin;   // batch origin, camera-relative
uniform float PTime;    // batch clock, seconds
uniform vec4 PMotion;   // gravity xyz, drag
uniform vec4 PShape;    // facing (0 camera, 1 velocity, 2 flat, 3 axis, 4 ribbon), stretch, pivot (0 centred, 1 from the base), unused
uniform vec4 PMode;     // blend (0 add, 1 alpha, 2 distort), softness, distortion, unused
uniform vec3 POrbit;    // orbit axis passes through here (from the batch origin)
uniform vec3 POrbitAxis; // unit orbit axis
uniform vec3 PRibbonNormal; // RIBBON: unit strip normal for the batch, 0 = turn to the camera
uniform vec4 PTurb;     // turbulence amplitude (blocks), frequency (Hz), pattern scale (waves per block), unused
uniform vec4 PRing;     // capacity, slots filled, stride (instance i draws slot i * stride; 0 or 1 = every slot), unused
uniform vec4 PCurves;   // colour keys (0 off, 1..4; -1 = ramp texture), alpha keys, size keys, unused
uniform vec3 PColorKey0;
uniform vec3 PColorKey1;
uniform vec3 PColorKey2;
uniform vec3 PColorKey3;
uniform vec4 PAlphaKeys;
uniform vec4 PSizeKeys;
uniform sampler2D PRamp;
uniform vec2 PRampRange; // ramp u at birth, at death

out vec2 pCorner;
out vec3 pPos;
out float pViewZ;
out vec4 pColor;
out float pAge;
out float pAlong;
out float pFacing;
flat out vec4 pData;
flat out vec3 pLife;
flat out vec4 pScreen;

struct Pt {
    bool alive;
    vec3 pos;     // camera-relative
    vec3 vel;
    float t;      // seconds since birth
    float age;    // 0..1
    float size;   // half-size with the size curve
    float birth;
    float ribbon; // RIBBON: chain id
    int prev;     // RIBBON: previous point's slot, -1 = none
    float along;  // RIBBON: place along the chain
    vec3 normal;  // RIBBON: strip normal (unit), 0 = turn to the camera
};

// inverse of ParticleBatch.octEncode
vec3 bhfxOctDecode(vec2 e) {
    vec3 n = vec3(e.x, e.y, 1.0 - abs(e.x) - abs(e.y));
    if (n.z < 0.0) {
        n.xy = (1.0 - abs(n.yx)) * vec2(n.x >= 0.0 ? 1.0 : -1.0, n.y >= 0.0 ? 1.0 : -1.0);
    }
    return normalize(n);
}

float bhfxHash(float n) {
    return fract(sin(n * 12.9898 + 78.233) * 43758.5453);
}

float bhfxSeed(int slot, float birth) {
    return bhfxHash(float(slot) + birth * 7.31);
}

// v turned by the angle with this cos / sin round the unit axis k (right-hand rule)
vec3 bhfxTurn(vec3 v, vec3 k, float c, float s) {
    return v * c + cross(k, v) * s + k * dot(k, v) * (1.0 - c);
}

vec2 bhfxScreenUv(vec3 p) {
    vec4 c = ViewProj * vec4(p, 1.0);
    return c.xy / max(c.w, 1e-4) * 0.5;
}

// n evenly spaced keys (1..4) at age 0..1
float bhfxCurve(vec4 k, float n, float age) {
    if (n < 1.5) {
        return k.x;
    }
    float x = clamp(age, 0.0, 1.0) * (n - 1.0);
    int i = int(min(floor(x), n - 2.0));
    return mix(k[i], k[i + 1], x - float(i));
}

vec3 bhfxColorKey(int i) {
    return i == 0 ? PColorKey0 : (i == 1 ? PColorKey1 : (i == 2 ? PColorKey2 : PColorKey3));
}

vec3 bhfxLifeColor(float age) {
    float n = PCurves.x;
    if (n < -0.5) {
        return textureLod(PRamp, vec2(mix(PRampRange.x, PRampRange.y, clamp(age, 0.0, 1.0)), 0.5), 0.0).rgb;
    }
    if (n < 0.5) {
        return vec3(1.0);
    }
    if (n < 1.5) {
        return PColorKey0;
    }
    float x = clamp(age, 0.0, 1.0) * (n - 1.0);
    int i = int(min(floor(x), n - 2.0));
    return mix(bhfxColorKey(i), bhfxColorKey(i + 1), x - float(i));
}

vec4 bhfxColor(int slot, float age) {
    vec4 c = texelFetch(PData, slot * 7 + 3);
    c.rgb *= bhfxLifeColor(age);
    if (PCurves.y > 0.5) {
        c.a *= bhfxCurve(PAlphaKeys, PCurves.y, age);
    }
    return c;
}

// smooth wobble, 0 at t = 0; nearby spawn points get nearby phases, so neighbours drift together
vec3 bhfxTurbulence(vec3 p0, float seed, float t, out vec3 dv) {
    float amp = PTurb.x / 1.5;
    float w = PTurb.y * 6.2831853;
    vec3 ph = p0 * (PTurb.z * 6.2831853) + seed * 2.0 + vec3(0.0, 2.1, 4.2);
    vec3 ph2 = ph.yzx * 1.7 + 1.3;
    vec3 a = vec3(1.0, 1.31, 0.87) * w;
    vec3 b = vec3(2.13, 1.73, 2.41) * w;
    dv = amp * (a * cos(a * t + ph) + 0.5 * b * cos(b * t + ph2));
    return amp * (sin(a * t + ph) - sin(ph) + 0.5 * (sin(b * t + ph2) - sin(ph2)));
}

Pt bhfxPoint(int slot) {
    Pt o;
    o.alive = false;
    o.pos = vec3(0.0);
    o.vel = vec3(0.0);
    o.t = 0.0;
    o.age = 0.0;
    o.size = 0.0;
    o.birth = 0.0;
    o.ribbon = 0.0;
    o.prev = -1;
    o.along = 0.0;
    o.normal = vec3(0.0);
    if (slot < 0 || slot >= int(PRing.y)) {
        return o;
    }
    int base = slot * 7;
    vec4 a0 = texelFetch(PData, base);
    vec4 a1 = texelFetch(PData, base + 1);
    vec4 a2 = texelFetch(PData, base + 2);
    vec4 a6 = texelFetch(PData, base + 6);
    float t = PTime - a0.w;
    float life = a1.w;
    o.birth = a0.w;
    if (t < 0.0 || t > life || life <= 0.0) {
        return o;
    }
    o.alive = true;
    o.t = t;
    float k = PMotion.w;
    float decay = exp(-k * t);
    float travel = k > 1e-4 ? (1.0 - decay) / k : t;
    vec3 q = a0.xyz + a1.xyz * travel + 0.5 * PMotion.xyz * t * t; // from the batch origin
    vec3 vel = a1.xyz * decay + PMotion.xyz * t;
    if (PShape.x > 3.5) {
        vec4 a5 = texelFetch(PData, base + 5);
        o.ribbon = a5.x;
        o.along = a5.y;
        o.normal = a5.w > 1.0 ? bhfxOctDecode(vec2(a5.z, a5.w - 3.0)) : PRibbonNormal;
        o.prev = int(a6.w + 0.5) - 1;
    }
    if (PTurb.x > 0.0) {
        // a ribbon's points share one seed (its chain id): a random phase per point would zigzag the strip
        float seed = o.ribbon != 0.0 ? bhfxHash(o.ribbon * 3.17) : bhfxSeed(slot, a0.w);
        vec3 dv;
        q += bhfxTurbulence(a0.xyz, seed, t, dv);
        vel += dv;
    }
    if (a6.x != 0.0 || a6.y != 0.0 || a6.z != 0.0) {
        vec3 kx = POrbitAxis;
        vec3 r = q; // relative to the orbit centre
        vec3 r0 = a0.xyz;
        vec3 radial = r0 - kx * dot(r0, kx);
        float dist = length(radial);
        if (a6.z != 0.0 && dist > 1e-4) {
            vec3 inward = -radial / dist;
            float moved = a6.z * travel;
            if (moved < dist) {
                r += inward * moved;
                vel += inward * (a6.z * decay);
            } else {
                r += inward * dist; // reached the axis: stays on it
            }
        }
        float angle = a6.x * t + 0.5 * a6.y * t * t;
        float c = cos(angle);
        float s = sin(angle);
        r = bhfxTurn(r, kx, c, s);
        vel = bhfxTurn(vel, kx, c, s) + cross(kx, r) * (a6.x + a6.y * t);
        o.normal = bhfxTurn(o.normal, kx, c, s);
        q = POrbit + r;
    }
    o.pos = POrigin + q;
    o.vel = vel;
    o.age = t / life;
    o.size = mix(a2.x, a2.y, o.age) * (PCurves.z > 0.5 ? bhfxCurve(PSizeKeys, PCurves.z, o.age) : 1.0);
    return o;
}

// b follows a in the same ribbon (a's slot is b.prev)
bool bhfxLinked(Pt a, Pt b) {
    return a.alive && b.alive && b.ribbon != 0.0 && a.ribbon == b.ribbon && a.birth <= b.birth;
}

void bhfxCollapse() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0); // every vertex at one point outside the view: nothing is drawn
    pCorner = vec2(0.0);
    pPos = vec3(0.0);
    pViewZ = 0.0;
    pColor = vec4(0.0);
    pAge = 0.0;
    pAlong = 0.0;
    pFacing = 0.0;
    pData = vec4(0.0);
    pLife = vec3(0.0);
    pScreen = vec4(0.0);
}

void bhfxRibbon(int slot, vec2 corner) {
    Pt b = bhfxPoint(slot);
    if (!b.alive || b.prev < 0 || b.prev == slot) {
        bhfxCollapse();
        return;
    }
    int aSlot = b.prev;
    Pt a = bhfxPoint(aSlot);
    if (!bhfxLinked(a, b)) {
        bhfxCollapse();
        return;
    }
    // x runs from the older point (-1) to the newer (+1); each end turned by its point's backward tangent
    bool atB = corner.x > 0.0;
    vec3 tangent;
    Pt self;
    int selfSlot;
    if (atB) {
        self = b;
        selfSlot = slot;
        tangent = b.pos - a.pos;
    } else {
        self = a;
        selfSlot = aSlot;
        Pt before = bhfxPoint(a.prev);
        tangent = (a.prev >= 0 && bhfxLinked(before, a)) ? a.pos - before.pos : b.pos - a.pos;
    }
    if (dot(tangent, tangent) < 1e-12) {
        tangent = b.pos - a.pos;
    }
    if (dot(tangent, tangent) < 1e-12) {
        tangent = InvView[1].xyz;
    }
    vec3 dir = normalize(tangent);
    vec3 toEye = normalize(Eye - self.pos);
    // in the plane of the point's normal when it has one (and the strip doesn't run along it), else to the camera
    vec3 side = cross(dir, self.normal);
    float facing = 1.0;
    if (dot(side, side) > 1e-4) {
        side = normalize(side);
        facing = abs(dot(cross(side, dir), toEye));
    } else {
        side = cross(dir, toEye);
        side = dot(side, side) > 1e-8 ? normalize(side) : InvView[0].xyz;
    }
    vec3 world = self.pos + side * (self.size * corner.y);
    gl_Position = ViewProj * vec4(world, 1.0);

    pCorner = corner;
    pPos = world;
    pViewZ = gl_Position.w;
    pColor = bhfxColor(selfSlot, self.age);
    pAge = self.age;
    pAlong = self.along;
    pFacing = facing;
    pData = texelFetch(PData, slot * 7 + 4);
    pLife = vec3(self.age, self.t, bhfxSeed(slot, b.birth));
    if (PMode.x > 1.5) {
        vec3 centre = (a.pos + b.pos) * 0.5;
        vec2 c0 = bhfxScreenUv(centre);
        pScreen = vec4(bhfxScreenUv(centre + (b.pos - a.pos) * 0.5) - c0, bhfxScreenUv(centre + side * self.size) - c0);
    } else {
        pScreen = vec4(0.0);
    }
}

void main() {
    // thinned frames draw one slot in PRing.z, the same slots every frame; ribbons always get 1 (they link to every point)
    int slot = gl_InstanceID * max(1, int(PRing.z + 0.5));
    vec2 corner = vec2((gl_VertexID & 1) != 0 ? 1.0 : -1.0, (gl_VertexID & 2) != 0 ? 1.0 : -1.0);
    if (PShape.x > 3.5) {
        bhfxRibbon(slot, corner);
        return;
    }
    Pt p = bhfxPoint(slot);
    if (!p.alive) {
        bhfxCollapse();
        return;
    }
    int base = slot * 7;
    vec4 a2 = texelFetch(PData, base + 2);
    vec3 pos = p.pos;
    vec3 vel = p.vel;
    float size = p.size;
    float t = p.t;

    // half extents of the quad (ex along its x / uv.x, ey along y) and its centre
    vec3 ex;
    vec3 ey;
    vec3 centre = pos;
    if (PShape.x > 2.5) {
        // AXIS: the long side along the particle's own axis, from its base when pivot = 1; rotation tilts the quad
        // round that axis away from facing the camera (0 = faces it, pi/2 = edge-on), so bolts in every direction
        // show their angle in depth
        vec4 a5 = texelFetch(PData, base + 5);
        vec3 dir = dot(a5.xyz, a5.xyz) > 1e-8 ? normalize(a5.xyz) : InvView[1].xyz;
        float halfLen = a5.w > 0.0 ? a5.w * 0.5 : size;
        vec3 toEye = normalize(Eye - pos);
        vec3 side = cross(dir, toEye);
        side = dot(side, side) > 1e-8 ? normalize(side) : normalize(cross(dir, abs(dir.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
        vec3 facing = cross(side, dir);
        float rot = a2.z + a2.w * t;
        ex = dir * halfLen;
        ey = (side * cos(rot) + facing * sin(rot)) * size;
        centre = pos + dir * halfLen * PShape.z;
    } else {
        vec3 right;
        vec3 up;
        if (PShape.x > 1.5) {
            right = vec3(1.0, 0.0, 0.0);
            up = vec3(0.0, 0.0, 1.0);
        } else if (PShape.x > 0.5) {
            float speed = length(vel);
            vec3 dir = speed > 1e-4 ? vel / speed : InvView[1].xyz;
            vec3 toEye = normalize(Eye - pos);
            vec3 side = cross(dir, toEye);
            side = dot(side, side) > 1e-8 ? normalize(side) : InvView[0].xyz;
            right = dir * (1.0 + PShape.y * speed);
            up = side;
        } else {
            right = InvView[0].xyz;
            up = InvView[1].xyz;
        }
        if (PShape.x < 0.5 || PShape.x > 1.5) {
            float rot = a2.z + a2.w * t;
            float c = cos(rot);
            float s = sin(rot);
            vec3 r = right * c + up * s;
            up = up * c - right * s;
            right = r;
        }
        ex = right * size;
        ey = up * size;
        centre = pos + ex * PShape.z;
    }
    vec3 world = centre + ex * corner.x + ey * corner.y;
    gl_Position = ViewProj * vec4(world, 1.0);

    pCorner = corner;
    pPos = world;
    pViewZ = gl_Position.w;
    pColor = bhfxColor(slot, p.age);
    pAge = p.age;
    pAlong = 0.0;
    pFacing = 1.0;
    pData = texelFetch(PData, base + 4);
    pLife = vec3(p.age, t, bhfxSeed(slot, p.birth));
    if (PMode.x > 1.5) {
        // uv moved per unit of the quad's own x and y axes, for distortion offsets given in the quad's frame
        vec2 c0 = bhfxScreenUv(centre);
        pScreen = vec4(bhfxScreenUv(centre + ex) - c0, bhfxScreenUv(centre + ey) - c0);
    } else {
        pScreen = vec4(0.0);
    }
}
