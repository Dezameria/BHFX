package net.deizyn.bhfx.impl.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MolangTest {
    private static double eval(String text, double time) {
        return Molang.compile(text).eval(time);
    }

    @Test
    void arithmeticAndPrecedence() {
        assertEquals(7.0, eval("1 + 2 * 3", 0), 1e-9);
        assertEquals(9.0, eval("(1 + 2) * 3", 0), 1e-9);
        assertEquals(-4.0, eval("-2 * 2", 0), 1e-9);
        assertEquals(0.0, eval("1 / 0", 0), 1e-9); // no infinities
        assertEquals(2.5, eval("2.5f", 0), 1e-9);
    }

    @Test
    void timeAndFunctionsInDegrees() {
        assertEquals(1.0, eval("math.sin(query.anim_time * 90)", 1.0), 1e-9);
        assertEquals(-1.0, eval("math.cos(q.anim_time * 180)", 1.0), 1e-9);
        assertEquals(3.0, eval("math.clamp(q.anim_time, 0, 3)", 10.0), 1e-9);
        assertEquals(5.0, eval("math.lerp(0, 10, 0.5)", 0), 1e-9);
        assertEquals(Math.PI, eval("Math.PI", 0), 1e-9);
    }

    @Test
    void comparisonsAndTernary() {
        assertEquals(10.0, eval("q.anim_time > 1 ? 10 : 20", 2.0), 1e-9);
        assertEquals(20.0, eval("q.anim_time > 1 ? 10 : 20", 0.5), 1e-9);
        assertEquals(1.0, eval("1 < 2 && !(2 < 1)", 0), 1e-9);
    }

    @Test
    void whatTheSubsetLacksIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("variable.x * 2"));
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("math.random(0, 1)"));
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("v.a = 1; return v.a;"));
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("math.sin(1, 2)"));
        assertNull(Molang.number("q.anim_time"));
        assertEquals(-1.5, Molang.number(" -1.5 "));
    }
}
