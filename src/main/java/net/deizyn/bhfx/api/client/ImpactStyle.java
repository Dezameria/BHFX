package net.offkung.bhfx.api.client;

/**
 * One anime impact frame look: a kind plus two colours, so any palette works without new shader code.
 *
 * @param darkRgb     0xRRGGBB used for ink / shadows
 * @param lightRgb    0xRRGGBB used for paper / highlights
 * @param focusLines  density of radial lines towards the focus point, 0 = none
 * @param param       kind-specific: halftone dot size in pixels, slash angle in degrees
 */
public record ImpactStyle(Kind kind, int darkRgb, int lightRgb, float focusLines, float param) {
    /** Ids are part of the shader contract: never reorder. */
    public enum Kind {
        /** Edges inked dark, everything else split into dark / light by brightness. */
        INK,
        /** INK with dark and light swapped. */
        INK_INVERTED,
        /** Photographic negative with hard contrast (colours ignored). */
        NEGATIVE,
        /** Rotated dot screen: dots grow in the dark. */
        HALFTONE,
        /** Rows torn sideways with a colour split, tinted by the dark colour. */
        GLITCH,
        /** A cut through the focus point: one side ink, the other inverted, a bright seam between. */
        SLASH
    }

    public static ImpactStyle ink(int dark, int light) {
        return new ImpactStyle(Kind.INK, dark, light, 0f, 0f);
    }

    public static ImpactStyle inverted(int dark, int light) {
        return new ImpactStyle(Kind.INK_INVERTED, dark, light, 0f, 0f);
    }

    public static ImpactStyle negative() {
        return new ImpactStyle(Kind.NEGATIVE, 0x000000, 0xFFFFFF, 0f, 0f);
    }

    public static ImpactStyle halftone(int dark, int light, float dotSizePx) {
        return new ImpactStyle(Kind.HALFTONE, dark, light, 0f, Math.max(3f, dotSizePx));
    }

    public static ImpactStyle glitch(int tint) {
        return new ImpactStyle(Kind.GLITCH, tint, 0xFFFFFF, 0f, 0f);
    }

    public static ImpactStyle slash(int dark, int light, float angleDeg) {
        return new ImpactStyle(Kind.SLASH, dark, light, 0f, angleDeg);
    }

    public ImpactStyle withFocusLines(float density) {
        return new ImpactStyle(kind, darkRgb, lightRgb, Math.max(0f, density), param);
    }
}
