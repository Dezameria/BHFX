package net.deizyn.bhfx.impl.net;

import io.netty.buffer.Unpooled;
import net.deizyn.bhfx.api.Follow;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.StopMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /bhfx_play net: message sizes and the report. */
class NetMeterTest {
    private static S2CFxStart start(int id) {
        return new S2CFxStart(id, new ResourceLocation("bhfx", "test_flow"), new Vec3(1, 2, 3), new Vec3(1, 2, 3),
                -1, -1, Follow.POSITION, 99L, -1, 0, FxParams.EMPTY);
    }

    @Test
    void sizeIsTheEncodedMessagePlusOverhead() {
        S2CFxStart m = start(5);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        m.encode(buf);
        assertEquals(buf.writerIndex() + NetMeter.OVERHEAD, NetMeter.sizeOf(m));
        assertEquals(0, NetMeter.sizeOf("not a bhfx message"));
        assertTrue(NetMeter.sizeOf(new S2CFxStop(5, StopMode.CANCEL)) < NetMeter.sizeOf(m));
    }

    @Test
    void startsCountBatches() {
        List<S2CFxStart> three = new ArrayList<>(List.of(start(1), start(2), start(3)));
        assertEquals(1, NetMeter.starts(start(1)));
        assertEquals(3, NetMeter.starts(new S2CFxStartBatch(three)));
        assertEquals(0, NetMeter.starts(new S2CFxStop(1, StopMode.CANCEL)));
    }

    @Test
    void reportGivesRatesPerSecondAndTheBusiestPlayer() {
        NetMeter.Count a = new NetMeter.Count("alice");
        a.bytes = 10 * 1024;
        a.packets = 20;
        a.starts = 40;
        NetMeter.Count b = new NetMeter.Count("bob");
        b.bytes = 30 * 1024;
        b.packets = 20;
        b.starts = 60;
        List<String> lines = NetMeter.report(new ArrayList<>(List.of(a, b)), 200, 3, 1, 12); // 10 s
        String all = String.join("\n", lines);
        assertTrue(all.contains("over 10 s"), all);
        assertTrue(all.contains("total: 4.0 KB/s, 4.0 packets/s, 10.0 starts/s, to 2 player(s)"), all);
        assertTrue(all.contains("avg 2.00 KB/s, max 3.00 KB/s (bob)"), all);
        assertTrue(all.indexOf("bob") < all.indexOf("alice"), all);
        assertTrue(all.contains("3 by maxStartsPerTick, 1 by maxActivePerCaster; 12 effect(s) running now"), all);
    }

    @Test
    void emptyReportSaysSo() {
        List<String> lines = NetMeter.report(new ArrayList<>(), 20, 0, 0, 0);
        assertTrue(String.join("\n", lines).contains("nothing sent"));
        assertFalse(NetMeter.running());
    }
}
