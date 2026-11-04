package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

/** Shader passes requested this frame. {@code shader} "ns:name" loads assets/ns/shaders/bhfx/name.fsh. */
public interface PassList {
    void add(ResourceLocation shader, Consumer<Uniforms> setup);

    void add(ResourceLocation shader, PassOptions options, Consumer<Uniforms> setup);

    /**
     * Draw a particle batch this frame, at {@link PassOptions#PARTICLES} order. Without {@code within} bounds, bhfx
     * culls the batch with a sphere it works out from the spawns.
     */
    @ApiStatus.Experimental
    default void particles(FxParticles particles) {
        particles(particles, PassOptions.PARTICLES, u -> {
        });
    }

    /**
     * Draw a particle batch this frame. {@code options.order()} places it among the passes: passes before it are
     * behind the particles, passes after it see them in {@code DiffuseSampler}. {@code setup} sets the material's own
     * uniforms and textures. Batches with nothing alive cost nothing.
     */
    @ApiStatus.Experimental
    void particles(FxParticles particles, PassOptions options, Consumer<Uniforms> setup);
}
