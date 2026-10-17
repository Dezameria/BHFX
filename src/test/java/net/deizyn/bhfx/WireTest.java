package net.deizyn.bhfx;

import io.netty.buffer.Unpooled;
import net.deizyn.bhfx.api.FxParam;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.ScreenPulse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What goes over the network must come back unchanged (protocol 2). */
class WireTest {
    private static FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void paramsRoundTripEveryType() {
        FxParam<Float> f = FxParam.f("f");
        FxParam<Integer> i = FxParam.i("i");
        FxParam<Boolean> b = FxParam.b("b");
        FxParam<Vec3> v = FxParam.vec3("v");
        FxParam<Integer> c = FxParam.color("c");
        FxParam<float[]> fs = FxParam.floats("fs");
        FxParam<List<Vec3>> ps = FxParam.points("ps");
        FxParam<Integer> e = FxParam.entity("e");
        FxParams sent = FxParams.builder()
                .put(f, 1.5f).put(i, -7).put(b, true).put(v, new Vec3(1, 2, 3)).put(c, 0xFF40C0)
                .put(fs, new float[]{0.25f, 4f}).put(ps, List.of(new Vec3(0, 1, 0), new Vec3(5, 6, 7))).put(e, 42)
                .build();

        FriendlyByteBuf out = buf();
        sent.write(out);
        FxParams got = FxParams.read(out);

        assertEquals(1.5f, got.get(f, 0f));
        assertEquals(-7, got.get(i, 0));
        assertTrue(got.get(b, false));
        assertEquals(new Vec3(1, 2, 3), got.get(v, Vec3.ZERO));
        assertEquals(0xFF40C0, got.get(c, 0));
        assertArrayEquals(new float[]{0.25f, 4f}, got.get(fs, new float[0]));
        assertEquals(List.of(new Vec3(0, 1, 0), new Vec3(5, 6, 7)), got.get(ps, List.of()));
        assertEquals(42, got.get(e, 0));
        assertEquals(0, out.readableBytes());
    }

    @Test
    void emptyParamsReadBackAsEmpty() {
        FriendlyByteBuf out = buf();
        FxParams.EMPTY.write(out);
        assertSame(FxParams.EMPTY, FxParams.read(out));
    }

    @Test
    void oversizedListsAreCutOnWrite() {
        float[] many = new float[FxParam.MAX_FLOATS + 10];
        List<Vec3> points = new ArrayList<>();
        for (int n = 0; n < FxParam.MAX_POINTS + 5; n++) {
            points.add(new Vec3(n, 0, 0));
        }
        FxParams sent = FxParams.builder().put(FxParam.floats("fs"), many).put(FxParam.points("ps"), points).build();
        FriendlyByteBuf out = buf();
        sent.write(out);
        FxParams got = FxParams.read(out);
        assertEquals(FxParam.MAX_FLOATS, got.get(FxParam.floats("fs"), new float[0]).length);
        assertEquals(FxParam.MAX_POINTS, got.get(FxParam.points("ps"), List.of()).size());
    }

    @Test
    void hostileInputIsRejected() {
        FriendlyByteBuf tooMany = buf();
        tooMany.writeVarInt(65);
        assertThrows(IllegalArgumentException.class, () -> FxParams.read(tooMany));

        FriendlyByteBuf badType = buf();
        badType.writeVarInt(1);
        badType.writeUtf("x");
        badType.writeByte(99);
        assertThrows(IllegalArgumentException.class, () -> FxParams.read(badType));
    }

    @Test
    void builderLimitsEntries() {
        FxParams.Builder builder = FxParams.builder();
        for (int n = 0; n < 64; n++) {
            builder.put(FxParam.i("p" + n), n);
        }
        builder.put(FxParam.i("p0"), 1); // replacing an existing name is fine
        assertThrows(IllegalStateException.class, () -> builder.put(FxParam.i("one_more"), 0));
    }

    @Test
    void screenPulseRoundTripsAndClamps() {
        ScreenPulse sent = ScreenPulse.of()
                .shake(3f)                      // clamped to 1
                .flash(0x12FFF0D8, 0.7f, 8)     // colour masked to 24 bits
                .vignette(0x500808, 0.6f, 500)  // ticks clamped to 200
                .aberration(0.8f, 12)
                .zoomBlur(0.3f, 0)              // ticks clamped to 1
                .desaturate(0.5f, 20);
        FriendlyByteBuf out = buf();
        sent.write(out);
        ScreenPulse got = ScreenPulse.read(out);

        assertEquals(1f, got.shake());
        assertEquals(0xFFF0D8, got.flashRgb());
        assertEquals(0.7f, got.flash());
        assertEquals(8, got.flashTicks());
        assertEquals(200, got.vignetteTicks());
        assertEquals(0.8f, got.aberration());
        assertEquals(1, got.zoomBlurTicks());
        assertEquals(0.5f, got.desaturate());
        assertEquals(200, got.length());
        assertEquals(0, out.readableBytes());
    }

    @Test
    void emptyPulseIsEmpty() {
        assertTrue(ScreenPulse.of().isEmpty());
    }
}
