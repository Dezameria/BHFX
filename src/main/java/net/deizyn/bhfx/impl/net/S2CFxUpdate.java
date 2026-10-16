package net.deizyn.bhfx.impl.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.deizyn.bhfx.api.FxParams;

import java.util.function.Supplier;

public record S2CFxUpdate(int handleId, FxParams changed) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(handleId);
        changed.write(buf);
    }

    public static S2CFxUpdate decode(FriendlyByteBuf buf) {
        return new S2CFxUpdate(buf.readVarInt(), FxParams.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.deizyn.bhfx.impl.client.ClientPacketHandler.update(this));
        ctx.get().setPacketHandled(true);
    }
}
