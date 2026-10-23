# Bugs

Format: **symptom → cause → fix (or workaround)**, with the date and files. Bugs that aren't fixed yet go under "Open".

## Open / known limitations

- 2026-10-22 (minor, display only): `/bhfx status` keeps showing the last measured GPU time after every effect has stopped (e.g. "0 fx, 0 pass(es) … 4.38 ms GPU" for ~40 s until the post idles). Seen in the Mingyue Eclipse log 2026-10-22 00:02:48–00:04:09. Not fixed.

## Fixed

### 2026-10-24 — Mirror behind another mirror wiped its reflection; lower part cut at the floor's horizon (fixed in code, not tested in game)
- Symptom: with a mirror floor and a mirror wall standing on it, the wall's reflection was missing below a line that matched the floor's horizon; mirrors behind other mirrors broke up the same way.
- Cause: `MirrorRenderer.setArea` opened each surface's screen area with `GL_ALWAYS` and no test against the world, and the floor (nearest, so drawn last) also covered the screen where its faces lie *behind* the wall, clearing the wall's reflection there. The composite then sampled the empty pixels.
- Fix: `MirrorRenderer.drawFaceDepth` draws a depth pass of this frame's reflecting faces into its own target (`faces`); the new core shader `mirror_area` opens/closes each surface's area only where no nearer mirror face hides it. Works on both paths: the first try (same day) tested against the main depth instead, which the shader-pack path cannot use (the user's screenshot with Complementary still showed the cut).

### 2026-10-23 — Mirror missed players in F5 / under your feet; Figura physics off in the mirror (fixed in code, not tested in game)
- Symptom: a floor mirror never showed the player standing on it; in F5 the mirror in front sometimes showed nothing; Figura fine parts (hair physics) were in slightly wrong places in the reflection.
- Cause: (1) the front-side test used the feet, which lie exactly on a floor mirror's plane. (2) Only 3 points (eyes, middle, feet) were tried and each had to land on the mirror, so a player whose reflection only partly overlapped a small mirror was dropped. (3) The BeeHouse Figura build culls avatars outside the camera frustum (`Avatar.culled`), so a player behind the camera had no avatar in the mirror. (4) The mirror pass ran after the entity pass and re-ran render scripts, so physics scripts stepped twice and read part matrices written by the mirrored render.
- Fix: box-centre side test; 8 sample points plus a fallback ray; `FiguraMirror` clears `culled` and sets `renderScriptsSkipped` for the mirror pass; reflections are drawn at `AFTER_SOLID_BLOCKS`, before the entity pass, so the real render writes the part matrices last.

### 2026-10-23 — Mirror reflection leaked out of the mirror (fixed in code, not tested in game)
- Symptom: on a floor mirror the reflected player showed outside the mirror face, and clouds, caves and mob items showed through the area.
- Cause: the first renderer clipped with the stencil buffer (`RenderTarget.enableStencil()`) and pushed depth to the far plane with `glDepthRange(1,1)`. On the user's setup the main target had no working stencil, and with no stencil buffer GL treats the stencil test as always passing, so nothing was clipped and the far-depth write opened a hole in the world's depth.
- Fix: draw the reflection into its own `TextureTarget` and copy it onto the depth-tested mirror faces with `mirror_composite`; the world's depth/stencil are never written. Known edge: with Fabulous graphics, translucent held block items use `ITEM_ENTITY_TARGET` and may draw into the item-entity target instead of the reflection.

### 2026-10-22 — Settings sliders wrote float noise to the toml (fixed in code, not tested in game)
- **Symptom**: after moving the Shake slider, `bhfx-client.toml` held `shakeScale = 0.30000000000000004`.
- **Cause**: `BHFxSettingsScreen.Slider.current()` snapped with `Math.round(v / step) * step`, which is not exact in binary floating point.
- **Fix**: round the snapped value again to 6 decimals before `set()`.

### 2026-10-20 — Effects disappear after resizing / minimizing the window (fixed in code, not tested in game)
- **Symptom**: after the window is resized (maximize/restore, drag, fullscreen) post effects stop rendering entirely.
- **Cause**: `RenderTarget.resize` deletes and recreates the main target's FBO and colour/depth textures, and the driver often hands back the same ids. `PostRenderer.attachOutput` only re-attached when the colour *id* changed, so `outFbo` kept the deleted (orphaned) texture. Every stage runs through `outFbo`: the snapshot blit read the frozen pre-resize image, the passes and bloom sampled that, and the composite wrote back into the orphan. The whole chain, not just the final write, was disconnected from the screen. It recovered only after `release()` (30 s idle) cleared `outAttached`.
- **Fix**: `PostRenderer.render` compares `frameBufferId`, colour id, depth id and size with the last frame; on any change it calls `release()` + `resolution.reset()` and rebuilds everything.

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
