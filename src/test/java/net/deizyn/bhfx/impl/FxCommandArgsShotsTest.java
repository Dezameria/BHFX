package net.deizyn.bhfx.impl;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxCommandArgsShotsTest {
    private static FxCommandArgs args(String snbt) throws CommandSyntaxException {
        CompoundTag tag = TagParser.parseTag(snbt);
        return FxCommandArgs.from(tag);
    }

    @Test
    void shotsFromAStringOrAListAreSortedAndNeverReachTheEffect() throws CommandSyntaxException {
        FxCommandArgs a = args("{shots:\"1, 0.2,0.5\", gui:true, power:2}");
        assertArrayEquals(new float[]{0.2f, 0.5f, 1f}, a.shots());
        assertTrue(a.gui());
        String params = a.params().toString();
        assertFalse(params.contains("shots"), params);
        assertFalse(params.contains("gui"), params);
        assertTrue(params.contains("power"), params);
        assertArrayEquals(new float[]{0.25f, 2f}, args("{shots:[2.0d,0.25d]}").shots());
        assertArrayEquals(new float[]{3f}, args("{shots:3}").shots());
    }

    @Test
    void noShotsMeansDefaults() throws CommandSyntaxException {
        FxCommandArgs a = args("{power:2}");
        assertNull(a.shots());
        assertFalse(a.gui());
    }

    @Test
    void badShotsAreRefused() {
        assertThrows(CommandSyntaxException.class, () -> args("{shots:\"0.5,soon\"}"));
        assertThrows(CommandSyntaxException.class, () -> args("{shots:\"-1\"}"));
        assertThrows(CommandSyntaxException.class, () -> args("{shots:\"700\"}"));
        assertThrows(CommandSyntaxException.class, () -> args("{shots:[]}"));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 33; i++) {
            many.append(i).append(',');
        }
        assertThrows(CommandSyntaxException.class, () -> args("{shots:\"" + many + "\"}"));
    }
}
