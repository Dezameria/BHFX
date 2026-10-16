package net.offkung.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Uniform setter for one pass. Names the shader does not declare are ignored. */
public interface Uniforms {
    Uniforms f1(String name, float x);

    Uniforms f2(String name, float x, float y);

    Uniforms f3(String name, float x, float y, float z);

    Uniforms f4(String name, float x, float y, float z, float w);

    Uniforms vec3(String name, Vec3 v);

    /** 0xRRGGBB (sRGB) converted to linear rgb, with alpha. Sets a vec4. */
    Uniforms color(String name, int rgb, float alpha);

    /** A float array: uploaded as vec4[] when the length is a multiple of 4, else as float[]. */
    Uniforms floats(String name, float[] values);

    /** World points converted to camera-relative vec4(x, y, z, 1)[] (at most 32). */
    Uniforms points(String name, List<Vec3> worldPoints, FrameContext frame);

    /** A texture from the resource manager (e.g. "bhspells:textures/fx/noise.png"). Up to 12 per pass. */
    Uniforms texture(String sampler, ResourceLocation texture);
}
