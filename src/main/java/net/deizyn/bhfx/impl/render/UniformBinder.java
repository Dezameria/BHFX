package net.deizyn.bhfx.impl.render;

import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.FxTexture;
import net.deizyn.bhfx.api.client.Uniforms;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.HashSet;
import java.util.Set;

/** {@link Uniforms} for the pass being drawn. */
final class UniformBinder implements Uniforms {
    /** Units 0..5 are bhfx's own (scene, depth, aux, bloom, particle data); effect textures take the rest. */
    static final int FIRST_TEXTURE_UNIT = 6;
    static final int LAST_TEXTURE_UNIT = 15;

    private final GlStateGuard guard;
    private final FxTextures textures;
    private final Set<String> warned = new HashSet<>();
    private ShaderProgram program;
    private int nextUnit;
    private long now;

    UniformBinder(GlStateGuard guard, FxTextures textures) {
        this.guard = guard;
        this.textures = textures;
    }

    void begin(ShaderProgram program) {
        this.program = program;
        this.nextUnit = FIRST_TEXTURE_UNIT;
        this.now = System.nanoTime();
    }

    @Override
    public Uniforms texture(String name, FxTexture texture) {
        int loc = program.location(name);
        if (loc < 0 || texture == null) {
            return this;
        }
        if (nextUnit > LAST_TEXTURE_UNIT) {
            if (warned.add(name)) {
                BHFxMod.LOGGER.warn("bhfx: more than {} textures in one pass; {} is not bound",
                        LAST_TEXTURE_UNIT - FIRST_TEXTURE_UNIT + 1, name);
            }
            return this;
        }
        int unit = nextUnit++;
        guard.touchUnit(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.get(texture, now));
        GL20.glUniform1i(loc, unit);
        return this;
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
