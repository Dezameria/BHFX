package net.deizyn.bhfx.impl.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Measures bhfx's GPU time with a ring of GL_TIMESTAMP query pairs, read a few frames later only once the result is
 * available — so measuring never makes the CPU wait for the GPU (no glFinish).
 * <p>
 * Timestamps rather than GL_TIME_ELAPSED: only one TIME_ELAPSED query may be active at a time, and Minecraft keeps one
 * running around the whole frame while F3 is open (its GPU utilisation readout). Nesting ours inside it failed and
 * ended Minecraft's query early. Timestamp queries never conflict.
 */
final class GpuTimer {
    private static final int RING = 4;

    private final int[] starts = new int[RING];
    private final int[] ends = new int[RING];
    private final boolean[] pending = new boolean[RING];
    private int head;
    private boolean active;
    private Boolean supported;

    private boolean supported() {
        if (supported == null) {
            supported = GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_timer_query;
        }
        return supported;
    }

    void begin() {
        if (!supported()) {
            return;
        }
        if (starts[0] == 0) {
            GL15.glGenQueries(starts);
            GL15.glGenQueries(ends);
        }
        if (pending[head]) {
            return; // the GPU is more than RING frames behind; skip measuring this frame
        }
        GL33.glQueryCounter(starts[head], GL33.GL_TIMESTAMP);
        active = true;
    }

    void end() {
        if (!active) {
            return;
        }
        GL33.glQueryCounter(ends[head], GL33.GL_TIMESTAMP);
        pending[head] = true;
        head = (head + 1) % RING;
        active = false;
    }

    /** Milliseconds of the newest finished measurement, or -1 if none finished since the last poll. */
    double poll() {
        if (starts[0] == 0) {
            return -1;
        }
        double latest = -1;
        for (int k = 0; k < RING; k++) {
            int i = (head + k) % RING; // oldest first
            // the end stamp is written after the start stamp, so its availability covers both
            if (pending[i] && GL15.glGetQueryObjecti(ends[i], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                long t0 = GL33.glGetQueryObjecti64(starts[i], GL15.GL_QUERY_RESULT);
                long t1 = GL33.glGetQueryObjecti64(ends[i], GL15.GL_QUERY_RESULT);
                latest = Math.max(0L, t1 - t0) / 1.0e6;
                pending[i] = false;
            }
        }
        return latest;
    }

    void delete() {
        if (active) {
            end();
        }
        if (starts[0] != 0) {
            GL15.glDeleteQueries(starts);
            GL15.glDeleteQueries(ends);
            java.util.Arrays.fill(starts, 0);
            java.util.Arrays.fill(ends, 0);
        }
        java.util.Arrays.fill(pending, false);
        head = 0;
    }
}
