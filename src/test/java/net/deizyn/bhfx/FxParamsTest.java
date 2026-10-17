package net.deizyn.bhfx;

import net.deizyn.bhfx.api.FxParam;
import net.deizyn.bhfx.api.FxParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxParamsTest {
    @Test
    void intFillsFloatKey() {
        FxParams p = FxParams.of(FxParam.i("radius"), 12);
        assertEquals(12f, p.get(FxParam.f("radius"), 8f));
        assertTrue(p.has(FxParam.f("radius")));
    }

    @Test
    void wholeFloatFillsIntKeyButFractionDoesNot() {
        assertEquals(3, FxParams.of(FxParam.f("n"), 3f).get(FxParam.i("n"), 0));
        assertEquals(0, FxParams.of(FxParam.f("n"), 3.5f).get(FxParam.i("n"), 0));
        assertFalse(FxParams.of(FxParam.f("n"), 3.5f).has(FxParam.i("n")));
    }

    @Test
    void otherMismatchesFallBack() {
        FxParams p = FxParams.of(FxParam.b("ready"), true);
        assertEquals(7, p.get(FxParam.i("ready"), 7));
        assertEquals(1f, p.get(FxParam.f("missing"), 1f));
    }

    @Test
    void mergeReplacesByName() {
        FxParams a = FxParams.of(FxParam.i("level"), 1);
        FxParams b = FxParams.of(FxParam.i("level"), 4);
        assertEquals(4, a.merge(b).get(FxParam.i("level"), 0));
    }
}
