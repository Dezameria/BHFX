#version 150

// Opens (AreaDepth 1) or closes (AreaDepth 0) one mirror surface's screen area in the reflection target, but only
// where no nearer mirror face hides it: a fragment behind another mirror (a mirror floor running on behind a
// mirror wall) is dropped, so it cannot wipe the reflection of the surface in front of it.
uniform sampler2D Sampler0; // depth of this frame's reflecting mirror faces
uniform vec2 ScreenSize;
uniform vec2 DepthRange;    // near, far of the projection
uniform float AreaDepth;

out vec4 fragColor;

float distanceOf(float depth) {
    float near = DepthRange.x;
    float far = DepthRange.y;
    return near * far / (far - depth * (far - near));
}

void main() {
    float face = distanceOf(gl_FragCoord.z);
    float scene = distanceOf(texture(Sampler0, gl_FragCoord.xy / ScreenSize).r);
    if (face > scene + 0.02 + face * 0.004) {
        discard;
    }
    gl_FragDepth = AreaDepth;
    fragColor = vec4(0.0);
}
