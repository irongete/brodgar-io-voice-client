# Brodgar.io Proximity Voice Chat

Proximity voice chat for [Haven & Hearth](https://www.havenandhearth.com/)
community clients. Self-contained: one library jar + one adapter file + three lines
in `MapView`. Everything else — connection, mic capture, Opus, encryption,
spatialized playback — is handled for you. Only *relative* vectors ever leave the
machine; the server decides who hears whom.

Pure Java, no native libraries (Opus via the pure-Java
[Concentus](https://github.com/lostromb/concentus)). Targets Java 11 bytecode;
runs on any modern JRE.

## Privacy

The voice server never learns where you are in the world. It receives **no absolute
player positions and no grid / map ids** — only **relative tile-offset vectors**
between you and the players you can already see, which is the minimum needed for
proximity routing and spatial audio. Spatialization is computed locally on your
machine, and there is no peer-to-peer connection, so player IP addresses are never
exposed to other players.

## Full guide

The **[docs/](docs/)** folder is the complete integration guide:
[getting started](docs/getting-started.md) ·
[runtime controls](docs/runtime-controls.md) (mute, volume, mic mode, PTT) ·
[events & state](docs/events-and-state.md) ·
[configuration](docs/configuration.md) ·
[troubleshooting](docs/troubleshooting.md).

The rest of this file is the quick version.

## What's in this repo

| Path | What |
|---|---|
| `src/main/java/io/brodgar/voice/` | the library — everything under `io.brodgar.voice` |
| `adapter/io/brodgar/voice/Voice.java` | the drop-in adapter you copy into your client's source |
| `docs/` | the full integration guide |
| `pom.xml` | builds the library jar |

## Build

```
mvn -q package
```

produces two jars in `target/`:

- **`brodgar-voice-all.jar`** — fat jar (Opus + JSON bundled). Put this one on your
  client's classpath and you're done, nothing else to add.
- `brodgar-voice.jar` — thin jar, if you'd rather manage Concentus + minimal-json
  yourself.

Runs the unit suite (jitter buffer, FEC, AGC, spatializer, wire codec, crypto
round-trips incl. the RFC 5869 HKDF vector).

## Integrate (3 steps)

**1. Add the jar.** Put `brodgar-voice-all.jar` on your client's classpath (a
`lib/` fileset, or the jar manifest `Class-Path` if you launch with `-jar`).

**2. Copy the adapter.** Copy `adapter/io/brodgar/voice/Voice.java` into your
client's source at `src/io/brodgar/voice/Voice.java`. It uses only public `haven.*`
APIs. The server address is the `SERVER` constant at the top (`wss://voice.brodgar.io`).

**3. Add three lines to `haven/MapView.java`:**

```java
// in the MapView constructor (enter the game):
io.brodgar.voice.Voice.attach(this);

// in dispose() (logout — stop voice instantly):
io.brodgar.voice.Voice.detach(this);

// in hit(...) — the ground-click path:
if(inf == null) io.brodgar.voice.Voice.onMove(MapView.this, mc);
```

That's the whole required integration. It never blocks the game thread (all work
runs on background daemon threads), never throws into the game, and if the voice
server is unreachable the game is completely unaffected.

## Runtime control API

The whole control surface is **static calls on `io.brodgar.voice.Voice`** — no
handle to fetch, no `null` to guard, safe from any thread, and every setting is
remembered and re-applied across reconnects.

**Your microphone**

| Call | Effect |
|---|---|
| `Voice.setOpenMic(boolean)` | Mode: on = voice-activated, off = push-to-talk only. |
| `Voice.setMicMuted(boolean)` | Hard-mute your mic (others stop hearing you). |
| `Voice.setMicSensitivity(double)` | Open-mic threshold (lower = more sensitive, default 350). |
| `Voice.setPushToTalk(boolean)` | Hold-to-talk; bind it via `Voice.kb_ptt` (see docs). |

**Playback & other players** (keyed by the gob id your client already has)

| Call | Effect |
|---|---|
| `Voice.setMasterVolume(float)` | Master volume over everyone, `0..4`. |
| `Voice.setDeafened(boolean)` / `isDeafened()` | Silence / restore all incoming audio. |
| `Voice.togglePlayerMuted(long gob)` / `setPlayerMuted(gob, on)` | Mute/unmute one player. |
| `Voice.setPlayerVolume(long gob, float)` | Per-player volume, `0..4`. |

**"Talking" indicator** — light up an icon over speaking players (cheap enough per gob per frame):

```java
boolean talking = io.brodgar.voice.Voice.isSpeaking(gob.id); // works for others AND yourself
```

**State & events**

- `Voice.audible()` — who you can hear · `Voice.heardBy()` / `Voice.canHearMe(gob)` — who can hear you (surface this in your UI).
- `Voice.addListener(new io.brodgar.voice.VoiceListener(){ … })` for `onSpeaking`,
  `onAudibleSetChanged`, `onHeardByChanged`, `onConnectionState`, `onError`.

Callbacks fire on a background thread — stash values and read them from your UI loop.

## Set at connect time (in `Voice.java`'s `connect()`)

Via `VoiceConfig.builder()`: `spatialAudio`, `bitrate`, jitter buffer sizing,
`vad`/`vadThresholdRms`, `agc`, `autoReconnect`. The mic and speakers stay open
across automatic reconnects, so your mute/volume/mic state survives a dropped
connection.

## License

[MIT](LICENSE) © Brodgar.io — free to embed, modify and redistribute in your client.

The distributed jar bundles [Concentus](https://github.com/lostromb/concentus)
(pure-Java Opus) and [minimal-json](https://github.com/ralfstx/minimal-json); each
keeps its own license.
