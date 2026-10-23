#version 150

// The reflection was drawn into its own screen-sized target; copy the pixel under this mirror fragment.
uniform sampler2D Sampler0;
uniform vec2 ScreenSize;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, gl_FragCoord.xy / ScreenSize);
    if (color.a < 0.004) {
        discard;
    }
    fragColor = color;
}
