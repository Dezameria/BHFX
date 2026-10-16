package net.deizyn.bhfx.impl.net;

import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.deizyn.bhfx.BHFxMod;

public final class BHFxNetwork {
    /** Strict: client and server must both have bhfx with the same protocol. 2 = start batches and screen pulses (0.3). */
    private static final String PROTOCOL = "2";

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
        CHANNEL.messageBuilder(S2CFxUpdate.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFxUpdate::encode).decoder(S2CFxUpdate::decode).consumerMainThread(S2CFxUpdate::handle).add();
        CHANNEL.messageBuilder(S2CFxStartBatch.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFxStartBatch::encode).decoder(S2CFxStartBatch::decode).consumerMainThread(S2CFxStartBatch::handle).add();
        CHANNEL.messageBuilder(S2CScreenPulse.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CScreenPulse::encode).decoder(S2CScreenPulse::decode).consumerMainThread(S2CScreenPulse::handle).add();
    }

    public static <M> void send(PacketDistributor.PacketTarget target, M message) {
        CHANNEL.send(target, message);
    }
}
