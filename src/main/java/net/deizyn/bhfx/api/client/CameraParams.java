package net.deizyn.bhfx.api.client;

/**
 * Per-frame camera requests from one effect, handed to {@link Fx#camera}. Each call is gated by what the player allows
 * ({@code camera.control} in bhfx-client.toml) and by whose spell it is:
 * <ul>
 *   <li>{@link Level#SUBTLE} (default) — {@link #fov}, only for effects the local player cast</li>
 *   <li>effects cast by someone else change nothing unless the player turned on {@code camera.fromOthers}</li>
 * </ul>
 * A request above {@link #allowed()} is silently ignored, so effects can always ask and still look right without it
 * (screen shake via {@link FxContext#shake} and {@link ScreenParams} work for everyone regardless).
 * Several effects combine: the strongest FOV change wins.
 */
public interface CameraParams {
    enum Level { NONE, SUBTLE }

    /** What this effect may change on this client right now. */
    Level allowed();

    /** Multiply the field of view, e.g. 1.08 to punch out on release. Clamped to 0.7..1.3, scaled by the player's fovScale. */
    CameraParams fov(float multiplier);
}
