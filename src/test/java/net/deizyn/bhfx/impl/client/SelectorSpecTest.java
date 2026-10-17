package net.deizyn.bhfx.impl.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorSpecTest {
    @Test
    void plainNames() throws CommandSyntaxException {
        SelectorSpec s = SelectorSpec.parse("Steve");
        assertEquals(0, s.kind());
        assertEquals("Steve", s.name());
    }

    @Test
    void kindDefaults() throws CommandSyntaxException {
        assertEquals(1, SelectorSpec.parse("@s").limit());
        assertEquals("nearest", SelectorSpec.parse("@p").sort());
        assertEquals(1, SelectorSpec.parse("@p").limit());
        assertEquals("random", SelectorSpec.parse("@r").sort());
        assertEquals(Integer.MAX_VALUE, SelectorSpec.parse("@e").limit());
        assertEquals("arbitrary", SelectorSpec.parse("@a").sort());
    }

    @Test
    void options() throws CommandSyntaxException {
        SelectorSpec s = SelectorSpec.parse("@e[type=zombie,limit=3,sort=nearest,distance=..10,name=!\"Bob\"]");
        assertEquals('e', s.kind());
        assertEquals("minecraft:zombie", s.type());
        assertEquals(3, s.limit());
        assertEquals("nearest", s.sort());
        assertArrayEquals(new double[]{0.0, 10.0}, s.distance());
        assertEquals("Bob", s.name());
        assertTrue(s.notName());
        assertEquals("iron:golem", SelectorSpec.parse("@e[type=!iron:golem]").type());
        assertTrue(SelectorSpec.parse("@e[type=!iron:golem]").notType());
        assertNull(SelectorSpec.parse("@e").type());
    }

    @Test
    void distanceForms() throws CommandSyntaxException {
        assertArrayEquals(new double[]{5.0, 5.0}, SelectorSpec.parse("@e[distance=5]").distance());
        assertArrayEquals(new double[]{5.0, Double.MAX_VALUE}, SelectorSpec.parse("@e[distance=5..]").distance());
        assertArrayEquals(new double[]{2.0, 8.0}, SelectorSpec.parse("@e[distance=2..8]").distance());
    }

    @Test
    void serverOnlyAndBadOptionsAreRejected() {
        assertThrows(CommandSyntaxException.class, () -> SelectorSpec.parse("@e[tag=boss]"));
        assertThrows(CommandSyntaxException.class, () -> SelectorSpec.parse("@e[type=#minecraft:raiders]"));
        assertThrows(CommandSyntaxException.class, () -> SelectorSpec.parse("@e[limit=0]"));
        assertThrows(CommandSyntaxException.class, () -> SelectorSpec.parse("@e[sort=loudest]"));
        assertThrows(CommandSyntaxException.class, () -> SelectorSpec.parse("@e[distance=far]"));
        assertThrows(CommandSyntaxException.class, () -> SelectorSpec.parse("@x"));
        assertThrows(CommandSyntaxException.class, () -> SelectorSpec.parse("@e[type=zombie"));
        assertThrows(CommandSyntaxException.class, () -> SelectorSpec.parse("@e[colour=red]"));
    }
}
