# Events & state

## State you can read

| Call | Gives |
|---|---|
| `Voice.audible()` | `Set<Long>` — gobs whose voice you receive right now. |
| `Voice.heardBy()` | `Set<Long>` — gobs the server delivers **your** voice to. |
| `Voice.canHearMe(long gob)` | `boolean` — will this player hear you right now? |
| `Voice.isConnected()` | `boolean` — is a voice session live? |

All return live values (empty sets when not connected), safe to poll from your
render loop.

**Surface "who can hear me."** `heardBy()` is the honest signal for *"am I talking
to someone who will actually receive me?"* — a player is in the set only when
they're in proximity range with a live session. Showing it (a count, or an icon
over players in the set) is part of the design's transparency.

What `heardBy()` does **not** tell you, on purpose: it does not reveal whether the
other player has muted or deafened themselves (those are private client-side
choices), and there is no global "who has voice enabled" roster — only proximity.

## Events

Register a listener to react to changes instead of polling:

```java
io.brodgar.voice.Voice.addListener(new io.brodgar.voice.VoiceListener() {
    public void onSpeaking(long gobId, boolean speaking) { /* highlight the speaker */ }
    public void onAudibleSetChanged(java.util.Set<Long> gobs) { /* update your list */ }
    public void onHeardByChanged(java.util.Set<Long> gobs)   { /* update "who hears me" */ }
    public void onConnectionState(boolean connected)         { /* show a reconnecting spinner */ }
    public void onError(String code, String message, boolean fatal) { /* toast / log */ }
});
```

Every method has an empty default, so override only what you need. Remove with
`Voice.removeListener(...)`. Listeners are remembered and re-registered across
reconnects, so add once. **If you register from a UI widget, remove it when the
widget is destroyed** to avoid leaking a listener each time it's rebuilt.

> **Threading:** callbacks fire on a background thread. Don't touch widgets from
> them — stash what you need in a field and read it from your UI/render loop. (The
> `Voice.*` calls themselves are safe from any thread.)

**Push vs pull:** use the **events** to react to changes once; use the **pull**
calls (`isSpeaking`, `audible`, `heardBy`) in your render loop for live overlays.
