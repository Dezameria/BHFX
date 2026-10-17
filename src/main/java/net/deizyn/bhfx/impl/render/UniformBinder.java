package net.deizyn.bhfx.impl.render;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.client.Uniforms;
import org.lwjgl.opengl.GL20;

/** {@link Uniforms} for the pass being drawn. */
final class UniformBinder implements Uniforms {
    private ShaderProgram program;

    void begin(ShaderProgram program) {
        this.program = program;
    }

    @Override
    public Uniforms f1(String name, float x) {
        int loc = program.location(name);
        if (loc >= 0) {
            GL20.glUniform1f(loc, x);
        }
        return this;
    }

    @Override
    public Uniforms f2(String name, float x, float y) {
        int loc = program.location(name);
        if (loc >= 0) {
            GL20.glUniform2f(loc, x, y);
        }
        return this;
    }

    @Override
    public Uniforms f3(String name, float x, float y, float z) {
        int loc = program.location(name);
        if (loc >= 0) {
            GL20.glUniform3f(loc, x, y, z);
        }
        return this;
    }

    @Override
    public Uniforms f4(String name, float x, float y, float z, float w) {
        int loc = program.location(name);
        if (loc >= 0) {
            GL20.glUniform4f(loc, x, y, z, w);
        }
        return this;
    }

    @Override
    public Uniforms vec3(String name, Vec3 v) {
        return f3(name, (float) v.x, (float) v.y, (float) v.z);
    }

    @Override
    public Uniforms color(String name, int rgb, float alpha) {
        return f4(name, linear(rgb >> 16), linear(rgb >> 8), linear(rgb), alpha);
    }

    @Override
    public Uniforms floats(String name, float[] values) {
        int loc = arrayLocation(name);
        if (loc < 0 || values.length == 0) {
            return this;
        }
        if (values.length % 4 == 0) {
            GL20.glUniform4fv(loc, values);
        } else {
            GL20.glUniform1fv(loc, values);
        }
        return this;
    }

    private int arrayLocation(String name) {
        int loc = program.location(name + "[0]");
        return loc >= 0 ? loc : program.location(name);
    }

    private static float linear(int channel) {
        return (float) Math.pow((channel & 0xFF) / 255.0, 2.2);
    }
}
