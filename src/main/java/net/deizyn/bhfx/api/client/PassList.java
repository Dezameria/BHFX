package net.deizyn.bhfx.api.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** Shader passes requested this frame. {@code shader} "ns:name" loads assets/ns/shaders/bhfx/name.fsh. */
public interface PassList {
    /**
     * A point light for this frame: world surfaces within {@code radius} blocks of {@code position} (a world point)
     * are brightened in the colour {@code rgb} (0xRRGGBB), strongest at the centre and fading to nothing at the
     * radius. {@code strength}: 1 is a clear light, 2–3 a flash; it fades with the effect's presence on its own. Call
     * it every frame the light should show. The lights of every effect are drawn together in one built-in pass before
     * all other passes, so a light costs far less than a pass of its own; at most {@link #MAX_LIGHTS} show in one
     * frame (the ones farthest from the camera are dropped). Lights do not cast shadows and do not light particles,
     * models or the sky.
     */
    @ApiStatus.Experimental
    void light(Vec3 position, int rgb, float strength, float radius);

    /**
     * Draw {@code model} once for every living particle of {@code pieces}: debris, shards, blades, leaves that move on
     * the GPU like particles (velocity, gravity, drag, orbit and pull; turbulence is not applied) but are real
     * models. Each piece's scale is {@code scale} times the particle's {@link FxParticles.ParticleSpawn#size size}
     * (and the size curve), and its colour and alpha (with the over-life colour keys and alpha curve; a ramp texture
     * is not read) tint the material. How a piece is turned comes from the batch's
     * {@link FxParticles#facing facing}: {@code TUMBLE} = a random pose turning round
     * {@link FxParticles.ParticleSpawn#tumble} (a random axis when none is set); {@code VELOCITY} = the model's +z
     * along its flight, rolled by {@link FxParticles.ParticleSpawn#rotation}; any other = upright, turned round y by
     * the rotation and spin. The particle's {@code data} reaches the material as {@code s.instance}.
     * <p>
     * The batch is only a source of motion here: don't also pass it to {@link #particles} unless its sprites should
     * show too, and the material it was created with is not used. Pieces count against the particle budget like any
     * particle, so under load one in N shows.
     */
    @ApiStatus.Experimental
    void modelParticles(FxModel model, FxParticles pieces, float scale, @Nullable FxPose pose, ResourceLocation material,
                        PassOptions options, Consumer<Uniforms> setup);

    /** Most lights drawn in one frame. */
    int MAX_LIGHTS = 64;

    /**
     * A textured mark on the world for this frame: a square of half size {@code size} blocks centred on
     * {@code center} (a world point on or near the surface), flat on the ground unless {@link FxDecal#facing} says
     * otherwise, showing {@code texture}. Returns the mark to set its rotation, colour, glow and so on; call it every
     * frame the mark should show. The marks of every effect are drawn together in one built-in pass (after the lights,
     * before all other passes), so a mark costs far less than a pass of its own. At most {@link #MAX_DECALS} marks
     * and {@link #MAX_DECAL_TEXTURES} different textures show in one frame (the marks farthest from the camera are
     * dropped; a mark whose texture does not fit is not drawn). Use a clamped texture ({@link FxTexture#clamp}).
     */
    @ApiStatus.Experimental
    FxDecal decal(FxTexture texture, Vec3 center, float size);

    /** Most marks drawn in one frame. */
    int MAX_DECALS = 32;

    /** Most different textures the marks of one frame may use. */
    int MAX_DECAL_TEXTURES = 8;

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
