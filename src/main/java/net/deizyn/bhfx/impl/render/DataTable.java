package net.deizyn.bhfx.impl.render;

import net.deizyn.bhfx.BHFxMod;
import net.deizyn.bhfx.api.client.FxData;

import java.util.Arrays;

/**
 * {@link FxData}: the values in Java plus the range written since the last upload. The GL side ({@link DataTables})
 * creates the buffer on the first bind, so a table that is never drawn (post off) never touches GL.
 */
public final class DataTable implements FxData {
    final float[] data;
    private final int size;
    /** Entries written since the last upload: {@code [dirtyFrom, dirtyTo)}, empty when from >= to. */
    private int dirtyFrom;
    private int dirtyTo;
    boolean needsFull = true;
    int gpuBuffer;
    int gpuTexture;
    long lastBindNanos;
    volatile boolean closed;

    public DataTable(int requested) {
        int clamped = Math.max(1, Math.min(MAX_SIZE, requested));
        if (clamped != requested) {
            BHFxMod.LOGGER.warn("bhfx: data table of {} entries clamped to {}", requested, clamped);
        }
        this.size = clamped;
        this.data = new float[clamped * 4];
        clearDirty();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public FxData set(int index, float x, float y, float z, float w) {
        check(index, index + 1);
        int o = index * 4;
        data[o] = x;
        data[o + 1] = y;
        data[o + 2] = z;
        data[o + 3] = w;
        dirty(index, index + 1);
        return this;
    }

    @Override
    public FxData put(int index, float[] values) {
        if (values.length % 4 != 0) {
            throw new IllegalArgumentException("FxData.put needs 4 floats per entry, got " + values.length);
        }
        int count = values.length / 4;
        if (count == 0) {
            return this;
        }
        check(index, index + count);
        System.arraycopy(values, 0, data, index * 4, values.length);
        dirty(index, index + count);
        return this;
    }

    @Override
    public FxData fill(int from, int to, float x, float y, float z, float w) {
        if (to <= from) {
            return this;
        }
        check(from, to);
        for (int o = from * 4, end = to * 4; o < end; o += 4) {
            data[o] = x;
            data[o + 1] = y;
            data[o + 2] = z;
            data[o + 3] = w;
        }
        dirty(from, to);
        return this;
    }

    @Override
    public float get(int index, int component) {
        check(index, index + 1);
        if (component < 0 || component > 3) {
            throw new IndexOutOfBoundsException("FxData component " + component + " (0..3)");
        }
        return data[index * 4 + component];
    }

    /** First entry to upload, or -1 when nothing changed (everything after a full reset). */
    int uploadFrom() {
        if (needsFull) {
            return 0;
        }
        return dirtyFrom < dirtyTo ? dirtyFrom : -1;
    }

    /** End (exclusive) of the entries to upload; only valid when {@link #uploadFrom()} is not -1. */
    int uploadTo() {
        return needsFull ? size : dirtyTo;
    }

    void uploaded() {
        needsFull = false;
        clearDirty();
    }

    /** Free the table's GPU side without losing its values: the next bind sends everything again. */
    void lostGpu() {
        gpuBuffer = 0;
        gpuTexture = 0;
        needsFull = true;
    }

    long gpuBytes() {
        return (long) size * 16;
    }

    public void close() {
        closed = true;
    }

    private void dirty(int from, int to) {
        dirtyFrom = Math.min(dirtyFrom, from);
        dirtyTo = Math.max(dirtyTo, to);
    }

    private void clearDirty() {
        dirtyFrom = Integer.MAX_VALUE;
        dirtyTo = Integer.MIN_VALUE;
    }

    private void check(int from, int to) {
        if (from < 0 || to > size) {
            throw new IndexOutOfBoundsException("FxData entries " + from + ".." + (to - 1) + " outside 0.." + (size - 1));
        }
    }

    @Override
    public String toString() {
        return "FxData[" + size + "]" + (closed ? " closed" : "") + " " + Arrays.toString(Arrays.copyOf(data, Math.min(8, data.length)));
    }
}
