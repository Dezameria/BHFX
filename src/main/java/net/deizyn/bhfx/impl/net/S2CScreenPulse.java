package net.deizyn.bhfx.impl.net;

import net.deizyn.bhfx.api.ScreenPulse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** A screen pulse; {@code at == null} means full strength regardless of where the player is. */
public record S2CScreenPulse(@Nullable Vec3 at, float range, ScreenPulse pulse) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(at != null);
        if (at != null) {
            buf.writeDouble(at.x);
            buf.writeDouble(at.y);
            buf.writeDouble(at.z);
            buf.writeFloat(range);
        }
        pulse.write(buf);
    }

    public static S2CScreenPulse decode(FriendlyByteBuf buf) {
        Vec3 at = null;
        float range = 0f;
        if (buf.readBoolean()) {
            at = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            range = buf.readFloat();
        }
        return new S2CScreenPulse(at, range, ScreenPulse.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.deizyn.bhfx.impl.client.ClientPacketHandler.pulse(this));
        ctx.get().setPacketHandled(true);
    }
}
