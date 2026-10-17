# Bugs

Format: **symptom → cause → fix (or workaround)**, with the date and files. Bugs that aren't fixed yet go under "Open".

## Open / known limitations

- **`FxKey` is per dimension** (`ServerFx` uses `KeyInLevel(dimension, key)`). If the code calling stop only knows the caster's current level (for example, Iron's cancels the cast after the dimension change), the key isn't found.
  - Current workaround: BHSpells calls `BHFx.stop` on every level (`BhfxLoadedBridge.annihilationChargeEnd`).
  - Planned fix: add a `stopEverywhere(server, key)` API (decide during the API-lock step in [`roadmap.md`](roadmap.md)).

## Fixed

### 2026-10-17 — Fx following an entity stays on the server forever
- **Symptom**: if the followed entity disappears, dies or changes dimension within the first 10 ticks, an `UNTIL_STOPPED` effect is never removed. It gets re-sent to anyone passing the original spot, and it counts against `maxActivePerCaster`.
- **Cause**: `followLost()` returns true only once `followSeen` is true, but that flag was only set by the tracker, which runs every 10 ticks.
- **Fix**: `ServerFx.start` sets `followSeen` immediately when `level.getEntity(followId)` exists.
- Found by: `bhspells-reviewer`, while reviewing Annihilation Arrow.

### 0.3 — GPU timer clashes with F3
- **Symptom**: `GL_INVALID_OPERATION` every frame while F3 is open.
- **Cause**: a `GL_TIME_ELAPSED` query can't overlap F3's own query.
- **Fix**: `GpuTimer` switched to `GL_TIMESTAMP`.

### 0.3 — `/bhfx bench` reports negative values
- **Symptom**: a first-half / second-half bench gave a negative difference.
- **Cause**: FPS drifts over time on its own.
- **Fix**: toggle drawing off and on every 1 second.
