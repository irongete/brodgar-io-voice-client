package io.brodgar.voice.wire;

import io.brodgar.voice.MovementIntent;
import io.brodgar.voice.Protocol;
import io.brodgar.voice.ProtocolException;
import io.brodgar.voice.VisibleGob;
import io.brodgar.voice.msg.Bye;
import io.brodgar.voice.msg.ClientHello;
import io.brodgar.voice.msg.EdgesUpdate;
import io.brodgar.voice.msg.ErrorMessage;
import io.brodgar.voice.msg.Report;
import io.brodgar.voice.msg.ServerWelcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireCodecTest {

    private static byte[] key32() {
        byte[] k = new byte[Protocol.HANDSHAKE_KEY_BYTES];
        for (int i = 0; i < k.length; i++) {
            k[i] = (byte) (i * 3 + 1);
        }
        return k;
    }

    @Test
    void helloRoundTrip() throws Exception {
        String json = WireCodec.encode(new ClientHello(Protocol.VERSION, "test-client/1.0", key32()));
        ClientHello h = (ClientHello) WireCodec.decodeClientMessage(json);
        assertEquals(Protocol.VERSION, h.protoVersion());
        assertEquals("test-client/1.0", h.clientInfo());
        assertArrayEquals(key32(), h.publicKey());
    }

    @Test
    void helloRejectsWrongKeyLength() {
        String badJson = "{\"type\":\"hello\",\"proto\":1,\"client\":\"c\",\"pub\":\"00112233\"}";
        assertThrows(ProtocolException.class, () -> WireCodec.decodeClientMessage(badJson));
    }

    @Test
    void reportRoundTrip() throws Exception {
        Report in = new Report(481, 1721558400123L, 12345,
                Arrays.asList(new VisibleGob(22222, 10.2, -3.1), new VisibleGob(33333, -0.5, 7.0)),
                Collections.singletonList(new MovementIntent(1721558399870L, 6.0, 0.0)));
        Report out = (Report) WireCodec.decodeClientMessage(WireCodec.encode(in));
        assertEquals(481, out.seq());
        assertEquals(1721558400123L, out.tMillis());
        assertEquals(12345, out.selfGobId());
        assertEquals(in.visible(), out.visible());
        assertEquals(in.intents(), out.intents());
    }

    @Test
    void reportWithoutCharacter() throws Exception {
        Report in = new Report(1, 5, Report.NO_GOB, Collections.emptyList(), Collections.emptyList());
        Report out = (Report) WireCodec.decodeClientMessage(WireCodec.encode(in));
        assertEquals(Report.NO_GOB, out.selfGobId());
        assertTrue(out.visible().isEmpty());
    }

    @Test
    void reportMatchesSpecShape() throws Exception {
        // The documented report wire shape must stay parseable.
        String json = "{\"type\":\"report\",\"seq\":481,\"t\":1721558400123,"
                + "\"self\":{\"gobId\":12345},"
                + "\"visible\":[{\"gobId\":22222,\"dx\":10.2,\"dy\":-3.1}],"
                + "\"intents\":[{\"t\":1721558399870,\"dx\":6.0,\"dy\":0.0}]}";
        Report r = (Report) WireCodec.decodeClientMessage(json);
        assertEquals(12345, r.selfGobId());
        assertEquals(1, r.visible().size());
        assertEquals(22222, r.visible().get(0).gobId());
        assertEquals(10.2, r.visible().get(0).dx());
        assertEquals(1, r.intents().size());
    }

    @Test
    void welcomeRoundTrip() throws Exception {
        ServerWelcome in = new ServerWelcome(1, 7, "127.0.0.1", 7771,
                "00112233445566778899aabbccddeeff", key32(),
                48000, 1, 20, 512, 500, 2500, 256);
        ServerWelcome out = (ServerWelcome) WireCodec.decodeServerMessage(WireCodec.encode(in));
        assertEquals(7, out.sessionId());
        assertEquals("127.0.0.1", out.udpHost());
        assertEquals(7771, out.udpPort());
        assertEquals("00112233445566778899aabbccddeeff", out.udpTokenHex());
        assertArrayEquals(key32(), out.publicKey());
        assertEquals(48000, out.sampleRate());
        assertEquals(2500, out.freshnessMs());
    }

    @Test
    void edgesRoundTrip() throws Exception {
        EdgesUpdate in = new EdgesUpdate(new long[]{333, 111}, new long[]{111});
        EdgesUpdate out = (EdgesUpdate) WireCodec.decodeServerMessage(WireCodec.encode(in));
        assertArrayEquals(new long[]{111, 333}, out.hear());
        assertArrayEquals(new long[]{111}, out.heardBy());
    }

    @Test
    void errorAndByeRoundTrip() throws Exception {
        ErrorMessage e = (ErrorMessage) WireCodec.decodeServerMessage(
                WireCodec.encode(new ErrorMessage(Protocol.ERR_PROTO_MISMATCH, "boom", true)));
        assertEquals(Protocol.ERR_PROTO_MISMATCH, e.code());
        assertTrue(e.fatal());
        assertTrue(WireCodec.decodeClientMessage(WireCodec.encode(new Bye())) instanceof Bye);
    }

    @Test
    void rejectsGarbage() {
        assertThrows(ProtocolException.class, () -> WireCodec.decodeClientMessage("not json"));
        assertThrows(ProtocolException.class, () -> WireCodec.decodeClientMessage("[1,2,3]"));
        assertThrows(ProtocolException.class, () -> WireCodec.decodeClientMessage("{\"type\":\"nope\"}"));
        assertThrows(ProtocolException.class, () -> WireCodec.decodeServerMessage("{\"type\":\"report\"}"));
    }

    @Test
    void rejectsOversizedVisibleList() {
        List<VisibleGob> tooMany = new ArrayList<>();
        for (int i = 0; i < Protocol.MAX_VISIBLE + 1; i++) {
            tooMany.add(new VisibleGob(i + 1, 0, 0));
        }
        String json = WireCodec.encode(new Report(1, 1, 5, tooMany, Collections.emptyList()));
        assertThrows(ProtocolException.class, () -> WireCodec.decodeClientMessage(json));
    }

    @Test
    void rejectsNonFiniteAndHugeVectors() {
        assertThrows(ProtocolException.class, () -> WireCodec.decodeClientMessage(
                "{\"type\":\"report\",\"seq\":1,\"t\":1,\"self\":{\"gobId\":5},"
                        + "\"visible\":[{\"gobId\":2,\"dx\":1e300,\"dy\":0}],\"intents\":[]}"));
        assertThrows(ProtocolException.class, () -> WireCodec.decodeClientMessage(
                "{\"type\":\"report\",\"seq\":1,\"t\":1,\"self\":{\"gobId\":5},"
                        + "\"visible\":[{\"gobId\":2,\"dx\":NaN,\"dy\":0}],\"intents\":[]}"));
    }

    @Test
    void rejectsNonPositiveGobIds() {
        assertThrows(ProtocolException.class, () -> WireCodec.decodeClientMessage(
                "{\"type\":\"report\",\"seq\":1,\"t\":1,\"self\":{\"gobId\":0},\"visible\":[],\"intents\":[]}"));
        assertThrows(ProtocolException.class, () -> WireCodec.decodeClientMessage(
                "{\"type\":\"report\",\"seq\":1,\"t\":1,\"self\":{\"gobId\":5},"
                        + "\"visible\":[{\"gobId\":-3,\"dx\":0,\"dy\":0}],\"intents\":[]}"));
    }
}
