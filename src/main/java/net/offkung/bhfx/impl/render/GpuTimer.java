package net.offkung.bhfx.impl.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Measures bhfx's GPU time with a ring of GL_TIME_ELAPSED queries, read a few frames later only once the result is
 * available — so measuring never makes the CPU wait for the GPU (no glFinish).
 */
final class GpuTimer {
    private static final int RING = 4;

    private final int[] queries = new int[RING];
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
        if (queries[0] == 0) {
            GL15.glGenQueries(queries);
        }
        if (pending[head]) {
            return; // the GPU is more than RING frames behind; skip measuring this frame
        }
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, queries[head]);
        active = true;
    }

    void end() {
        if (!active) {
            return;
        }
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        pending[head] = true;
        head = (head + 1) % RING;
        active = false;
    }

    /** Milliseconds of the newest finished measurement, or -1 if none finished since the last poll. */
    double poll() {
        if (queries[0] == 0) {
            return -1;
        }
        double latest = -1;
        for (int k = 0; k < RING; k++) {
            int i = (head + k) % RING; // oldest first
            if (pending[i] && GL15.glGetQueryObjecti(queries[i], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                latest = GL33.glGetQueryObjecti64(queries[i], GL15.GL_QUERY_RESULT) / 1.0e6;
                pending[i] = false;
            }
        }
        return latest;
    }

    void delete() {
        if (active) {
            end();
        }
        if (queries[0] != 0) {
            GL15.glDeleteQueries(queries);
            java.util.Arrays.fill(queries, 0);
        }
        java.util.Arrays.fill(pending, false);
        head = 0;
    }
}
