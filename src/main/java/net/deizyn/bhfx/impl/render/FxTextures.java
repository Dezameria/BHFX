package net.deizyn.bhfx.impl.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.FxTexture;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Textures for {@link FxTexture}: decoded on Minecraft's background executor (the render thread never waits), uploaded
 * on the render thread the first frame after, with mipmaps. Grayscale images become {@code R8} with a swizzle (a quarter
 * of the memory); packed textures combine up to four masks into one {@code RGBA8}.
 */
final class FxTextures {
    private static final long UNUSED_NANOS = 60_000_000_000L;
    private static final long SWEEP_NANOS = 5_000_000_000L;
    private static final float ANISOTROPY = 8f;

    private record Decoded(ByteBuffer data, int width, int height, boolean gray) {
    }

    private static final class Entry {
        final FxTexture spec;
        @Nullable
        CompletableFuture<Decoded> pending;
        int id;
        long bytes;
        long lastUsed;
        boolean failed;

        Entry(FxTexture spec) {
            this.spec = spec;
        }
    }

    private final Map<FxTexture, Entry> entries = new HashMap<>();
    private int fallback;
    private long lastSweep;
    private Boolean swizzle;

    /**
     * The GL texture for {@code spec}, or a 1x1 transparent black texture while it loads (or when it failed). Starts
     * the load on first use. Render thread, with unit state already saved by the caller (this binds textures).
     */
    int get(FxTexture spec, long now) {
        Entry e = entries.computeIfAbsent(spec, this::start);
        e.lastUsed = now;
        if (e.id == 0 && !e.failed && e.pending != null && e.pending.isDone()) {
            finish(e);
        }
        return e.id != 0 ? e.id : fallback();
    }

    /** Start decoding without a GL context; uploaded the first time a pass uses it. */
    void preload(FxTexture spec) {
        Entry e = entries.computeIfAbsent(spec, this::start);
        e.lastUsed = System.nanoTime();
    }

    boolean ready(FxTexture spec) {
        Entry e = entries.get(spec);
        return e != null && e.id != 0;
    }

    long heldBytes() {
        long sum = fallback != 0 ? 4 : 0;
        for (Entry e : entries.values()) {
            sum += e.bytes;
        }
        return sum;
    }

    int count() {
        int n = 0;
        for (Entry e : entries.values()) {
            if (e.id != 0) {
                n++;
            }
        }
        return n;
    }

    /** Free textures nothing sampled for a minute. Cheap; checks every few seconds. */
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

    /** Free everything (resource reload, idle release). Loads in flight are discarded when they finish. */
    void clear() {
        RenderSystem.assertOnRenderThread();
        for (Entry e : entries.values()) {
            drop(e);
        }
        entries.clear();
        if (fallback != 0) {
            GL11.glDeleteTextures(fallback);
            fallback = 0;
        }
    }

    private void drop(Entry e) {
        if (e.id != 0) {
            GL11.glDeleteTextures(e.id);
            e.id = 0;
        }
        if (e.pending != null) {
            // free the decoded pixels whenever the decode finishes (now, or later on the background thread)
            e.pending.thenAccept(d -> MemoryUtil.memFree(d.data()));
            e.pending = null;
        }
    }

    private Entry start(FxTexture spec) {
        Entry e = new Entry(spec);
        boolean canSwizzle = swizzleSupported();
        e.pending = CompletableFuture.supplyAsync(() -> decode(spec, canSwizzle), Util.backgroundExecutor());
        return e;
    }

    private boolean swizzleSupported() {
        if (swizzle == null) {
            try {
                GLCapabilities caps = GL.getCapabilities();
                swizzle = caps.OpenGL33 || caps.GL_ARB_texture_swizzle;
            } catch (IllegalStateException e) {
                swizzle = false; // no context on this thread (preload from elsewhere): RGBA always works
                return false;
            }
        }
        return swizzle;
    }

    private void finish(Entry e) {
        Decoded d;
        try {
            d = e.pending.join();
        } catch (RuntimeException ex) {
            e.failed = true;
            e.pending = null;
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            BHFxMod.LOGGER.error("bhfx texture {} failed to load: {}", e.spec, cause.getMessage());
            return;
        }
        e.pending = null;
        try {
            e.id = upload(d, e.spec);
            long base = (long) d.width() * d.height() * (d.gray() ? 1 : 4);
            e.bytes = e.spec.isNearest() ? base : base * 4 / 3;
        } finally {
            MemoryUtil.memFree(d.data());
        }
    }

    // ------------------------------------------------------------------ decode (background thread)

    private static Decoded decode(FxTexture spec, boolean canSwizzle) {
        try {
            if (spec.isPacked()) {
                return decodePacked(spec);
            }
            try (NativeImage image = read(spec.sources().get(0))) {
                int w = image.getWidth();
                int h = image.getHeight();
                boolean gray = !spec.isSrgb() && canSwizzle && isGray(image);
                ByteBuffer out = MemoryUtil.memAlloc(w * h * (gray ? 1 : 4));
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int abgr = image.getPixelRGBA(x, y);
                        if (gray) {
                            out.put((byte) abgr);
                        } else {
                            out.put((byte) abgr).put((byte) (abgr >> 8)).put((byte) (abgr >> 16)).put((byte) (abgr >> 24));
                        }
                    }
                }
                return new Decoded(out.flip(), w, h, gray);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Decoded decodePacked(FxTexture spec) throws IOException {
        NativeImage[] images = new NativeImage[4];
        try {
            int w = -1;
            int h = -1;
            for (int c = 0; c < 4; c++) {
                ResourceLocation file = spec.sources().get(c);
                if (file == null) {
                    continue;
                }
                images[c] = read(file);
                if (w < 0) {
                    w = images[c].getWidth();
                    h = images[c].getHeight();
                } else if (images[c].getWidth() != w || images[c].getHeight() != h) {
                    throw new IOException("packed images differ in size: " + spec);
                }
            }
            ByteBuffer out = MemoryUtil.memAlloc(w * h * 4);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    for (int c = 0; c < 4; c++) {
                        out.put(images[c] == null ? 0 : (byte) images[c].getPixelRGBA(x, y));
                    }
                }
            }
            return new Decoded(out.flip(), w, h, false);
        } finally {
            for (NativeImage image : images) {
                if (image != null) {
                    image.close();
                }
            }
        }
    }

    private static NativeImage read(ResourceLocation file) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            throw new IOException("missing " + file);
        }
        try (InputStream in = resource.get().open()) {
            return NativeImage.read(in);
        }
    }

    private static boolean isGray(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = image.getPixelRGBA(x, y);
                int r = abgr & 0xFF;
                if (((abgr >> 8) & 0xFF) != r || ((abgr >> 16) & 0xFF) != r || (abgr >>> 24) != 0xFF) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ upload (render thread)

    private static int upload(Decoded d, FxTexture spec) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        int align = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL12.GL_UNPACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_PIXELS);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
        try {
            if (d.gray()) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, d.width(), d.height(), 0, GL11.GL_RED,
                        GL11.GL_UNSIGNED_BYTE, d.data());
                GL11.glTexParameteriv(GL11.GL_TEXTURE_2D, GL33.GL_TEXTURE_SWIZZLE_RGBA,
                        new int[]{GL11.GL_RED, GL11.GL_RED, GL11.GL_RED, GL11.GL_ONE});
            } else {
                int internal = spec.isSrgb() ? GL21.GL_SRGB8_ALPHA8 : GL11.GL_RGBA8;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, d.width(), d.height(), 0, GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE, d.data());
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, align);
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, skipPixels);
        }
        int wrap = spec.isClamped() ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
        if (spec.isNearest()) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        } else {
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE, ANISOTROPY); // GL_TEXTURE_MAX_ANISOTROPY_EXT
            }
        }
        return id;
    }

    private int fallback() {
        if (fallback == 0) {
            fallback = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fallback);
            ByteBuffer zero = MemoryUtil.memCalloc(4);
            try {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zero);
            } finally {
                MemoryUtil.memFree(zero);
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
        return fallback;
    }
}
