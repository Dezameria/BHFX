package net.deizyn.bhfx.api.client;

import net.deizyn.bhfx.impl.render.AnimationLibrary;
import net.deizyn.bhfx.impl.render.GeoAnimationLoader;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Blockbench / GeckoLib {@code .animation.json} played into an {@link FxPose}, so a model drawn by bhfx moves the
 * way it was animated in Blockbench: no entity, no GeckoLib.
 * <pre>{@code
 * static final FxAnimation ANIM = FxAnimation.of(new ResourceLocation("ns", "sword")); // animations/sword.animation.json
 * final FxPose pose = new FxPose();
 * // in passes():
 * ANIM.apply("swing", seconds(frame), pose);           // "animation.sword.swing" or just "swing"
 * out.model(SWORD, frame, 1f, pose, FxModel.TEXTURED, u -> {});
 * }</pre>
 * <ul>
 *   <li>Keyframes as Blockbench exports them: linear, step, smooth (catmullrom), bezier, GeckoLib easings, pre/post
 *       jumps, and a small part of Molang ({@code query.anim_time}, {@code math.sin} ...). Anything else is logged
 *       once and reads as 0 (1 for scale).</li>
 *   <li>{@code loop: true} wraps; otherwise the clip holds its last frame ({@link #done} tells when it ended).</li>
 *   <li>{@link #apply(String, float, FxPose, float)} blends over what the pose already holds (cross-fade two clips:
 *       apply the first at 1, the second at the fade).</li>
 *   <li>Read and parsed once, off the render thread, shared by every effect; before it is ready {@link #apply}
 *       returns false and leaves the pose alone ({@link #load} starts it early). Re-read on F3+T.</li>
 *   <li>Cheap: sampling allocates nothing (a binary search per animated channel).</li>
 * </ul>
 * Render thread only.
 */
@ApiStatus.Experimental
public final class FxAnimation {
    private final ResourceLocation file;
    @Nullable
    private GeoAnimationLoader.Library library;
    private int generation = -1;

    private FxAnimation(ResourceLocation file) {
        this.file = file;
    }

    /** {@code "ns:name"} → {@code assets/ns/animations/name.animation.json} (where GeckoLib keeps them). */
    public static FxAnimation of(ResourceLocation id) {
        return new FxAnimation(new ResourceLocation(id.getNamespace(), "animations/" + id.getPath() + ".animation.json"));
    }

    /** A file by its full path inside the resources, e.g. {@code ns:animations/name_animations.json}. */
    public static FxAnimation file(ResourceLocation file) {
        return new FxAnimation(file);
    }

    public ResourceLocation fileId() {
        return file;
    }

    /** Start loading now (e.g. in the effect's constructor), so the first frames already move. */
    public FxAnimation load() {
        library();
        return this;
    }

    /** Loaded (possibly with no clips, when the file failed: see the log). */
    public boolean ready() {
        return library() != null;
    }

    /** The clip names (full, and short where unique); empty while loading. */
    public List<String> clips() {
        GeoAnimationLoader.Library lib = library();
        return lib == null ? List.of() : List.copyOf(lib.clips().keySet());
    }

    public boolean has(String clip) {
        return clip(clip) != null;
    }

    /** The clip's length in seconds; NaN while loading or when there is no such clip. */
    public float length(String clip) {
        GeoAnimationLoader.Clip c = clip(clip);
        return c == null ? Float.NaN : c.length();
    }

    public boolean loops(String clip) {
        GeoAnimationLoader.Clip c = clip(clip);
        return c != null && c.loops();
    }

    /** A clip that doesn't loop has reached its end at {@code seconds}. False while loading, for loops and unknown clips. */
    public boolean done(String clip, float seconds) {
        GeoAnimationLoader.Clip c = clip(clip);
        return c != null && !c.loops() && seconds >= c.length();
    }

    /**
     * Put {@code clip} at {@code seconds} (since it started) into {@code pose}: every channel the clip animates is set;
     * other bones and channels are left as the pose has them ({@code pose.clear()} first to drop an earlier clip's).
     * False (pose untouched) while loading or when the file has no such clip.
     */
    public boolean apply(String clip, float seconds, FxPose pose) {
        return apply(clip, seconds, pose, 1f);
    }

    /** Like {@link #apply(String, float, FxPose)}, blended over the pose's values by {@code weight} (0..1). */
    public boolean apply(String clip, float seconds, FxPose pose, float weight) {
        GeoAnimationLoader.Clip c = clip(clip);
        if (c == null || pose == null) {
            return false;
        }
        c.apply(seconds, pose, weight);
        return true;
    }

    @Nullable
    private GeoAnimationLoader.Clip clip(String name) {
        GeoAnimationLoader.Library lib = library();
        return lib == null || name == null ? null : lib.clips().get(name);
    }

    @Nullable
    private GeoAnimationLoader.Library library() {
        int g = AnimationLibrary.generation();
        if (library == null || generation != g) {
            library = AnimationLibrary.get(file);
            generation = library != null ? g : -1;
        }
        return library;
    }

    @Override
    public String toString() {
        return "FxAnimation[" + file + "]";
    }
}
