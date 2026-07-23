package io.brodgar.voice.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatializerTest {

    @Test
    void centeredSourceIsBalanced() {
        // Directly ahead: equal in both ears.
        Spatializer.Gains g = Spatializer.compute(0, 5, 4f, 0.1f);
        assertEquals(g.left, g.right, 1e-6);
        assertTrue(g.left > 0);
    }

    @Test
    void rightSourcePansRight() {
        Spatializer.Gains g = Spatializer.compute(10, 0, 4f, 0.1f);
        assertTrue(g.right > g.left);
        assertTrue(g.left < 0.01f, "hard right should be almost silent on the left");
    }

    @Test
    void leftSourcePansLeft() {
        Spatializer.Gains g = Spatializer.compute(-10, 0, 4f, 0.1f);
        assertTrue(g.left > g.right);
        assertTrue(g.right < 0.01f);
    }

    @Test
    void saturatesPastEighthPi() {
        // Past ~22.5 deg off the forward axis, panning is already full (game-style).
        Spatializer.Gains wide = Spatializer.compute(10, 10, 4f, 0.1f); // 45 deg
        assertTrue(wide.left < 0.01f, "45 deg right should be full right");
        // A shallow angle pans only partially — audible on both ears.
        Spatializer.Gains shallow = Spatializer.compute(1, 20, 4f, 0.1f); // ~3 deg
        assertTrue(shallow.right > shallow.left);
        assertTrue(shallow.left > 0.1f, "a near-centre source stays in both ears");
    }

    @Test
    void withinNearRadiusIsFullVolume() {
        // 2 tiles ahead (inside near=4): attenuation 1, centered power ~0.707/ear.
        Spatializer.Gains g = Spatializer.compute(0, 2, 4f, 0.1f);
        assertEquals(Math.cos(Math.PI / 4), g.left, 1e-4);
    }

    @Test
    void distanceAttenuates() {
        float near = 4f;
        double closePower = power(Spatializer.compute(0, 5, near, 0.05f));
        double farPower = power(Spatializer.compute(0, 40, near, 0.05f));
        assertTrue(farPower < closePower, "farther must be quieter");
    }

    @Test
    void attenuationRespectsFloor() {
        Spatializer.Gains g = Spatializer.compute(0, 1000, 4f, 0.2f);
        double p = Math.hypot(g.left, g.right);
        assertTrue(p >= 0.2 - 1e-6, "floor keeps distant players audible: " + p);
    }

    @Test
    void zeroVectorIsCenteredFullVolume() {
        Spatializer.Gains g = Spatializer.compute(0, 0, 4f, 0.1f);
        assertEquals(g.left, g.right, 1e-6);
    }

    private static double power(Spatializer.Gains g) {
        return g.left * g.left + g.right * g.right;
    }
}
