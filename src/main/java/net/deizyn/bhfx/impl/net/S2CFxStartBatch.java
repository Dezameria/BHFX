package net.deizyn.bhfx.impl.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Several starts for one player from the same tick, in order. At most {@link #MAX} per packet. */
public record S2CFxStartBatch(List<S2CFxStart> starts) {
    /** With params capped at 32 KB each this stays well under the 1 MB payload limit. */
    public static final int MAX = 16;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(starts.size());
        for (S2CFxStart start : starts) {
            start.encode(buf);
        }
    }

    public static S2CFxStartBatch decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX) {
            throw new IllegalArgumentException("bhfx batch of " + n + " starts");
        }
        List<S2CFxStart> starts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            starts.add(S2CFxStart.decode(buf));
        }
        return new S2CFxStartBatch(starts);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            for (S2CFxStart start : starts) {
                net.deizyn.bhfx.impl.client.ClientPacketHandler.start(start);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
