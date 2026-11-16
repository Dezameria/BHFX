package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

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

    /**
     * Draw a model this frame at {@link PassOptions#PARTICLES} order (see {@link FxModel}). {@code frame}: where it
     * stands, in world space (not camera-relative): the model's x, y, z go along the frame's axes, its origin (the
     * model's 0, 0, 0, between its feet in Blockbench) at the frame's origin; its front (Blockbench's north) is the
     * frame's -z. {@code scale}: 1 = the size it was made at (16 pixels a block). {@code material}: {@link FxModel#TEXTURED},
     * {@link FxModel#GLOW}, {@link FxModel#DISSOLVE} or your own. {@code setup} sets {@code MColor}, {@code MParams} and
     * any uniforms of your material. Without {@code within} bounds, it is culled by its own bounds.
     */
    @ApiStatus.Experimental
    default void model(FxModel model, FxFrame frame, float scale, ResourceLocation material, Consumer<Uniforms> setup) {
        model(model, frame, scale, material, PassOptions.PARTICLES, setup);
    }

    /** Like {@link #model(FxModel, FxFrame, float, ResourceLocation, Consumer)}, with an order and bounds. */
    @ApiStatus.Experimental
    default void model(FxModel model, FxFrame frame, float scale, ResourceLocation material, PassOptions options,
                       Consumer<Uniforms> setup) {
        model(model, frame, scale, null, material, options, setup);
    }

    /**
     * Like {@link #model(FxModel, FxFrame, float, ResourceLocation, Consumer)}, with its bones posed by {@code pose}
     * (null: as modelled). The pose is read when the model is drawn, later this frame: to draw two different poses in
     * one frame, use two {@link FxPose} objects.
     */
    @ApiStatus.Experimental
    default void model(FxModel model, FxFrame frame, float scale, @Nullable FxPose pose, ResourceLocation material,
                       Consumer<Uniforms> setup) {
        model(model, frame, scale, pose, material, PassOptions.PARTICLES, setup);
    }

    /** Posed, with an order and bounds. When posed bones reach past the model's own bounds, give {@code within}. */
    @ApiStatus.Experimental
    void model(FxModel model, FxFrame frame, float scale, @Nullable FxPose pose, ResourceLocation material,
               PassOptions options, Consumer<Uniforms> setup);

    /**
     * Draw {@code count} copies of a model in one go (a thousand swords): copy {@code i} stands where entries
     * {@code 4i .. 4i + 3} of {@code instances} say ({@link FxData#setInstance}: camera-relative frame times its scale,
     * then four values its material reads as {@code s.instance}). Give {@code options} {@code within} bounds round all
     * of them, or they are never culled. Each copy may be mirrored or not; back faces are dropped per copy.
     */
    @ApiStatus.Experimental
    default void models(FxModel model, FxData instances, int count, ResourceLocation material, PassOptions options,
                        Consumer<Uniforms> setup) {
        models(model, instances, count, null, material, options, setup);
    }

    /**
     * Like {@link #models(FxModel, FxData, int, ResourceLocation, PassOptions, Consumer)}, every copy posed alike by
     * {@code pose} (null: as modelled). For copies that differ, vary them in the material by {@code s.instance}.
     */
    @ApiStatus.Experimental
    void models(FxModel model, FxData instances, int count, @Nullable FxPose pose, ResourceLocation material,
                PassOptions options, Consumer<Uniforms> setup);
}
