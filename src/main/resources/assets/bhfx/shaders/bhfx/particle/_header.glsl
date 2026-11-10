#version 150
// bhfx particle material header: put before every assets/<ns>/shaders/bhfx/particle/<name>.fsh. The material defines
// vec4 material(Particle p); _footer.glsl calls it. Same scene uniforms as a pass (scene.glsl works here too).
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
uniform float PTime;
uniform vec4 PMode;

in vec2 pCorner;
in vec3 pPos;
in float pViewZ;
in vec4 pColor;
in float pAge;
in float pAlong;
in float pFacing;
flat in vec4 pData;
flat in vec3 pLife;
flat in vec4 pScreen;
out vec4 fragColor;

struct Particle {
    vec2 uv;       // 0..1 across the quad (for VELOCITY facing, x runs along the motion)
    vec2 corner;   // -1..1 across the quad; length(corner) is the distance from the centre
    float age;     // 0 at birth .. 1 at death (RIBBON: of the point at this end, so it runs along the ribbon)
    float seconds; // seconds since birth
    float seed;    // 0..1, stable for the particle's life
    vec4 color;    // from the spawn (as on screen; values above 1 glow)
    vec4 data;     // free values from the spawn
    vec3 pos;      // camera-relative world position of this pixel
    float along;   // RIBBON: smooth place along the ribbon (blocks from its first point, or as set per point); else 0
    float facing;  // RIBBON in a plane: 1 face-on .. 0 edge-on; else 1
};
