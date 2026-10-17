package net.deizyn.bhfx.api;

import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable set of typed effect parameters. */
public final class FxParams {
    public static final FxParams EMPTY = new FxParams(Map.of());
    private static final int MAX_ENTRIES = 64;

    private final Map<String, Entry<?>> entries;

    private FxParams(Map<String, Entry<?>> entries) {
        this.entries = entries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static <T> FxParams of(FxParam<T> key, T value) {
        return builder().put(key, value).build();
    }

    /**
     * The value for {@code key}, or {@code fallback} when absent or stored with another type. An int also fills a
     * float key, and a whole float an int key (values typed in commands rarely match the effect's key exactly).
     */
    public <T> T get(FxParam<T> key, T fallback) {
        Object value = value(key);
        return value == null ? fallback : key.type().javaType.cast(value);
    }

    public boolean has(FxParam<?> key) {
        return value(key) != null;
    }

    @Nullable
    private Object value(FxParam<?> key) {
        Entry<?> entry = entries.get(key.name());
        if (entry == null) {
            return null;
        }
        if (entry.type == key.type()) {
            return entry.value;
        }
        if (entry.type == FxParam.Type.INT && key.type() == FxParam.Type.FLOAT) {
            return ((Integer) entry.value).floatValue();
        }
        if (entry.type == FxParam.Type.FLOAT && key.type() == FxParam.Type.INT) {
            float f = (Float) entry.value;
            return f == Math.rint(f) ? (Object) (int) f : null;
        }
        return null;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Values of {@code other} replace values of this set with the same name. */
    public FxParams merge(FxParams other) {
        if (other.isEmpty()) {
            return this;
        }
        Map<String, Entry<?>> merged = new LinkedHashMap<>(entries);
        merged.putAll(other.entries);
        return new FxParams(Collections.unmodifiableMap(merged));
    }

    @ApiStatus.Internal
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Map.Entry<String, Entry<?>> e : entries.entrySet()) {
            buf.writeUtf(e.getKey(), 64);
            e.getValue().write(buf);
        }
    }

    @ApiStatus.Internal
    public static FxParams read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n == 0) {
            return EMPTY;
        }
        if (n < 0 || n > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many bhfx params: " + n);
        }
        Map<String, Entry<?>> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String name = buf.readUtf(64);
            map.put(name, Entry.read(buf));
        }
        return new FxParams(Collections.unmodifiableMap(map));
    }

    @Override
    public String toString() {
        return "FxParams" + entries.keySet();
    }

    private record Entry<T>(FxParam.Type<T> type, T value) {
        void write(FriendlyByteBuf buf) {
            buf.writeByte(type.id);
            type.writer.write(buf, value);
        }

        static Entry<?> read(FriendlyByteBuf buf) {
            return readTyped(FxParam.Type.byId(buf.readByte()), buf);
        }

        private static <T> Entry<T> readTyped(FxParam.Type<T> type, FriendlyByteBuf buf) {
            return new Entry<>(type, type.reader.read(buf));
        }
    }

    public static final class Builder {
        private final Map<String, Entry<?>> map = new LinkedHashMap<>();

        private Builder() {
        }

        public <T> Builder put(FxParam<T> key, T value) {
            if (map.size() >= MAX_ENTRIES && !map.containsKey(key.name())) {
                throw new IllegalStateException("bhfx params are limited to " + MAX_ENTRIES + " entries");
            }
            map.put(key.name(), new Entry<>(key.type(), value));
            return this;
        }

        public Builder putAll(FxParams params) {
            map.putAll(params.entries);
            return this;
        }

        public FxParams build() {
            return map.isEmpty() ? EMPTY : new FxParams(Collections.unmodifiableMap(new LinkedHashMap<>(map)));
        }
    }
}
