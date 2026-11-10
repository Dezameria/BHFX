# Bugs

Format: **symptom → cause → fix (or workaround)**, with the date and files. Bugs that aren't fixed yet go under "Open".

## Open / known limitations

- 2026-10-22 (minor, display only): `/bhfx status` keeps showing the last measured GPU time after every effect has stopped (e.g. "0 fx, 0 pass(es) … 4.38 ms GPU" for ~40 s until the post idles). Seen in the Mingyue Eclipse log 2026-10-22 00:02:48–00:04:09. Not fixed.

- 2026-11-07 (open, scale plan phase 1/2): effects flicker under `/bhfx stress 70 32 10 bhfx:test_flow bhfx:test_vortex bhfx:test_particles` (user, 2026-11-07 round). Likely causes, both by design today:
  - The particle slot budget (`PostRenderer.particleCap`, MEDIUM 16,384) is spent in draw order, and a batch that doesn't fit is skipped whole. Three of these effects already use 13,184 slots (2026-11-04), so 32 of them need far more than the cap; which batches fit changes as `filled` grows and as effects are evicted and added.
  - `maxActiveFx` 32 evicts the oldest effect for every new start (38 evictions in this run), so running effects vanish mid-play.
  - Not confirmed with `/bhfx status` ("particle slot(s)", "skipped") during the run.
  - Fix in code 2026-11-07 (phase 1, not re-tested): particles are thinned evenly (one slot in N for every batch) instead of whole batches being skipped, and effects past `maxActiveFx` (now 96) fade out and come back instead of being dropped ([`decisions.md`](decisions.md) 2026-11-07).

- 2026-11-10: RIBBON materials see a different `p.seed` (and `p.seconds`) on every segment: `pLife` is `flat`, taken from one end point. Anything keyed on them (noise offsets, dissolve) jumps at each segment, so a strip breaks into slices as it fades (seen in BHSpells `anime_shockwave`, screenshot). Use `p.along`, `p.corner`, `p.age` (interpolated) and per-ribbon constants in `p.data` instead. bhfx's own `test_swirl.fsh` offsets its noise by `p.seed` and has the same flaw. Possible fix in bhfx: a per-ribbon seed, or document it in the particle header.

## Fixed

### 2026-11-05 — bhfx's own `runClient` never started (fixed, checked in game)
- Symptom: `gradlew runClient` in `bhfx/` died at launch every time since Carpet was added as a dev dependency (logs 2026-10-24 to 2026-11-05). `latest.log`: `Mixin apply failed carpet.mixins.json:MinecraftMixin ... Critical injection failure: @Inject annotation on onCloseGame could not find any targets matching '...m_91320_...'`. Reported by the user as "เปิด runclient ของ bhfx ไม่ติด".
- Cause: Carpet (`fg.deobf("maven.modrinth:XqqOkvZz:XMYeNIOx")`) ships a refmap with SRG names (`m_91320_`). In dev, Minecraft uses official/parchment names, and the run config had no refmap remapping, so the mixin found no target.
- Fix: `build.gradle` `runs.configureEach` sets `mixin.env.remapRefMap=true` and `mixin.env.refMapRemappingFile=build/createSrgToMcp/output.srg`. Checked 2026-11-05 04:49: the log shows `Remapping refMap carpet.refmap.json`, and the client reaches the title screen (`Sound engine started`) with no mixin errors. Any other `fg.deobf` mod with mixins needs the same properties.

### 2026-11-04 — Particle effects vanished / flickered when several ran at once (fixed in code, not re-tested in game)
- Symptom: `test_flow` + `test_vortex` + `test_particles` together (one each, 13,184 slots, under the MEDIUM slot cap): some parts disappear or flicker as the camera moves.
- Cause: `PassListImpl.drawList` applied the client option `maxPassesPerFrame` (default 8) to every entry, particle batches included. `test_flow` alone has 8 batches; three effects ~20, so the farthest effect's batches were dropped, and which ones changed with the camera distance.
- Fix: the cap counts screen passes only; particle batches are limited by the per-frame slot cap in `PostRenderer` (consecutive batches share one input copy). JUnit `PassListImplTest`. Config comment updated.

### 2026-11-04 — Second `test_flow` / `test_vortex` round: helix start shook and kinked when walking; vortex hole stayed behind; water hid ground points (fixed in code, not re-tested in game)
- Helix start shaking, and attached + walking the helices "swing and split into sheets": `FlowTestFx` streamed the helices from `tick()` (20 Hz) with the tick position, while the orbit centre was set per frame from the frame position. The ribbon's newest end jumped once a tick, and the tick position runs up to a tick of walking ahead of the frame centre, so each tick's points sat at a different offset. Fix: the helices are streamed in `passes()` from the frame position, like the comets. Rule written into the `FxParticles.orbit` javadoc.
- `test_vortex` hole ring didn't follow the caster: `VortexTestFx` set the `streaks` orbit centre only at each pulse. Fix: set it every frame in `passes()`.
- Ground points over water landed on the bed, which the water's depth hides: `LevelGround.top` now counts fluid surfaces (see `decisions.md`).
- Comet head looked square: `particle/ribbon.fsh` softens the newest 4 % of a ribbon.

### 2026-11-04 — Ramp colours never applied; ribbons with turbulence zigzag; orbiting particles swung when the axis moved (fixed in code, not re-tested in game)
Found in the user's first `test_flow` / `test_vortex` round (BHSpells `runClient`, 21:33–22:05).
- Ramp: the comet tail stayed white (should end red), the embers and the puff were white. Cause: `drawBatch` used the ramp only when `textureReady`, but a texture is uploaded only when something binds it (`FxTextures.get`), and nothing bound a ramp used only by `colorOverLife`, so it was never ready. Fix: `PostRenderer.drawBatch` always binds `PRamp` (the fallback while loading) and switches to ramp mode once ready.
- Zigzag: the comet and helix ribbons looked like lightning (sharp kinks, spiky edges). Cause: `bhfxTurbulence` adds a random per-particle phase (`bhfxSeed` × 2 rad), so neighbouring ribbon points wobbled independently. Fix: in `vertex.vsh` a ribbon point takes its seed from its chain id, so a ribbon wobbles as one strip. Other facings keep the per-particle seed.
- Orbit with a moving axis: attached (`attach @s`) and walking, `test_flow` helices and `test_vortex` dust swung about in big arcs. Cause: particles were stored relative to the batch origin (the first spawn, fixed in the world) and turned round the axis where it is now, so moving the axis changed every particle's radius. The `FxParticles.orbit` javadoc already promised the opposite. Fix: `ParticleBatch` `Cursor.orbit` / `pull` store the particle relative to the orbit centre at that moment (once), `vertex.vsh` places it round the current centre; cull sphere = reach + |centre| (was + 2|centre|). JUnit `orbitingParticlesAreStoredRelativeToTheCentreSoTheyMoveWithIt`. Non-orbiting particles (embers, smoke) still stay in the world and trail behind a moving caster, by design.

### 2026-11-04 — BHSpells `runClient` ran an old bhfx after the jar in `libs/` was replaced (workaround)
- Symptom: after `gradlew build` in bhfx copied a new `bhfx-0.3.9.jar` into `BHSpells/libs/` (15:50), BHSpells' client still loaded the 0.3.9 built at 14:19: no `LevelGround`, `FlowTestFx` or `particle/ribbon.fsh`, so `bhfx:test_flow` and the four new features were missing. Reported by the user as "run client ไม่ติด".
- Cause: BHSpells resolved `net.deizyn:bhfx:0.3.9` from **mavenLocal**, not `libs/`: `~/.m2/repository/net/deizyn/bhfx/0.3.9/bhfx-0.3.9.jar` was the 14:18 `publishToMavenLocal`, and Gradle prefers a repository with a pom over a flatDir artifact, whatever the order. The deobf cache `.input` showed it (`orig=` = the ~/.m2 jar's sha1). Clearing ForgeGradle's deobf cache alone (19:53) only rebuilt it from the same old jar. ForgeGradle's deobf copy (`~/.gradle/caches/forge_gradle/deobf_dependencies/net/deizyn/bhfx/<version>_*`) can also lag behind a replaced jar.
- Fix: bhfx `build.gradle` `copyToBHSpells` now depends on `publishToMavenLocal` (publishing waits for `reobfJar`), so ~/.m2 and `libs/` get the same jar (sha1 checked equal 2026-11-04), and afterwards deletes ForgeGradle's deobf copy of `<mod_version>`. If BHSpells' client is running it holds that copy, the delete fails and the task logs a warning: close the client and delete the folder (or rebuild). Check with `unzip -l` of the cached jar that the new classes are there.

### 2026-11-02 — Floor effects drop out in horizontal lines (fixed; user confirmed in game the same day)
- Symptom: horizontal lines across the lower screen where a floor effect is missing and the plain ground shows (bright lines on `lunebris_aura`'s darkened floor, dark lines across its white sigil star). BHSpells `runClient`, MEDIUM quality; the user first saw it after pass bounds (0.3.7), likely because cheaper passes changed the FX resolution.
- Cause: passes run below native resolution at a non-integer ratio, and `sceneAt` read depth (NEAREST) at the pixel's uv but unprojected at that same uv. Between neighbour pixels the uv moved ~1.18 depth texels while the depth moved 1 or 2, so `dFdx/dFdy(s.pos)` normals tilted every few rows and `floorish` (normal.y ≥ 0.6) failed there.
- Fix: `scene.glsl` `sceneAt` snaps uv to the centre of the depth texel it reads before unprojecting, so the position always matches its depth sample.
- First attempt (same day, did not help): outside a bounded pass's rect, copy the composite's own `sceneLow` 1:1 instead of a separate scaled blit (kept; it makes the outside exactly what the composite divides by). Also added client option `quality.passBounds` (default true) to switch bounds off for A/B checks.

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
