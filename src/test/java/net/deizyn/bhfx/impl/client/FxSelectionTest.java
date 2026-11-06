package net.deizyn.bhfx.impl.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which effects are built: pinned always, the guaranteed radius next, then the rest by distance, without blinking. */
class FxSelectionTest {
    private static final FxSelection.Rules RULES = new FxSelection.Rules(48, 8, 4, 256, 40);

    private static FxSelection.Candidate c(String key, double distance) {
        return new FxSelection.Candidate(key, false, distance, false, 0);
    }

    private static FxSelection.Candidate shown(String key, double distance, long since) {
        return new FxSelection.Candidate(key, false, distance, true, since);
    }

    private static List<String> chosen(List<FxSelection.Candidate> all) {
        List<String> out = new ArrayList<>();
        for (FxSelection.Candidate x : all) {
            if (x.show()) {
                out.add((String) x.key());
            }
        }
        return out;
    }

    @Test
    void nearestFirstUpToTheCap() {
        List<FxSelection.Candidate> all = List.of(c("far", 200), c("a", 10), c("b", 60), c("c", 30), c("d", 100), c("e", 5));
        FxSelection.choose(all, RULES, 1000);
        assertEquals(List.of("a", "b", "c", "e"), chosen(all).stream().sorted().toList());
    }

    @Test
    void pinnedAlwaysShownEvenPastTheCapAndCull() {
        List<FxSelection.Candidate> all = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            all.add(new FxSelection.Candidate("own" + i, true, 500, false, 0));
        }
        all.add(c("near", 1));
        FxSelection.choose(all, RULES, 1000);
        assertEquals(6, chosen(all).size());
        assertFalse(all.get(6).show(), "the cap is used up by pinned effects");
    }

    @Test
    void nothingPastTheCullDistance() {
        List<FxSelection.Candidate> all = List.of(c("x", 300));
        FxSelection.choose(all, RULES, 1000);
        assertFalse(all.get(0).show());
    }

    @Test
    void shownEffectKeepsItsPlaceAgainstSlightlyNearerNewOnes() {
        // 4 shown at ~100 blocks; 4 new ones a little nearer must not push them out (margin 8)
        List<FxSelection.Candidate> all = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            all.add(shown("old" + i, 100 + i, 0));
        }
        for (int i = 0; i < 4; i++) {
            all.add(c("new" + i, 95 + i));
        }
        FxSelection.choose(all, RULES, 1000);
        assertEquals(List.of("old0", "old1", "old2", "old3"), chosen(all));
    }

    @Test
    void clearlyNearerNewOnesDoReplaceFarShownOnes() {
        List<FxSelection.Candidate> all = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            all.add(shown("old" + i, 150, 0));
        }
        all.add(c("near", 20));
        FxSelection.choose(all, RULES, 1000);
        assertTrue(all.get(4).show());
        assertEquals(4, chosen(all).size());
    }

    @Test
    void shownEffectStaysUntilClearlyOutsideTheRadius() {
        // cap 1: one shown at 52 (just past 48), one new at 47 inside; the shown one counts as inside up to 56 and is
        // ordered as 44, so it stays
        FxSelection.Rules rules = new FxSelection.Rules(48, 8, 1, 256, 40);
        List<FxSelection.Candidate> all = List.of(shown("old", 52, 0), c("new", 47));
        FxSelection.choose(all, rules, 1000);
        assertEquals(List.of("old"), chosen(all));
    }

    @Test
    void justShownEffectIsHeldEvenWhenSomethingNearerArrives() {
        FxSelection.Rules rules = new FxSelection.Rules(48, 8, 1, 256, 40);
        List<FxSelection.Candidate> all = List.of(shown("old", 200, 990), c("new", 2));
        FxSelection.choose(all, rules, 1000); // shown 10 ticks ago, hold is 40
        assertEquals(List.of("old"), chosen(all));
        all = List.of(shown("old", 200, 900), c("new", 2));
        FxSelection.choose(all, rules, 1000); // held long enough: the nearer one takes the place
        assertEquals(List.of("new"), chosen(all));
    }

    @Test
    void everythingInsideTheRadiusBeatsAnythingOutside() {
        FxSelection.Rules rules = new FxSelection.Rules(48, 8, 2, 256, 40);
        List<FxSelection.Candidate> all = List.of(shown("outShown", 60, 0), c("in1", 40), c("in2", 45));
        FxSelection.choose(all, rules, 1000);
        assertEquals(List.of("in1", "in2"), chosen(all));
    }

    @Test
    void sameInputSameAnswer() {
        List<FxSelection.Candidate> a = List.of(c("a", 10), c("b", 10), c("c", 10), c("d", 10), c("e", 10));
        FxSelection.choose(a, RULES, 1000);
        List<String> first = chosen(a);
        FxSelection.choose(a, RULES, 1000);
        assertEquals(first, chosen(a));
        assertEquals(4, first.size());
    }
}
