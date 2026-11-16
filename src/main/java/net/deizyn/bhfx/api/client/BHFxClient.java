package net.deizyn.bhfx.api.client;

import net.deizyn.bhfx.api.BodyPart;
import net.deizyn.bhfx.impl.client.BodyParts;
import net.deizyn.bhfx.impl.client.EntityPoints;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;
import net.deizyn.bhfx.api.FxParams;
import net.deizyn.bhfx.api.FxSpawn;
import net.deizyn.bhfx.api.StopMode;
import net.deizyn.bhfx.impl.client.ClientFxManager;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.Optional;

/** Client-side entry point. Render thread only. Register effects with {@link RegisterFxEvent}. */
public final class BHFxClient {
    private BHFxClient() {
    }

    /**
     * Play an effect on this client only, with no packet — e.g. from an entity's client tick (vanilla already syncs the
     * entity to late joiners) or an Epic Fight {@code Side.CLIENT} animation event. Range and audience are ignored.
     */
    public static LocalFxHandle play(ResourceLocation fxId, FxSpawn spawn) {
        return ClientFxManager.startLocal(fxId, spawn);
    }

    /** Stop a local effect; {@code mode} picks its ending. Unknown or finished handles are ignored. */
    public static void stop(LocalFxHandle handle, StopMode mode) {
        ClientFxManager.stopLocal(handle, mode);
    }

    /** Merge new values into a local effect's params; its {@code onParamsChanged} runs. Ignored once it has ended. */
    public static void update(LocalFxHandle handle, FxParams changed) {
        ClientFxManager.updateLocal(handle, changed);
    }

    /**
     * The running instance behind a local handle, when it is still playing and of {@code type} — for effects driven by
     * client-side cues (e.g. "a blow landed here") that do not fit in params. Do not keep it past the effect's end.
     */
    public static <T extends Fx> Optional<T> fx(LocalFxHandle handle, Class<T> type) {
        return ClientFxManager.localFx(handle, type);
    }

    /** Whether a local effect is still playing (including its ending). */
    public static boolean isAlive(LocalFxHandle handle) {
        return ClientFxManager.isLocalAlive(handle);
    }

    /** Camera shake on this client, 0..1, fading with the camera's distance from {@code source} out to {@code range}. */
    public static void shake(Vec3 source, float amount, double range) {
        ClientFxManager.shake(source, amount, range);
    }

    /** The player's effect settings (quality after any automatic downgrade, comfort options). */
    public static FxSettings settings() {
        return ClientFxManager.settings();
    }

    /** True when effects are enabled and post-processing is working on this client. */
    public static boolean postAvailable() {
        return ClientFxManager.postAvailable();
    }

    /**
     * Start loading a texture now (decoded off the render thread), so it is ready by the time a pass first samples it,
     * e.g. from {@code onStart} or right after registering effects. Cheap to call again.
     */
    @ApiStatus.Experimental
    public static void preload(FxTexture texture) {
        ClientFxManager.preloadTexture(texture);
    }

    /** Whether a texture has loaded and is on the GPU (until then samplers read zero). */
    @ApiStatus.Experimental
    public static boolean textureReady(FxTexture texture) {
        return ClientFxManager.textureReady(texture);
    }

    /**
     * True while the entity renders running now draw a player into a mirror reflection rather than into the world. Lets
     * a renderer hook show or hide someone only in mirrors (e.g. invisible except in a mirror).
     */
    public static boolean drawingMirrorReflection() {
        return ClientFxManager.drawingMirrorReflection();
    }

    /**
     * Where {@code part} of {@code entity} is, at {@code partialTick}: the posed model from the newest frame when it was
     * drawn, else an estimate ({@link BodyPose#exact()} false). Asking also asks bhfx to capture the entity next frame.
     */
    public static BodyPose bodyPart(Entity entity, BodyPart part, float partialTick) {
        return BodyParts.pose(entity, part, partialTick);
    }

    /**
     * For renderers that replace an entity's vanilla model (e.g. Epic Fight's patched renderers), whose parts bhfx cannot
     * see: true when bhfx wants {@code entity}'s parts reported from the entity render running now. Cheap; check it
     * before working out joint matrices.
     */
    @ApiStatus.Experimental
    public static boolean wantsBodyParts(Entity entity) {
        return BodyParts.wants(entity);
    }

    /**
     * Report where {@code part} is, from inside {@code entity}'s render in the world's entity pass. {@code poseOnStack}
     * is the pose stack's matrix moved to the point (as {@code stack.last().pose()} would be after translating there):
     * camera-relative, camera-rotated, like every entity render. Use vanilla's model axes: y down, the face towards -z,
     * the entity's right towards -x. Reported parts win over bhfx's own vanilla-model capture for that frame. Ignored
     * outside the entity pass (GUI, hand, mirror reflections). {@link BodyPart#FEET} needs no report: it is the
     * midpoint of both feet.
     */
    @ApiStatus.Experimental
    public static void reportBodyPart(Entity entity, BodyPart part, Matrix4f poseOnStack) {
        BodyParts.report(entity, part, poseOnStack, true);
    }

    /**
     * Named point {@code name} on {@code entity} (a GeckoLib bone, a weapon's tip), as a renderer reported it in the
     * last few frames with {@link #reportPoint}; empty when none did. Asking is what makes reporters start: the first
     * ask about an entity is usually empty. Not estimated, unlike {@link #bodyPart}.
     */
    @ApiStatus.Experimental
    public static Optional<BodyPose> point(Entity entity, String name) {
        return EntityPoints.point(entity, name);
    }

    /** Every named point {@code entity} reported in the last few frames, by name (see {@link #point}). */
    @ApiStatus.Experimental
    public static Map<String, BodyPose> points(Entity entity) {
        return EntityPoints.points(entity);
    }

    /**
     * For renderers: true when an effect asked about {@code entity}'s named points recently and the world's entity pass
     * is running. Cheap; check it once per entity render before working out matrices.
     */
    @ApiStatus.Experimental
    public static boolean wantsPoints(Entity entity) {
        return EntityPoints.wants(entity);
    }

    /**
     * Report point {@code name} of {@code entity} from inside its render in the world's entity pass. {@code poseOnStack}
     * is the pose stack's matrix moved to the point (camera-relative, camera-rotated, as every entity render is). Its
     * axes become the point's: forward = -z, up = +y, right = +x, as in Blockbench and GeckoLib bone space. Ignored
     * unless {@link #wantsPoints} (so outside the entity pass, in mirror reflections, or when nobody asked). At most 128
     * points per entity.
     */
    @ApiStatus.Experimental
    public static void reportPoint(Entity entity, String name, Matrix4f poseOnStack) {
        EntityPoints.report(entity, name, poseOnStack);
    }

    /** Like {@link #reportPoint(Entity, String, Matrix4f)}, for a reporter that already has the point in world space. */
    @ApiStatus.Experimental
    public static void reportPoint(Entity entity, String name, BodyPose worldPose) {
        EntityPoints.report(entity, name, worldPose);
    }
}
