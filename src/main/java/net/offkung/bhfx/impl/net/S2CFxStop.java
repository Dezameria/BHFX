package net.offkung.bhfx.impl.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.offkung.bhfx.api.StopMode;

import java.util.function.Supplier;

public record S2CFxStop(int handleId, StopMode mode) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(handleId);
        buf.writeEnum(mode);
    }

    public static S2CFxStop decode(FriendlyByteBuf buf) {
        return new S2CFxStop(buf.readVarInt(), buf.readEnum(StopMode.class));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.offkung.bhfx.impl.client.ClientPacketHandler.stop(this));
        ctx.get().setPacketHandled(true);
    }
}
