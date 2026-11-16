package net.deizyn.bhfx.api;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A point on an entity's body that an effect can ride on ({@link FxSpawn#part}) or ask for each frame
 * ({@code FrameContext.part}). Left and right are the entity's own. On humanoid models each point follows the posed
 * model (walking, swinging, crouching, Epic Fight animations when a renderer reports them); elsewhere it is estimated
 * from the entity's size and body rotation.
 */
public enum BodyPart {
    /** Centre of the head. */
    HEAD,
    /** Centre of the torso. */
    BODY,
    /** Top of the left arm, where it meets the body; carries the upper arm's axes (it bends apart from the forearm
     * under Epic Fight). */
    LEFT_SHOULDER,
    /** Top of the right arm (see {@link #LEFT_SHOULDER}). */
    RIGHT_SHOULDER,
    /** Middle of the left arm (about the elbow); carries the forearm's axes. */
    LEFT_ARM,
    /** Middle of the right arm (about the elbow); carries the forearm's axes. */
    RIGHT_ARM,
    /** End of the left arm, where an item is held. */
    LEFT_HAND,
    /** End of the right arm, where an item is held. */
    RIGHT_HAND,
    /** Middle of the left leg (about the knee). */
    LEFT_LEG,
    /** Middle of the right leg (about the knee). */
    RIGHT_LEG,
    /** Sole of the left foot. */
    LEFT_FOOT,
    /** Sole of the right foot. */
    RIGHT_FOOT,
    /** Halfway between both soles: the ground under the entity, following its stance. */
    FEET;

    private static final BodyPart[] VALUES = values();

    /** Lower-case name used in commands and SNBT, e.g. {@code right_hand}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The part named {@code id} ({@code right_hand}, case ignored), or null. */
    @Nullable
    public static BodyPart byId(String id) {
        for (BodyPart p : VALUES) {
            if (p.name().equalsIgnoreCase(id)) {
                return p;
            }
        }
        return null;
    }

    /** Wire form: -1 for none. */
    public static int toWire(@Nullable BodyPart part) {
        return part == null ? -1 : part.ordinal();
    }

    @Nullable
    public static BodyPart fromWire(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }
}
