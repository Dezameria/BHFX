package net.deizyn.bhfx.impl.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * GPU side of {@link DataTable}s: one buffer texture ({@code RGBA32F}) each, made on the first bind, sent only the
 * range written since the last bind. Same pattern as {@link ParticleRenderer}.
 */
final class DataTables {
    /** Tables not bound for this long give their GPU buffer back (a re-bind sends everything again). */
    private static final long IDLE_NANOS = 5_000_000_000L;

    private final GlStateGuard guard;
    private final List<DataTable> resident = new ArrayList<>();
    private FloatBuffer scratch;
    private long heldBytes;

    DataTables(GlStateGuard guard) {
        this.guard = guard;
    }

    long heldBytes() {
        return heldBytes + (scratch != null ? (long) scratch.capacity() * 4 : 0);
    }

    int count() {
        return resident.size();
    }

    /** Send what changed, then bind {@code t} as a buffer texture on {@code unit} (left active). */
    void bind(DataTable t, int unit, long now) {
        guard.touchBufferUnit(unit);
        if (t.gpuBuffer == 0) {
            t.gpuBuffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, t.gpuBuffer);
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, t.gpuBytes(), GL15.GL_DYNAMIC_DRAW);
            t.gpuTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, t.gpuTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, t.gpuBuffer);
            t.needsFull = true;
            heldBytes += t.gpuBytes();
            resident.add(t);
        }
        upload(t);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, t.gpuTexture);
        t.lastBindNanos = now;
    }

    private void upload(DataTable t) {
        int from = t.uploadFrom();
        if (from < 0) {
            return;
        }
        int to = t.uploadTo();
        int floats = (to - from) * 4;
        if (scratch == null || scratch.capacity() < floats) {
            if (scratch != null) {
                MemoryUtil.memFree(scratch);
            }
            scratch = MemoryUtil.memAllocFloat(Math.max(floats, 4096));
        }
        scratch.clear();
        scratch.put(t.data, from * 4, floats).flip();
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, t.gpuBuffer);
        GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, (long) from * 16, scratch);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        t.uploaded();
    }

    /** Free tables whose effect ended or that were not bound for a while. */
    void sweep(long now) {
        Iterator<DataTable> it = resident.iterator();
        while (it.hasNext()) {
            DataTable t = it.next();
            if (t.closed || now - t.lastBindNanos > IDLE_NANOS) {
                free(t);
                it.remove();
            }
        }
        if (resident.isEmpty() && scratch != null) {
            MemoryUtil.memFree(scratch);
            scratch = null;
        }
    }

    void releaseAll() {
        for (DataTable t : resident) {
            free(t);
        }
        resident.clear();
        heldBytes = 0;
        if (scratch != null) {
            MemoryUtil.memFree(scratch);
            scratch = null;
        }
    }

    private void free(DataTable t) {
        if (t.gpuTexture != 0) {
            GL11.glDeleteTextures(t.gpuTexture);
        }
        if (t.gpuBuffer != 0) {
            GL15.glDeleteBuffers(t.gpuBuffer);
            heldBytes -= t.gpuBytes();
        }
        t.lostGpu();
    }
}
