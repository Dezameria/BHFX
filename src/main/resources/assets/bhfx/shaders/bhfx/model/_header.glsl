#version 150
// bhfx model material header: put before every assets/<ns>/shaders/bhfx/model/<name>.fsh. The material defines
// vec4 material(Surface s); _footer.glsl calls it. Same scene uniforms as a pass (scene.glsl works here too).
// Output: rgb as it goes on the image (display-encoded, values above 1 glow), a = coverage. A material that does
// `#define BHFX_ADD` adds its light instead of covering (glow, holograms).
uniform sampler2D DiffuseSampler;
uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform mat4 InvProj;
uniform mat4 InvView;
uniform mat4 ViewProj;
uniform vec3 Eye;
uniform vec2 OutSize;
uniform vec2 ScreenSize;
uniform float GameTime;
uniform vec4 FogColor;
uniform vec4 FogRange;

uniform sampler2D MTexture; // the model's texture (FxModel.texture); transparent black without one
uniform vec4 MColor;        // tint, from the effect (default 1, 1, 1, 1)
uniform vec4 MParams;       // free values from the effect (default 0); the built-in materials say what they read
uniform vec4 MMode;         // unused, world light at the model's origin 0..1, presence 0..1, unused
uniform float MInstanced;   // 1 when drawn with PassList.models

in vec3 mPos;
in vec3 mNormal;
in vec2 mUv;
in vec3 mLocal;
flat in float mBone;
in float mViewZ;
flat in vec4 mInstance;
flat in float mIndex;
flat in float mDet;
out vec4 fragColor;

struct Surface {
    vec2 uv;        // texture coordinates (the model's own UV mapping)
    vec3 pos;       // camera-relative world position of this pixel
    vec3 normal;    // world-space unit normal (outward)
    vec3 local;     // position in model space (blocks; the model's own frame)
    float bone;     // index of the bone this face belongs to (order of the .geo.json)
    vec3 view;      // unit direction from the eye to this pixel
    float light;    // world light at the model's origin, 0 dark .. 1 full
    float seconds;  // GameTime
    vec4 instance;  // PassList.models: this copy's four free values (FxData.setInstance); else 0
    float index;    // PassList.models: which copy (0 ..); else 0
};
