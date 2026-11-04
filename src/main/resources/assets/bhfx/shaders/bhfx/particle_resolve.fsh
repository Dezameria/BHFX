// bhfx: applies the distortion that DISTORT particle batches wrote (a screen-uv offset per pixel in AuxSampler):
// the image behind them is read shifted. Drawn once per run of particle batches, only when one of them distorts.

uniform sampler2D AuxSampler;

void main() {
    vec2 offset = texture(AuxSampler, texCoord).xy;
    vec2 uv = clamp(texCoord + offset, vec2(0.0005), vec2(0.9995));
    fragColor = vec4(texture(DiffuseSampler, uv).rgb, 1.0);
}
