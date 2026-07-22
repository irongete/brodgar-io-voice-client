package io.brodgar.voice.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoGainTest {

    private static final int N = 960;

    private static short[] tone(int amp) {
        short[] s = new short[N];
        for (int i = 0; i < N; i++) {
            s[i] = (short) (amp * Math.sin(2 * Math.PI * i / 32.0));
        }
        return s;
    }

    private static double rms(short[] s) {
        long sum = 0;
        for (short v : s) {
            sum += (long) v * v;
        }
        return Math.sqrt((double) sum / s.length);
    }

    @Test
    void boostsAQuietSignalTowardTheTarget() {
        AutoGain agc = AutoGain.defaults();
        double before = rms(tone(300));
        for (int i = 0; i < 60; i++) {
            agc.process(tone(300), N); // converge on quiet input
        }
        short[] last = tone(300);
        agc.process(last, N);
        assertTrue(rms(last) > before * 2, "quiet signal must be boosted: " + before + " -> " + rms(last));
    }

    @Test
    void cutsAHotSignalAndStaysInRange() {
        AutoGain agc = AutoGain.defaults();
        double before = rms(tone(20000));
        for (int i = 0; i < 60; i++) {
            agc.process(tone(20000), N);
        }
        short[] last = tone(20000);
        agc.process(last, N);
        assertTrue(rms(last) < before, "hot signal must be reduced: " + before + " -> " + rms(last));
        for (short v : last) {
            assertTrue(v >= Short.MIN_VALUE && v <= Short.MAX_VALUE);
        }
    }

    @Test
    void leavesNearSilenceUntouched() {
        AutoGain agc = AutoGain.defaults();
        short[] silence = new short[N]; // rms 0, below the floor
        agc.process(silence, N);
        for (short v : silence) {
            assertEquals(0, v);
        }
    }
}
