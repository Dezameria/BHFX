package net.offkung.bhfx.api.client;

/**
 * @param order lower draws first, sorted across all effects. Suggested: world-space 0, full-screen overlays 100.
 */
public record PassOptions(int order) {
    public static final PassOptions DEFAULT = new PassOptions(0);
    public static final PassOptions OVERLAY = new PassOptions(100);
}
