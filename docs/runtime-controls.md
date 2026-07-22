# Runtime controls

Everything you wire into menus, keybinds and your HUD is a **static call on
`io.brodgar.voice.Voice`**. There is no session object to fetch and no `null` to
guard — call the methods any time, from any thread:

```java
io.brodgar.voice.Voice.setMasterVolume(1.5f);
io.brodgar.voice.Voice.setPlayerMuted(gob.id, true);
```

`Voice` remembers every setting and re-applies it automatically after a dropped
connection, so your users' choices survive reconnects. Getters read the remembered
value, so they work even before the first connection.

## Master switch

An "Enable voice chat" checkbox. Off closes the connection entirely (nothing sent
or received) and **stays off**; on (re)starts it while you're in-game.

```java
io.brodgar.voice.Voice.setEnabled(on);          // true / false
boolean on        = io.brodgar.voice.Voice.isEnabled();
boolean connected = io.brodgar.voice.Voice.isConnected();   // live session up?
```

To ship **disabled by default**, call `Voice.setEnabled(false)` once at startup.

## Your microphone

| Call | Effect |
|---|---|
| `Voice.setOpenMic(boolean)` / `isOpenMic()` | **Mode.** On = open-mic (voice-activated); off = mic closed, only push-to-talk opens it. |
| `Voice.setMicMuted(boolean)` / `isMicMuted()` | Hard-mute your mic (others stop hearing you), independent of the mode. |
| `Voice.setMicSensitivity(double)` / `micSensitivity()` | Open-mic threshold (~16-bit RMS; **lower = more sensitive**, default 350). |
| `Voice.setPushToTalk(boolean)` | Hold-to-talk: open the mic while a key is held. |

**Open-mic vs push-to-talk** is a single choice via `setOpenMic`:

```java
io.brodgar.voice.Voice.setOpenMic(true);    // voice-activated
io.brodgar.voice.Voice.setOpenMic(false);   // push-to-talk only
```

**Push-to-talk:** call `Voice.setPushToTalk(true)` when your PTT key goes down and
`false` when it comes up (works when open-mic is off; harmless when it's on).
`Voice.kb_ptt` is a ready-made `haven.KeyBinding` (default **V**) you can offer in
your keybindings UI and match against key events, if you'd rather not define your
own.

## What you hear

| Call | Effect |
|---|---|
| `Voice.setDeafened(boolean)` / `isDeafened()` | Silence **all** incoming audio while staying connected. |
| `Voice.setMasterVolume(float)` / `masterVolume()` | Master playback volume over everyone, `0..4` (1 = unity). |

```java
io.brodgar.voice.Voice.setMasterVolume(sliderPercent / 100f);   // 0–400 % slider
io.brodgar.voice.Voice.setDeafened(!io.brodgar.voice.Voice.isDeafened());
```

*A total "voice off, stay connected" toggle is just both:*
`Voice.setMicMuted(x); Voice.setDeafened(x);`

## Per player

All keyed by **gob id** — the id your client already has for every visible gob —
so hang these off whatever UI you like (a right-click menu, a party list, …).

| Call | Effect |
|---|---|
| `Voice.setPlayerMuted(long gob, boolean)` / `isPlayerMuted(gob)` | Mute/unmute one player locally (their audio is dropped). |
| `Voice.togglePlayerMuted(long gob)` | Flip that player's mute. |
| `Voice.setPlayerVolume(long gob, float)` / `playerVolume(gob)` | Per-player volume, `0..4` (1 = unity). |

```java
io.brodgar.voice.Voice.togglePlayerMuted(gob.id);
io.brodgar.voice.Voice.setPlayerVolume(gob.id, sliderPercent / 100f);
```

Per-player mutes and volumes are remembered and re-applied across reconnects too.

## "Talking" indicator

Light up an icon over speaking players. Cheap enough to call per gob every frame:

```java
boolean talking = io.brodgar.voice.Voice.isSpeaking(g.id);   // works for OTHER players AND yourself
```

For a list-style UI, `Voice.speakingGobs()` returns everyone talking right now
(including you). "Speaking" carries a ~300 ms hangover so the icon doesn't flicker.

*A player you've locally muted still reports as speaking — they are talking, you
just chose not to hear them — so you can show a "muted but talking" state.*
