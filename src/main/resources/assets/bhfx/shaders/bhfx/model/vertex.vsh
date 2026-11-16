#version 150
// bhfx model vertex shader, shared by every model material. Vertices come from FxModels (a .geo.json baked like
// GeckoLib does: blocks, x mirrored, rest pose applied). MOrigin + MAxes put model space into the world (camera-relative,
// like everything bhfx draws); MAxes' columns are the frame's x, y, z axes times the scale. Instanced (MInstanced 1),
// copy gl_InstanceID stands where MInstances entries 4i .. 4i + 2 say ((axis, origin component) each), entry 4i + 3
// goes to the material as s.instance. Posed (MBoneCount > 0, FxPose), a vertex of bone b first goes through
// MBones[3b .. 3b + 2] (rows of a 3x4: rest pose to posed, model space); bones from MBoneCount on stay as baked.

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

out vec3 mPos;
out vec3 mNormal;
out vec2 mUv;
out vec3 mLocal;
flat out float mBone;
out float mViewZ;
flat out vec4 mInstance;
flat out float mIndex;
flat out float mDet;

void main() {
    vec3 origin = MOrigin;
    mat3 axes = MAxes;
    mInstance = vec4(0.0);
    mIndex = 0.0;
    if (MInstanced > 0.5) {
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
