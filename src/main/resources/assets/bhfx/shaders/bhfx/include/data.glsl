// Reading FxData tables (Fx.createData, Uniforms.data). Declare each table yourself:
//     uniform samplerBuffer Orbs;
// and bind it from Java with u.data("Orbs", table). Each entry is one vec4.

/** Number of entries in the table (0 when nothing is bound). */
int fxDataSize(samplerBuffer table) {
    return textureSize(table);
}

/** Entry i of the table; vec4(0) outside 0 .. size - 1. */
vec4 fxData(samplerBuffer table, int i) {
    return (i >= 0 && i < textureSize(table)) ? texelFetch(table, i) : vec4(0.0);
}

// ---------------------------------------------------------------- frames (bones)
// A frame is a place plus three unit axes: a bone, a hand, anything a shape is drawn in. Java writes one into three
// entries with FxData.setFrame(entry, frame) (an FxFrame, camera-relative); read it once per pixel with fxFrame and
// measure shapes at fxToLocal(frame, p).

struct FxFrame {
    vec3 o;  // origin, camera-relative
    vec3 x;
    vec3 y;
    vec3 z;
};

/** The frame stored at entries first .. first + 2 (FxData.setFrame). */
FxFrame fxFrame(samplerBuffer table, int first) {
    vec4 a = fxData(table, first);
    vec4 b = fxData(table, first + 1);
    vec4 c = fxData(table, first + 2);
    return FxFrame(vec3(a.w, b.w, c.w), a.xyz, b.xyz, c.xyz);
}

/** p (camera-relative) in the frame's own coordinates. */
vec3 fxToLocal(FxFrame f, vec3 p) {
    vec3 q = p - f.o;
    return vec3(dot(q, f.x), dot(q, f.y), dot(q, f.z));
}

/** A point given in the frame's coordinates, camera-relative. */
vec3 fxToWorld(FxFrame f, vec3 l) {
    return f.o + f.x * l.x + f.y * l.y + f.z * l.z;
}

/** A direction given in the frame's coordinates, in the world. */
vec3 fxDirToWorld(FxFrame f, vec3 d) {
    return f.x * d.x + f.y * d.y + f.z * d.z;
}
