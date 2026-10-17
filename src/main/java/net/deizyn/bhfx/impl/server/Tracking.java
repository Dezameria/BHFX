package net.deizyn.bhfx.impl.server;

import net.deizyn.bhfx.api.FxSpawn;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** The tracker's rules, kept free of {@code ServerLevel} so they can be unit tested. */
final class Tracking {
    /** Expiry used for effects that rely on the client-side default duration. */
    static final int ASSUMED_DEFAULT_TICKS = 200;
    static final int GRACE_TICKS = 40;
    /** Players must move this far past the range before they count as having left (avoids resend flapping). */
    static final double LEAVE_MARGIN = 16.0;

    private Tracking() {
    }

    /** Game time after which the server forgets the effect. */
    static long expiresAt(long startTick, int duration) {
        if (duration == FxSpawn.UNTIL_STOPPED) {
            return Long.MAX_VALUE;
        }
        return startTick + (duration == FxSpawn.DEFAULT_DURATION ? ASSUMED_DEFAULT_TICKS : duration) + GRACE_TICKS;
    }

    /**
     * Whether an effect that follows an entity has lost it. Only an entity that was seen can be lost: a late joiner may
     * learn about the effect before it tracks the entity. Seeing it at start counts, so an entity that vanishes before
     * the first tracker pass still ends the effect.
     */
    static final class Follow {
        private final boolean follows;
        private boolean seen;

        Follow(boolean follows, boolean presentAtStart) {
            this.follows = follows;
            this.seen = follows && presentAtStart;
        }

        boolean lost(boolean presentNow) {
            if (!follows) {
                return false;
            }
            if (presentNow) {
                seen = true;
                return false;
            }
            return seen;
        }
    }

    /** Which players already got a persistent effect; they get it again only after leaving well past its range. */
    static final class Reach {
        private final double enter2;
        private final double leave2;
        private final Set<UUID> sentTo = new HashSet<>();

        Reach(double range) {
            this.enter2 = range * range;
            this.leave2 = (range + LEAVE_MARGIN) * (range + LEAVE_MARGIN);
        }

        /** Called on every pass with the player's squared distance; true when the effect must be sent now. */
        boolean shouldSend(UUID player, double distanceSqr) {
            if (sentTo.contains(player)) {
                if (distanceSqr > leave2) {
                    sentTo.remove(player);
                }
                return false;
            }
            if (distanceSqr <= enter2) {
                sentTo.add(player);
                return true;
            }
            return false;
        }

        /** The player's client lost its effects (logout, respawn, dimension change). */
        void forget(UUID player) {
            sentTo.remove(player);
        }
    }
}
