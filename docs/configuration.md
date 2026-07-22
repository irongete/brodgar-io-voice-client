# Configuration

Connect-time options live in `Voice.java`'s `connect()`, built with
`VoiceConfig.builder()`. Edit the defaults there. Runtime controls (mute, volume,
mic mode, …) are separate — see [runtime controls](runtime-controls.md).

```java
VoiceConfig cfg = io.brodgar.voice.VoiceConfig.builder()
        .serverUri(SERVER)
        .clientInfo("my-client/1.0")
        .spatialAudio(true)
        .bitrate(24_000)
        .agc(true)
        .autoReconnect(true)
        .build();
```

## Server address

The `SERVER` constant at the top of `Voice.java`:

```java
private static final String SERVER = "wss://voice.brodgar.io";
```

## Options

| Builder call | Default | What |
|---|---|---|
| `serverUri(String)` | — | The `wss://` server address (required). |
| `clientInfo(String)` | `brodgar-voice/…` | Free-form client identifier. |
| `spatialAudio(boolean)` | `true` | Stereo-pan + attenuate remote players by position. |
| `spatialNearTiles(float)` | `4` | Full-volume radius (no attenuation within it). |
| `spatialMinGain(float)` | `0.08` | Attenuation floor so distant-but-in-range players stay audible. |
| `bitrate(int)` | `24000` | Opus bitrate, 8k–64k. |
| `complexity(int)` | `3` | Opus encoder complexity 0–10 (higher = better / more CPU). |
| `jitterPrefillFrames(int)` | `2` | Jitter-buffer prefill (× 20 ms). |
| `jitterMaxFrames(int)` | `10` | Jitter-buffer cap (× 20 ms). |
| `vad(boolean)` | `true` | Start voice-activated (also `Voice.setOpenMic` at runtime). |
| `vadThresholdRms(double)` | `350` | Open-mic threshold (also `Voice.setMicSensitivity` at runtime). |
| `vadHangoverMs(int)` | `250` | How long the mic stays open after you stop speaking. |
| `agc(boolean)` | `true` | Automatic gain control on transmit (normalizes your loudness). |
| `autoReconnect(boolean)` | `true` | Transparent reconnect after a drop. |
| `reconnectBackoffMs(long,long)` | `1000, 30000` | Reconnect backoff min / max. |
| `reportIntervalMs(int)` | `500` | Presence-report cadence. |
| `connectTimeoutMs(long)` | `5000` | Handshake timeout. |

The mic mode, sensitivity, mute, deafen and every volume are **also** adjustable
live via `Voice.*`, and those live values survive reconnects.

## Custom audio devices (advanced)

By default the library uses the system's **default** microphone and speakers. To
use a specific device, or a backend other than `javax.sound`, plug your own into
the builder:

```java
.audioSource(myMic)    // implements io.brodgar.voice.audio.AudioSource
.audioSink(mySpeaker)  // implements io.brodgar.voice.audio.AudioSink
```

- `AudioSource.read(short[] frame)` must **block** until a full 960-sample (20 ms)
  **mono** frame is ready, then return the sample count (or `-1` on end).
- `AudioSink.write(short[] frame, int frames)` must **block** to pace playback;
  `frame` is interleaved **stereo** (L, R, L, R, …), holding `2 * frames` shorts.

To enumerate usable devices (e.g. to build a picker), call
`io.brodgar.voice.audio.AudioDevices.list()` — each `Device` exposes `name`,
`description`, `capture`, `playback`.
