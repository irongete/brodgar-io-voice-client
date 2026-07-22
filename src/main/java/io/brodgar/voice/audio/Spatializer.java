package io.brodgar.voice.audio;

/**
 * Turns the local vector to a speaker into stereo gains: distance attenuation
 * plus equal-power left/right panning. Pure math, computed entirely on the
 * client from the player's own relative vectors, which never leave the machine.
 *
 * <p>Convention: {@code dx} is tiles east (→ right ear), {@code dy} is tiles
 * south (front/back, which stereo cannot render, so it only affects distance).
 */
public final class Spatializer {

    /** Per-ear linear gains, already including distance attenuation. */
    public static final class Gains {
        public final float left;
        public final float right;

        public Gains(float left, float right) {
            this.left = left;
            this.right = right;
        }
    }

    /** Both ears at full volume — used when spatialization is disabled. */
    public static final Gains CENTERED_FULL = new Gains(1f, 1f);

    private Spatializer() {
    }

    /**
     * @param dx      tiles east of the local player (negative = west)
     * @param dy      tiles south of the local player
     * @param near    full-volume radius in tiles (no attenuation within it)
     * @param minGain attenuation floor so distant-but-in-range players stay audible
     */
    public static Gains compute(double dx, double dy, float near, float minGain) {
        double dist = Math.hypot(dx, dy);

        // Inverse-distance attenuation with a near plateau and a floor.
        float atten;
        if (dist <= near) {
            atten = 1f;
        } else {
            atten = (float) Math.max(minGain, near / dist);
        }

        // Equal-power pan from the east/west component: pan -1 (left) .. +1 (right).
        double pan = dist < 1e-6 ? 0.0 : clamp(dx / dist, -1.0, 1.0);
        double angle = (pan + 1.0) * 0.25 * Math.PI; // 0 .. pi/2
        float left = (float) Math.cos(angle);
        float right = (float) Math.sin(angle);
        return new Gains(atten * left, atten * right);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
