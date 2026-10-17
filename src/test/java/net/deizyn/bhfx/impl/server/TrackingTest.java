package net.deizyn.bhfx.impl.server;

import net.deizyn.bhfx.api.FxSpawn;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackingTest {
    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID SAM = UUID.randomUUID();

    private static double sq(double d) {
        return d * d;
    }

    @Test
    void expiry() {
        assertEquals(1000 + 60 + Tracking.GRACE_TICKS, Tracking.expiresAt(1000, 60));
        assertEquals(1000 + Tracking.ASSUMED_DEFAULT_TICKS + Tracking.GRACE_TICKS,
                Tracking.expiresAt(1000, FxSpawn.DEFAULT_DURATION));
        assertEquals(Long.MAX_VALUE, Tracking.expiresAt(1000, FxSpawn.UNTIL_STOPPED));
    }

    /** 2026-10-17 bug: an entity gone before the first tracker pass left an UNTIL_STOPPED effect on the server forever. */
    @Test
    void entityGoneBeforeFirstPassEndsTheEffect() {
        Tracking.Follow follow = new Tracking.Follow(true, true);
        assertTrue(follow.lost(false));
    }

    @Test
    void entityNotYetTrackedIsNotLost() {
        Tracking.Follow follow = new Tracking.Follow(true, false);
        assertFalse(follow.lost(false));
        assertFalse(follow.lost(false));
        assertFalse(follow.lost(true));
        assertTrue(follow.lost(false));
    }

    @Test
    void effectsThatDoNotFollowAreNeverLost() {
        assertFalse(new Tracking.Follow(false, false).lost(false));
    }

    @Test
    void sendsOnceOnEnteringRange() {
        Tracking.Reach reach = new Tracking.Reach(16);
        assertFalse(reach.shouldSend(ALEX, sq(30)));
        assertTrue(reach.shouldSend(ALEX, sq(10)));
        assertFalse(reach.shouldSend(ALEX, sq(5)));
        assertTrue(reach.shouldSend(SAM, sq(16)));
    }

    @Test
    void walkingJustOutsideDoesNotResend() {
        Tracking.Reach reach = new Tracking.Reach(16);
        assertTrue(reach.shouldSend(ALEX, sq(10)));
        assertFalse(reach.shouldSend(ALEX, sq(16 + Tracking.LEAVE_MARGIN - 1))); // still counts as there
        assertFalse(reach.shouldSend(ALEX, sq(10)));
    }

    @Test
    void leavingFarThenComingBackResends() {
        Tracking.Reach reach = new Tracking.Reach(16);
        assertTrue(reach.shouldSend(ALEX, sq(10)));
        assertFalse(reach.shouldSend(ALEX, sq(16 + Tracking.LEAVE_MARGIN + 1)));
        assertTrue(reach.shouldSend(ALEX, sq(10)));
    }

    @Test
    void forgottenPlayersGetItAgain() {
        Tracking.Reach reach = new Tracking.Reach(16);
        assertTrue(reach.shouldSend(ALEX, sq(10)));
        reach.forget(ALEX); // respawn / relog / dimension change
        assertTrue(reach.shouldSend(ALEX, sq(10)));
    }
}
