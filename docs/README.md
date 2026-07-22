# Brodgar.io Proximity Voice Chat — integration guide

Everything you need to add proximity voice chat to a Haven & Hearth client, from
the first three lines in `MapView` to wiring push-to-talk, mute and volume into
your menus.

## Contents

1. **[Getting started](getting-started.md)** — install the jar, wire up `MapView`, verify it works.
2. **[Runtime controls](runtime-controls.md)** — mute, volume, mic mode, PTT, per-player mute/volume, speaking indicator.
3. **[Events & state](events-and-state.md)** — listeners, "who can hear me".
4. **[Configuration](configuration.md)** — connect-time options and custom audio devices.
5. **[Troubleshooting & notes](troubleshooting.md)** — headphones, threading, "I can't hear anyone".

## The 30-second version

- Build → drop **`brodgar-voice-all.jar`** onto your client's classpath.
- Copy **`adapter/io/brodgar/voice/Voice.java`** into `src/io/brodgar/voice/`.
- Add **3 lines** to `MapView` (attach / detach / onMove).
- Done — the mic opens, voice is voice-activated and spatialized, and it reconnects automatically.

## Quick reference

The whole API is static calls on **`io.brodgar.voice.Voice`** — no handle to
fetch, no `null` to guard, safe from any thread, and every setting survives
reconnects.

| You want to… | Call |
|---|---|
| Enable / disable the whole system | `Voice.setEnabled(on)` |
| Is a session live? | `Voice.isConnected()` |
| Open-mic vs push-to-talk | `Voice.setOpenMic(true / false)` |
| Mute your own mic | `Voice.setMicMuted(true)` |
| Open-mic sensitivity | `Voice.setMicSensitivity(rms)` |
| Deafen (silence everyone) | `Voice.setDeafened(true)` |
| Master volume (0–4) | `Voice.setMasterVolume(gain)` |
| Mute a player | `Voice.togglePlayerMuted(gobId)` |
| Per-player volume (0–4) | `Voice.setPlayerVolume(gobId, gain)` |
| Is a gob talking? | `Voice.isSpeaking(gobId)` |
| Who can hear me? | `Voice.heardBy()` / `Voice.canHearMe(gobId)` |
| Push-to-talk | `Voice.setPushToTalk(down)` (+ the `Voice.kb_ptt` keybinding) |
| React to changes | `Voice.addListener(listener)` |
