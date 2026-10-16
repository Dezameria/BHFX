package net.deizyn.bhfx.api;

/** Whether an effect's position tracks an entity. */
public enum Follow {
    NONE,
    /** Effect position = the entity's interpolated position. */
    POSITION,
    /** Position plus the entity's look direction (available to the effect). */
    POSITION_AND_LOOK
}
