// bhfx: lays a low-resolution layer (PassOptions.halfRes) over the image. The layer holds premultiplied colour and
// coverage; each pixel takes the layer texels round it, weighted by how close their depth is to its own, so the layer
// does not bleed over the edge of something nearer or farther than what it was drawn against.

uniform sampler2D AuxSampler; // the layer
uniform vec2 LayerSize;       // its size in texels

float viewZ(float depth) {
    vec4 p = InvProj * vec4(0.0, 0.0, depth * 2.0 - 1.0, 1.0);
    return p.z / p.w;
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float z0 = viewZ(texture(DepthSampler, texCoord).r);
    vec2 t = texCoord * LayerSize - 0.5;
    vec2 f = fract(t);
    vec2 corner = (floor(t) + 0.5) / LayerSize;
    vec2 px = 1.0 / LayerSize;
    vec4 sum = vec4(0.0);
    float total = 0.0;
    for (int j = 0; j < 2; j++) {
        for (int i = 0; i < 2; i++) {
            vec2 uv = clamp(corner + vec2(float(i), float(j)) * px, 0.5 * px, 1.0 - 0.5 * px);
            float bilinear = (i == 0 ? 1.0 - f.x : f.x) * (j == 0 ? 1.0 - f.y : f.y);
            float z = viewZ(texture(DepthSampler, uv).r);
            float w = (bilinear + 1e-4) / (0.02 + abs(z - z0) / max(abs(z0), 0.1));
            sum += texture(AuxSampler, uv) * w;
            total += w;
        }
    }
    vec4 layer = sum / total;
    fragColor = vec4(base * (1.0 - clamp(layer.a, 0.0, 1.0)) + layer.rgb, 1.0);
}
