package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.Follow;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.FxSpawn;

/**
 * Start data handed to an effect's constructor.
 *
 * @param handleId  positive for server-started effects, negative for local ones
 * @param casterId  entity id or -1
 * @param followId  entity id or -1
 * @param duration  ticks, {@link FxSpawn#UNTIL_STOPPED} or {@link FxSpawn#DEFAULT_DURATION}
 * @param startAge  ticks already elapsed when this client received it (0 normally)
 */
public record FxInit(ResourceLocation id, int handleId, Vec3 origin, Vec3 target, int casterId, int followId,
                     Follow follow, long seed, int duration, int startAge, FxParams params) {
}
