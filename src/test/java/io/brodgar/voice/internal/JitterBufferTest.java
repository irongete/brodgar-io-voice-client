package io.brodgar.voice.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JitterBufferTest {

    private static byte[] f(int marker) {
        return new byte[]{(byte) marker};
    }

    @Test
    void primesThenPlaysInOrder() {
        JitterBuffer jb = new JitterBuffer(2, 8);
        jb.put(10, f(10), true);
        assertNull(jb.poll(), "must not play before prefill");
        jb.put(11, f(11), false);
        JitterBuffer.Slot s1 = jb.poll();
        assertNotNull(s1);
        assertEquals(10, s1.seq16);
        assertArrayEquals(f(10), s1.frame);
        assertEquals(11, jb.poll().seq16);
    }

    @Test
    void reorderedPacketsPlayInOrder() {
        JitterBuffer jb = new JitterBuffer(3, 8);
        jb.put(5, f(5), true);
        jb.put(7, f(7), false);
        jb.put(6, f(6), false);
        assertEquals(5, jb.poll().seq16);
        assertEquals(6, jb.poll().seq16);
        assertEquals(7, jb.poll().seq16);
    }

    @Test
    void lostPacketYieldsConcealmentSlotThenContinues() {
        JitterBuffer jb = new JitterBuffer(2, 8);
        jb.put(1, f(1), true);
        jb.put(3, f(3), false); // 2 never arrives
        assertEquals(1, jb.poll().seq16);
        JitterBuffer.Slot lost = jb.poll();
        assertNotNull(lost);
        assertEquals(2, lost.seq16);
        assertNull(lost.frame, "missing packet must surface as a loss slot");
        assertEquals(3, jb.poll().seq16);
        assertEquals(1, jb.lost());
    }

    @Test
    void latePacketIsDropped() {
        JitterBuffer jb = new JitterBuffer(1, 8);
        jb.put(10, f(10), true);
        assertEquals(10, jb.poll().seq16);
        jb.put(11, f(11), false);
        assertEquals(11, jb.poll().seq16);
        jb.put(9, f(9), false); // ancient
        assertEquals(1, jb.lateDropped());
        assertNull(jb.poll()); // nothing new buffered
    }

    @Test
    void wrapsAroundU16Boundary() {
        JitterBuffer jb = new JitterBuffer(2, 8);
        jb.put(65534, f(1), true);
        jb.put(65535, f(2), false);
        jb.put(0, f(3), false);
        jb.put(1, f(4), false);
        assertEquals(65534, jb.poll().seq16);
        assertEquals(65535, jb.poll().seq16);
        assertEquals(0, jb.poll().seq16);
        assertEquals(1, jb.poll().seq16);
        assertEquals(0, jb.lost());
    }

    @Test
    void overflowFastForwardsToFreshAudio() {
        JitterBuffer jb = new JitterBuffer(2, 4);
        for (int i = 0; i < 10; i++) {
            jb.put(i, f(i), i == 0);
        }
        assertTrue(jb.fastForwards() > 0);
        assertTrue(jb.depth() <= 4);
        JitterBuffer.Slot s = jb.poll();
        assertNotNull(s);
        assertTrue(s.seq16 >= 6, "must have skipped ahead, got " + s.seq16);
    }

    @Test
    void emptyStreakReprimes() {
        JitterBuffer jb = new JitterBuffer(2, 8, 3);
        jb.put(1, f(1), true);
        jb.put(2, f(2), false);
        assertEquals(1, jb.poll().seq16);
        assertEquals(2, jb.poll().seq16);
        assertNull(jb.poll());
        assertNull(jb.poll());
        assertNull(jb.poll()); // third empty poll trips the reset
        assertTrue(jb.isPriming());
        // New spurt far away in seq space plays cleanly after re-priming.
        jb.put(500, f(9), true);
        assertNull(jb.poll());
        jb.put(501, f(8), false);
        assertEquals(500, jb.poll().seq16);
    }

    @Test
    void spurtStartOnEmptyBufferResetsTimeline() {
        JitterBuffer jb = new JitterBuffer(1, 8);
        jb.put(100, f(1), true);
        assertEquals(100, jb.poll().seq16);
        // Sender stopped, later starts a new spurt with a jump; empty buffer +
        // spurt flag must not create a giant artificial gap.
        jb.put(4000, f(2), true);
        JitterBuffer.Slot s = jb.poll();
        assertNotNull(s);
        assertEquals(4000, s.seq16);
        assertEquals(0, jb.lost());
        assertFalse(jb.isPriming());
    }

    @Test
    void lossExposesFecFrameFromTheImmediatelyNextPacket() {
        JitterBuffer jb = new JitterBuffer(2, 8);
        jb.put(1, f(1), true);
        jb.put(3, f(3), false); // 2 lost; packet 3 carries in-band FEC for 2
        assertEquals(1, jb.poll().seq16);
        JitterBuffer.Slot lost = jb.poll();
        assertEquals(2, lost.seq16);
        assertNull(lost.frame);
        assertArrayEquals(f(3), lost.fecFrame, "next packet carries FEC for the lost one");
        assertArrayEquals(f(3), jb.poll().frame);
    }

    @Test
    void lossHasNoFecFrameWhenTheNextPacketIsAlsoMissing() {
        JitterBuffer jb = new JitterBuffer(2, 8);
        jb.put(1, f(1), true);
        jb.put(4, f(4), false); // 2 and 3 both lost; 4 carries FEC for 3, not 2
        assertEquals(1, jb.poll().seq16);
        JitterBuffer.Slot lost2 = jb.poll();
        assertEquals(2, lost2.seq16);
        assertNull(lost2.frame);
        assertNull(lost2.fecFrame, "FEC only recovers the immediately-preceding packet");
    }
}
