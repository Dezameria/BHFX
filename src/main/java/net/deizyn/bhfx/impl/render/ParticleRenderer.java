package net.deizyn.bhfx.impl.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * GPU side of particle batches. Each batch owns one buffer texture (GL 3.1, so no instanced vertex attributes are
 * needed) holding its ring; only the slots spawned since the last upload are sent. Drawn as attribute-less instanced
 * triangle strips: four vertices per slot, positions computed in the vertex shader.
 */
final class ParticleRenderer {
    static final int UNIT_DATA = 5;
    /** Batches not drawn for this long give their GPU buffer back (a re-draw uploads everything again). */
    private static final long IDLE_NANOS = 5_000_000_000L;

    private final GlStateGuard guard;
    private final List<ParticleBatch> resident = new ArrayList<>();
    private FloatBuffer scratch;
    private long heldBytes;

    ParticleRenderer(GlStateGuard guard) {
        this.guard = guard;
    }

    long heldBytes() {
        return heldBytes + (scratch != null ? (long) scratch.capacity() * 4 : 0);
    }

    int residentCount() {
        return resident.size();
    }

    /** Send what changed, then bind the batch's data on {@link #UNIT_DATA} for {@code program}. */
    void bind(ParticleBatch b, ShaderProgram program) {
        if (b.gpuBuffer == 0) {
            b.gpuBuffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, b.gpuBuffer);
            long bytes = (long) b.capacityValue() * ParticleBatch.FLOATS * 4;
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, bytes, GL15.GL_DYNAMIC_DRAW);
            b.gpuTexture = GL11.glGenTextures();
            guard.touchUnit(UNIT_DATA);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, b.gpuTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, b.gpuBuffer);
            b.needsFull = true;
            heldBytes += bytes;
            resident.add(b);
        }
        upload(b);
        guard.touchUnit(UNIT_DATA);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, b.gpuTexture);
        int loc = program.location("PData");
        if (loc >= 0) {
            GL20.glUniform1i(loc, UNIT_DATA);
        }
        b.lastDrawNanos = System.nanoTime();
    }

    /** Leave no buffer texture bound (Minecraft never uses them, but keep the state clean). */
    void unbind() {
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
    }

    void draw(ParticleBatch b) {
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, b.filled);
    }

    private void upload(ParticleBatch b) {
        b.updateStats(); // spawns made after the batch was added this frame still count for its cull sphere and end
        int count = b.needsFull ? b.filled : b.pending;
        int start = b.needsFull ? 0 : b.pendingStart();
        b.needsFull = false;
        b.pending = 0;
        if (count <= 0) {
            return;
        }
        if (scratch == null) {
            scratch = MemoryUtil.memAllocFloat(ParticleBatch.MAX_CAPACITY * ParticleBatch.FLOATS);
        }
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, b.gpuBuffer);
        int cap = b.capacityValue();
        int first = Math.min(count, cap - start);
        put(b, start, first);
        if (count > first) {
            put(b, 0, count - first); // the pending run wrapped round the ring
        }
    }

    private void put(ParticleBatch b, int slot, int slots) {
        int offset = slot * ParticleBatch.FLOATS;
        int length = slots * ParticleBatch.FLOATS;
        scratch.clear();
        scratch.put(b.data, offset, length).flip();
        GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, (long) offset * 4, scratch);
    }

    /** Free batches whose effect ended or that were not drawn for a while. */
    void sweep(long now) {
        Iterator<ParticleBatch> it = resident.iterator();
        while (it.hasNext()) {
            ParticleBatch b = it.next();
            if (b.closed || now - b.lastDrawNanos > IDLE_NANOS) {
                free(b);
                it.remove();
            }
        }
        if (resident.isEmpty() && scratch != null) {
            MemoryUtil.memFree(scratch);
            scratch = null;
        }
    }

    void releaseAll() {
        for (ParticleBatch b : resident) {
            free(b);
        }
        resident.clear();
        heldBytes = 0;
        if (scratch != null) {
            MemoryUtil.memFree(scratch);
            scratch = null;
        }
    }

    private void free(ParticleBatch b) {
        if (b.gpuTexture != 0) {
            GL11.glDeleteTextures(b.gpuTexture);
        }
        if (b.gpuBuffer != 0) {
            GL15.glDeleteBuffers(b.gpuBuffer);
            heldBytes -= (long) b.capacityValue() * ParticleBatch.FLOATS * 4;
        }
        b.gpuTexture = 0;
        b.gpuBuffer = 0;
        b.needsFull = true;
    }
}
