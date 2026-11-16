// bhfx:glow — the model as a shell of light in MColor: brighter at the rim, the texture's light parts showing through
// (a flat glow without a texture). MParams.x: pulse strength (0 steady). Adds light; no world light needed.
#define BHFX_ADD

vec4 material(Surface s) {
    vec4 t = texture(MTexture, s.uv);
    float rim = pow(1.0 - abs(dot(s.normal, s.view)), 2.0);
    float painted = dot(t.rgb, vec3(0.3, 0.59, 0.11)) * t.a;
    float body = 0.35 + 0.65 * painted;
    float pulse = 1.0 + MParams.x * 0.3 * sin(s.seconds * 6.0);
    return vec4(MColor.rgb * (body * 0.5 + rim * 1.8) * pulse, MColor.a);
}
