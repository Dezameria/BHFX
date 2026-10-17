package net.deizyn.bhfx.api;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A typed parameter key. Create keys once as constants and reuse them on both sides:
 * <pre>{@code static final FxParam<Float> POWER = FxParam.f("power");}</pre>
 */
public final class FxParam<T> {
    public static final int MAX_FLOATS = 64;
    public static final int MAX_POINTS = 32;

    public static final FxParam<Integer> SPELL_LEVEL = i("spell_level");
    public static final FxParam<Float> RADIUS = f("radius");
    public static final FxParam<Integer> COLOR = color("color");

    private final String name;
    private final Type<T> type;

    private FxParam(String name, Type<T> type) {
        this.name = Objects.requireNonNull(name);
        this.type = type;
    }

    public static FxParam<Float> f(String name) { return new FxParam<>(name, Type.FLOAT); }
    public static FxParam<Integer> i(String name) { return new FxParam<>(name, Type.INT); }
    public static FxParam<Boolean> b(String name) { return new FxParam<>(name, Type.BOOL); }
    public static FxParam<Vec3> vec3(String name) { return new FxParam<>(name, Type.VEC3); }
    /** Colour as 0xRRGGBB. */
    public static FxParam<Integer> color(String name) { return new FxParam<>(name, Type.COLOR); }
    /** Up to {@link #MAX_FLOATS} floats. */
    public static FxParam<float[]> floats(String name) { return new FxParam<>(name, Type.FLOATS); }
    /** Up to {@link #MAX_POINTS} world positions. */
    public static FxParam<List<Vec3>> points(String name) { return new FxParam<>(name, Type.POINTS); }
    public static FxParam<Integer> entity(String name) { return new FxParam<>(name, Type.ENTITY); }

    public String name() {
        return name;
    }

    Type<T> type() {
        return type;
    }

    /** Wire codec for one value type. Ids are part of the network protocol: never reorder. */
    static final class Type<T> {
        static final Type<Float> FLOAT = new Type<>(0, (b, v) -> b.writeFloat(v), FriendlyByteBuf::readFloat, Float.class);
        static final Type<Integer> INT = new Type<>(1, FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt, Integer.class);
        static final Type<Boolean> BOOL = new Type<>(2, FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean, Boolean.class);
        static final Type<Vec3> VEC3 = new Type<>(3, Type::writeVec3, Type::readVec3, Vec3.class);
        static final Type<Integer> COLOR = new Type<>(4, (b, v) -> b.writeInt(v), FriendlyByteBuf::readInt, Integer.class);
        static final Type<float[]> FLOATS = new Type<>(5, Type::writeFloats, Type::readFloats, float[].class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        static final Type<List<Vec3>> POINTS = new Type<>(6, Type::writePoints, Type::readPoints, (Class) List.class);
        static final Type<Integer> ENTITY = new Type<>(7, FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt, Integer.class);

        private static final Type<?>[] BY_ID = {FLOAT, INT, BOOL, VEC3, COLOR, FLOATS, POINTS, ENTITY};

        final int id;
        final Writer<T> writer;
        final Reader<T> reader;
        final Class<T> javaType;

        private Type(int id, Writer<T> writer, Reader<T> reader, Class<T> javaType) {
            this.id = id;
            this.writer = writer;
            this.reader = reader;
            this.javaType = javaType;
        }

        static Type<?> byId(int id) {
            if (id < 0 || id >= BY_ID.length) {
                throw new IllegalArgumentException("Unknown bhfx param type " + id);
            }
            return BY_ID[id];
        }

        interface Writer<T> { void write(FriendlyByteBuf buf, T value); }
        interface Reader<T> { T read(FriendlyByteBuf buf); }

        private static void writeVec3(FriendlyByteBuf buf, Vec3 v) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }

        private static Vec3 readVec3(FriendlyByteBuf buf) {
            return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        private static void writeFloats(FriendlyByteBuf buf, float[] values) {
            int n = Math.min(values.length, MAX_FLOATS);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeFloat(values[i]);
            }
        }

        private static float[] readFloats(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_FLOATS) {
                throw new IllegalArgumentException("Too many floats: " + n);
            }
            float[] values = new float[n];
            for (int i = 0; i < n; i++) {
                values[i] = buf.readFloat();
            }
            return values;
        }

        private static void writePoints(FriendlyByteBuf buf, List<Vec3> points) {
            int n = Math.min(points.size(), MAX_POINTS);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                writeVec3(buf, points.get(i));
            }
        }

        private static List<Vec3> readPoints(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_POINTS) {
                throw new IllegalArgumentException("Too many points: " + n);
            }
            List<Vec3> points = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                points.add(readVec3(buf));
            }
            return List.copyOf(points);
        }
    }
}
