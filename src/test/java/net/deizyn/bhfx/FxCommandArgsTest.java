package net.deizyn.bhfx;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.deizyn.bhfx.api.FxParam;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.FxSpawn;
import net.deizyn.bhfx.impl.FxCommandArgs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxCommandArgsTest {
    private static FxCommandArgs args(String snbt) throws CommandSyntaxException {
        CompoundTag tag = new TagParser(new StringReader(snbt)).readStruct();
        return FxCommandArgs.from(tag);
    }

    @Test
    void emptyUsesEffectDefaults() throws CommandSyntaxException {
        FxCommandArgs a = FxCommandArgs.from(null);
        assertEquals(FxSpawn.DEFAULT_DURATION, a.durationTicks());
        assertTrue(a.params().isEmpty());
        assertNull(a.range());
    }

    @Test
    void holdPlaysUntilStopped() throws CommandSyntaxException {
        assertEquals(FxSpawn.UNTIL_STOPPED, args("{hold:true}").durationTicks());
    }

    @Test
    void durationSecondsBecomeTicks() throws CommandSyntaxException {
        assertEquals(60, args("{duration:3}").durationTicks());
        assertEquals(50, args("{duration:2.5}").durationTicks());
        assertFalse(args("{duration:3}").params().has(FxParam.i("duration")));
    }

    @Test
    void commandOptionsNeverReachTheEffect() throws CommandSyntaxException {
        FxCommandArgs a = args("{hold:true, range:16}");
        assertEquals(16.0, a.range());
        assertFalse(a.params().has(FxParam.b("hold")));
        assertFalse(a.params().has(FxParam.i("range")));
        assertFalse(a.params().has(FxParam.f("range")));
    }

    @Test
    void valuesKeepTheirTypes() throws CommandSyntaxException {
        FxParams p = args("{radius:12, level:5, charged:true, glow:0.5, tint:\"#FF40C0\"}").params();
        assertEquals(12f, p.get(FxParam.f("radius"), 0f)); // int into a float key
        assertEquals(5, p.get(FxParam.i("level"), 0));
        assertTrue(p.get(FxParam.b("charged"), false));
        assertEquals(0.5f, p.get(FxParam.f("glow"), 0f));
        assertEquals(0xFF40C0, p.get(FxParam.color("tint"), 0));
    }

    @Test
    void badInputIsReported() {
        assertThrows(CommandSyntaxException.class, () -> args("{hold:true, duration:3}"));
        assertThrows(CommandSyntaxException.class, () -> args("{duration:0}"));
        // the old name is refused, not passed to the effect
        assertThrows(CommandSyntaxException.class, () -> args("{seconds:3}"));
        assertThrows(CommandSyntaxException.class, () -> args("{hold:2}"));
        assertThrows(CommandSyntaxException.class, () -> args("{tint:\"red\"}"));
        assertThrows(CommandSyntaxException.class, () -> args("{pos:[1,2,3]}"));
        assertThrows(CommandSyntaxException.class, () -> args("{range:0}"));
    }
}
