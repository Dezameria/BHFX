package net.offkung.bhfx.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.offkung.bhfx.api.FxParam;
import net.offkung.bhfx.api.client.FrameContext;
import net.offkung.bhfx.api.client.Uniforms;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** {@link Uniforms} for the pass being drawn. Units 0-3 are bhfx's own; custom textures use 4-15. */
final class UniformBinder implements Uniforms {
    static final int FIRST_CUSTOM_UNIT = 4;
    private static final int UNITS = 16;

    private final GlStateGuard guard;
    /** Textures not yet loaded; loaded after the GL section so Minecraft's binding cache is never bypassed. */
    private final Set<ResourceLocation> pendingTextures = new HashSet<>();
    private final float[] pointScratch = new float[FxParam.MAX_POINTS * 4];
    private ShaderProgram program;
    private int nextUnit;

    UniformBinder(GlStateGuard guard) {
        this.guard = guard;
    }

    void begin(ShaderProgram program) {
        this.program = program;
        this.nextUnit = FIRST_CUSTOM_UNIT;
    }

    /** Called outside the guarded GL section. */
    void loadPendingTextures() {
        if (pendingTextures.isEmpty()) {
            return;
        }
        for (ResourceLocation rl : pendingTextures) {
            Minecraft.getInstance().getTextureManager().getTexture(rl);
        }
        pendingTextures.clear();
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

    @Override
    public Uniforms points(String name, List<Vec3> worldPoints, FrameContext frame) {
        int loc = arrayLocation(name);
        int n = Math.min(worldPoints.size(), FxParam.MAX_POINTS);
        if (loc < 0 || n == 0) {
            return this;
        }
        float[] upload = pointScratch.length == n * 4 ? pointScratch : new float[n * 4];
        for (int i = 0; i < n; i++) {
            Vec3 r = frame.rel(worldPoints.get(i));
            upload[i * 4] = (float) r.x;
            upload[i * 4 + 1] = (float) r.y;
            upload[i * 4 + 2] = (float) r.z;
            upload[i * 4 + 3] = 1f;
        }
        GL20.glUniform4fv(loc, upload);
        return this;
    }

    @Override
    public Uniforms texture(String sampler, ResourceLocation texture) {
        int loc = program.location(sampler);
        if (loc < 0 || nextUnit >= UNITS) {
            return this;
        }
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture, null);
        if (tex == null) {
            pendingTextures.add(texture);
            return this;
        }
        int unit = nextUnit++;
        guard.touchUnit(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.getId());
        GL20.glUniform1i(loc, unit);
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
