# Troubleshooting & notes

## Use headphones

There is **no acoustic echo cancellation**. On open speakers your own playback
feeds back into the mic and everyone hears an echo (and, in proximity, feedback).
This is a deliberate trade-off to keep the client pure-Java with no native
dependencies. Recommend headphones to your users.

## "I can't hear anyone / no one hears me"

The server decides audibility from proximity: two players hear each other only
while both are close enough in-world. A freshly connected session may take a few
seconds before it starts receiving.

Check `Voice.heardBy()` and `Voice.audible()` to see the current sets. If both stay
empty, confirm you're actually near the other player in-world and that both clients
connected (log line `brodgar voice connected`). Also check nobody is deafened
(`Voice.isDeafened()`), muted (`Voice.isMicMuted()`), or has master volume at 0.

## Nothing connects

- Voice problems never affect the game — if the server is unreachable you'll just
  see `WARNING: brodgar voice could not connect (game unaffected)` in the log.
- Check the `SERVER` address in `Voice.java` and that the server is running / reachable.
- Confirm `brodgar-voice-all.jar` is on the client's runtime classpath.

## Threading

Every `VoiceListener` callback fires on a **background thread** — don't touch
widgets from them; stash values and read them from your UI/render loop. The static
`io.brodgar.voice.Voice.*` calls are safe to make from any thread.

## Reconnection & logout

- Drops reconnect automatically with exponential backoff. **Every setting you've
  applied via `Voice.*`** (mute, deafen, volumes, mic mode, per-player mutes,
  listeners) is re-applied on reconnect — nothing is lost.
- `Voice.detach()` on logout closes the session **instantly**, so you never keep
  talking for a character you've left.

## Performance

The integration never blocks the game thread and never throws into the game. The
only recurring cost is a brief snapshot of visible players every 500 ms on a
background thread — no measurable FPS impact.
