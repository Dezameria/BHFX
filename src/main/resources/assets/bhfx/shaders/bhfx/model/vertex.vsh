#version 150
// bhfx model vertex shader, shared by every model material. Vertices come from FxModels (a .geo.json baked like
// GeckoLib does: blocks, x mirrored, rest pose applied). MOrigin + MAxes put model space into the world (camera-relative,
// like everything bhfx draws); MAxes' columns are the frame's x, y, z axes times the scale. Instanced (MInstanced 1),
// copy gl_InstanceID stands where MInstances entries 4i .. 4i + 2 say ((axis, origin component) each), entry 4i + 3
// goes to the material as s.instance. Posed (MBoneCount > 0, FxPose), a vertex of bone b first goes through
// MBones[3b .. 3b + 2] (rows of a 3x4: rest pose to posed, model space); bones from MBoneCount on stay as baked.
// Particle pieces (MInstanced 2, PassList.modelParticles): copy gl_InstanceID is one slot of a particle batch (PData,
// the layout of particle/vertex.vsh) and moves like that particle: velocity, gravity, drag, orbit and pull (no
// turbulence). Its scale is MAxes times the particle's size; it is turned by the batch's facing: TUMBLE = a random
// start pose turning round the tumble axis (a random one when none was set), VELOCITY = the model's +z along the
// velocity, rolled by rotation + spin t, else upright, turned round y by rotation + spin t. The particle's colour and
// the over-life curves go to the material as a tint (mTint); its free data as s.instance.

in vec3 MPos;
in vec3 MNormal;
in vec2 MUv;
in float MBone;

uniform mat4 ViewProj;
uniform vec3 MOrigin;
uniform mat3 MAxes;
uniform float MInstanced;
uniform samplerBuffer MInstances;
uniform float MBoneCount;
uniform vec4 MBones[192]; // FxPose.MAX_BONES * 3

uniform samplerBuffer PData;
uniform vec3 POrigin;
uniform float PTime;
uniform vec4 PMotion;
uniform vec4 PShape;
uniform vec3 POrbit;
uniform vec3 POrbitAxis;
uniform vec4 PRing;
uniform vec4 PWindow;
uniform vec4 PCurves;
uniform vec3 PColorKey0;
uniform vec3 PColorKey1;
uniform vec3 PColorKey2;
uniform vec3 PColorKey3;
uniform vec4 PAlphaKeys;
uniform vec4 PSizeKeys;

out vec3 mPos;
out vec3 mNormal;
out vec2 mUv;
out vec3 mLocal;
flat out float mBone;
out float mViewZ;
flat out vec4 mInstance;
flat out float mIndex;
flat out float mDet;
flat out vec4 mTint;

float bhfxHash(float n) {
    return fract(sin(n * 12.9898 + 78.233) * 43758.5453);
}

vec3 bhfxTurn(vec3 v, vec3 k, float c, float s) {
    return v * c + cross(k, v) * s + k * dot(k, v) * (1.0 - c);
}

vec3 bhfxOctDecode(vec2 e) {
    vec3 n = vec3(e.x, e.y, 1.0 - abs(e.x) - abs(e.y));
    if (n.z < 0.0) {
        n.xy = (1.0 - abs(n.yx)) * vec2(n.x >= 0.0 ? 1.0 : -1.0, n.y >= 0.0 ? 1.0 : -1.0);
    }
    return normalize(n);
}

vec3 bhfxUnpackUnit(float code) {
    float v = floor(code + 0.5) - 1.0;
    float qx = floor(v / 2048.0);
    float qy = v - qx * 2048.0;
    return bhfxOctDecode(vec2(qx, qy) / 2047.0 * 2.0 - 1.0);
}

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

// colour keys only: a ramp texture (colour mode -1) is not read here
vec3 bhfxLifeColor(float age) {
    float n = PCurves.x;
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

void bhfxNothing() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    mPos = vec3(0.0);
    mNormal = vec3(0.0, 1.0, 0.0);
    mUv = vec2(0.0);
    mLocal = vec3(0.0);
    mBone = 0.0;
    mViewZ = 1.0;
    mDet = 1.0;
    mInstance = vec4(0.0);
    mIndex = 0.0;
    mTint = vec4(0.0);
}

// One particle slot as a place and frame for the model. False when the slot is not alive now.
bool bhfxPiece(out vec3 origin, out mat3 axes) {
    int stride = max(1, int(PRing.z + 0.5));
    int run1 = int(PWindow.y + 0.5);
    int slot = gl_InstanceID < run1 ? int(PWindow.x + 0.5) + gl_InstanceID * stride : (gl_InstanceID - run1) * stride;
    if (slot < 0 || slot >= int(PRing.y)) {
        return false;
    }
    int base = slot * 7;
    vec4 a0 = texelFetch(PData, base);
    vec4 a1 = texelFetch(PData, base + 1);
    vec4 a2 = texelFetch(PData, base + 2);
    vec4 a6 = texelFetch(PData, base + 6);
    float t = PTime - a0.w;
    float life = a1.w;
    if (t < 0.0 || t > life || life <= 0.0) {
        return false;
    }
    float k = PMotion.w;
    float decay = exp(-k * t);
    float travel = k > 1e-4 ? (1.0 - decay) / k : t;
    vec3 q = a0.xyz + a1.xyz * travel + 0.5 * PMotion.xyz * t * t;
    vec3 vel = a1.xyz * decay + PMotion.xyz * t;
    bool tumble = PShape.x > 4.5;
    if (!tumble && (a6.x != 0.0 || a6.y != 0.0 || a6.z != 0.0)) {
        vec3 kx = POrbitAxis;
        vec3 r = q;
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
                r += inward * dist;
            }
        }
        float angle = a6.x * t + 0.5 * a6.y * t * t;
        float c = cos(angle);
        float s = sin(angle);
        r = bhfxTurn(r, kx, c, s);
        vel = bhfxTurn(vel, kx, c, s) + cross(kx, r) * (a6.x + a6.y * t);
        q = POrbit + r;
    }
    float age = t / life;
    float size = mix(a2.x, a2.y, age) * (PCurves.z > 0.5 ? bhfxCurve(PSizeKeys, PCurves.z, age) : 1.0);
    float seed = bhfxHash(float(slot) + a0.w * 7.31);
    vec3 x = vec3(1.0, 0.0, 0.0);
    vec3 y = vec3(0.0, 1.0, 0.0);
    vec3 z = vec3(0.0, 0.0, 1.0);
    if (tumble) {
        // a random start pose, then round the tumble axis at the tumble rate
        vec3 k0 = normalize(vec3(bhfxHash(seed * 13.1), bhfxHash(seed * 29.7), bhfxHash(seed * 47.3)) - 0.5 + 1e-3);
        float a = seed * 6.2831853;
        vec3 k1 = a6.w > 0.5 ? bhfxUnpackUnit(a6.w)
                : normalize(vec3(bhfxHash(seed * 61.3), bhfxHash(seed * 83.9), bhfxHash(seed * 97.1)) - 0.5 + 1e-3);
        float b = a2.w * t;
        x = bhfxTurn(bhfxTurn(x, k0, cos(a), sin(a)), k1, cos(b), sin(b));
        y = bhfxTurn(bhfxTurn(y, k0, cos(a), sin(a)), k1, cos(b), sin(b));
        z = bhfxTurn(bhfxTurn(z, k0, cos(a), sin(a)), k1, cos(b), sin(b));
    } else if (PShape.x > 0.5 && PShape.x < 1.5) {
        // the model's +z along the velocity, rolled round it
        float speed = length(vel);
        z = speed > 1e-4 ? vel / speed : vec3(0.0, 1.0, 0.0);
        x = cross(vec3(0.0, 1.0, 0.0), z);
        x = dot(x, x) > 1e-6 ? normalize(x) : vec3(1.0, 0.0, 0.0);
        y = cross(z, x);
        float roll = a2.z + a2.w * t;
        x = bhfxTurn(x, z, cos(roll), sin(roll));
        y = bhfxTurn(y, z, cos(roll), sin(roll));
    } else {
        float yaw = a2.z + a2.w * t;
        x = bhfxTurn(x, y, cos(yaw), sin(yaw));
        z = bhfxTurn(z, y, cos(yaw), sin(yaw));
    }
    origin = POrigin + q;
    axes = mat3(x, y, z) * MAxes * size;
    vec4 tint = texelFetch(PData, base + 3);
    tint.rgb *= bhfxLifeColor(age);
    if (PCurves.y > 0.5) {
        tint.a *= bhfxCurve(PAlphaKeys, PCurves.y, age);
    }
    mTint = tint;
    mInstance = texelFetch(PData, base + 4);
    mIndex = float(slot);
    return true;
}

void main() {
    vec3 origin = MOrigin;
    mat3 axes = MAxes;
    mInstance = vec4(0.0);
    mIndex = 0.0;
    mTint = vec4(1.0);
    if (MInstanced > 1.5) {
        if (!bhfxPiece(origin, axes)) {
            bhfxNothing();
            return;
        }
    } else if (MInstanced > 0.5) {
        int first = gl_InstanceID * 4;
        vec4 a = texelFetch(MInstances, first);
        vec4 b = texelFetch(MInstances, first + 1);
        vec4 c = texelFetch(MInstances, first + 2);
        mInstance = texelFetch(MInstances, first + 3);
        origin = vec3(a.w, b.w, c.w);
        axes = mat3(a.xyz, b.xyz, c.xyz);
        mIndex = float(gl_InstanceID);
        // a copy whose numbers are broken (NaN, infinite, absurdly far or big) draws nothing: left as it is, its
        // triangles could cover the screen thousands of times over and hang the GPU
        vec4 bad = vec4(a.x + a.y + a.z + a.w, b.x + b.y + b.z + b.w, c.x + c.y + c.z + c.w, 0.0);
        if (any(isnan(bad)) || any(isinf(bad)) || dot(origin, origin) > 1.0e8
                || max(max(dot(a.xyz, a.xyz), dot(b.xyz, b.xyz)), dot(c.xyz, c.xyz)) > 1.0e4) {
            gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
            mPos = vec3(0.0);
            mNormal = vec3(0.0, 1.0, 0.0);
            mUv = vec2(0.0);
            mLocal = vec3(0.0);
            mBone = 0.0;
            mViewZ = 1.0;
            mDet = 1.0;
            mTint = vec4(0.0);
            return;
        }
    }
    mDet = determinant(axes);
    vec3 pos = MPos;
    vec3 normal = MNormal;
    int bone = int(MBone + 0.5);
    if (bone < int(MBoneCount + 0.5)) {
        vec4 r0 = MBones[bone * 3];
        vec4 r1 = MBones[bone * 3 + 1];
        vec4 r2 = MBones[bone * 3 + 2];
        vec4 p = vec4(MPos, 1.0);
        pos = vec3(dot(r0, p), dot(r1, p), dot(r2, p));
        normal = vec3(dot(r0.xyz, MNormal), dot(r1.xyz, MNormal), dot(r2.xyz, MNormal));
    }
    vec3 world = origin + axes * pos;
    mPos = world;
    mLocal = MPos; // where it was modelled: patterns stay on the part as it moves
    mUv = MUv;
    mBone = MBone;
    vec3 n = axes * normal;
    mNormal = dot(n, n) > 1e-12 ? normalize(n) : vec3(0.0, 1.0, 0.0);
    gl_Position = ViewProj * vec4(world, 1.0);
    mViewZ = gl_Position.w;
}
