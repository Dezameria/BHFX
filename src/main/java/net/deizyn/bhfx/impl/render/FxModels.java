package net.deizyn.bhfx.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.deizyn.bhfx.BHFxMod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * GPU side of {@code FxModel}: each {@code .geo.json} baked on Minecraft's background executor ({@link GeoModelLoader}),
 * uploaded the first frame after into its own vertex array (position, normal, uv, bone: attribute locations 0..3, see
 * {@code ShaderManager}), freed a minute after its last draw, on resource reload and when bhfx goes idle.
 */
final class FxModels {
    private static final long UNUSED_NANOS = 60_000_000_000L;
    private static final long SWEEP_NANOS = 5_000_000_000L;

    /** A model ready to draw. Bounds in model space (blocks). */
    record Mesh(int vao, int vbo, int vertexCount, float[] min, float[] max, long bytes) {
    }

    private static final class Entry {
        final ResourceLocation file;
        @Nullable
        CompletableFuture<GeoModelLoader.Data> pending;
        @Nullable
        Mesh mesh;
        boolean failed;
        long lastUsed;

        Entry(ResourceLocation file) {
            this.file = file;
        }
    }

    private final Map<ResourceLocation, Entry> entries = new HashMap<>();
    private long lastSweep;

    /** The mesh for {@code file}, or null while it loads (or when it failed). Starts the load on first use. */
    @Nullable
    Mesh get(ResourceLocation file, long now) {
        Entry e = entries.computeIfAbsent(file, FxModels::start);
        e.lastUsed = now;
        if (e.mesh == null && !e.failed && e.pending != null && e.pending.isDone()) {
            finish(e);
        }
        return e.mesh;
    }

    long heldBytes() {
        long n = 0;
        for (Entry e : entries.values()) {
            if (e.mesh != null) {
                n += e.mesh.bytes();
            }
        }
        return n;
    }

    int count() {
        int n = 0;
        for (Entry e : entries.values()) {
            if (e.mesh != null) {
                n++;
            }
        }
        return n;
    }

    void sweep(long now) {
        if (now - lastSweep < SWEEP_NANOS) {
            return;
        }
        lastSweep = now;
        Iterator<Entry> it = entries.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (now - e.lastUsed > UNUSED_NANOS) {
                drop(e);
                it.remove();
            }
        }
    }

    /** Free everything (resource reload, idle release). Loads in flight are dropped when they finish. */
    void clear() {
        RenderSystem.assertOnRenderThread();
        for (Entry e : entries.values()) {
            drop(e);
        }
        entries.clear();
    }

    private static void drop(Entry e) {
        if (e.mesh != null) {
            GL30.glDeleteVertexArrays(e.mesh.vao());
            GL15.glDeleteBuffers(e.mesh.vbo());
            e.mesh = null;
        }
        e.pending = null;
    }

    private static Entry start(ResourceLocation file) {
        Entry e = new Entry(file);
        e.pending = CompletableFuture.supplyAsync(() -> bake(file), Util.backgroundExecutor());
        return e;
    }

    private static GeoModelLoader.Data bake(ResourceLocation file) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            throw new IllegalStateException("missing " + file);
        }
        try (InputStream in = resource.get().open()) {
            return GeoModelLoader.load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    /** Upload on the render thread. The caller's VAO binding is restored by {@code GlStateGuard}. */
    private static void finish(Entry e) {
        GeoModelLoader.Data d;
        try {
            d = e.pending.join();
        } catch (RuntimeException ex) {
            e.failed = true;
            e.pending = null;
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            BHFxMod.LOGGER.error("bhfx model {} failed to load: {}", e.file, cause.getMessage());
            return;
        }
        e.pending = null;
        if (d.vertexCount() == 0) {
            e.failed = true;
            BHFxMod.LOGGER.warn("bhfx model {} has no faces to draw", e.file);
            return;
        }
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buf = MemoryUtil.memAllocFloat(d.vertices().length);
        try {
            buf.put(d.vertices()).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buf);
        }
        int stride = GeoModelLoader.FLOATS * 4;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 12L);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 24L);
        GL20.glEnableVertexAttribArray(3);
        GL20.glVertexAttribPointer(3, 1, GL11.GL_FLOAT, false, stride, 32L);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        // leave the caller's vertex array bound: the frame goes on drawing full-screen passes with it
        GL30.glBindVertexArray(previousVao);
        e.mesh = new Mesh(vao, vbo, d.vertexCount(), d.min(), d.max(), (long) d.vertices().length * 4);
        BHFxMod.LOGGER.debug("bhfx model {}: {} triangles", e.file, d.vertexCount() / 3);
    }
}
