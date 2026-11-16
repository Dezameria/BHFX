package net.deizyn.bhfx.api.client;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A pose for a model's bones, by bone name: turn, move, scale or hide bones of an {@link FxModel} as it is drawn
 * ({@link PassList#model(FxModel, FxFrame, float, FxPose, net.minecraft.resources.ResourceLocation, java.util.function.Consumer)}).
 * The numbers are Blockbench's animation numbers, so values read off Blockbench's animation panel work as they are:
 * <ul>
 *   <li>{@link #rotation}: degrees, added to the bone's own rotation, applied Z then Y then X round its pivot, like
 *       GeckoLib does;</li>
 *   <li>{@link #position}: pixels (16 a block), in the parent's space;</li>
 *   <li>{@link #scale}: round the bone's pivot ({@link #hide} = scale 0). Children follow their parent.</li>
 * </ul>
 * Bones the pose doesn't name stay as modelled; a bone name the model doesn't have is ignored. Keep one pose per effect
 * and set it again each frame (setting a value allocates nothing after the bone's first use). Cheap: the GPU moves the
 * vertices (no re-bake, no extra draw), the CPU work is a few matrices per bone once per draw. Up to
 * {@link #MAX_BONES} bones of a model can move (bones after that in the file stay put). Negative scales turn faces
 * inside out; avoid them. Render thread only.
 */
@ApiStatus.Experimental
public final class FxPose {
    /** Bones of one model that a pose can move (the first ones in the {@code .geo.json}). */
    public static final int MAX_BONES = 64;

    private final Map<String, float[]> bones = new HashMap<>();
    private int version;

    public FxPose() {
    }

    /** Turn {@code bone} by x, y, z degrees on top of its modelled rotation (Blockbench animation rotation). */
    public FxPose rotation(String bone, float x, float y, float z) {
        float[] c = channels(bone, true);
        c[0] = x;
        c[1] = y;
        c[2] = z;
        version++;
        return this;
    }

    /** Move {@code bone} by x, y, z pixels (Blockbench animation position). */
    public FxPose position(String bone, float x, float y, float z) {
        float[] c = channels(bone, true);
        c[3] = x;
        c[4] = y;
        c[5] = z;
        version++;
        return this;
    }

    /** Scale {@code bone} (and its children) round its pivot. */
    public FxPose scale(String bone, float x, float y, float z) {
        float[] c = channels(bone, true);
        c[6] = x;
        c[7] = y;
        c[8] = z;
        version++;
        return this;
    }

    public FxPose scale(String bone, float s) {
        return scale(bone, s, s, s);
    }

    /** Hide {@code bone} and its children (scale 0: its triangles collapse, nearly free on the GPU). */
    public FxPose hide(String bone) {
        return scale(bone, 0f);
    }

    /** {@code bone} back to how it was modelled. */
    public FxPose reset(String bone) {
        if (bones.remove(bone) != null) {
            version++;
        }
        return this;
    }

    /** Every bone back to how it was modelled. */
    public FxPose clear() {
        if (!bones.isEmpty()) {
            bones.clear();
            version++;
        }
        return this;
    }

    public boolean isEmpty() {
        return bones.isEmpty();
    }

    /** Goes up on every change. */
    @ApiStatus.Internal
    public int version() {
        return version;
    }

    /** rotation xyz, position xyz, scale xyz of {@code bone}, or null when the pose leaves it alone. Don't change it. */
    @ApiStatus.Internal
    @Nullable
    public float[] channels(String bone) {
        return bones.get(bone);
    }

    private float[] channels(String bone, boolean create) {
        float[] c = bones.get(bone);
        if (c == null && create) {
            c = new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1};
            bones.put(bone, c);
        }
        return c;
    }
}
