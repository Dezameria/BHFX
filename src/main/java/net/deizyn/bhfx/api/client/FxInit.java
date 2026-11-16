package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.BodyPart;
import net.deizyn.bhfx.api.Follow;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.FxSpawn;
import org.jetbrains.annotations.Nullable;

/**
 * Start data handed to an effect's constructor.
 *
 * @param handleId  positive for server-started effects, negative for local ones
 * @param casterId  entity id or -1
 * @param followId  entity id or -1
 * @param duration  ticks, {@link FxSpawn#UNTIL_STOPPED} or {@link FxSpawn#DEFAULT_DURATION}
 * @param startAge  ticks already elapsed when this client received it (0 normally)
 * @param part      the followed body point ({@link FxSpawn#part}), or null for the entity's position
 */
public record FxInit(ResourceLocation id, int handleId, Vec3 origin, Vec3 target, int casterId, int followId,
                     Follow follow, long seed, int duration, int startAge, FxParams params, @Nullable BodyPart part) {
    /** Without a body part (the form before 0.3.12). */
    public FxInit(ResourceLocation id, int handleId, Vec3 origin, Vec3 target, int casterId, int followId,
                  Follow follow, long seed, int duration, int startAge, FxParams params) {
        this(id, handleId, origin, target, casterId, followId, follow, seed, duration, startAge, params, null);
    }
}
