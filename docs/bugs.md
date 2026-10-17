# Bugs

Format: **symptom → cause → fix (or workaround)**, with the date and files. Bugs that aren't fixed yet go under "Open".

## Open / known limitations

- (none)

## Fixed

### 2026-10-17 — `FxKey` was per dimension (0.3.2)
- **Symptom**: stopping by key from a level other than the one the effect started in did nothing (Iron's cancels a cast from the new level after a dimension change).
- **Cause**: `ServerFx` stored keys as `KeyInLevel(dimension, key)`.
- **Fix**: keys are server-wide (`ServerFx.KEYS: Map<FxKey, Integer>`); `BHFx.stop(level, key, mode)` and `handle(level, key)` find the effect in any dimension. BHSpells' loop over every level still works and can be simplified later.

### 2026-10-17 — Effects not visible in the Mingyue Eclipse modpack (resolved)
- **Symptom**: bhfx loaded and warmed up its shaders, but the user saw no effects.
- **Cause (most likely)**: the tests ran while the Oculus shader pack was on, and `offWithShaderpack = true` turns post-processing off by design (the log shows `/bhfx bench` running with "shader pack active; post effects off").
- **Result**: after a restart with the new jar and `offWithShaderpack = false`, the user confirmed effects are visible both with the pack on (Complementary, "post forced on") and with it off. No downgrade fired; bhfx GPU time was 0.3–0.7 ms.

### 2026-10-17 — Auto-downgrade message always said "-1.0 ms"
- **Symptom**: the chat/log message read "effects used -1.0 ms of GPU per frame" (seen in the Mingyue Eclipse modpack).
- **Cause**: `ClientFxManager.checkBudget` called `r.resetBudget()` before formatting `r.averageGpuMs()`, so it printed the reset value. The downgrade itself was real.
- **Fix**: read the average into `usedMs` before the reset. Build passed; not yet seen in game.

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
