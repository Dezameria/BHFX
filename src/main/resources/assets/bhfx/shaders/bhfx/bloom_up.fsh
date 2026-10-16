// bhfx bloom, one level up: a soft 8-tap ring of the smaller level (spread by Radius), added onto this level's
// downsample so every size of glow survives.

uniform sampler2D AuxSampler;  // this level's downsample
uniform vec2 TexelSize;        // 1 / size of the smaller level
uniform float Radius;          // 1 = normal spread

void main() {
    vec2 h = TexelSize * Radius;
    vec3 sum = vec3(0.0);
    sum += texture(DiffuseSampler, texCoord + vec2(-2.0 * h.x, 0.0)).rgb;
    sum += texture(DiffuseSampler, texCoord + vec2(2.0 * h.x, 0.0)).rgb;
    sum += texture(DiffuseSampler, texCoord + vec2(0.0, -2.0 * h.y)).rgb;
    sum += texture(DiffuseSampler, texCoord + vec2(0.0, 2.0 * h.y)).rgb;
    sum += texture(DiffuseSampler, texCoord + vec2(-h.x, -h.y)).rgb * 2.0;
    sum += texture(DiffuseSampler, texCoord + vec2(h.x, -h.y)).rgb * 2.0;
    sum += texture(DiffuseSampler, texCoord + vec2(-h.x, h.y)).rgb * 2.0;
    sum += texture(DiffuseSampler, texCoord + vec2(h.x, h.y)).rgb * 2.0;
    fragColor = vec4(sum / 12.0 * 0.9 + texture(AuxSampler, texCoord).rgb, 1.0);
}
