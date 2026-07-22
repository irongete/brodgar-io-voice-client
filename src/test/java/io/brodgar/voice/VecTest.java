package io.brodgar.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VecTest {

    @Test
    void arithmetic() {
        Vec a = new Vec(3, -4);
        Vec b = new Vec(1, 2);
        assertEquals(new Vec(4, -2), a.plus(b));
        assertEquals(new Vec(2, -6), a.minus(b));
        assertEquals(new Vec(-3, 4), a.negate());
    }

    @Test
    void normAndDistance() {
        assertEquals(5.0, new Vec(3, 4).norm(), 1e-9);
        assertEquals(0.0, new Vec(3, 4).dist(new Vec(3, 4)), 1e-9);
        assertEquals(5.0, new Vec(0, 0).dist(new Vec(3, 4)), 1e-9);
    }

    @Test
    void oppositeVectorsSumToZero() {
        Vec ab = new Vec(5, 0);
        Vec ba = new Vec(-5, 0);
        assertEquals(0.0, ab.plus(ba).norm(), 1e-9);
    }

    @Test
    void differenceDropsACommonOrigin() {
        // Two offsets measured from the same origin; subtracting cancels it.
        Vec toA = new Vec(10 - 11, 10 - 18);
        Vec toB = new Vec(15 - 11, 12 - 18);
        assertEquals(new Vec(5, 2), toB.minus(toA));
    }
}
