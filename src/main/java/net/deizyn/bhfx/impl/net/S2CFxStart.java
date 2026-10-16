package net.deizyn.bhfx.impl.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.deizyn.bhfx.api.Follow;
import net.deizyn.bhfx.api.FxParams;

import java.util.function.Supplier;

public record S2CFxStart(int handleId, ResourceLocation fxId, Vec3 origin, Vec3 target, int casterId, int followId,
                         Follow follow, long seed, int duration, int startAge, FxParams params) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(handleId);
        buf.writeResourceLocation(fxId);
        writeVec3(buf, origin);
        writeVec3(buf, target);
        buf.writeVarInt(casterId + 1);
        buf.writeVarInt(followId + 1);
        buf.writeEnum(follow);
        buf.writeLong(seed);
        buf.writeInt(duration);
        buf.writeVarInt(startAge);
        params.write(buf);
    }

    public static S2CFxStart decode(FriendlyByteBuf buf) {
        return new S2CFxStart(buf.readVarInt(), buf.readResourceLocation(), readVec3(buf), readVec3(buf),
                buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readEnum(Follow.class), buf.readLong(), buf.readInt(),
                buf.readVarInt(), FxParams.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.deizyn.bhfx.impl.client.ClientPacketHandler.start(this));
        ctx.get().setPacketHandled(true);
    }

    private static void writeVec3(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec3(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
