package net.deizyn.bhfx.api.client;

import net.minecraft.world.phys.Vec3;

/** Uniform setter for one pass. Names the shader does not declare are ignored. */
public interface Uniforms {
    /** Sets a float. */
    Uniforms f1(String name, float x);

    /** Sets a vec2. */
    Uniforms f2(String name, float x, float y);

    /** Sets a vec3. */
    Uniforms f3(String name, float x, float y, float z);

    /** Sets a vec4. */
    Uniforms f4(String name, float x, float y, float z, float w);

    /** Sets a vec3 from a vector as is; for a world point pass {@code frame.rel(point)} (shaders work camera-relative). */
    Uniforms vec3(String name, Vec3 v);

    /** 0xRRGGBB (sRGB) converted to linear rgb, with alpha. Sets a vec4. */
    Uniforms color(String name, int rgb, float alpha);

    /** A float array: uploaded as vec4[] when the length is a multiple of 4, else as float[]. */
    Uniforms floats(String name, float[] values);
}
