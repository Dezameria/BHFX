package net.offkung.bhfx.impl.client;

/** Smooth 1D value noise in [-1, 1] for camera shake: continuous, so the camera sways instead of jittering. */
final class CameraNoise {
    private CameraNoise() {
    }

    static float sample(double x, double seed) {
        double i = Math.floor(x);
        double f = x - i;
        double u = f * f * (3.0 - 2.0 * f);
        return (float) (lerp(hash(i + seed), hash(i + 1.0 + seed), u) * 2.0 - 1.0);
    }

    private static double hash(double n) {
        long bits = Double.doubleToLongBits(n * 0.1031 + 0.5);
        bits ^= bits >>> 33;
        bits *= 0xff51afd7ed558ccdL;
        bits ^= bits >>> 33;
        bits *= 0xc4ceb9fe1a85ec53L;
        bits ^= bits >>> 33;
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
