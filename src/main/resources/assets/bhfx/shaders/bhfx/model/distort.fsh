// bhfx:distort — the model as clear glass or hot air: it shows the image behind it pushed sideways by its surface
// (more at the rim, where the surface turns away), so the world bends through it. Covers nothing by itself.
// MParams.x: bend, in blocks of sideways push at the rim (0.15 is clear glass, 0.4 a strong lens).
// MParams.y: ripple, a moving wobble added over the surface (0 none, 0.5 heat haze). MParams.z: ripples per block
// (default 3 when 0). MColor.rgb: tint of the image seen through (1, 1, 1 none). MColor.a: how much of the bend
// shows, 0..1 (fade the material with this). MParams.w: light added at the rim in MColor's colour (0 none).
// Reads the image: give the model a later order than the models it should bend (see bhfxBehind in the header).

vec4 material(Surface s) {
    // the surface's tilt as the camera sees it
    vec2 tilt = vec2(dot(s.normal, InvView[0].xyz), dot(s.normal, InvView[1].xyz));
    float scale = MParams.z > 0.0 ? MParams.z : 3.0;
    vec3 q = s.local * scale * 6.2832;
    float t = s.seconds;
    vec2 ripple = vec2(sin(q.y + t * 5.0) + sin(q.z * 1.3 - t * 3.7), cos(q.x * 1.1 - t * 4.3) + cos(q.y * 0.7 + t * 2.9)) * 0.5;
    vec2 push = tilt * MParams.x + ripple * MParams.y * 0.1;
    // blocks at this pixel's distance to screen uv (the projection's scale comes with OutSize's aspect)
    vec2 perBlock = vec2(OutSize.y / OutSize.x, 1.0) / max(mViewZ, 0.3);
    vec3 behind = bhfxBehind(push * perBlock * 0.6);
    float rim = pow(1.0 - abs(dot(s.normal, s.view)), 3.0);
    return vec4(behind * MColor.rgb + MColor.rgb * rim * MParams.w, MColor.a);
}
