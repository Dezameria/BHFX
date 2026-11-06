package net.deizyn.bhfx.impl.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which known effects are built and drawn on this client (scale plan phase 1). Pure, for tests.
 * <ul>
 *   <li>Pinned effects (local ones, your own, effects that follow you) are always shown.</li>
 *   <li>Every effect within the guaranteed radius is shown, nearest first if even those don't fit the cap.</li>
 *   <li>The rest share what is left of the cap by distance, out to the cull distance.</li>
 * </ul>
 * Only the distance from the local player counts, never the view direction, so turning the camera swaps nothing.
 * Shown effects get a margin (they stay until clearly past an edge) and a minimum hold time, so an effect near an edge
 * doesn't blink in and out.
 */
public final class FxSelection {
    /** One known effect as the selection sees it. {@link #show} is the output. */
    public static final class Candidate {
        final Object key;
        final boolean pinned;
        final double distance;
        final boolean shown;
        final long shownSince;
        boolean show;

        public Candidate(Object key, boolean pinned, double distance, boolean shown, long shownSince) {
            this.key = key;
            this.pinned = pinned;
            this.distance = distance;
            this.shown = shown;
            this.shownSince = shownSince;
        }

        public Object key() {
            return key;
        }

        public boolean show() {
            return show;
        }
    }

    /**
     * @param radius     guaranteed radius (blocks)
     * @param margin     extra distance a shown effect keeps before it counts as outside, and its head start in the order
     * @param cap        most effects shown (pinned and held ones may go over it)
     * @param cull       nothing farther is shown
     * @param holdTicks  a shown effect stays at least this long
     */
    public record Rules(double radius, double margin, int cap, double cull, int holdTicks) {
    }

    private FxSelection() {
    }

    /** Sets {@link Candidate#show} on every candidate. */
    public static void choose(List<Candidate> all, Rules rules, long now) {
        List<Candidate> inner = new ArrayList<>();
        List<Candidate> outer = new ArrayList<>();
        int used = 0;
        for (Candidate c : all) {
            c.show = false;
            double slack = c.shown ? rules.margin() : 0.0;
            if (c.pinned) {
                c.show = true;
                used++;
            } else if (c.shown && now - c.shownSince < rules.holdTicks() && c.distance <= rules.cull() + slack) {
                c.show = true; // held: just appeared, don't take it away yet
                used++;
            } else if (c.distance <= rules.radius() + slack) {
                inner.add(c);
            } else if (c.distance <= rules.cull() + slack) {
                outer.add(c);
            }
        }
        Comparator<Candidate> order = Comparator.comparingDouble(c -> c.distance - (c.shown ? rules.margin() : 0.0));
        inner.sort(order);
        outer.sort(order);
        for (Candidate c : inner) {
            if (used < rules.cap()) {
                c.show = true;
                used++;
            }
        }
        for (Candidate c : outer) {
            if (used < rules.cap()) {
                c.show = true;
                used++;
            }
        }
    }
}
