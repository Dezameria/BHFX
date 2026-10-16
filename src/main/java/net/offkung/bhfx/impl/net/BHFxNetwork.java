package net.offkung.bhfx.impl.net;

import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.offkung.bhfx.BHFxMod;

public final class BHFxNetwork {
    /** Strict: client and server must both have bhfx with the same protocol. */
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            BHFxMod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered;

    private BHFxNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(S2CFxStart.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFxStart::encode).decoder(S2CFxStart::decode).consumerMainThread(S2CFxStart::handle).add();
        CHANNEL.messageBuilder(S2CFxStop.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFxStop::encode).decoder(S2CFxStop::decode).consumerMainThread(S2CFxStop::handle).add();
    }

    public static <M> void send(PacketDistributor.PacketTarget target, M message) {
        CHANNEL.send(target, message);
    }
}
