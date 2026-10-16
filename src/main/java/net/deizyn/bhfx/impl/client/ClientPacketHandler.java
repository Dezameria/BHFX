package net.deizyn.bhfx.impl.client;

import net.deizyn.bhfx.api.client.FxInit;
import net.deizyn.bhfx.impl.net.S2CFxStart;
import net.deizyn.bhfx.impl.net.S2CFxStop;
import net.deizyn.bhfx.impl.net.S2CFxUpdate;
import net.deizyn.bhfx.impl.net.S2CScreenPulse;

public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void start(S2CFxStart p) {
        ClientFxManager.start(new FxInit(p.fxId(), p.handleId(), p.origin(), p.target(), p.casterId(), p.followId(),
                p.follow(), p.seed(), p.duration(), p.startAge(), p.params()));
    }

    public static void stop(S2CFxStop p) {
        ClientFxManager.stop(p.handleId(), p.mode());
    }

    public static void pulse(S2CScreenPulse p) {
        ClientFxManager.pulse(p.at(), p.range(), p.pulse());
    }

    public static void update(S2CFxUpdate p) {
        ClientFxManager.update(p.handleId(), p.changed());
    }
}
