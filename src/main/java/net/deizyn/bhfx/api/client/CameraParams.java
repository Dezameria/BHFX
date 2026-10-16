package net.deizyn.bhfx.api.client;

/**
 * Per-frame camera requests from one effect, handed to {@link Fx#camera}. Each call is gated by what the player allows
 * ({@code camera.control} in bhfx-client.toml) and by whose spell it is:
 * <ul>
 *   <li>{@link Level#SUBTLE} (default) — {@link #fov} and {@link #hideHand}, only for effects the local player cast</li>
 *   <li>{@link Level#FULL} (player opt-in) — also {@link #roll}, {@link #hideHud} and {@link #override}</li>
 *   <li>effects cast by someone else change nothing unless the player turned on {@code camera.fromOthers}</li>
 * </ul>
 * A request above {@link #allowed()} is silently ignored, so effects can always ask and still look right without it
 * (screen shake via {@link FxContext#shake} and {@link ScreenParams} work for everyone regardless).
 * Several effects combine: the strongest FOV change wins, rolls add up (capped at 30 degrees), hiding is any-of, and
 * the override with the highest weight wins.
 */
public interface CameraParams {
    enum Level { NONE, SUBTLE, FULL }

    /** What this effect may change on this client right now. */
    Level allowed();

    /** Multiply the field of view, e.g. 1.08 to punch out on release. Clamped to 0.7..1.3, scaled by the player's fovScale. */
    CameraParams fov(float multiplier);

    /** Hide the first-person hand and held item this frame. */
    CameraParams hideHand();

    /** Tilt the camera around the view axis. FULL only. */
    CameraParams roll(float degrees);

    /** Hide the HUD (chat stays) this frame. FULL only. */
    CameraParams hideHud();

    /**
     * Blend the camera towards a pose, 0 = untouched, 1 = exactly the pose. For short cut-ins on the caster's own
     * screen. FULL only. The pose should keep the camera out of blocks; bhfx does not check collisions.
     */
    CameraParams override(CameraPose pose, float weight);
}
