// bhfx:textured — the model as painted: MTexture times MColor, shaded by two fixed lights the way Minecraft shades
// entities, darkened by the world's light where it stands. Texels with alpha under 0.1 are cut out.

vec4 material(Surface s) {
    vec4 t = texture(MTexture, s.uv);
    if (t.a < 0.1) {
        return vec4(0.0);
    }
    vec3 l0 = normalize(vec3(0.2, 1.0, -0.7));
    vec3 l1 = normalize(vec3(-0.2, 1.0, 0.7));
    float shade = 0.4 + 0.6 * min(1.0, (max(dot(s.normal, l0), 0.0) + max(dot(s.normal, l1), 0.0)) * 0.6);
    float light = mix(0.12, 1.0, s.light);
    return vec4(t.rgb * MColor.rgb * shade * light, MColor.a);
}
