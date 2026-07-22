package io.brodgar.voice.crypto;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoTest {

    @Test
    void twoPartiesDeriveTheSameSecret() {
        KeyPair alice = KeyExchange.generateKeyPair();
        KeyPair bob = KeyExchange.generateKeyPair();

        byte[] aPub = KeyExchange.encodePublicKey(alice.getPublic());
        byte[] bPub = KeyExchange.encodePublicKey(bob.getPublic());
        assertEquals(KeyExchange.PUBLIC_KEY_BYTES, aPub.length);

        byte[] secretA = KeyExchange.agree(alice.getPrivate(), KeyExchange.decodePublicKey(bPub));
        byte[] secretB = KeyExchange.agree(bob.getPrivate(), KeyExchange.decodePublicKey(aPub));
        assertArrayEquals(secretA, secretB, "ECDH must agree");
        assertEquals(32, secretA.length);
        assertFalse(Arrays.equals(new byte[32], secretA), "secret must be non-trivial");
    }

    @Test
    void publicKeyRoundTripsThroughRawEncoding() {
        KeyPair kp = KeyExchange.generateKeyPair();
        byte[] raw = KeyExchange.encodePublicKey(kp.getPublic());
        byte[] raw2 = KeyExchange.encodePublicKey(KeyExchange.decodePublicKey(raw));
        assertArrayEquals(raw, raw2);
    }

    @Test
    void hkdfIsDeterministicAndSeparatesByInfo() {
        byte[] ikm = "shared-secret".getBytes();
        byte[] salt = "salt".getBytes();
        byte[] a = Hkdf.deriveKey(ikm, salt, "c2s".getBytes(), 32);
        byte[] b = Hkdf.deriveKey(ikm, salt, "c2s".getBytes(), 32);
        byte[] c = Hkdf.deriveKey(ikm, salt, "s2c".getBytes(), 32);
        assertArrayEquals(a, b, "same inputs -> same key");
        assertFalse(Arrays.equals(a, c), "different info -> different key");
        assertEquals(32, a.length);
    }

    @Test
    void hkdfRfc5869TestVector1() {
        // RFC 5869 appendix A.1
        byte[] ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = hex("000102030405060708090a0b0c");
        byte[] info = hex("f0f1f2f3f4f5f6f7f8f9");
        byte[] okm = Hkdf.deriveKey(ikm, salt, info, 42);
        assertEquals("3cb25f25faacd57a90434f64d0362f2a"
                + "2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
                + "34007208d5b887185865", hex(okm));
    }

    @Test
    void aeadSealOpenRoundTrip() throws Exception {
        SessionKeys keys = freshKeys();
        AeadChannel client = keys.clientChannel();
        AeadChannel server = keys.serverChannel();

        byte[] msg = "hello opus frame".getBytes();
        byte[] sealed = client.seal(msg);        // client -> server uses c2s
        assertArrayEquals(msg, server.open(sealed), "server opens client's c2s traffic");

        byte[] down = "downstream".getBytes();
        byte[] sealedDown = server.seal(down);   // server -> client uses s2c
        assertArrayEquals(down, client.open(sealedDown), "client opens server's s2c traffic");
    }

    @Test
    void wrongKeyCannotOpen() {
        SessionKeys keys = freshKeys();
        SessionKeys other = freshKeys();
        byte[] sealed = keys.clientChannel().seal("secret".getBytes());
        assertThrows(GeneralSecurityException.class, () -> other.serverChannel().open(sealed));
    }

    @Test
    void tamperingIsRejected() {
        SessionKeys keys = freshKeys();
        byte[] sealed = keys.clientChannel().seal("secret".getBytes());
        sealed[sealed.length - 1] ^= 0x01; // flip a tag bit
        assertThrows(GeneralSecurityException.class, () -> keys.serverChannel().open(sealed));
    }

    @Test
    void distinctNoncesForEqualPlaintext() {
        AeadChannel c = freshKeys().clientChannel();
        byte[] a = c.seal("same".getBytes());
        byte[] b = c.seal("same".getBytes());
        assertFalse(Arrays.equals(a, b), "random nonce must make ciphertexts differ");
    }

    @Test
    void endToEndKeyAgreementProducesWorkingChannels() throws Exception {
        // Full handshake: client and server each derive keys from ECDH + token salt.
        KeyPair clientKp = KeyExchange.generateKeyPair();
        KeyPair serverKp = KeyExchange.generateKeyPair();
        byte[] token = "0123456789abcdef".getBytes();

        byte[] sClient = KeyExchange.agree(clientKp.getPrivate(), serverKp.getPublic());
        byte[] sServer = KeyExchange.agree(serverKp.getPrivate(), clientKp.getPublic());
        SessionKeys clientKeys = SessionKeys.derive(sClient, token);
        SessionKeys serverKeys = SessionKeys.derive(sServer, token);

        byte[] up = "mic frame".getBytes();
        assertArrayEquals(up, serverKeys.serverChannel().open(clientKeys.clientChannel().seal(up)));
        byte[] dn = "forwarded frame".getBytes();
        assertArrayEquals(dn, clientKeys.clientChannel().open(serverKeys.serverChannel().seal(dn)));
    }

    private static SessionKeys freshKeys() {
        KeyPair a = KeyExchange.generateKeyPair();
        KeyPair b = KeyExchange.generateKeyPair();
        byte[] secret = KeyExchange.agree(a.getPrivate(), b.getPublic());
        return SessionKeys.derive(secret, "token-salt".getBytes());
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
