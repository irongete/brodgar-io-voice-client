# Getting started

## Requirements

- A client based on [hafen](https://github.com/dolda2000/hafen-client).
- Java 11 or newer at runtime (the library is Java 11 bytecode).
- Internet access to the Brodgar voice server at `wss://voice.brodgar.io`.

## 1. Add the library

Build it:

```
mvn -q package        # or ./mvnw -q package
```

Drop **`target/brodgar-voice-all.jar`** into your client's `lib/`. It's a fat jar —
the Opus codec and JSON parser are bundled, so there's nothing else to add. Make
sure your build's classpath includes it (e.g. a `lib/**/*.jar` fileset, or add it
to the jar's manifest `Class-Path` if you launch with `-jar`).

## 2. Copy the adapter

Copy **`adapter/io/brodgar/voice/Voice.java`** into your client's source at
`src/io/brodgar/voice/Voice.java`. It uses only public `haven.*` APIs, so it needs
no changes to any existing class.

The server address is the `SERVER` constant at the top:

```java
private static final String SERVER = "wss://voice.brodgar.io";
```

## 3. Wire up MapView (3 lines)

These three lines are the **whole** required integration.

**a) Connect on entering the game** — in the `MapView` constructor:

```java
io.brodgar.voice.Voice.attach(this);
```

**b) Disconnect on logout** — in `MapView.dispose()`:

```java
io.brodgar.voice.Voice.detach(this);
```

(The character can linger server-side after logout; this stops voice instantly.)

**c) Report movement** — in the ground-click path, `MapView.hit(...)`:

```java
if(inf == null) io.brodgar.voice.Voice.onMove(MapView.this, mc);
```

`inf == null` is a click on the ground (a move order); `mc` is the destination.
Reporting it the instant a move is issued is what keeps proximity routing honest.

## 4. Verify

Launch the client. You should see in the log:

```
brodgar voice connected to wss://voice.brodgar.io
```

Open two clients (two characters) standing near each other in-world and talk — you
should hear each other, panned by relative position. If the voice server is down,
the game is completely unaffected (you'll just see a warning in the log).

## What happens automatically

- The **microphone opens** and transmits **voice-activated** by default — no
  push-to-talk needed to start.
- Audio is **spatialized** (stereo pan + distance attenuation) to match the screen.
- The connection **auto-reconnects** with backoff if it drops.
- Only **relative vectors** ever leave the machine; the server decides who hears whom.

## Next

- Wire menus, sliders and a speaking indicator with the **[runtime controls](runtime-controls.md)** (all static `Voice.*` calls, including push-to-talk and per-player mute/volume).
- Tune connect-time options in **[configuration](configuration.md)**.
