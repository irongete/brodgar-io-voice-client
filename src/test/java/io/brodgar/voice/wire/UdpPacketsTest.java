package io.brodgar.voice.wire;

import io.brodgar.voice.Protocol;
import io.brodgar.voice.ProtocolException;
import io.brodgar.voice.crypto.AeadChannel;
import io.brodgar.voice.crypto.KeyExchange;
import io.brodgar.voice.crypto.SessionKeys;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UdpPacketsTest {

    private static byte[] token() {
        byte[] t = new byte[Protocol.UDP_TOKEN_BYTES];
        for (int i = 0; i < t.length; i++) {
            t[i] = (byte) (i * 7 + 1);
        }
        return t;
    }

    private static byte[] sealedStub() {
        // A minimally valid sealed blob (content is opaque to the framer).
        return new byte[Protocol.UDP_NONCE_BYTES + Protocol.UDP_TAG_BYTES + 4];
    }

    // ------------------------------------------------------------- framing

    @Test
    void clientFrameRoundTrip() throws Exception {
        byte[] sealed = sealedStub();
        byte[] pkt = UdpPackets.clientFrame(Protocol.PT_AUDIO, token(), sealed);
        UdpPackets.ClientFrame f = UdpPackets.parseClientFrame(pkt, pkt.length);
        assertEquals(Protocol.PT_AUDIO, f.type);
        assertArrayEquals(token(), f.token);
        assertArrayEquals(sealed, f.sealed);
    }

    @Test
    void serverFrameRoundTrip() throws Exception {
        byte[] sealed = sealedStub();
        byte[] pkt = UdpPackets.serverFrame(Protocol.PT_AUDIO_FWD, sealed);
        UdpPackets.ServerFrame f = UdpPackets.parseServerFrame(pkt, pkt.length);
        assertEquals(Protocol.PT_AUDIO_FWD, f.type);
        assertArrayEquals(sealed, f.sealed);
    }

    @Test
    void clientAndServerFramesAreDistinguished() {
        byte[] audio = UdpPackets.clientFrame(Protocol.PT_AUDIO, token(), sealedStub());
        assertThrows(ProtocolException.class, () -> UdpPackets.parseServerFrame(audio, audio.length));
        byte[] fwd = UdpPackets.serverFrame(Protocol.PT_AUDIO_FWD, sealedStub());
        assertThrows(ProtocolException.class, () -> UdpPackets.parseClientFrame(fwd, fwd.length));
    }

    @Test
    void rejectsBadMagicVersionAndLength() {
        byte[] ok = UdpPackets.serverFrame(Protocol.PT_PONG, sealedStub());
        byte[] badMagic = ok.clone();
        badMagic[0] = 'X';
        assertThrows(ProtocolException.class, () -> UdpPackets.parseServerFrame(badMagic, badMagic.length));
        byte[] badVer = ok.clone();
        badVer[2] = 9;
        assertThrows(ProtocolException.class, () -> UdpPackets.parseServerFrame(badVer, badVer.length));
        assertThrows(ProtocolException.class,
                () -> UdpPackets.parseServerFrame(new byte[]{'B', 'V'}, 2));
        assertThrows(ProtocolException.class,
                () -> UdpPackets.parseServerFrame(new byte[Protocol.MAX_DATAGRAM_BYTES + 1], Protocol.MAX_DATAGRAM_BYTES + 1));
    }

    @Test
    void rejectsTruncatedSealedPayload() {
        byte[] tooShort = UdpPackets.serverFrame(Protocol.PT_PONG,
                new byte[Protocol.UDP_NONCE_BYTES + Protocol.UDP_TAG_BYTES]); // no inner byte
        assertThrows(ProtocolException.class, () -> UdpPackets.parseServerFrame(tooShort, tooShort.length));
    }

    // ------------------------------------------------------------- inner codecs

    @Test
    void audioInnerRoundTrip() throws Exception {
        byte[] opus = {1, 2, 3, 4, 5};
        byte[] pt = UdpPackets.encodeAudioInner(65535, Protocol.FLAG_SPURT_START, opus, opus.length);
        UdpPackets.AudioInner a = UdpPackets.decodeAudioInner(pt);
        assertEquals(65535, a.seq);
        assertEquals(Protocol.FLAG_SPURT_START, a.flags);
        assertArrayEquals(opus, a.opus);
    }

    @Test
    void forwardedInnerRoundTrip() throws Exception {
        byte[] opus = {9, 8, 7};
        byte[] pt = UdpPackets.encodeForwardedInner(0x0102030405060708L, 42, 0, opus, opus.length);
        UdpPackets.ForwardedInner f = UdpPackets.decodeForwardedInner(pt);
        assertEquals(0x0102030405060708L, f.senderGob);
        assertEquals(42, f.seq);
        assertArrayEquals(opus, f.opus);
    }

    @Test
    void timestampInnerRoundTrip() throws Exception {
        byte[] pt = UdpPackets.encodeTimestampInner(123456789012345L);
        assertEquals(123456789012345L, UdpPackets.decodeTimestampInner(pt));
    }

    @Test
    void rejectsOversizedOpusInner() {
        byte[] big = new byte[Protocol.MAX_OPUS_FRAME_BYTES + 1];
        assertThrows(IllegalArgumentException.class,
                () -> UdpPackets.encodeAudioInner(1, 0, big, big.length));
    }

    // ------------------------------------------------------------- full path

    @Test
    void fullEncryptedAudioPathClientToServer() throws Exception {
        // Two parties agree keys, then a full seal->frame->parse->open->decode.
        KeyPair c = KeyExchange.generateKeyPair();
        KeyPair s = KeyExchange.generateKeyPair();
        byte[] secretC = KeyExchange.agree(c.getPrivate(), s.getPublic());
        byte[] secretS = KeyExchange.agree(s.getPrivate(), c.getPublic());
        SessionKeys clientKeys = SessionKeys.derive(secretC, token());
        SessionKeys serverKeys = SessionKeys.derive(secretS, token());
        AeadChannel client = clientKeys.clientChannel();
        AeadChannel server = serverKeys.serverChannel();

        byte[] opus = {10, 20, 30, 40};
        byte[] inner = UdpPackets.encodeAudioInner(7, 0, opus, opus.length);
        byte[] datagram = UdpPackets.clientFrame(Protocol.PT_AUDIO, token(), client.seal(inner));

        UdpPackets.ClientFrame frame = UdpPackets.parseClientFrame(datagram, datagram.length);
        assertArrayEquals(token(), frame.token);
        UdpPackets.AudioInner decoded = UdpPackets.decodeAudioInner(server.open(frame.sealed));
        assertEquals(7, decoded.seq);
        assertArrayEquals(opus, decoded.opus);
    }

    @Test
    void forgedTokenWithoutKeyFailsToOpen() throws Exception {
        KeyPair c = KeyExchange.generateKeyPair();
        KeyPair s = KeyExchange.generateKeyPair();
        SessionKeys serverKeys = SessionKeys.derive(
                KeyExchange.agree(s.getPrivate(), c.getPublic()), token());

        // Attacker knows the token but not the key: crafts a bogus sealed blob.
        byte[] bogus = new byte[Protocol.UDP_NONCE_BYTES + Protocol.UDP_TAG_BYTES + 4];
        byte[] datagram = UdpPackets.clientFrame(Protocol.PT_AUDIO, token(), bogus);
        UdpPackets.ClientFrame frame = UdpPackets.parseClientFrame(datagram, datagram.length);
        assertThrows(java.security.GeneralSecurityException.class,
                () -> serverKeys.serverChannel().open(frame.sealed));
    }
}
