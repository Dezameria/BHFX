// bhfx model material footer: hide what the world covers, apply presence, write premultiplied colour.

// clip-space w of the scene at uv, the same measure as mViewZ (see particle/_footer.glsl)
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
    bool hidden = bhfxSceneViewZ(screenUv) < mViewZ;
    Surface s;
    s.uv = mUv;
    s.pos = mPos;
    s.normal = normalize(mNormal);
    s.local = mLocal;
    s.bone = mBone;
    s.view = normalize(mPos - Eye);
    s.light = MMode.y;
    s.seconds = GameTime;
    s.instance = mInstance;
    s.index = mIndex;
    // the material runs before any discard: texture() needs implicit derivatives (see docs/shaders.md)
    vec4 c = material(s) * mTint;
    float a = clamp(c.a, 0.0, 1.0) * MMode.z;
    // instanced copies are drawn without culling: keep only each copy's outward faces (a mirrored copy flips them)
    bool back = MInstanced > 0.5 && (gl_FrontFacing != (mDet > 0.0));
    if (hidden || back || a <= 0.0) {
        discard;
    }
#ifdef BHFX_ADD
    fragColor = vec4(max(c.rgb, 0.0) * a, 0.0);
#else
    fragColor = vec4(max(c.rgb, 0.0) * a, a);
#endif
}
