package net.deizyn.bhfx.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.deizyn.bhfx.api.StopMode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The scopes of stop: all, fx, near, name, and finish. */
class FxCommandsStopTest {
    private static final ResourceLocation HERE = new ResourceLocation("overworld");
    private static final ResourceLocation THERE = new ResourceLocation("the_nether");
    private static final ResourceLocation RING = new ResourceLocation("bhfx", "ring");
    private static final ResourceLocation BEAM = new ResourceLocation("bhfx", "beam");

    private final Set<Integer> alive = new HashSet<>();
    private final List<String> stopped = new ArrayList<>();
    private final FxCommands.Started<Integer> started = new FxCommands.Started<>(alive::contains, (h, mode) -> {
        alive.remove(h);
        stopped.add(h + (mode == StopMode.FINISH ? "f" : ""));
    });
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private final CommandSourceStack source = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 4,
            "test", Component.literal("test"), null, null);

    @BeforeEach
    void setUp() {
        // what the client and server do, minus resolving entities
        dispatcher.register(FxCommands.stop(started, (ctx, by, mode) ->
                started.stop(FxCommands.which(ctx, by, List.of(), Vec3.ZERO, HERE), mode)));
        add(1, RING, null, new Vec3(3, 0, 0), HERE);
        add(2, RING, "a", new Vec3(40, 0, 0), HERE);
        add(3, BEAM, "a", new Vec3(5, 0, 0), HERE);
        add(4, BEAM, null, new Vec3(1, 0, 0), THERE);
    }

    private void add(int handle, ResourceLocation fx, String name, Vec3 at, ResourceLocation dim) {
        alive.add(handle);
        started.add(handle, fx, name, at, dim);
    }

    private int run(String command) throws CommandSyntaxException {
        return dispatcher.execute(command, source);
    }

    @Test
    void stopAlone_stopsEverything() throws CommandSyntaxException {
        assertEquals(4, run("stop"));
        assertEquals(Set.of(), alive);
    }

    @Test
    void stopFx_stopsOnlyThatEffect() throws CommandSyntaxException {
        assertEquals(Set.of(BEAM, RING), started.fxIds());
        assertEquals(2, run("stop fx bhfx:ring"));
        assertEquals(Set.of(3, 4), alive);
    }

    @Test
    void stopNear_sameDimensionWithinRadius() throws CommandSyntaxException {
        // 4 is 1 block away but in another dimension; 2 is 40 blocks away
        assertEquals(2, run("stop near 10"));
        assertEquals(Set.of(2, 4), alive);
    }

    @Test
    void stopName_endsTheWholeGroup() throws CommandSyntaxException {
        assertEquals(Set.of("a"), started.names());
        assertEquals(2, run("stop name a"));
        assertEquals(Set.of(1, 4), alive);
        assertEquals(Set.of(), started.names());
        assertEquals(0, run("stop name a"));
    }

    @Test
    void finishPlaysTheFullEnding() throws CommandSyntaxException {
        run("stop fx bhfx:beam finish");
        assertEquals(List.of("3f", "4f"), stopped);
    }

    @Test
    void finishedEffectsAreForgotten() throws CommandSyntaxException {
        alive.remove(1); // ended on its own
        assertEquals(1, run("stop near 10"));
        assertEquals(List.of("3"), stopped);
    }
}
