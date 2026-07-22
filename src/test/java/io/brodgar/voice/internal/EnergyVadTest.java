package io.brodgar.voice.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyVadTest {

    private static short[] tone(int n, double amplitude) {
        short[] f = new short[n];
        for (int i = 0; i < n; i++) {
            f[i] = (short) (Math.sin(i * 0.2) * amplitude);
        }
        return f;
    }

    private static short[] silence(int n) {
        return new short[n];
    }

    @Test
    void loudFrameIsVoice() {
        EnergyVad vad = new EnergyVad(300, 5);
        assertTrue(vad.accept(tone(960, 8000), 960));
    }

    @Test
    void quietFrameIsSilence() {
        EnergyVad vad = new EnergyVad(300, 0);
        assertFalse(vad.accept(silence(960), 960));
    }

    @Test
    void hangoverKeepsTailThenStops() {
        EnergyVad vad = new EnergyVad(300, 3);
        assertTrue(vad.accept(tone(960, 8000), 960)); // voice arms hangover
        assertTrue(vad.accept(silence(960), 960));    // hangover 1
        assertTrue(vad.accept(silence(960), 960));    // hangover 2
        assertTrue(vad.accept(silence(960), 960));    // hangover 3
        assertFalse(vad.accept(silence(960), 960));   // exhausted → silence
    }

    @Test
    void voiceReArmsHangover() {
        EnergyVad vad = new EnergyVad(300, 2);
        vad.accept(tone(960, 8000), 960);
        vad.accept(silence(960), 960);            // hangover 1
        assertTrue(vad.accept(tone(960, 8000), 960)); // re-arm
        assertTrue(vad.accept(silence(960), 960));    // hangover 1 again
        assertTrue(vad.accept(silence(960), 960));    // hangover 2
        assertFalse(vad.accept(silence(960), 960));
    }

    @Test
    void rmsMatchesExpectation() {
        short[] flat = new short[100];
        java.util.Arrays.fill(flat, (short) 1000);
        assertTrue(Math.abs(EnergyVad.rms(flat, 100) - 1000) < 1e-6);
    }
}
