# Worklog

Daily work log, newest at the top. Format:

```
## YYYY-MM-DD — short title
- repo: files touched — what changed
- result: build / tested in game / not tested
- next: what remains (move it to roadmap.md if it is longer)
```

---

## 2026-11-17 — Stage C3: bolts and beams (0.3.22)
- bhfx: `FxBolt` (api), `FxParticles.BEAM` + `particle/beam.fsh`, `BoltTestFx` (`bhfx:test_bolt`), `ClientBootstrap`; JUnit `FxBoltTest` (2). 0.3.21 → 0.3.22.
- result: build + glslang + JUnit passed; shot in game by Claude (night, noon): works. Stress with 71 effects: 0.63 ms GPU, 0.65 ms CPU. Removed the two rows of grass blocks the wall tests had left in the `bhfx/run` test world. BHSpells untouched (`libs/` restored).
- next: stage D (particle storage pool) or E (opt-in LOD, near-camera fade, half-res stage), or C4 / C5 (mesh particles, model afterimages); the user picks.

---

## 2026-11-17 — Stage C2: marks on surfaces (0.3.21)
- bhfx: `FxDecal` + `PassList.decal`, `MAX_DECALS`, `MAX_DECAL_TEXTURES` (api), `PassListImpl` (`DecalCursor`, `decalRaw` → `decals`, `decalsEntry`), `decals.fsh`, `DecalsTestFx` (`bhfx:test_decals`), `textures/fx/magic_circle.png` (drawn by a script), `DevControl` (waits while minimised); JUnit `PassListDecalsTest` (2). 0.3.20 → 0.3.21.
- result: build + glslang + JUnit passed; shot in game by Claude (noon, night, ground and wall): works. Stress with 71 effects: 0.47 ms GPU. The first in-game run was void (window minimised). BHSpells untouched (`libs/` restored).
- next: C3 bolts / beams on ribbons with a built-in textured-ribbon material.

---

## 2026-11-17 — Stage C1: point lights (0.3.20)
- bhfx: `PassList.light` + `MAX_LIGHTS` (api), `PassListImpl` (`lights`, `light`, `lightsEntry`, `isEmpty`, `drawList`), `lights.fsh`, `LightsTestFx` (`bhfx:test_lights`), `ClientBootstrap`; JUnit `PassListLightsTest` (2). 0.3.19 → 0.3.20.
- result: build + glslang + JUnit passed; shot in game by Claude (night, noon, wall): works; daylight gain lowered once. Stress with 71 lit effects: 0.46 ms GPU. BHSpells untouched (`libs/` restored).
- next: C2 `FxDecals` (textured marks on ground and walls, one pass), then C3 bolts / beams.

---

## 2026-11-17 — Where render CPU goes (0.3.19)
- bhfx: `PostRenderer` (`phases()`, `PHASE_NAMES`, timing round stride / batch upload / uniforms / draw / loop / sweep / bloom / composite; empty batches skipped, `lastIdleBatches`), `FrameStats` (phase sums, split line), `ClientFxManager.renderFrame` (`lastCollectNanos`, `lastDrew`), `ShaderProgram` (`FAST`, `U_*`, index setters), `ClientFxCommands.stress` (seed from the arguments). 0.3.18 → 0.3.19.
- result: build + JUnit passed; three runs in game by Claude. The CPU cost is per draw call in the driver; uniforms by index and skipping empty batches measured no gain. Table in [`testing.md`](testing.md). BHSpells untouched.
- next: stage C1 (`FxLights`). CPU: nothing more unless a real scene needs it (batch merging).

---

## 2026-11-17 — Stage B: in-place runs, rect copies, uniform cache (0.3.18)
- bhfx: `PostRenderer.render` (particle / model runs in place when the input is an effect target and no material reads `DiffuseSampler`; a bounded pass copies only the last pass's rect, `otherAll` / `ox0..oy1`; `lastCopyCount` in `/bhfx status`), `ShaderProgram` (`Slot`, `set1`..`set4`, `setInt`, `setMatrix`), `UniformBinder.f1`..`f4`, `PostRenderer.begin` / `bindUnit`. 0.3.17 → 0.3.18.
- result: build + JUnit passed; in game by Claude (new and old code, same script): same look, no errors, **no measurable gain** (table in [`testing.md`](testing.md)). BHSpells untouched (`libs/` restored).
- next: find where render CPU per batch goes (split timing), make stress runs repeatable (fixed spots, fixed resolution); then stage C1 (`FxLights`).

---

## 2026-11-17 — Stage A: measuring tools and baseline (0.3.17)
- bhfx: `FrameStats` (fps, longest frame, slots drawn, thinning, `report(now, window)`), `ClientFxManager.statsLines(seconds)`, `PostRenderer.lastParticleCount()`, `DevControl` lines `stats [s]` and `fps`; JUnit `FrameStatsTest` (2). 0.3.16 → 0.3.17.
- result: build + JUnit passed; baseline measured in game by Claude through dev control (two runs), table in [`testing.md`](testing.md). At 70 effects: 186 fps, render CPU 1.23 ms, GPU 1.53 ms. BHSpells untouched (`libs/` restored).
- note: `/bhfx stress <count> <radius> <seconds>`: seconds is the time the starts are spread over; 10 gives all 70 at once, 30 only about 29.
- next: stage B (in-place particle / model runs, rect-only copies, frame uniforms once per program, resolved locations), compared against this baseline and by sheets.

---

## 2026-11-16 — Review and plan: performance and new effect forms
- bhfx: read `PostRenderer` (frame loop, `begin`, `drawBatch`, bloom, composite), `ParticleRenderer`, `UniformBinder`, the particle / model shader headers and includes. No code change.
- found: every particle / model run copies the whole input target first; a bounded pass copies the whole target before shading its rect; frame-constant uniforms are set again on every draw; built-in uniforms go through `String` lookups per batch; there is no CPU time reading and fps is capped in the stress runs. Fine as is: bloom, snapshot copy, particle upload, GPU timer.
- result: plan in [`roadmap.md`](roadmap.md) "Performance and new effect forms" (stages A–F). Not started; waits for the user's pick.

---

## 2026-11-16 — Particle live window (0.3.16)
- bhfx: `ParticleBatch` (`live`, `liveStart`, `advanceWindow`, `windowRuns`, `WINDOW_MARGIN` 0.25 s), `PostRenderer` (window advanced in `updateParticleStride`; budget, stride and instances count the window; `PWindow` uniform), `ParticleRenderer.draw`, particle `vertex.vsh` (instance → slot through the two runs of the window, multiples of the stride as before); JUnit `ParticleWindowTest` (4). 0.3.15 → 0.3.16.
- result: build + JUnit passed; Claude measured in game alone via dev control (old code stashed for the baseline): instances drawn at 25 s down 73–91 %, sheets look the same. BHSpells untouched (`libs/` restored).
- Stress before / after (Claude, dev control, `status` line added to `DevControl`): thinning at 70 effects 1 in 64 → 1 in 5, GPU 0.75–1.5 → 1.3–2.1 ms (living particles drawn instead of dead ones), idle after the end 0.77 → 0.12 ms. Table in [`testing.md`](testing.md).
- next: optimization plan items 4 (`/bhfx stats` peak vs capacity), 2 and 3 (opt-in LOD, near-camera fade) when wanted.

---

## 2026-11-16 — Shoot polish and dev remote control (0.3.15)
- bhfx: `FxShots` (`sheet.png` contact sheet, per-shot `/bhfx status`, queue for `/bhfx shoots`, `last_batch.txt`), `ClientFxCommands` (`shoots`, `solo` stop of earlier test effects, `shootAt`), `FxCommandArgs` (`solo`), `DevControl` (new: `bhfx_dev/inbox` → commands, `outbox` log, `status.txt`, no pause on focus loss), `ClientEvents` (tick hook, `shoots`), `build.gradle` (`-PquickPlay`, `-Pwindow`), `docs/dev-control.md` (new), README / CHANGELOG / decisions; 0.3.14 → 0.3.15. BHSpells untouched (its `libs/` jar put back after the build copied 0.3.15 in).
- result: build passed; then Claude ran the whole loop alone (quick-play launch, 10-line inbox file, 19 shots, quit). Details in [`testing.md`](testing.md).

---

## 2026-11-16 — Particle tests by `/bhfx shoot`
- In game (bhfx's own client, 2560x1369): `test_particles`, `test_flow`, `test_vortex`, `test_swirl`, 8 shots each, read by Claude. Results in [`testing.md`](testing.md) (0.3.9 section): flow / vortex / swirl behave as their checklists say; vortex colours read white / warm / salmon because the test materials multiply by the fire ramp (not a renderer bug); the burst ring of `test_particles` and swirl's tilted rings not seen in the frames.
- Unblocks the particle optimization plan (roadmap).

---

## 2026-11-16 — Pushed bhfx
- bhfx: `feature/scale-phase0` pushed to `origin` (Dezameria/BHFX), `b684ab4..48a8ea7` (17 commits: 0.3.12–0.3.14). BHSpells left alone at the user's request (8 local commits, not pushed).

---

## 2026-11-16 — Remaining tests by `/bhfx shoot`
- In game (bhfx's own client), Claude read the shots: `test_sdf` ✅, `test_data` ✅ (behind-terrain hiding not shown), `test_model attach @s` sword in the hand (first person, and third person at 3 s), `test_points` not judgeable there (needs BHSpells + a GeckoLib entity, third person). Dark dots in `test_data` glow measured as star rims, not a bug.
- bhfx: `FxShots` hides chat during `gui:true` runs (chat covered half the shots). Build passed; not re-checked in game.
- Lesson: effects from earlier commands were still playing in later shots; `/bhfx stop` between runs.

---

## 2026-11-16 — `/bhfx shoot`, version 0.3.14; model LOD not needed
- Bench by the user: `bhspells:thousand_swords` bhfx GPU 1.12–1.16 ms avg, +0.00–0.02 ms per frame at 137 fps (two runs, 20 s). Under the 1.5 ms bar, so no model LOD.
- bhfx: `impl/client/FxShots` (new), `ClientFxCommands.shoot`, `ClientEvents` (`shoot` registered, `RenderTickEvent` END hook), `FxCommandArgs` (`shots`, `gui` reserved keys); JUnit `FxCommandArgsShotsTest` (3). README / API_PLAN command tables, `release.md`. 0.3.13 → 0.3.14 (BHSpells `build.gradle` too; `mods.toml` unchanged).
- result: build passed; then in game (bhfx's own runClient): 8 of 8 shots of `bhfx:test_model`, times within 8 ms, Claude read the PNGs and found all five swords right.

---

## 2026-11-16 — runClient crash: test animation unreadable by GeckoLib
- bhfx: `assets/bhfx/animations/test_model.animation.json` (`"2.0": 1` → `[1, 1, 1]`), `GeoAnimationLoader` (warns on one-number keyframes), `GeoAnimationLoaderTest` (10). Details in [`bugs.md`](bugs.md).
- result: build passed; BHSpells runClient then loaded to the title screen and into a world (22:29), `bhfx warmed up 104 shader(s)`, no `broken`, no bhfx errors; the only `GL ERROR`s were `65539: Invalid key 0` (typing commands). The user started `bhfx:test_model` and `bhspells:thousand_swords`; how they look is not reported yet.

---

## 2026-11-16 — Version 0.3.13
- bhfx: `gradle.properties` 0.3.12 → 0.3.13; `CHANGELOG.md` (0.3.12 closed 2026-11-16 with body parts .. waves / `FxGround`; `FxPose` and `FxAnimation` moved under 0.3.13); `API_PLAN.md` 5.7 version tags; `release.md` (raise the patch version per finished batch, the user's rule; range text corrected to `[0.3.12,0.4)`).
- BHSpells: `build.gradle` `fg.deobf("net.deizyn:bhfx:0.3.13")` ×2, `libs/bhfx-0.3.13.jar` (by `copyToBHSpells`). `mods.toml` stays `[0.3.12,0.4)`: BHSpells uses no 0.3.13 API yet.
- result: see the commit; not run in game.

---

## 2026-11-16 — Animated models (M3): `FxAnimation`
- bhfx: `api/client/FxAnimation` (new), `impl/render/GeoAnimationLoader` (new: clips, channels, interpolation, easing, bezier), `impl/render/Molang` (new: compiled subset), `impl/render/AnimationLibrary` (new: background load, cache, reload), `ClientBootstrap` (reload listener), `assets/bhfx/animations/test_model.animation.json` (new: "swing", "pop"), `ModelTestFx` (fifth, animated sword); JUnit `GeoAnimationLoaderTest` (9), `MolangTest` (4). Offline: all 15 BHSpells animation files load with no warnings; 0.17 µs per `apply` of `dafeng_bamboo` "Bloom".
- result: bhfx `gradlew build publishToMavenLocal` passed. Not run in game.
- next: in-game check ([`testing.md`](testing.md)).

---

## 2026-11-16 — Posed models (M2): `FxPose`, bones moved on the GPU
- bhfx: `api/client/FxPose` (new), `PassList.model(..., pose, ...)` / `models(..., pose, ...)` (old overloads now defaults with no pose), `impl/render/BoneRig` (new), `GeoModelLoader.Data` + `boneParents` / `bonePivots` / `boneRotations`, `FxModels.Mesh.rig`, `ModelDraw.pose`, `PostRenderer` (`MBones` / `MBoneCount` upload, cull sphere grown by `BoneRig.reach`), `model/vertex.vsh` (per-bone 3x4), `_header.glsl` (`s.local` note), `ModelTestFx` (fourth, posed sword); JUnit `BoneRigTest` (7). Performance choices in [`decisions.md`](decisions.md).
- BHSpells: `libs/bhfx-0.3.12.jar` refreshed; `compileJava` passed (no source change).
- result: bhfx `gradlew build publishToMavenLocal` passed (JUnit, glslang). Not run in game.
- next: in-game check ([`testing.md`](testing.md)); M3 `.animation.json`.

---

## 2026-11-16 — Travelling waves and `FxGround` (orbital-laser review items 2 and 3)
- Checkpoint commits first: bhfx `b6d64ba` (all 0.3.12 work so far), BHSpells `8da5b49` (thousand_swords, GeckoLib points).
- bhfx: `impl/client/Waves` (new: delay = distance / speed, sounds via `SoundManager.playDelayed`, shakes queued per client tick, bounded `Schedule`), `FxContext.soundWave` (×3, defaults) / `shakeWave` (×2) / `waveDelay` / `SPEED_OF_SOUND`, `BHFxClient.soundWave` / `shakeWave` / `waveDelay`, `ClientFxManager.tick` (fires and clears waves); `api/client/FxGround` (new: `surface`, `Batch`), `impl/render/LevelGround` made public; test effect `impl/client/test/WaveTestFx` (`bhfx:test_wave`); JUnit `WavesTest` (4), `FxGroundTest` (3).
- BHSpells `ThousandSwordsFx` round 3: ground through `FxGround` (base at once, strike points 0.5 ms per tick, the rest just before the first volley; its own block scan removed, so leaves now count as ground like everywhere in bhfx), launch / impact sounds and shakes as waves (near trident, mid explosion, far thunder).
- result: bhfx `gradlew build` passed, published + copied; BHSpells `compileJava` passed. Not run in game.

---

## 2026-11-16 — Game crash during `thousand_swords`: NVIDIA driver, hardening
- User: the game crashed. `latest.log` just stops after a 9.5 s stall; no crash report. Windows event log: `java.exe` access violation in `nvoglv64.dll` at 19:08:20 plus LiveKernelEvents, 0.3 s after the model's re-upload (details and hypotheses in [`bugs.md`](bugs.md), Open).
- bhfx: `GlStateGuard` (array / texture-buffer / renderbuffer bindings saved and restored), `FxModels.finish` (restores the previous VAO), `model/vertex.vsh` (broken instances draw nothing).
- result: bhfx `gradlew build` passed (`checkShaders` 23), published + copied; BHSpells `compileJava` passed. Not confirmed that it fixes the crash.

---

## 2026-11-16 — BHSpells `thousand_swords` round 2: swords planted under the floor
- User (in game): the swords stick in below the ground. Cause: `ThousandSwordsFx.ground()` read `Heightmap.Types.MOTION_BLOCKING_NO_LEAVES`, a server-side heightmap the client doesn't have; and `^ ^ ^12` targets tilt with pitch.
- BHSpells `ThousandSwordsFx`: ground found by scanning blocks (collision top / fluid surface, leaves skipped) under the target first (±24, becomes the base height for the dome and strikes), then under each strike point (±10). Lesson in [`integrations/bhspells.md`](integrations/bhspells.md).
- result: BHSpells `compileJava` passed. Not seen in game yet.

---

## 2026-11-16 — Instanced models; BHSpells `thousand_swords` from the user's Blockbench sword
- User: `Geberia_Feihua.bbmodel` (a Java-block Blockbench project, 59 cubes, embedded 1024² texture; every cube pivots on the sword's tip), "a thousand swords slashing", the pivot is the tip.
- bhfx: `PassList.models` (instanced), `FxData.setInstance`, `ModelDraw` (+ `instances`, `count`), `PassListImpl.models`, `PostRenderer.drawModel` (instanced path: no cull, `glDrawArraysInstanced`), `model/vertex.vsh` (per-copy frame from `MInstances` by `gl_InstanceID`, `mInstance`, `mIndex`, `mDet`), `_header.glsl` (`MInstanced`, `Surface.instance` / `index`), `_footer.glsl` (per-copy back-face drop).
- BHSpells: `docs/bhfx/tools/bbmodel_to_geo.py` (new converter), `assets/bhspells/geo/geberia_feihua.geo.json` + `textures/fx/geberia_feihua.png` (converted, tip at 0,0,0), `shaders/bhfx/model/flying_sword.fsh`, `compat/bhfx/client/ThousandSwordsFx` (`bhspells:thousand_swords`, registered in `BhfxClientBridge`, id in `BhfxIds`), `docs/bhfx/thousand_swords.md` + `thousand_swords_model.png`.
- Checked: the converted model bakes with `GeoModelLoader` (708 triangles) and a software render of it (and of `bhfx:test_model`) from the baked mesh and texture looks right from four sides.
- result: bhfx `gradlew build` passed (JUnit, `checkShaders` 23), published + copied; BHSpells `compileJava` passed; `flying_sword.fsh` passed glslang (assembled by hand). Not run in game.
- next: in-game round 1 ([`testing.md`](testing.md)).

---

## 2026-11-16 — Models drawn by bhfx, M1: static `.geo.json` with materials (0.3.12)
- User: turn models themselves into effects through bhfx. Built M1 of the plan (static models, three materials); bones, animation and instancing later.
- Read GeckoLib 4.8.3's `BakedModelFactory`, `GeoQuad`, `FaceUV.Rotation`, `RenderUtils` and `GeoEntityRenderer.renderRecursively` (decompiled with ForgeFlower from the ForgeGradle cache into the session scratchpad) so the bake matches it.
- bhfx: `api/client/FxModel` (new), `PassList.model` (×2), `PassListImpl` (+ `Entry.model`, `drawsGeometry()`: models don't count as screen passes); `impl/render/GeoModelLoader`, `FxModels`, `ModelDraw` (new); `PostRenderer` (model group with `modelFbo` + depth renderbuffer, `drawModel`, `modelRect`, `warmupModel`, cull flip for mirrored frames, sweep / release / status / held bytes); `ShaderManager.getModel` + attribute locations bound in `link`; `UniformBinder.blankTexture`, `FxTextures.blank`; `ShaderWarmup` (model/ materials); `gradle/glsl-check.gradle` (model materials). Shaders `model/vertex.vsh`, `_header.glsl`, `_footer.glsl`, `textured.fsh`, `glow.fsh`, `dissolve.fsh`. Test effect `impl/client/test/ModelTestFx` (`bhfx:test_model`) with `geo/test_model.geo.json` and `textures/fx/test_model.png` (written by a stdlib-only script). JUnit `GeoModelLoaderTest` (8).
- Found: API_PLAN 5.3 listed `vnoise3` / `fbm3` / `hash22` in `noise.glsl`; they never existed (fixed the row).
- result: `gradlew build` passed (all JUnit, `checkShaders` 23 shaders), published + copied to BHSpells; all 17 BHSpells `.geo.json` bake without errors (offline run). Not run in game.
- next: [`testing.md`](testing.md) "Models drawn by bhfx"; then M2.

---

## 2026-11-16 — Named points on entities; GeckoLib bones from BHSpells (0.3.12)
- User: let bhfx effects ride on GeckoLib model bones, without changing anything that exists.
- bhfx: `impl/client/EntityPoints` (new: named points per entity, wanted only when asked, never estimated, 128 max, pruned), `BodyParts` (only new package-private accessors for its capture window: `capturingWorld`, `frame`, `toWorldRotation`, `camera`; behaviour unchanged), `BHFxClient.point` / `points` / `wantsPoints` / `reportPoint` (×2), `FrameContext.point(name)` / `point(entity, name)` / `points()` + `FrameContextImpl`, `EntityPoints.clear()` next to `BodyParts.clear()` (`ClientEvents`, `ClientFxManager`); test effect `impl/client/test/PointsTestFx` (`bhfx:test_points`, reuses `test_data.fsh`).
- BHSpells: `compat/bhfx/client/GeckoBhfxPoints` (new) + one line in `client/fx/BHFxClientIntegration.register`. Checked against GeckoLib 4.8.3's bytecode: `GeoRenderEvent.Entity.CompileRenderLayers` is posted on `MinecraftForge.EVENT_BUS` the first time a renderer's layers are read; `renderRecursively` calls `applyRenderLayersForBone` after `translateAwayFromPivotPoint`.
- result: bhfx `gradlew build` passed (JUnit, `checkShaders`), published + copied to BHSpells; BHSpells `compileJava` passed. Not run in game; no JUnit for points (needs entities and a capture window).
- next: [`testing.md`](testing.md) checklist (first item: existing GeckoLib spells unchanged).

---

## 2026-11-16 — 3D shapes from signed distances, and bones (0.3.12)
- User: build layers 1 + 2 of the 3D plan (SDF includes; bones from Java). Layer 3 (mesh baking) left for later ([`decisions.md`](decisions.md)). Read first how `../orbital-laser`'s Sky Breaks hand moves: Rust samples arm poses 30/s into a table (`sb_hand.rs` `Arm::table`), the pass lerps two poses (`hd_pose`), rebuilds 15 finger bones per pixel (`hd_set`) and measures 18 baked per-bone distance grids (`hb_piece`) in each bone's frame.
- bhfx: shaders `include/sdf3d.glsl`, `include/sdf_march.glsl` (new), frame functions added to `include/data.glsl`, `test_sdf.fsh`; `api/client/FxFrame` (new record), `FxData.setFrame` (default method); test effect `impl/client/test/SdfTestFx` (`bhfx:test_sdf`, registered in `ClientBootstrap`); JUnit `FxFrameTest` (6).
- Docs: `API_PLAN.md` 5.3 + new 5.6, `CHANGELOG.md`, `testing.md` checklist, `decisions.md`, `roadmap.md`, `README.md` include list.
- result: `gradlew build` passed (JUnit, `checkShaders` 19 shaders OK), published to mavenLocal and copied to BHSpells. Not run in game; the hand's proportions and the curl directions are untested guesses.
- next: the `testing.md` checklists for `test_sdf` and `test_data`; a BHSpells spell on it.

---

## 2026-11-16 — `FxData`: runtime data tables for passes (0.3.12)
- User: build the `FxData` plan on 1.20.1 now. Added to the unreleased 0.3.12 (with body parts), on `feature/scale-phase0`, uncommitted.
- bhfx: `api/client/FxData` (new), `Fx.createData`, `Uniforms.data`; `impl/render/DataTable` + `DataTables` (new: buffer texture per table, dirty-range upload, 5 s idle free), `UniformBinder` (binds tables; points every active `samplerBuffer` at unit 5 so none sits on unit 0 next to `DiffuseSampler`), `ShaderProgram.bufferSamplers`, `GlStateGuard.touchBufferUnit` (clears buffer textures on restore), `PostRenderer` (tables in sweep / release / held bytes / status; `binder.begin` in both warmups), `ParticleRenderer` (buffer unit marked; `unbind` now activates unit 5 first: it used to unbind on whatever unit the setup left active). Shaders: `include/data.glsl`, `test_data.fsh`. Test effect `impl/client/test/DataTestFx` (`bhfx:test_data`). JUnit `DataTableTest` (7).
- Docs: `API_PLAN.md` 4.2, 4.4, 5.3, new 5.5; `CHANGELOG.md`; `testing.md` checklist; `plan-fx-data.md` status; `README.md` include list.
- result: `gradlew build` passed (all JUnit suites, `checkShaders` 18 shaders OK incl. `samplerBuffer` under `#version 150`), published to mavenLocal and copied to BHSpells by the build. Not run in game.
- next: the `testing.md` checklist; then one BHSpells effect on `FxData`.

---

## 2026-11-16 — Plans: `FxData` next, and a 1.21.1 NeoForge port (docs only)
- Read `../orbital-laser` (a Horizon mod, Rust/WGSL) to see what its effects do that bhfx can't. Only bending the world
  itself (The Fold, `set-warp`) is out of reach; runtime data tables, block textures in a pass and half-res stages
  have workarounds. Chose `FxData` as the next feature ([`decisions.md`](decisions.md) 2026-11-16).
- bhfx: `docs/plan-fx-data.md`, `docs/plan-neoforge-1.21.1.md` (new); `docs/README.md` (lists `plan-*.md`),
  `docs/roadmap.md`, `docs/decisions.md`.
- result: docs only; no code changed, nothing built. Version facts in the port plan are from memory, not checked.
- next: `FxData` after 0.3.12 is committed and `feature/scale-phase0` is merged; two questions about the port in
  [`roadmap.md`](roadmap.md).

---

## 2026-11-14 — BHSpells `bhspells:higanbana_domain` round 10: auspicious-cloud sheet removed
- User: remove `textures/fx/higan_curl_sheet.png` altogether.
- BHSpells: PNG deleted; `HiganbanaDomainFx` without `CURL_SHEET` / `CURL_RATE` / `curls` / `curlFlow`; `gen_higan_smoke_sheets.py` writes only `higan_smoke_sheet.png` (byte-identical to before); `preview_higan_mist.html` and the `higan_mist.fsh` header updated.
- result: `compileJava` + glslcheck pass; no reference to the sheet left in `src/`. Not seen in game.

---

## 2026-11-14 — BHSpells `bhspells:higanbana_domain` round 9: cumulus flipbook, still xiangyun shapes
- User (a red cumulus photo; three xiangyun drawings): cumulus shape for the smoke; clouds after the drawings in place of `higan_curl_sheet`; mid-round: no need for such a big flipbook, base it on the pictures.
- BHSpells `docs/bhfx/tools/gen_higan_smoke_sheets.py` rewritten for 2×2 sheets: `higan_smoke_sheet.png` a 4-frame cumulus flipbook (11 KB), `higan_curl_sheet.png` four still xiangyun shapes after the drawings (17 KB). `particle/higan_mist.fsh`: uniform `Grid` (cols, rows, frames) — flipbook or one still cell per particle (`data.z`). `HiganbanaDomainFx`: `mistUniforms(u, sheet, frames)`, curls pick one of the four shapes, smaller tilt and growth.
- Lessons: a cloud built from several masses must fade each pixel by the base of the mass it belongs to (argmin of the per-mass SDFs), or per-mass horizontal masks leave straight vertical seams; let the lowest billows reach past the base so the fade, not a billow's round edge, ends the bottom. A few still variants picked per particle (and mirrored) give more variety per kilobyte than a long flipbook of one shape.
- result: `compileJava` + glslcheck pass; checked coloured in the sprite preview. Not seen in game.

---

## 2026-11-14 — BHSpells `bhspells:higanbana_domain` round 8: soft smoke and auspicious-cloud sheets
- User (pictures: Chinese auspicious clouds; soft blurry red smoke): change the two smoke textures to those feels.
- BHSpells `docs/bhfx/tools/gen_higan_smoke_sheets.py` rewritten: `higan_smoke_sheet.png` = soft fuzzy cloud (lumps summed into one mass, warped 3D value-noise fbm so frames flow, rising dissolve threshold; 64 px cells, 14 KB); `higan_curl_sheet.png` = xiangyun (smooth-union lobes, flat base, Archimedean spiral lines, inset top line, waving wisps; 128 px cells, 42 KB); 16 grey levels. `higan_mist.fsh` fringe light 0.25; `mistUniforms` body crimson ×0.6.
- Lesson: a soft, blurry sheet needs few pixels (64 px cells look the same once filtered) — spend resolution only on sheets with thin lines. And with a "grey = detail" colour scheme, keep a soft sheet's grey nearly flat, or its thin fringe gets the detail colour and reads as a rim.
- result: `compileJava` + glslcheck pass; checked coloured in the sprite preview. Not seen in game.

---

## 2026-11-14 — BHSpells `bhspells:higanbana_domain` round 7: smoke spread with the radius, red faint mist, smaller sheets
- User: the smoke is bunched up even with a bigger radius; spread it and its rates with the radius; spread the smoke round the caster over everything in red; make the mist faint; keep files small.
- BHSpells `HiganbanaDomainFx`: the mist ring round the caster → field mist over 0.92R; curls over 0.85R; rates × `areaK` = (radius/32)² (0.3..2.5), vortex throw × `spreadK`; column and vortex rates down; lower alphas; red mist palette. `higanbana_domain.fsh` `THICK` 0.16 → 0.1. `gen_higan_smoke_sheets.py` gained `levels` (24): sheets 69 + 93 KB → 33 + 43 KB.
- Lesson: anything an effect spreads "round the caster" should take its area and its spawn rate from the radius param, or a bigger domain just looks emptier with the same clump in the middle.
- result: `compileJava` + glslcheck pass. Not seen in game.

---

## 2026-11-14 — BHSpells `bhspells:higanbana_domain` round 6: original smoke back, flipbook mist, claws over the field
- User: the round-4 drawn cloud feels odd; bring the original smoke back and mix it in; more black-red smoke ribbons round the body and over the whole area; use two 4x4 hand-painted-style smoke flipbooks (a swelling cloud, S-shaped curls) instead.
- BHSpells: `particle/higan_smoke.fsh` restored to the rounds 1-3 cel puff (light from uniform `Glow`); new `particle/higan_mist.fsh` (4x4 greyscale+alpha flipbook, frames blended over the life, white body -> black-red, grey -> crimson, lower fringe lit); new `docs/bhfx/tools/gen_higan_smoke_sheets.py` -> `textures/fx/higan_smoke_sheet.png`, `higan_curl_sheet.png` (512², 69 KB / 93 KB); `HiganbanaDomainFx`: batches `mist` / `curls`, mist ring and vortex moved to `mist`, new curl stream, claws back to 3 / 3 / 2.6 a second, new `fieldClaw` (10 a second over the field). Previews `preview_higan_mist.html`; `preview_higan_cloud.html` removed.
- Lesson: an image-generator prompt for a sprite sheet can be met procedurally (SDF lobes with smooth union + noise edges, crescent shading, holes for dissipation; stamped discs along an integrated S-curve for tendrils) — deterministic, aligned cells, and the material colours it, so a later hand-made sheet in the same layout drops in.
- result: `compileJava` + glslcheck pass; sheets checked by eye and coloured in the sprite preview. Not seen in game.

---

## 2026-11-14 — BHSpells `bhspells:higanbana_domain` round 5: spider lily shape, cheaper
- User (photo of a red spider lily): the ground flowers should have its outline, not as detailed; mind optimisation.
- BHSpells `particle/higan_flower.fsh` rewritten: stem, wide head, 10 stamens fanning sideways and curving up with orange anthers, 6 ribbon petals curling back into loops. Cost: curves as parabolas with a first-order distance (no `atan`), curls as rings, early exit outside head and stem, and an `fwidth`-based LOD that draws a starburst below ~40 px (also stops thin lines flickering). `HiganbanaDomainFx` flowers 0.9–1.5 blocks. Preview `docs/bhfx/tools/preview_higan_flower.html`.
- result: `compileJava` + glslcheck pass; checked in the sprite preview. Not seen in game.

---

## 2026-11-14 — BHSpells `bhspells:higanbana_domain` round 4: ground off bodies, anime cloud ring
- User (first in-game screenshot + their anime cel-smoke picture): the floor texture was painted over the character; wants the swirl round the body bigger, wrapping the area, turning less, like mist / clouds, in the style of the picture.
- BHSpells `higanbana_domain.fsh`: ground effects only on up-facing surfaces (`dFdx`/`dFdy` normal) below `1.2 + 0.15·r` above the centre; centre light skips the caster and is capped at 1. `higan_smoke.fsh` rewritten as an anime effect cloud (seven painted-over lobes, crescent cel light on the top lobe, curl arcs, outline, coral / mauve palette by height, near-camera fade); `HiganbanaDomainFx`: `cloudUniforms`, new slow three-tier cloud ring (`ringFlow`), vortex slowed, claws cut to accents, smoke `data` layout changed. Preview `docs/bhfx/tools/preview_higan_cloud.html`.
- Lessons: a pass that paints "the ground" by height band also paints entities standing on it — mask by surface normal (and a height cap near the caster). For drawn cel clouds, shade the lobe that is painted on top (last one containing the pixel), not the nearest one (that gives jagged stripes at Voronoi seams), and light it as a crescent outside an offset shadow circle rather than by a straight gradient.
- result: BHSpells `compileJava` + glslcheck pass; cloud checked in the sprite preview. Not seen in game again yet.

---

## 2026-11-14 — BHSpells `bhspells:higanbana_domain` round 3: claws, smoke vortex, spreading web, thorns
- User (red ink swirl, red-black barbed claw strokes, Arcane's cells in a black web): big swirling smoke spreading round; no line rings curling in on the ground, moving smoke texture instead; claw-like aura texture; power webbing over the whole area; no heartbeat sound.
- BHSpells `HiganbanaDomainFx`: the round-2 smoke ribbons → `higan_claw` blades (3 layers, bigger, barbed, hooked, burning edge + additive halo); new smoke vortex (`vortexFlow`, big `higan_smoke` clouds orbiting and pushed out to ~20 blocks); new `higan_thorn` (AXIS thorns shooting out of the ground behind the web front, sinking back on wither); ink strokes and the flat ground strokes removed; `WARDEN_HEARTBEAT` removed. `higanbana_domain.fsh`: sigil / pit / ring → `floorSmokeAt` (polar painterly smoke swirl from blurred mips, cel edges); pools → a warped-Voronoi black web spreading out over 3 s with a ragged front. Deleted `higan_swirl.fsh`, `higan_stroke.fsh`.
- Lesson: fine-grained noise reads as marble on the ground; sampling a higher mip level (`textureLod(..., 2.5–3)`) gives the big soft painterly shapes, then a contrast stretch + hard threshold gives the cel edge.
- result: BHSpells `compileJava` + glslcheck pass; ground checked in the browser preview. Not tested in game. Notes: `BHSpells/docs/bhfx/higanbana_domain.md`.

---

## 2026-11-14 — BHSpells `bhspells:higanbana_domain` rounds 1–2: red-black domain of death
- User (10 reference pictures): the most epic, frightening, powerful aura — red-black smoke spreading over the whole area as if covered by death; concept higanbana. Mid-round: make it smoky, swirling round the body with a technique like radiant_aura.
- BHSpells: new `client/HiganbanaDomainFx` (`BhfxIds.HIGANBANA_DOMAIN`, registered in `BhfxClientBridge`), pass `higanbana_domain.fsh` (dead ground with Voronoi blood pools and black veins, a log-spiral higanbana sigil of petal blades and hooked stamens, shock front and heartbeat rings, centre light, an absorbing 32-step black-red shroud with a wall at the edge and a column round the caster, a vortex sky and far haze while inside), OVERLAY `higanbana_grade.fsh` (drained red grade, ink creeping in from the screen edges), materials `higan_swirl` (cel smoke ribbon), `higan_flower` (procedural spider lily on AXIS), `higan_petal`, `higan_glint`, `higan_smoke` (cel-banded billow), `higan_stroke`. Round 2: radiant_aura's four-layer storm (`storm` / `swirl`) done as smoke ribbons. Preview `docs/bhfx/tools/preview_higanbana.html` (pass only). Design notes: `BHSpells/docs/bhfx/higanbana_domain.md`.
- Lessons: for a dark domain, keep the ground and smoke mostly black and put red only in the pools, sigil edges, centre and underglow (the first draft read as neon); an edge falloff on the volume can eat a wall drawn at the edge, so tear the wall's face with the noise instead; while the sky is replaced, haze far terrain into the same colours or a normal-coloured band shows at the horizon.
- result: BHSpells `compileJava` + glslcheck (8 shaders) pass; the pass checked in the browser preview (inside / outside / night). Not tested in game.
- bhfx `.claude/launch.json`: added `bhspells-root-preview-2` (port 8769) for when 8767 is held by another session.

---

## 2026-11-14 — BHSpells meditation_aura: dark / black colours
- User: make `bhspells:meditation_aura` support black. Everything was additive, so black drew nothing.
- BHSpells `meditation_aura.fsh`: field accumulated as an untinted scalar `glow`; `darkness(tint)` = 1 − smoothstep(0.15, 0.55, max channel) splits it into added light (`tint·glow·(1−dark)`) and absorption (`mix(col, tint, 0.85·(1−exp(−1.3·glow))·dark)`, capped so it stays smoke). `MeditationAuraFx`: same curve in Java; dark > 0.5 → wisps and motes on ALPHA with plain colour, no bloom, first-person vignette in the colour, SMOKE fallback without post.
- Lesson: for an additive effect to take a dark colour, keep the shape field untinted until the end — `tint × field` is zero for black and loses the shape.
- result: BHSpells `compileJava` + glslcheck pass; not tested in game. Design note: `BHSpells/docs/bhfx/meditation_aura.md`.

---

## 2026-11-14 — `bhspells:ink_sphere` round 7: smaller `ink_flame.png`
- User: the texture feels too big; lower the resolution if needed, without losing much detail.
- BHSpells `docs/bhfx/tools/gen_ink_flame_texture.py`: new `out_size` (box-filter shrink from the drawing size, so it is supersampled; the strands are shrunk before they are sharpened, so they stay thin) and `levels` (grey levels per channel, for PNG size). Same seed at 512 / 256 levels reproduces the old file byte for byte. Shipped 256×256 at 32 levels: 339 KB → 58 KB, a quarter of the VRAM; 128 smeared the strands. Preview takes `?tex=` to compare files.
- result: compared offline (strips at equal display size) and in `preview_ink_sphere.html`. Texture only, no code change (the shader reads `textureSize`). Not tested in game.

## 2026-11-14 — `bhspells:ink_sphere` round 6: white / black lines as params
- User: can the overlap of the white and black shades, bottom and top, be adjusted?
- BHSpells: new params `white` (`BhfxIds.WHITE_LINE`, how high the white flames reach, fraction of the height from the bottom, 0.56) and `black` (`BhfxIds.BLACK_LINE`, where the black ink tongues start, 0.28), read in `InkSphereFx`, sent as `P2.xy`, used by `inkAt` in `ink_sphere.fsh`; preview takes `?white=` / `?black=`.
- result: BHSpells `compileJava` passed, glslcheck OK, checked in the preview at 0.35 / 0.5 and 0.75 / 0.15. Not tested in game.

## 2026-11-14 — `bhspells:ink_sphere` round 5: spawn position is the centre
- User: make the spawn point the sphere's centre.
- BHSpells `InkSphereFx`: `center()` returns the effect position as it is (it was position + radius); the bottom used for the base glow and the pass bounds is centre − radius. Shader unchanged. `BhfxIds` / class docs and the test commands in `docs/bhfx/ink_sphere.md` updated (`at ^ ^8 ^20` rests it on the ground; `at ~ ~ ~` puts you in the middle).
- result: BHSpells `compileJava` passed. Not tested in game.

## 2026-11-14 — `bhspells:ink_sphere` round 4: no sideways swing
- User: "not so curved, flowing from the bottom to the top", then while it was being changed: keep the sphere's curve, just don't swing.
- BHSpells `ink_sphere.fsh`: tried a straight-up layout (u across the sphere as the camera sees it), then went back to round 3's wrap round the sphere and removed only the sway: the `sin` wobble of u by height and time, each layer's drift round the sphere, and most of the strand bend (0.14 → 0.04). Every layer now only flows straight up its meridian.
- result: glslcheck OK, checked in `preview_ink_sphere.html`. Shader only. Not tested in game.

## 2026-11-14 — `bhspells:flame_sphere` renamed `bhspells:ink_sphere`, round 3: ink-flame texture
- User: call it an ink sphere, take the fire sounds out, study the reference's pattern (a texture is fine): ink, but a little like fire, a magic liquid drifting round the sphere, as close to the reference as possible.
- BHSpells: renamed id / `BhfxIds.INK_SPHERE` / `InkSphereFx` / `ink_sphere.fsh` / docs / preview. New tileable texture `textures/fx/ink_flame.png` from `docs/bhfx/tools/gen_ink_flame_texture.py` (spectral noise, wraps by construction, channels equalised: R strands, G flame body, B ink columns; optional comparison strip like the reference's left panel). The shader lays it out by height on the sphere (white tongues from a height-rising threshold on G with R strands in them, a solid white band at the bottom that hides the pole, black ink spikes from B above the middle), each layer flowing up and round at its own speed, explicit mip level from texel vs pixel size. Fire sounds removed (beacon hum kept).
- result: BHSpells `compileJava` passed, glslcheck OK, checked in `preview_ink_sphere.html`. Not tested in game.

## 2026-11-14 — `bhspells:flame_sphere` round 2: black and white ink on the surface instead of fire
- User: what flows up the dome is not really fire but a black and white ink feel; look at the reference's texture again.
- BHSpells `flame_sphere.fsh`: removed the raymarched flame volume; the ink is painted on the sphere's surface (`inkAt` / `inkLight`, near wall plus the far wall faintly through it): coordinates round the vertical axis × arc from the bottom pole, drifting up, two layers of domain warp stretched vertically, veins = ridges of the warped noise at three scales, a white body from a height-rising threshold on vertically stretched columns (black ink hangs down between the tongues), grey veins fading towards the top, and the ink climbs with `rise`. `noise_fbm.png` ridges came out speckled (moon surface), so the pattern uses `wrapNoise`, a smooth value noise that wraps round the sphere (no atan seam). `FlameSphereFx` docs, fallback particles (`SQUID_INK` / `END_ROD`) and `BhfxIds` doc updated.
- result: BHSpells `compileJava` passed, glslcheck OK, checked in `preview_flame_sphere.html` (dark and daylight backdrops, timeline). Not tested in game.

## 2026-11-14 — `bhspells:flame_sphere` round 1: sphere of energy with white flames rising inside
- User (reference picture: dark grey-blue see-through sphere, white-hot flames licking up from the lower half, white glow at the base) chose radius 8, fixed in the world, flames rising from the bottom on appearing and dying down from the top on going, grey-blue + white, light on the ground, refraction through the shell.
- BHSpells: new `client/FlameSphereFx` + `flame_sphere.fsh` (one pass, no particles): `raySphere` shell refracting `DiffuseSampler` through `projectUv`, a 40-step flame volume (`flameAt`: cylindrical noise round the vertical axis + 3D value noise flowing up, contrast-stretched, a fire mask whose threshold rises with height so the white body breaks into tongues, ridge wisps), base glow in the air, light on the world, dimmed when the camera is inside. `BhfxIds.FLAME_SPHERE`, registered in `BhfxClientBridge`. Spawn position = bottom of the sphere. Preview `docs/bhfx/tools/preview_flame_sphere.html` (fetches the shader between its `preview` markers). Notes: `BHSpells/docs/bhfx/flame_sphere.md`.
- result: BHSpells `compileJava` passed, glslcheck OK, checked in the browser preview (timeline, daylight / dark backdrops, camera inside). Not tested in game.
- next: user tries `/bhfx test bhspells:flame_sphere at ^ ^ ^20`.

## 2026-11-15 — `bhspells:yin_yang_burst` round 8: heads curl into the arms
- User (two in-game screenshots, arrow at the head/arm joint, red X on grey lines at the white arm's edge): the head looked like a round lump sticking out; wants its edge to curl on into the pattern.
- BHSpells `yin_yang_burst.fsh`: the head's round edge met the arm's straight edge at a right angle at the disc rim. Winding outside the disc is now `1.3·√(r − 1)` (infinitely steep at the rim), so the edge carries on along the rim and peels off into the arm like a tomoe tail; noise and brush coordinates use a softened copy. Removed the round 6 strands (read as rings, likely what the X marked).
- result: glslcheck OK, checked in `preview_yin_yang.html?strip=1`. Shader only. Not tested in game.

## 2026-11-14 — `bhspells:yin_yang_burst` round 7: bigger true-shape yin-yang, cleaner particles, softer blackout
- User (in-game screenshot with the desired S drawn on it): bigger yin-yang, the head bulged out of shape; particles spawned oddly; the blackout should not be pitch black.
- BHSpells: `DISC_RADIUS` 1.6 → 2.6 with the shader's disc-unit radii scaled down (`SWIRL_REACH` 4.2); no winding inside the disc, constant shear across it and only 25% lead there, so heads, eyes and the S keep their shape; ink clouds from blocks only start outside the painted rim and never on the viewer's side of it; `BLACK_DEPTH` 0.85. The cream specks across the user's screenshot were not the texture (bhfx samples LINEAR + mipmaps); probably rain in front of the plane, asked the user.
- result: BHSpells `compileJava` passed, glslcheck OK, checked in `preview_yin_yang.html?strip=1`. Not tested in game.

## 2026-11-14 — `bhspells:yin_yang_burst` round 6: not round, whipping arms, alternating bands drawn inwards
- User: "getting there", but too round; the reference whips and alternates its bands, as if drawing white and black in from all round.
- BHSpells `yin_yang_burst.fsh`: winding eases off outside (`0.75·log(1+r²)` instead of linear, which wrapped the outside into rings), arm tips hook back unevenly (`hook`), the arm split becomes `r·mix(cos a, cos(3a + bandPhase), bands)` so the arms break into alternating bands whose phase slides them inwards, counter-colour strands along the arms, grey patches far out that come in pure, a non-circular rim, `SWIRL_REACH` 5.5. `YinYangBurstFx.RIM` 4.4 to match.
- result: BHSpells `compileJava` passed, glslcheck OK, checked in `preview_yin_yang.html?strip=1`. Not tested in game.

## 2026-11-14 — `bhspells:yin_yang_burst` round 5: outer swirl turning the wrong way
- User: the outer swirl seemed to turn the wrong way against the yin-yang.
- Two causes. (1) `yin_yang_burst.fsh`: the arms were wound with `+twist`, so outer parts led the counter-clockwise spin; now `th = spin − twist·shape` (arms trail). (2) `YinYangBurstFx`: a positive `orbit` is counter-clockwise seen from the axis tip, and the axis pointed away from the viewer, so the ink particles turned clockwise on screen; the batches now orbit round `-axis`.
- result: BHSpells `compileJava` passed, glslcheck OK, direction checked frame by frame in `preview_yin_yang.html?strip=1`. Not tested in game.
- Lesson for `orbit` users: to turn counter-clockwise as the viewer sees it, the orbit axis must point towards the viewer.

## 2026-11-14 — `bhspells:yin_yang_burst` round 4: yin and yang as two bodies swallowing each other
- User: the swirl still turned oddly, like one plate; wants yin and yang separate so they really swirl into each other.
- BHSpells `yin_yang_burst.fsh` `inkField`: yang and yin are evaluated in their own frames (`yangBody`), each with its own lead/lag, arm coil and a non-accumulating core shear (scaled by `sway`); overlaps fold one over the other in inflowing tongues, gaps are marbled churn, eyes ride with their heads (`eyeAt`). `preview_yin_yang.html` gained `?strip=1` (9 frames 0.3 s apart) and `?anim=1`.
- result: glslcheck OK, checked in the browser preview strip. No Java change. Not tested in game.

## 2026-11-14 — `bhspells:yin_yang_burst` round 3: the held vortex as a thick-paint yin-yang
- User (reference: a swirling thick-paint yin-yang, with arrows drawn showing flow into the centre): round 2's held vortex read as "lines and rings", not a yin-yang.
- BHSpells `yin_yang_burst.fsh`: new `inkField` (core barely twisted `th = spin + twist·r²/(1+r)`, low twist so two broad arms sweep out, torn cloud edges far out, dry-brush streaks along the arms in the unwound frame, emboss relief from a second sample). Removed the time-growing winding and the marbled filaments. Fixed a hole in the centre that had been there since round 1 (the fill mask was 0 at r = 0 even when formed). `YinYangBurstFx`: `AHEAD` 3.2 → 5.5, slower vortex spin, STREAK strands and motes removed, block ink as clouds that end at the swirl's rim. New WebGL preview `BHSpells/docs/bhfx/tools/preview_yin_yang.html` (fetches the real shader functions), served by the new `bhspells-root-preview` config in `bhfx/.claude/launch.json`.
- result: BHSpells `compileJava` passed, glslcheck OK, checked in the browser preview. Not tested in game.

## 2026-11-14 — `bhspells:yin_yang_burst` round 2: stirred ink, timed vortex that sucks in the surroundings
- User: wants it to look stirred together like ink, and a set time for the vortex to keep rolling up, sucking black and white in from everything around.
- BHSpells `yin_yang_burst.fsh`: yin-yang field folded 3 times by texture noise after the spiral, marbled filaments where black meets white, grey only at the collapse; winding grows with time (`P3.z`); suction on the camera-facing plane drags the scene in along the spiral (two-phase crossfaded flow, re-projected with `projectUv`) and inks it, colour drains farther out. `YinYangBurstFx`: new `BhfxIds.SWIRL` param (vortex seconds, 6), explicit `duration` sets it too, `hold:true` whirls until a stop which collapses and bursts (`endTicks` returns the rest of the sequence), spin accumulated per frame, ink strands/clouds torn off real block surfaces (light/dark by `MapColor`) spiralling in.
- result: BHSpells `compileJava` passed, glslcheck OK. Not tested in game.

## 2026-11-14 — BHSpells new `bhspells:yin_yang_burst` (yin-yang smoke → grey water burst → blackout)
- User (3 references: thick-paint black/white yin-yang swirl, cloudy yin-yang, black/white smoke vortex): black and white aura smoke gathering in front into a yin-yang like liquid or smoke, swirling, merging, bursting into grey water that covers everything, then a pitch-black screen fading to grey and slowly back to colour.
- BHSpells: `YinYangBurstFx` + `yin_yang_burst.fsh` (camera-facing disc: smooth yin-yang SDF warped by log-polar inflowing noise and wound by radius into spiral arms; then a `raySphere` water ball with surface refraction/froth and depth murk) + `yin_yang_burst_grade.fsh` (OVERLAY: under-water wobble, blackout, drained colour, grey wash). Particles: ALPHA smoke puffs orbit + pull, vortex motes, droplets, spray, reused `haki_wave` shell. `BhfxIds.YIN_YANG_BURST`, registered in `BhfxClientBridge`. Params `radius`, `scale`. Notes: `BHSpells/docs/bhfx/yin_yang_burst.md`.
- result: BHSpells `compileJava` passed, glslcheck OK. Not tested in game; the client must restart to register the new effect.

## 2026-11-14 — aura_release round 3: parts did not fade out at the end
- User: when it goes, parts of it do not fade out.
- Cause: bhfx drops the effect (and all its live particles) the tick `age > duration`, while `burn()` kept spawning full-strength particles living up to 1.8 s until the last tick; a stop ended after 20 ticks, also under the particle life; and the overexposed column faded linearly, so it looked unchanged until it popped off.
- BHSpells `AuraReleaseFx` + `aura_release.fsh`: `DIE_TICKS` 30, particle lives capped at `MAX_LIFE` 1.3 s and at the time left, emission and alpha fade with the effect, eased fade, column brightness by fade², a dissolve uniform (core sinks and thins, the rest drifts up into tongues). Lesson recorded in `integrations/bhspells.md` and the `bhfx-particles` skill.
- result: BHSpells `compileJava` passed, glslcheck OK. Not tested in game.

## 2026-11-14 — aura_release round 2: the column in meditation_aura's style
- User: wants the meditation_aura feel. A first try put meditation_aura's sheath on the body; the user said no: the feel belongs in the rising column itself. That try was removed and `MeditationAuraFx` restored as it was.
- BHSpells `aura_release.fsh`: `pillarAt` rewritten with meditation_aura's technique at column scale (glowing skin round a flared SDF core, tongues lifted by upward-flowing 3D value noise that detach and carry the column past its core, streaks kept), 32 steps with an early-out. `AuraReleaseFx`: scaled-up `meditation_wisp` wisps peel off the column; sustained `power_line` lines halved. Also fixed `pow(negative, 2.0)` in `meditation_aura.fsh`'s breath ring (NaN trap).
- result: BHSpells `compileJava` passed, glslcheck OK. Not tested in game.

## 2026-11-14 — BHSpells new `bhspells:aura_release` (anime aura release column)
- User (reference: an anime power-release scene): a white column of energy erupting up from the floor like an upturned flame, radial burst at the base, layered rising light, turbulent edges, overexposed core, cracked floor; a guideline, not an exact copy.
- BHSpells: `AuraReleaseFx` + `aura_release.fsh` (raymarched column with seamless atan noise via `textureLod`, cast light, radial ground glow, Voronoi cracks, one shockwave ring, wash-out) reusing materials `power_line`, `power_haze`, `haki_wave`, plus `bhfx:streak` / `bhfx:dot` and vanilla block chips. `BhfxIds.AURA_RELEASE`, registered in `BhfxClientBridge`. Params `radius`, `height`, `color`, `color2`. Notes: `BHSpells/docs/bhfx/aura_release.md`.
- result: BHSpells `compileJava` passed, glslcheck OK. Not tested in game; the client must restart to register the new effect.

## 2026-11-14 — BHSpells new `bhspells:meditation_aura` (white meditation aura on the body's shape)
- User (reference: a seated, cross-legged figure in a hugging aura): a faint white aura rising off the body, based on the model; rising upwards only, or just wrapping the body; a calm, focused meditation feel.
- BHSpells: `MeditationAuraFx` + `meditation_aura.fsh` (11 capsules rebuilt from body parts every frame, raymarched thin sheath, tongues lifted by upward-flowing 3D noise, floor light and a breath ring) + material `particle/meditation_wisp.fsh` (VELOCITY wisps) + DOT motes; `BhfxIds.MEDITATION_AURA`, registered in `BhfxClientBridge`. Params `color`, `height` (rise, 0 = wrap only), `density`, `speed`, `scale`. Notes: `BHSpells/docs/bhfx/meditation_aura.md`.
- Tuned in a new WebGL preview of the real pass (`tools/preview_meditation_aura.html`): first version painted a white sheet over the body's front; tongues are now gated off inside the sheath and dimmed over the body's own pixels, raymarch tightened (36 steps, sphere one block smaller than the pass bounds).
- result: BHSpells `compileJava` passed (the running client predates only these files, so it was safe), glslcheck OK, `processResources` done. Not tested in game; the client must restart to register the new effect.

## 2026-11-14 — confinement_song round 2: ghost sound ring away from the effect (fixed)
- User (saw it in game): the echo rings look right, but another ring shows somewhere else in the world.
- Cause: `confinement_song.fsh` measured each sound sphere's rim (and the heart glow and burst flash) from the cage to the whole view line, `tc` unclamped. Looking away from the effect, the line behind the camera passes the cage just the same, so a mirrored ring appeared opposite it.
- Fix: `centreFront` also needs `tc > 0` (`smoothstep(0, 0.4, tc)`). glslcheck OK, `processResources` done (reload resources or restart the client). Not re-tested in game.

## 2026-11-14 — BHSpells new `bhspells:confinement_song` (บทเพลงกักขัง)
- User (two reference images: a glowing crystal cube cage with a figure inside, a tilted astrolabe ring): a cage that lasts 1–2 min, then slowly cracks and bursts with a magic circle. The feel is power carried on guqin notes; the sound's echo resonates with what is inside.
- BHSpells: `ConfinementSongFx` + `confinement_song.fsh` (ray-traced corner-up cube, staff ring and ladder ring, guqin ground circle with 13 hui and 7 strings, sound pulses out and echoes back that make the cage ring, Voronoi cracks, burst circle) + materials `particle/song_note.fsh` (note glyphs) and `particle/cage_shard.fsh` (TUMBLE triangle shards). `BhfxIds.CONFINEMENT_SONG`, registered in `BhfxClientBridge`. A pentatonic melody on harp + guitar note-block sounds drives the pulses. Default: stands 90 s, then bursts; `hold:true` + `stop finish` breaks it. Notes: `BHSpells/docs/bhfx/confinement_song.md`.
- result: BHSpells `compileJava` passed, glslcheck OK (3 shaders). Not tested in game; checklist in [`testing.md`](testing.md).
- tooling: `bhfx-effect-recipes/scripts/glslcheck.py` now spots a particle material from a relative path too (`particle/x.fsh` from the shader folder was checked as a pass and failed).

## 2026-11-14 — BHSpells `crimson_mist` round 2: `density` makes a visible difference
- User: changing `density` a lot gives the same mist. Cause: clamped 0..1, scaled opacity only 0.25×..2×, did not change bank coverage.
- BHSpells `crimson_mist.fsh`: opacity `THICK × (density/0.5)^1.5`, banks fill more air with density (`cover`), ground light by density; `CrimsonMistFx` clamps 0..3.
- result: glslcheck OK; shader copied with `processResources` (client running, so `compileJava` skipped; the new clamp needs a restart). Not tested in game.

## 2026-11-14 — BHSpells new `bhspells:crimson_mist` (wide thin red mist)
- User (reference image of a green glowing fog): a wide red mist covering the area, not thick, things inside still visible.
- BHSpells: `CrimsonMistFx` + `crimson_mist.fsh` (raymarched ground mist like `shadow_domain`'s: even haze + billowing 3D-noise banks, self-lit red with pale pockets, red light on the ground, ragged edge, rolls out over 50 ticks); `BhfxIds.CRIMSON_MIST`, registered in `BhfxClientBridge`. Params `color`, `radius` (40), `height` (6), `density` (0.5), `speed`. Notes: `BHSpells/docs/bhfx/crimson_mist.md`, row in `reusable_effects.md`.
- result: BHSpells `compileJava` passed, glslcheck OK. Not tested in game; checklist in [`testing.md`](testing.md).

## 2026-11-14 — arm_crack texture made smaller (131 KB → 32 KB)
- User: file too big; lower resolution is fine if the detail stays.
- `gen_crack_texture.py`: still draws at 512, then box-shrinks to the output size (thin cracks lifted by ^0.8 so hairlines survive), quantises the crack channel to 16 levels and glow/order to 8, writes RGBA (alpha 255) with PNG adaptive row filters. `arm_cracks.png` is now 256² at 34.8 KB; a 2x side-by-side with the 512 version keeps every crack incl. hairlines (softer). Shader and coordinates unchanged.
- bhfx `FxTextures.read`: decodes with `NativeImage.read(Format.RGBA, in)`. Before, an RGB or grey PNG kept its own format and `getPixelRGBA` threw, so such a texture could not load (found while trying an RGB crack texture). `build` + `publishToMavenLocal` passed.

## 2026-11-14 — arm_shatter round 6: crack texture redone to match the reference
- User: wants the texture like the reference (cracked plaster / dried mud).
- BHSpells `docs/bhfx/tools/gen_crack_texture.py` rewritten: a crack network (4 thick main cracks, ~60 medium cracks branching off existing ones at 55–125° and running until they hit another crack, 150+ hairlines, chips at junctions, specks clustered along cracks), 512² instead of 256²; `textures/fx/arm_cracks.png` regenerated (same channels). Preview `docs/bhfx/tools/preview_arm_cracks.png`.
- result: texture only; no code change. Not tested in game.

## 2026-11-14 — arm_shatter round 5: cracks painted onto the arm through depth, new crack texture
- User: the cracks still read as an outer layer; wants them on the body like the ground cracks, on all 6 faces, in the style of a reference image (thick chipped main cracks plus hairlines).
- BHSpells: new pass `shaders/bhfx/arm_shatter.fsh` (depth decal: each scene pixel taken into the four arm boxes, mapped to the nearest of six faces, one texture tile round the arm); new tileable texture `textures/fx/arm_cracks.png` grown by `docs/bhfx/tools/gen_crack_texture.py` (pure Python; r crack, g glow core, b opening order); pieces now `particle/arm_chunk.fsh` sampling the same texture; `arm_skin.fsh` and the per-frame skin panes removed.
- result: glslcheck OK (pass and material), BHSpells `compileJava` passed. Not tested in game.
- lesson: to put something on a model's surface, use a depth-decal pass with the body part boxes, not particles above the skin (they always read as a separate layer). See `integrations/bhspells.md`.

## 2026-11-14 — arm_shatter: arms vanished while still waiting cracked (fixed)
- User: the arms disappear for a while before they break. latest.log (00:54) shows a held test stopped ~22 s later.
- Cause: `ArmShatterFx.tick` kept the arms hidden when the sequence tick was past `HIDE` (34), but before the break the sequence tick is the plain age, so a `hold:true` effect hid the arms 1.7 s in while still cracked. The frame side already required `fallenApart`.
- Fix: the tick-side `ShatteredArms.keep` also requires `breakAge >= 0`; every other phase test already sits behind the not-broken branch or checks it. `compileJava` passed. Not tested in game.

## 2026-11-14 — Shoulder points; arm_shatter round 4 (cracks on the skin, visible break, `gone` param)
- User: arms vanished before they visibly broke; wants a set time before they return; Epic Fight did not split upper and lower arm; cracks floated like an outer layer.
- bhfx: `BodyPart.LEFT_/RIGHT_SHOULDER` (top of the arm, upper arm's axes): vanilla layer at arm (∓1, −2) px, estimate (±0.375, 1.5), first-person estimate; `CHANGELOG.md`. Still 0.3.12, protocol 3 (unreleased). `build` (116 tests) + `publishToMavenLocal`.
- BHSpells: `EpicFightBhfxBodyParts` reports shoulders from `Arm_R/L` (`tools/ef_body_points.py`). `ArmShatterFx` rewritten: forearm and upper arm as separate boxes; cracks are a Voronoi pattern in the new `arm_skin.fsh` on TUMBLE panes flush on each face (`softness(0)`), replacing the AXIS strips and `arm_crack.fsh` (deleted); a 6-tick breaking stage (cracks widen, skin turns to stone) before the arm falls apart into its own surface plates, spawned in `passes()` the frame the arm starts hiding; new param `gone` (`BhfxIds.GONE`, seconds, 2.1); slim arms.
- result: glslcheck OK, BHSpells `compileJava` passed. Not tested in game.

## 2026-11-14 — arm_shatter round 3: hold cracked until stop finish
- User: keep the arms cracked and break them only on `/bhfx stop finish`.
- BHSpells `ArmShatterFx`: sequence time now runs from `breakAge`; timed effects break at tick 28 as before, `hold:true` cracks to full and holds (breathing glow, occasional surge, slow embers and sounds) with no looping; `endTicks(FINISH)` plays the break out (52 ticks), `onStop(FINISH)` sets the break; CANCEL fades in 10 ticks and releases the arms; the fade-out follows the end of the sequence, not `stopProgress`, for a finish.
- result: BHSpells `compileJava` passed. Not tested in game.

## 2026-11-14 — Epic Fight body parts fixed from bind data; arm_shatter round 2 (arms break off for real)
- User: Epic Fight points "not very accurate"; wants the arms to actually crack and break off.
- BHSpells `EpicFightBhfxBodyParts`: rewritten to `stack * poseMatrices[joint] * local`, with `local` per point computed from Epic Fight 20.14.17 `biped.json` (see `integrations/bhspells.md`). Found that BHSpells builds against Epic Fight 20.14.17, not the 20.9.7 jar checked on 2026-11-13; layer behaviour re-checked on 20.14.17 (same), mode rule now reads `enableOriginalModel`.
- BHSpells: new `client/ShatteredArms` (per-entity hide with a 250 ms lapse, kept alive every frame by the effect; vanilla `RenderLivingEvent.Pre/Post` at LOWEST hides arms and sleeves, `RenderHandEvent` cancels first-person hands), `compat/epicfight/client/EpicFightShatteredArms` (`PrepareModelEvent` hides `leftArm/rightArm/leftSleeve/rightSleeve`), mixins `ItemInHandLayerShatteredMixin`, `HumanoidArmorLayerShatteredMixin`, `WearableItemLayerShatteredMixin`, and `MixinPatchedItemInHandLayer` also checks it.
- BHSpells `ArmShatterFx` round 2: 80-tick round; at the break each arm becomes 12 chunks filling its volume plus flakes, sparks, dust; arms hidden 1.4 s → 3.5 s with molten shoulder stumps; arms return at 3.5 s with faint cooling cracks.
- result: BHSpells `compileJava` passed, refmap resolves the vanilla mixin targets; Epic Fight mixin targets checked against the 20.14.17 jar with javap. Not tested in game.

## 2026-11-13 — `bhspells:arm_shatter`, a body part test effect
- User asked how to test body parts, with an effect where both arms crack and break apart.
- BHSpells: `ArmShatterFx` (`BhfxIds.ARM_SHATTER`, registered in `BhfxClientBridge`), materials `arm_crack.fsh` (AXIS crack pieces re-emitted every frame so they follow the posed arm) and `arm_shard.fsh` (TUMBLE stone flakes); design note `BHSpells/docs/bhfx/arm_shatter.md`.
- result: glslcheck OK on both materials, BHSpells `compileJava` passed. Not tested in game.
- next: in-game round 1 (checklist in the design note).

## 2026-11-13 — Body parts: effects on a hand, foot, head (0.3.12, protocol 3)
- User asked for effects placed on the left/right arm, legs, body and head of a Minecraft model, and on the feet.
- bhfx: new `api/BodyPart` (11 points incl. `LEFT_/RIGHT_FOOT` and `FEET`; shoulders added 2026-11-14) and `api/client/BodyPose`; `FxSpawn.part(...)`, `FxInit.part` (old constructor kept), `FrameContext/FxContext.part(...)`, `BHFxClient.bodyPart / wantsBodyParts / reportBodyPart`. `impl/client/BodyParts` (capture store, estimate fallback, first-person hands from the camera) and `BodyPartLayer` (render layer added to every `LivingEntityRenderer` in `ClientBootstrap.addLayers`); capture window opened in `ClientEvents` after the mirror reflections at `AFTER_SOLID_BLOCKS`, closed at every later stage. `S2CFxStart` carries the part (protocol 2 → 3). `FxCommandArgs`: `{part:right_hand}`. Version 0.3.11 → 0.3.12, `CHANGELOG.md`.
- BHSpells: `compat/epicfight/client/EpicFightBhfxBodyParts` (Epic Fight `PatchedLayer` reporting biped joints), registered in `EpicFightLoadedBridge` when bhfx is loaded; `build.gradle` and `mods.toml` need bhfx `[0.3.12,0.4)`.
- result: bhfx `build` passed (116 JUnit tests incl. new `partPicksABodyPoint`, `startRoundTripsItsBodyPart`; checkShaders) and `publishToMavenLocal`; BHSpells `compileJava` passed. **Not tested in game.**
- next: in-game checklist in [`testing.md`](testing.md); tune Epic Fight joint offsets after seeing them.

## 2026-11-12 — BHSpells new `bhspells:prism_flame_field`
- User: a field version of `prism_flame`, like `flame_field`.
- BHSpells: `FlameFieldFx(init, prism)`; `bhspells:prism_flame_field` registered as `new FlameFieldFx(init, true)` (white / grey defaults, `prism` rainbow strength via `FieldC`, slate vignette, beacon / amethyst sounds). New `prism_flame_field.fsh` = `flame_field.fsh`'s grid, DDA, edge, ignite and LOD with prism colouring (white by heat, grey folds, pastel-rainbow slivers on each fire's outline, white-rainbow floor pools and glitter). `BhfxIds.PRISM_FLAME_FIELD`. Notes: `BHSpells/docs/bhfx/prism_flame_field.md`. `flame_field` unchanged.
- result: `compileJava` OK, glslcheck OK, numpy preview of single fires. Not tested in game.
- next: keep `prism_flame_field.fsh` in step with `flame_field.fsh`.

## 2026-11-12 — BHSpells `sky_shatter` round 10: big landing ripples, felt farther
- User: bigger ripples when shards land; widen how far each piece is felt.
- BHSpells `SkyShatterFx`: reach 12–40 (was 5–18), `RIPPLE_SECONDS` 2.2, new param `impact` (0.25–3) scaling reach and every range; jolt range (60 + 60 power), impact frames / FOV 90, `ctx.shake` (40 + 60 power), explosion volume 4 + 4 power, ear thud 120. `sky_shatter.fsh`: band follows terrain over 3 + 0.15 reach, second wave widens with reach, light band dimmed for big waves (a wave rolling past the camera washed the screen), crack mark size kept. Checked in the preview (`?land=1&pitch=-20`).
- result: glslcheck OK. Java (rounds 8 and 10) not compiled: the client is running. Not tested in game.

## 2026-11-12 — BHSpells `sky_shatter` round 9: glowing ground cracks
- User: make the texture cracks glow. `sky_shatter.fsh`: the crack cores glow for the mark's whole life (white-hot first, then a throbbing glow in `color`, over 1 so bloom catches it); darkening only beside the cracks and the scorch. Checked in the preview.
- result: glslcheck OK. Java from round 8 still not compiled (client running). Not tested in game.

## 2026-11-12 — BHSpells `sky_shatter` round 8: ground crack marks from a texture
- User: when glass hits the ground, leave a crack in the ground, from a texture.
- BHSpells: `textures/vfx/sky_shatter/ground_crack.png` / `ground_scorch.png` (copies of `effeks/texture_remana/crack/*.png`); `SkyShatterFx` binds them (`GroundCrack`, `GroundScorch`), landings keep a random angle and stay listed 6 s (`SCAR_SECONDS`), `LANDS` 32. `sky_shatter.fsh`: per landing a `groundDecal` mark that bursts open in 0.2 s, glows white-hot, cools to dark fissures with a faint glow over scorch, fades after 6 s; procedural spoke cracks of round 7 removed; ring / hit glare lowered. Checked in the preview (`?land=1&ls=0.45&pitch=-50`).
- result: glslcheck OK. Java **not compiled** (the user's client was running; compiling would mix classes, `bugs.md` 2026-11-12). Not tested in game.

## 2026-11-12 — BHSpells `sky_shatter` round 7: heavy landings
- User: shake the screen when shards hit the ground; a real dimension-breaking impact, violent shock ripples, heavy sound.
- BHSpells `SkyShatterFx`: landings carry `power` (shard size against the biggest), reach 5–18; `screen()` jolt from the strongest fresh landing (shake, aberration, zoom blur, flash, `screen.ripple`), glitch / negative `ImpactSequence` for heavy ones (power >= 0.7), FOV kick, `ctx.shake` by power; sounds: layered blast / glass / amethyst per tick's strongest hit, sonic boom for heavy, a thud at the ear within 64 blocks, creaks while cracking; 3–8 shards burst back up per hit. `sky_shatter.fsh`: waves push the ground image out and up (heave), narrow ring bands (shockRing's tail washed the ground white), radiating ground cracks that glow then scorch; `RIPPLE_SECONDS` 1.6. Preview got `?land=1` (three hits).
- result: `compileJava` OK, glslcheck OK, ground look checked in the preview. Not tested in game.

## 2026-11-12 — BHSpells `sky_shatter` round 6: bigger, `speed`, working `hold` / `duration`, healing
- User: make it bigger; can hold / duration be set.
- BHSpells `SkyShatterFx`: `radius` default 64 (12–160), new `spread` (cap half angle, 66 degrees, 30–85) and `speed` (timeline, 0.25–4); default duration from speed (last fall + `LINGER` 7 s + 1.6 s heal); `fade()` treated `UNTIL_STOPPED` (−1, `hold:true`) as fully faded, so held effects drew nothing: fixed; `endTicks(FINISH)` 32 ticks. `sky_shatter.fsh`: holes close in from their edges as it heals (`shut = heal x cap x 0.22`, glowing closing edge), then the cracks fade. Checked in `preview_sky_shatter.html` (level view; heal steps with `?fade=`), preview gained `r` / `spread` / `speed` / `fade` options.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-12 — BHSpells `sky_shatter` round 5: the pass drew nothing (`Timing.w`), slow crack
- User: still no cracks in the sky; wants the sky to crack slowly, break open onto another sky, and the glass to drift down like a dimension breaking.
- Cause found with a new WebGL preview of the real shader (`BHSpells/docs/bhfx/tools/preview_sky_shatter.html`, `build_sky_shatter_preview.py`, `serve_save.py` which also saves the canvas as PNG): `sky_shatter.fsh` read fade from `Timing.w`, Java sends `Timing.y`; every pixel returned early since round 3. Fixed; `patch` renamed and added to both reserved scans.
- Look (checked in the preview at pitch 30 and 0): cracks creep 3 s with main cracks leading and more borders appearing over time, thinner lines, lighter halo / micro cracks / start star, seams open 1 s before a fall only on lit cracks, thinner hole lip; drops 3.8 s to ~8 s, duration 15 s; shards drift down (gravity −7, drag 0.3, slow spin).
- result: `compileJava` OK, glslcheck OK, bhfx `checkShaders` OK. Not tested in game.

## 2026-11-12 — BHSpells `sky_shatter` round 4: cracks in view, longer, clearer
- User (tested round 3): the glass falls right away, no cracks to be seen. Causes: the web was straight overhead (out of a level view), cracks had 0.7 s and the first cell fell at 1.1 s, pale cyan added on a bright day sky.
- BHSpells `SkyShatterFx`: the cap is turned to an axis 60 degrees up towards where the caster (else the camera) looks (`setFrame`, `capWorld`; params `yaw`, `elevation`), cells unchanged; `CRACK_SPREAD` 1.4, `DROP_START` 2.4, `DROP_SPAN` 2.2, duration 230 ticks. `sky_shatter.fsh` / `particle/sky_shard.fsh`: frame from `CapA` / `CapB1` / `CapB2`; dark band beside cracks, brighter cores, faster sky dim, seams opening onto the void 0.8 s before a cell falls.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-12 — Fix: `packed` in the particle vertex shader broke every particle material
- User: no effect showed at all. `latest.log`: all 31 particle materials failed to compile on NVIDIA (`bhfxUnpackUnit(float packed)`, reserved word); the `sky_shatter` pass itself compiled. The test spots were y −33 / −47 / −52 (under ground): there the dome is behind the cave roof, so the pass draws nothing either, as it should (the sky is not visible).
- bhfx: parameter renamed (`particle/vertex.vsh`); reserved-word scan added to `gradle/glsl-check.gradle` and `.claude/skills/bhfx-effect-recipes/scripts/glslcheck.py` (checked it fails on `packed`; bhfx 17 and BHSpells 55 shaders pass). `bugs.md`, `shaders.md` updated.
- result: bhfx `build publishToMavenLocal` OK, jar in `BHSpells/libs` has the fix, deobf copy cleared. Not re-tested in game.

## 2026-11-12 — Dev client crash in `SkyShatterFx` (stale classes)
- User: the game crashed. `crash-2026-11-12_12.28.55-client.txt`: `NoSuchMethodError` for the round-2 `Landing` constructor. The client started 12:17 and BHSpells was recompiled 12:26 while it ran (old outer class + new nested record). No code change; restart `runClient`. Recorded in `bugs.md` and the `bhfx-dev-loop` skill.

## 2026-11-12 — BHSpells `sky_shatter` round 3 (Voronoi web, fitted shards, ground ripples); `glass_shatter` removed
- User: make the sky shatter the effect (sky breaking, glass falling), with a ripple when shards hit the ground; take only what it needs from `glass_shatter` and remove `glass_shatter`.
- BHSpells `SkyShatterFx`: cells are now a Voronoi web on the dome's cap flattened to blocks from the top (seeds dense at the top, half-plane clipped 48-gon, centroid, box in the tangent plane by gnomonic projection); drops from the top outwards; big shards are TUMBLE panes in the tangent plane cut to their cell by `particle/sky_shard.fsh` (splinters keep the kite); landings record ripples (newest 16, 1.1 s) drawn by `sky_shatter.fsh` (two waves pushing the ground image out through `projectUv`, `shockRing` light) plus `screen.ripple` for the nearest, glass dust (`skirt`). `sky_shatter.fsh` cracks: uneven front with main-crack angles, per-border weights, repeating energy pulses, micro cracks. New param `shards`. Removed: `GlassShatterFx`, `glass_shatter.fsh`, `particle/glass_shard.fsh`, `BhfxIds.GLASS_SHATTER`, its note; references in both repos updated (TUMBLE stays, `sky_shatter` uses it).
- result: `compileJava` OK, glslcheck OK. Not tested in game (checklist in `BHSpells/docs/bhfx/sky_shatter.md`).

## 2026-11-12 — Version 0.3.11
- User: bump the version first.
- bhfx: `gradle.properties` `mod_version` 0.3.11; `CHANGELOG.md` "Unreleased" (TUMBLE, mirror panel) is now "0.3.11 — 2026-11-12 (protocol 2)". BHSpells: `build.gradle` `fg.deobf("net.deizyn:bhfx:0.3.11")`, `mods.toml` `[0.3.11,0.4)`; `copyToBHSpells` replaced `libs/bhfx-0.3.10.jar` with `bhfx-0.3.11.jar`. `release.md`, `integrations/bhspells.md`, `roadmap.md` updated.
- result: bhfx `build publishToMavenLocal` OK, BHSpells `compileJava processResources` OK (expanded `mods.toml` has `[0.3.11,0.4)`). Not run in game. Nothing committed; the branch is still `feature/scale-phase0` (merge to `main` waits on U1/U2).

## 2026-11-12 — `TUMBLE` particles; BHSpells `bhspells:glass_shatter`; `sky_shatter` shards tumble
- User: adapt a "fantasy glass shatter" OpenGL / C++ brief (cracks, energy, 3D Voronoi shards, particles, one timeline, bloom) to bhfx.
- bhfx: `FxParticles.Facing.TUMBLE`, `ParticleSpawn.tumble(axis, rad/s)`, `normal()` / `axis()` also for TUMBLE (`ParticleBatch`: `packUnit` / `unpackUnit`, cull size like AXIS, `rotation()` ignored); `particle/vertex.vsh`: TUMBLE branch (pane frame turned by `bhfxTurn` round the tumble axis, `pFacing` = |n . view|), ribbon checks now `3.5 < facing < 4.5`, `bhfxUnpackUnit`. `ParticleBatchTest` +2. CHANGELOG "Unreleased", decision in `decisions.md`.
- BHSpells: new `GlassShatterFx` (`BhfxIds.GLASS_SHATTER`): Voronoi cells by half-plane clipping (dense round the impact), pass `glass_shatter.fsh` (pane, cracks along the cell borders with uneven front and main-crack angles, energy pulses, micro cracks, gather / flash / ring, debug views), material `particle/glass_shard.fsh` (cuts each TUMBLE pane to its cell, fresnel / rim / glint, noise dissolve), flyers + hovers, sparks, crystal dust where shards dissolve, ribbon trails. `SkyShatterFx` shards now TUMBLE (lie in the dome at birth) and spawn one tick earlier to match their holes. Notes: `BHSpells/docs/bhfx/glass_shatter.md`.
- result: bhfx `build publishToMavenLocal` OK (JUnit, `checkShaders` 17 OK). BHSpells `compileJava` OK, glslcheck OK. Not tested in game (checklists in `testing.md` and the BHSpells note).
- next: in-game round; release bhfx with TUMBLE as a new version (see `roadmap.md`).

## 2026-11-12 — BHSpells new standalone `bhspells:sky_shatter` (sky breaking like glass)
- User: a sky-shattering effect whose falling shards have real depth.
- BHSpells: `SkyShatterFx` (registered in `BhfxClientBridge`, id `BhfxIds.SKY_SHATTER`), pass `sky_shatter.fsh` (spider-web cells on a dome of `radius` round the position, cracks spreading from the top, cells falling out onto a starry void with parallax), particle material `particle/sky_shard.fsh` (AXIS glass pane that bends `SceneSampler` behind it, rim glow, glint when face on). Java and shader build the same cells from an integer hash (`cellHash`), so each shard leaves its own hole; landing time from the heightmap with the vertex shader's drag/gravity formula, landing sparks by `delay`. Notes: `BHSpells/docs/bhfx/sky_shatter.md`.
- result: `compileJava` OK, glslcheck OK. Not tested in game.
- lesson: a particle material can refract the world by sampling `SceneSampler` at `gl_FragCoord.xy / OutSize` plus an offset (ALPHA + `BHFX_PREMULTIPLIED`); with the per-pixel depth test this makes glass that sits in the world.

## 2026-11-12 — BHSpells `impact_frame`: standalone long white impact frame
- User: a separate impact frame with an adjustable duration, feeling like a long white explosion at the spawn point.
- BHSpells: new `ImpactFrameFx` (`BhfxIds.IMPACT_FRAME`, `bhspells:impact_frame`) + pass `impact_frame.fsh` (white ball growing to `radius` in 0.3 s and staying, boiling rim, halo, light on the world). Screen: 0.2 s of flipped cels + strong white flash / jolt / zoom blur / FOV punch, then an ink frame (white paper, black ink, focus lines re-drawn every 3 ticks) held for the spawn's `duration` (default 3 s), fading over the last 30 %. `hold:true` holds until stopped (16-tick fade). Params: radius, color, strength, frames. Notes: `BHSpells/docs/bhfx/impact_frame.md`.
- result: `compileJava` OK, glslcheck OK. Not tested in game (checklist in `testing.md`).

## 2026-11-12 — Mirror panel (thin wall mirror that joins into one sheet)
- User: a mirror that sticks on a wall, where panels placed side by side join into one mirror.
- bhfx: new `MirrorPanelBlock` (`bhfx:mirror_panel`, `FACING` = clicked face, 1/16 thick plate, `THICKNESS`, no support check), shares `MirrorBlockEntity` (block entity type now lists both blocks; `setBlockState` re-files a turned panel). `MirrorTracker`: `PANELS`, `addPanel`, `Key.panel` (in `planeId`, so panel fronts and block faces of one layer are separate surfaces), panel `faceAt` = layer + 1/16 or layer + 15/16, `hasFace(pos, facing, panel)`. `MirrorRenderer`: `Plane.panel`; `onPlane` steps back half the plate thickness instead of half a block. Assets: blockstate (6 facings, uvlock), block/item model (no AO, so no dark seams), loot, recipe (3 glass panes over 3 iron nuggets → 6), lang, functional-blocks tab. `MirrorTrackerTest` +4 tests. CHANGELOG "Unreleased".
- result: `build publishToMavenLocal` passed (tests incl. the new ones). Not tested in game (checklist in `testing.md`). `mod_version` not bumped (still 0.3.10).

## 2026-11-12 — BHSpells `qi_blast` round 8: rings keep running, `ring_time` / `ground_time`
- User: the inner rings should keep expanding until they fade, like the first; the ground should stay longer and the rings vanish faster; make both adjustable.
- BHSpells `qi_blast.fsh`: `frontRun` (sqrt, never stops) for the ground fronts and the heave, faded by `Params3.w` = ring seconds; debris keeps the stopping `frontAt`. `QiBlastFx`: params `ring_time` (1.2) and `ground_time` (default and max = the blast's length); `ringRadius` / `ringSpeed`; rings batch drag 0.6 with lives = ring_time; outer shock and screen ripple on the same curve; plate / flow / inner / ground_beat on ground_time.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-12 — BHSpells `qi_blast` round 7 crash fix
- User: the game crashed. `crash-2026-11-12_02.26.09-client.txt`: `ConcurrentModificationException` in `ClientFxManager.tick` — `QiBlastFx.tick` started `impact_ring` / `ground_beat` with `BHFxClient.play` while bhfx iterates its effects.
- BHSpells `QiBlastFx`: starts go through `playLater` (`Minecraft.getInstance().tell`), the cancel-stop of the children is deferred the same way. Trap recorded in `bugs.md` (open: bhfx could queue starts made during its tick).
- result: `compileJava` OK. Not re-tested in game.

## 2026-11-12 — BHSpells `qi_blast` round 7: no fizz, ground_beat + impact_ring, aimed beam
- User: remove the fizzy stuff on the ground, add ground_beat and impact_ring, and make the beam go wherever the crosshair hits with no range limit.
- BHSpells `qi_blast.fsh`: glowing cracks and rim lightning removed (`Params3.w` unused). `QiBlastFx`: plays `impact_ring` + `ground_beat` through `BHFxClient.play` at the blast and an `impact_ring` per later ripple, stopped on cancel (`children`, `onStop`); beam mode aims by its own ray (`aim`, `aimPoint()`, 1024 blocks, blocks + fluids + entities); the blast's visuals are drawn within max(256, 10 x radius) instead of `near`.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-12 — BHSpells `qi_blast` round 6: nuclear dome, outer shock, ground flow
- User: the ring should keep expanding like a nuclear blast and slowly vanish; the ground should spread out with anime_shockwave's feel, hard.
- BHSpells: `QiBlastFx.domeRadius()` (was `sweptRadius`): 0.72 of the radius in the sweep, then creeping to full by 0.9 x length; shell bright at first then fading over 0.3-0.95 x length; fireball grows with it; new `Params4` (outer shock radius / strength to 1.8 x radius, ground flow). `qi_blast.fsh`: outer shock shell + ground ring, anime_shockwave's polar flow at blast size and far stronger, 24 spokes on the fronts.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-12 — BHSpells `qi_blast` round 5: ripples and anime_shockwave pieces
- User (reference: anime blast seen from above, bright plate, rings of dark debris, lightning round the rim): make it ripple hard, and use anime_shockwave's pieces on the ground and around.
- BHSpells `qi_blast.fsh`: 4 ripples (`RIPPLE_DELAY/REACH/GAIN`, `frontAt`) each with its own front; ground heave (image re-sampled at the raised and leaned point via `projectUv`); energy plate; debris rings (`DEBRIS_AT`); rim lightning; new `Params3` (sweep, plate, swell, crackle). `QiBlastFx`: same ripple tables, `ripple()` per later ripple (rings, shell, line burst, sound, shake), `surge()` for 1.4 s with `wallArc()` (`power_wave`), `power_line` and `power_haze` batches; screen ripple follows the newest ripple; rings capacity 1024.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-12 — BHSpells `qi_blast` round 4: laser mode `bhspells:qi_beam`
- User: fire it as a laser from yourself, a blue qi beam that explodes where it lands.
- BHSpells: `QiBlastFx` beam mode (`beam` param, new id `BhfxIds.QI_BEAM` registered as `new QiBlastFx(init, true)`): gather at the caster's right hand (`muzzle()`, followed per frame from the client entity), beam head flies at 140 b/s, blast at `blastAt = charge + travel`, beam holds 0.5 s then thins over 0.6 s; new params `width`, charge default 0.6 in beam mode; `beamSparks` batch; screen/camera gather and fire kick use distance to the hand. `qi_blast.fsh`: new `Core`, `BeamA`, `BeamB` uniforms (core no longer fixed above Target), beam core / sheath / hand glow / head ball, beam light on the ground, near-eye fade. Notes: `BHSpells/docs/bhfx/qi_blast.md`.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-12 — BHSpells `blaze_aura` round 2: prism_flame in fire colours
- User (in-game screenshot of round 1): odd lines, "just the fire, the same feel as prism_flame".
- BHSpells: `BlazeAuraFx` and `particle/blaze_tongue.fsh` deleted, params `INK` / `SHADOW` / `SMOKE` / `EYES` removed. `PrismFlameFx` gets `Look` (`PRISM` / `BLAZE`); `bhspells:blaze_aura` is registered as `new PrismFlameFx(init, Look.BLAZE)`: same timing and shape, `blaze_aura.fsh` rewritten as `prism_flame.fsh`'s shape with fire colouring (`Tint3` red, red-orange edge glow instead of the rainbow), new `particle/blaze_wisp.fsh` (fire-coloured `prism_wisp`), ember motes, fire sounds, `FLAME` fallback. Prism look unchanged.
- result: `compileJava` OK, glslcheck OK. Not tested in game.
- next: keep `blaze_aura.fsh` shape in step with `prism_flame.fsh`.

## 2026-11-12 — BHSpells `qi_blast` round 3: length from `duration`
- User asked why not `duration`. `QiBlastFx`: param `length` dropped; the post-blast length is now `duration() / 20 - charge` (min 0.5 s), so the fades always fit the framework's own end and server spawns set it the usual way; `defaultDuration` = charge + 6 s; `hold:true` uses 6 s and ends itself with `end(FINISH)`.
- result: `compileJava` OK. Not tested in game.

## 2026-11-12 — BHSpells new standalone `bhspells:blaze_aura`
- User: three references of a body on fire (manga ink wall of flame with a dark figure and glowing eyes, painted white-orange flame strips leaping off the shoulders, a flame pillar with yellow sparks): "another burning effect with this feel". Made as a new effect; `prism_flame` untouched.
- BHSpells: new `BlazeAuraFx` + `blaze_aura.fsh` (sheet through the body like `prism_flame`: heat field column + rising noise + ridges, white/yellow/orange/red ramp, comic ink outline + hatching, dark silhouette window over the body, depth-tested glowing eyes from `getEyePosition`/`getViewVector`, flickering ground pool + ignition ring) + particle `blaze_tongue.fsh` (RIBBON flame strips, premultiplied ALPHA); reuses `power_haze`, `haki_wave`, built-in `DOT` / `STREAK`. `BhfxIds.BLAZE_AURA`, new params `INK`, `SHADOW`, `SMOKE`, `EYES`; registered in `BhfxClientBridge`. Notes: `BHSpells/docs/bhfx/blaze_aura.md`.
- result: `compileJava` OK, glslcheck OK, numpy preview of the sheet. Not tested in game (checklist in `testing.md`). bhfx itself unchanged.

## 2026-11-12 — BHSpells `qi_blast` round 2: no light column, `length`
- User: drop the light going up into the sky, and make how long the blast lasts adjustable.
- BHSpells: `qi_blast.fsh` pillar block, its light on the ground and `Params3` removed; `QiBlastFx`: pillar strengths gone, new param `length` (replaced by `duration` in round 3; seconds from the blast to the end, 6, 1.5..30) sets `defaultDuration` and stretches every fade after the blast by `length/6` (dome opens in 1.6 s, faster only when shorter; the hit's frames / flash / kick unchanged).
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-12 — BHSpells new standalone `bhspells:qi_blast` (ระเบิดปราณ)
- User: a heavy blue "boom" with a wide reach, like the world is ending, like a qi blast.
- BHSpells: new `QiBlastFx` + `qi_blast.fsh` (one full-screen pass: core, dome shell + inner haze, pillar of light, ground shock front / cracks / scorch, light on the world, blue grade); reuses `power_wave` (rings) and `haki_wave` (DISTORT shells), built-in `STREAK` / `DOT`; `BhfxIds.QI_BLAST`, registered in `BhfxClientBridge`. Design notes: `BHSpells/docs/bhfx/qi_blast.md`.
- result: BHSpells `compileJava` OK, glslcheck OK. Not tested in game (checklist in `testing.md`). bhfx itself unchanged.
- next: first in-game look; watch for a white screen at the blast.

## 2026-11-11 — BHSpells Lunebris: range 30, real stun, banner/downslam damage, `lunebris_impact`
- BHSpells: `LunebrisSpell` (lock range and `AOE_RADIUS` 20 → 30, aim assist 1.2, 20° cone fallback `findTargetInCone`, `BANNER_DAMAGE` / `DOWNSLAM_DAMAGE` 20 x power multiplier), `LunebrisManager` (enemies in the ring hit and stunned until the combo ends, stunned players pinned back, debuffs 10 s / 15 s on the target, `lunebris_impact` at the ground impact), new `LunebrisStunEffect` + `LunebrisStunClientEvents` + `LunebrisStunSkillMixin` / `LunebrisStunLocalPlayerPatchMixin` (the stun also blocks input and Epic Fight skills on the stunned player's client), `EarthRoarStunService.applyStunWithoutIcon`; bhfx side: new `LunebrisImpactFx` (`bhspells:lunebris_impact`) reusing the `lunebris_surge` pass and the lash / flame / haki_wave materials; `lunebris_aura` / `lunebris_surge` now get radius 30.
- result: BHSpells `build` OK. Not tested in game (checklist in `testing.md`).
- next: in-game look at radius 30 and the impact; stun icon is a copy of `earth_roar_stun.png`.

## 2026-11-11 — BHSpells `prism_flame` round 8: `speed`
- User: make the speed adjustable. `PrismFlameFx`: param `speed` (`BhfxIds.SPEED`, 1, 0.1..4) scales the flame's flow clock, the strands' rise and turn (life / sqrt), and the glitter and shimmer rise; surges stay on `beat`. The strand helper's own `speed` argument renamed `pace`.
- result: `compileJava` OK. Not tested in game.

## 2026-11-11 — BHSpells `prism_flame` round 7: wider, faint middle
- User (round 6 approved): make the flame wider so the middle is faint, it doesn't need to be very bright.
- BHSpells: `PrismFlameFx.DEFAULT_RADIUS` 1.15 → 1.6; `prism_flame.fsh` new `core` term makes the flame hollow like an aura (alpha and streak glow lower through the middle, less so in the tongues above the head).
- result: `compileJava` OK, glslcheck OK, numpy preview. Not tested in game.

## 2026-11-11 — BHSpells `prism_flame` round 6: soft oval window over the body
- User (round 5 screenshot, close up): the clear window over the body is a rectangle; just make it faint.
- BHSpells `prism_flame.fsh`: the box (narrow smoothsteps across and a cut at the head) became a soft oval round the body's middle, its edge broken by the flame's noise, fading gradually (alpha down to x0.25).
- result: glslcheck OK, numpy preview has no straight edges. Not tested in game.

## 2026-11-11 — BHSpells `prism_flame` round 5: clear over the body, ground light on the ground
- User (round 4 seen in game): the middle is too dense, the body can't be seen, fade it over the body only; the light / ring spreading under the feet should stay on the ground.
- BHSpells `prism_flame.fsh`: new `BodyA` window in blocks from the followed body's bounding box (alpha x0.15, rainbow x0.3, halo x0.2 there); the halo no longer spreads sideways at the feet; new ground light on floor pixels only (`groundBand` + upward normal): pool round the feet and the burst / surge ring (`GroundA`). `PrismFlameFx`: removed the `ground` batch (flat `radiant_ring` quads that floated over uneven ground), new `ring(t, s)` from time like `surge()`, strand roots moved out of the window, pass bounds cover the ring.
- result: `compileJava` OK, glslcheck OK, numpy preview shows the body clear. Not tested in game.

## 2026-11-11 — BHSpells `prism_flame` round 4: power-up feel
- User: make it able to be tall, and make it feel like powering up. `height` already existed (since round 2); surges now also add height.
- BHSpells `PrismFlameFx`: 12-tick charge (low flickering flame, motes pulled in, building tremble), a burst (flame shoots up, `haki_wave`, ground ring, dust thrown out along the ground, `power_line` aura lines, a rush of strands, ripple / flash / shake / FOV), then a surge every `beat` seconds (`BhfxIds.BEAT`, 1.6; 0 = burst only) worked out from time alone; aura lines also stream up all the time. `prism_flame.fsh`: `FlameB.x` is now a flow clock that runs faster in a surge, `FlameB.w` the surge (brighter, stronger rainbow and halo).
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-11 — BHSpells `prism_flame` round 3: tip line, view from above, strands back
- User (round 2 screenshots, seen in game): what is the dotted line above the head; it looks odd from above; wants the round 1 flame strands back.
- BHSpells `prism_flame.fsh`: above the tip the width is 0 and the one centre pixel column still passed → `shape` and the rainbow `edge` now times `smoothstep(0, 0.03, prof)`; the sheet fades out from |dir.y| 0.7 to 0.92 instead of only halving. `PrismFlameFx`: strands back (`particle/prism_wisp.fsh` recreated, rainbow follows `prism`), 14 a second, rooted inside the flame and scaled by `height` / `radius`; ground glow smaller and fainter.
- result: `compileJava` OK, glslcheck OK (2 files), numpy preview shows no line above the tip. Round 3 not tested in game.

## 2026-11-11 — BHSpells `prism_flame` round 2: one big steady white flame, no red
- User: no red; one big, steady flame; the fire looked floaty.
- BHSpells: removed the crimson smoke, the particle tongues, the ribbon strands, ash and the start flash, and deleted `particle/prism_flame.fsh`, `prism_wisp.fsh`, `crimson_blaze.fsh`. New pass `shaders/bhfx/prism_flame.fsh`: one sheet through the body's axis facing the camera, drawn over the body, a teardrop that splits into a few slow tongues above the head, marbled streaks, thinner over the body, rainbow slivers on the edges, white halo. `PrismFlameFx` keeps light glitter, `power_haze` and a white ground glow. Params now `color`, `color2`, `height` (3.2), `radius` (1.15), new `prism` (`BhfxIds.PRISM`, rainbow strength), `scale`.
- The shape was checked on a numpy port of the shader maths (3 tries; the first read as a candle). Image: `BHSpells/docs/bhfx/tools/preview_prism_flame.png`.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-11 — BHSpells `prism_flame`: white flame with a rainbow sheen burning on the body
- User sent a reference (a hand in milky white flame with prism-like rainbow slivers on its edges, crimson billowing fire-smoke behind) and asked for that fire and those colours burning on the body.
- BHSpells: new `client/PrismFlameFx` (`bhspells:prism_flame`, `BhfxIds.PRISM_FLAME`, registered in `BhfxClientBridge`). Attach it: white flame tongues, flowing white strands (RIBBON drawn at once, curling round the body), crimson fire-smoke drawn under the white, rainbow glitter, dark ash specks, `power_haze`, a red glow pool (`radiant_ring`), a flare on start. Params `color`, `color2`, `color3`, `scale`, `density`.
- New materials `particle/prism_flame.fsh`, `prism_wisp.fsh`, `crimson_blaze.fsh` (all premultiplied ALPHA; each fades within 1.5 blocks of the camera so the caster's first-person view isn't covered). Design notes: `BHSpells/docs/bhfx/prism_flame.md`.
- result: BHSpells `compileJava` OK, glslcheck OK (3 files). Not tested in game.

## 2026-11-11 — BHSpells `power_wave.png` and `wind_slash.png` as small as they can go
- User: make both as small as possible while keeping them good-looking.
- Method: tried resolution x bits-per-channel options and scored each by rendering it the way its material does (fully drawn and half dissolved, bilinear like the GPU) against the original, then looked at 3x crops. Lessons: an unused channel costs real bytes (power_wave's B was a copy of R); the dissolve channel (G) needs more bits than the others because the materials sharpen it through a narrow smoothstep (power_wave grainy at 5 bits, wind_slash rough at 4); thin hair lines rule out lowering wind_slash's resolution, soft bands allow halving power_wave's height.
- BHSpells `gen_power_wave.py`: saved at 512x256, R5 G6 A5, B empty → 181 KB → 52 KB (the script was first checked to reproduce the committed file exactly). `gen_wind_slash.py`: R4 G5 A5, B empty → 76 KB → 51 KB. Shader comments updated; no Java / shader logic change.
- result: regenerated, ~39–40 dB against the originals, side-by-side looks the same. Not tested in game.

## 2026-11-11 — BHSpells `wind_slash.png` under 100 KB
- User: the texture file is too big (236 KB), keep it under 100 KB, lower resolution is fine.
- BHSpells `docs/bhfx/scripts/gen_wind_slash.py`: still drawn at 1024x1024, saved at 512x512 (`OUT_SIZE`, 64 rows per band) with 6 bits per channel (`OUT_BITS`) and PNG optimize → 76 KB. No shader / Java change (the material samples bands by fraction).
- result: regenerated, preview checked (shapes intact). Not tested in game.

## 2026-11-11 — BHSpells `radiant_aura` round 9: the wind spreads out
- User: make the wind itself spread out too.
- BHSpells `RadiantAuraFx`: every gust now spirals outwards as it climbs (0.5–1.2 b/s, feet ring x1.4) and widens over its life (half-width x0.8 → x1.5); new `spread` = `radius` / 2.8 (0.6..4) scales the layer rings, the outward drift and the glint / dust areas, so `radius` widens the whole aura (heights unchanged).
- result: `compileJava` OK. Not tested in game.

## 2026-11-11 — BHSpells `radiant_aura` round 8: gold crystals instead of rocks, `radius`
- User asked what the black particles were (the floating rock chips, dark with a thin gold rim) and picked gold crystal / light shards, plus an adjustable radius.
- BHSpells: `particle/radiant_debris.fsh` removed, new `particle/radiant_crystal.fsh` (kite shard, two flat faces, ridge, glinting rim, translucent glowing gold). `RadiantAuraFx`: `debris` → `crystals` (capacity 256); new param `radius` (`FxParam.RADIUS`, `DEFAULT_CRYSTAL_RADIUS` 2.8, 0.5..16, times the size) for the area they rise from; rate follows the area (x0.3..4). `BhfxIds.RADIANT_AURA` doc lists it.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-11 — BHSpells `radiant_aura` round 7: the wind as a 4-layer storm
- User sent a motion sheet and four rules: orbit round the character, a helix climbing from the feet to the head, 2–4 layers at different heights and speeds, broken arcs with sharp tips (plus a flat ring at the feet, tilted rings, some gusts flying out).
- BHSpells `RadiantAuraFx`: `slash()` now takes a layer (tables `LAYER_HEIGHT/RADIUS/TILT/LEAN/HALF/SPIN/RATE/RISE/START`): feet ring, low, middle, head; each layer's ring plane tips towards a direction that wobbles round (`PRECESS`), arcs 90–195° of that ring climb along their length and rise as they turn; 15% fly outwards; layers switch on from the feet up 0–0.45 s after the burst; the 8 burst gusts spread over the layers and fly out. New param `speed` (`BhfxIds.SPEED`, 1; negative turns the other way). Per-layer debt replaces `SLASH_RATE`.
- result: `compileJava` OK. Not tested in game.

## 2026-11-11 — BHSpells `radiant_aura` rounds 4–6: wind texture, sharp → flat → layered ribbons
- Round 4 (user sent anime wind sheets, "not soft, a gusting storm"): sickle sheets with needle tips, hooked heads, claw barbs, dark-core / bright-rim shading (shadow in B). User: looks 3D; wants a texture, the sketch as the main reference.
- Round 5: the sketch drawn directly in flat colour (straight top edge, stepped lower edge, capsule slits, swallowtail head). Mid-way the user sent a brief: ribbons of wind of uneven thickness, curling, sharp tips, not smoke puffs; soft feathered edges; layered translucent strokes.
- Round 6 (current): `gen_wind_slash.py` builds each of 8 bands (1024x1024, 128 rows each) from layered translucent strokes (back ribbon, main ribbon swelling / pinching with a curled needle head, 1–2 crossing ribbons, hair-thin speed lines, crescent fragments), crisp leading edge, feathered trailing edge, flat brush-streak tone; soft slits and forked heads as sketch accents. R and G premultiplied by A. `radiant_ribbon.fsh`: 8 bands, flat colour (gold body, `color2` where brightest), dissolve by G / A. `RadiantAuraFx` slash uniforms: `Tint` color2, `Tint2` color at 0.9.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-11 — BHSpells `radiant_aura` round 3: wind wisps from the user's sketch
- User: the round 2 texture looked odd (saw-tooth lower edge); sent a sketch: long thin wisps, smooth edges, pointed and forked tips, slits inside, slivers peeling off, small steps, many variants.
- BHSpells `docs/bhfx/scripts/gen_wind_slash.py` rewritten (overlapping pointed strands, slits, slivers); `wind_slash.png` now 1024x512, 8 bands of 64 rows. `radiant_ribbon.fsh` reads 8 bands. `RadiantAuraFx.slash()`: 32 points, span 75–165°, half-width 0.24–0.44 x s, band 0..7; capacity 896.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-11 — BHSpells `radiant_aura` round 2: ribbons become textured wind slashes
- User (Roblox reference): wants the ribbons round the body to look like wind slashes — pointed crescents with a torn, spiky edge and loose slivers.
- BHSpells: new `textures/fx/wind_slash.png` (4 slash shapes; R brightness, G dissolve order, A coverage) from new `docs/bhfx/scripts/gen_wind_slash.py`. `radiant_ribbon.fsh` rewritten: texture by `p.along` / `p.corner.y`, drawn head first then frays by G, ALPHA premultiplied. `RadiantAuraFx`: the 7 endless streams are gone; `slash()` lays one 24-point arc at a time (`ribbon()` + `along(0..1)`, random span / radius / height / tilt / lean, orbiting 3.4–5.6 rad/s), 11 a second plus 8 big ones at the burst (queued to the next frame).
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-10 — BHSpells `radiant_aura`: golden aura with spiralling ribbons
- User sent two references (a cultivator wrapped in golden light ribbons with glints; an anime aura with lightning and floating rocks) and asked for an aura with that feel, in gold.
- BHSpells: new `client/RadiantAuraFx` (`bhspells:radiant_aura`, `BhfxIds.RADIANT_AURA`, registered in `BhfxClientBridge`). 10-tick gather (dust pulled in), one burst, then 7 RIBBON streams spiralling round the body in a radial plane, the `golden_aura` pass as body glow, glints, dust, `power_line`, `power_haze`, gold `haki_bolt` / `haki_strike` crackle, floating rock chips, ground glow pool and ring pulses. Params `color`, `color2`, `scale`.
- New materials `particle/radiant_ribbon.fsh` (uses a `Time` uniform and `p.along`, not `p.seed`), `radiant_glint.fsh`, `radiant_debris.fsh`, `radiant_ring.fsh`. Design notes: `BHSpells/docs/bhfx/radiant_aura.md`.
- result: BHSpells `compileJava` OK, glslcheck OK (4 files). Not tested in game.

## 2026-11-10 — Version 0.3.10
- The user asked to bump the version (roadmap U3, ahead of the U1 in-game round).
- bhfx: `gradle.properties` `mod_version` 0.3.9 → 0.3.10; `CHANGELOG.md` 0.3.10 section (phase 0 and 1, ribbon plane / along, scoped stop). Protocol stays 2.
- BHSpells: `build.gradle` `fg.deobf("net.deizyn:bhfx:0.3.10")` (compileOnly + runtimeOnly); `mods.toml` bhfx range `[0.3.9,0.4)` → `[0.3.10,0.4)`, because `PlasmaClashFx`, `AnimePunchFx` and `AnimeShockwaveFx` call `ribbonNormal` / `along` / `normal`, which 0.3.9 lacks; `libs/` now holds `bhfx-0.3.10.jar` only (the copy task removed 0.3.9).
- docs: `release.md`, `integrations/bhspells.md` ranges; `roadmap.md` U3.
- result: bhfx `build publishToMavenLocal` passed; BHSpells `build` passed against it. Not run in game. Nothing committed, branch not merged.
- next: U1 in game (the new version means `runClient` remaps the jar; no deobf cache to clear), then merge into `main`.

## 2026-11-10 — scoped `/bhfx stop` and `/bhfx_play stop`: `fx`, `near`, `name`
- Asked: `/bhfx stop` ended everything; effects started `at` a point (or at the look target) could not be stopped on their own. The user picked stop by effect id, by distance, and by a name given at start.
- bhfx `impl/FxCommands`: `stop [<who> | fx <id> | near <radius> | name <name>] [finish]` (`StopBy`, `which`, `describe`); `Started` keeps each effect's start point, dimension id, name and a weak ref to the attached entity (`Info`), and suggests running ids and names.
- bhfx `impl/FxCommandArgs`: `{name:a}` is a command option (1-32 of `[0-9A-Za-z_.+-]`), never an effect param.
- bhfx `impl/client/ClientFxCommands`, `impl/server/ServerFxEvents`: pass position, dimension and name to `Started`; stop messages say the scope. `README.md` command table updated.
- Tests: `FxCommandsStopTest` (6, runs the real brigadier grammar), `FxCommandArgsTest` +1. Dimension keys are `ResourceLocation`s because `ResourceKey` needs Minecraft's bootstrap, which fails in JUnit here.
- result: `gradlew build publishToMavenLocal` passed (all JUnit, glslang). Not tested in game.
- next: in-game check in [`testing.md`](testing.md).

## 2026-11-10 — BHSpells `anime_shockwave` round 4: wave walls drawn from one texture, varied shapes
- User (screenshot): the curved ribbons turn into sliced panels as they dissolve; use a texture and vary the curve / size instead.
- Cause: `power_wave.fsh` offset its noise by `p.seed`, which is flat per ribbon segment, so every segment sampled a different place (see `bugs.md`).
- New `textures/fx/power_wave.png` (4 bands: 3 open crescents + 1 seamless ring band; R brightness, G dissolve order, A coverage), generated by `BHSpells/docs/bhfx/scripts/gen_power_wave.py`. `power_wave.fsh` lays it over the arc by `p.along` / `p.corner.y`, frays by G; no `p.seed`. `waveArc()`: random band, span 45–144°, radius, spiral, height arch, lean twisting end to end, width 0.35–0.8. Burst rings use band 3, repeated 2x and scrolling.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-10 — BHSpells `anime_shockwave` round 3: 3D power wave, procedural, old textures out
- User: old textures can go; no need for extras like debris; wants depth, off the ground, like a wave of released power.
- BHSpells `AnimeShockwaveFx` rebuilt: running = open RIBBON arcs standing up and leaning out (`waveArc()`, 11/s, carried-over rate in `passes()`), aura lines up-and-out (`power_line`, ADD), heat shimmer (`power_haze`, DISTORT), faint ground flow (0.3). FINISH = flash, three full leaning wall rings + a flat ring in the air, burst lines, `haki_wave` pressure shell, ground ring, ripple. Debris, cracks, flat smoke sheets, slashes removed.
- New materials `power_wave.fsh` (premultiplied: dim body + bright core, streaks along `p.along`, tears with age), `power_line.fsh`, `power_haze.fsh`. Old `smoke_*.png` (7) and `smoke_*.fsh` (5) moved out of the repo to a scratchpad backup (they were never in git). `anime_shockwave.fsh`: crack texture removed.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-10 — BHSpells `anime_shockwave` round 2: steady outward smoke, explosion only on FINISH
- User: should be smoke streaming out steadily, not repeated explosion pulses; explode only on `finish`; the textures look off.
- BHSpells `AnimeShockwaveFx`: running = steady flow (`stream`s of flat smoke puffs, wind lines, wind slashes, hopping pebbles from a ground-snapped `FxEmitter.ring`, emitted in `passes()`); `onStop(FINISH)` triggers the old burst; CANCEL or a timed run (default 60 ticks) just dies down; `endTicks` 0 → `ceil(20/speed)+2` so live particles finish. Fixes: burst rings now grow (`sizeOverLife`; `pull` never grew them), slash rotation from the world direction, cracks darken instead of glowing.
- `anime_shockwave.fsh`: new flow layer (two polar noise layers scrolling outward, `Params1`, `Noise`). `smoke_streak.fsh`: picks one of the texture's 8 strands and lays it along the motion (was drawing all 8 as a comb). `smoke_debris.fsh`: picks one cell of the 4x4 rock sheet (was drawing all 16 rocks).
- Notes in `BHSpells/docs/bhfx/anime_shockwave.md`.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-10 — BHSpells `lunebris_surge` round 8: the pillar becomes ink spreading round the body
- User (screenshot of the tall pillar): make that ink spread round the body instead.
- BHSpells `lunebris_surge.fsh`: pillar replaced by a flattened ink cloud (`inkDensity`: three crossing noises, darker rim, middle clearing with age), ray-marched 20 steps in its ellipsoid, 35% when the camera is inside. `LunebrisSurgeFx`: `PILLAR_*` / `pillar()` → `INK_*`, `ink()` (2.4 s, fade-out over the last 1 s), `inkRadius()` (blooms to 6 x scale in 0.6 s, creeps +15%); the pass stays on until the ink is gone; bounds follow the ink.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-10 — BHSpells `lunebris_surge` round 7: big rays
- User: big rays. `LunebrisSurgeFx.RAY_LENGTH` 4.5 → 12, `RAY_WIDTH` 0.5 → 1.6; `lunebris_rays.fsh` root width `max(0.07, W × 0.16)` so the wide rays still start narrow at the body.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-10 — BHSpells `lunebris_surge` round 6: rays of dark light
- User: the lines spreading out still read oddly; make them dark light shooting out.
- BHSpells: new pass `lunebris_rays.fsh` (14 black god rays from the chest, narrow at the body, widening and fading out, halo, pale sheen, outward streaks; every cycle starts at the release so the first is a volley, then each flickers on its own 0.55–1 s cycle at a new angle). `LunebrisSurgeFx`: `spires` batch, `spire()` and its volley / stream removed; `RAYS` pass (`RAY_*`, `rayIntensity`, 2.4 s + 0.6 s fade, burst 0.4 s), bounded round the chest.
- result: `compileJava` OK, glslcheck OK. Round 6 not tested in game.

## 2026-11-10 — BHSpells `lunebris_surge` round 5: lashes round the body, pillar back, one smoke flame
- User: round 4 misread "the ink going only up" — it meant the big lashes thrown high into the sky: make them go every way but shorter, round the body. Take the smoke round the body out; the one smoke flame, appearing with the old flame, is the smoke there; keep the smoke ring spreading out.
- BHSpells `LunebrisSurgeFx`: `spire()` now any direction (a little up), speed 9–15, drag 2.2, life 0.6–0.9 s (stops 3–5 blocks out), width 0.45–0.85; the release volley uses it too. Pillar restored (`PILLAR_*`, `pillar()`, `SurgeB.zw`, `SurgeC.y`); the ink burst removed from Java and `lunebris_surge.fsh`. Hold chest wisps, release chest puffs and the foot smoke removed. `lunebris_body.fsh`: smoke flame denser (×0.75, cap 0.8) and a step lighter grey. `LunebrisManager.castLunebris`: no vanilla large smoke on the body when bhfx shows.
- result: `compileJava` OK, glslcheck OK. Round 5 not tested in game.

## 2026-11-10 — BHSpells `lunebris_surge` round 4: smoke flame, all-round ink burst, long spires, roar
- User: add a black flame made of smoke (like the smoke puffs, flame-shaped), one, on the body, keeping the old flame; the ink that only goes up should go out in every direction; the black lines thrown up should keep going for a while; add a violent, echoing lion-king roar.
- BHSpells `lunebris_body.fsh`: `smokeFlame` layer behind the core (1.55 x wide, 1.35 x tall, soft, fbm puffs, `BodyB.z`), depth cylinder 1.9 → 2.7 x width. `lunebris_surge.fsh`: pillar replaced by an ink burst (`spikeReach`, ray-marched 18 steps in a sphere round the chest, `SurgeB.zw` = strength, reach). `LunebrisSurgeFx`: `spires` stream for 2.4 s after the release (drag 0.45, life 1.1–1.6 s, capacity 256); roar 3 ticks after the release (ravager roar + warden roar + dragon growl, 8 `haki_wave` waves from the mouth every 3 ticks, rumble / zoom / ripple / FOV pulses for 1.6 s).
- result: `compileJava` OK, glslcheck OK. Round 4 not tested in game.

## 2026-11-10 — BHSpells `lunebris_surge` round 3: one burst, bigger flame, smoke fades, `scale`
- User: still two bursts of the same kind → one burst (or two that differ: less smoke clumped on the body, more fire); the flame of the hold should be body-sized too; keep the smoke spreading round; smoke should fade out; make the size adjustable.
- BHSpells `LunebrisSurgeFx`: the first spurt (6 ticks after the release, same lashes + chest smoke) and the `haki_wave` shell (a second air ring at another speed) are gone; later surges are body-flame flares (`flare()`, 0.55 strength, 0.6 s, from 1.5 s on, every 14–34 ticks) with a few thin lashes, no smoke; chest smoke cut (hold knot every other tick, release 16 → 4 small); the 64-puff ground smoke ring is back; body flame 3.0/0.85 → 3.6/1.05 and full from early in the hold. New param `BhfxIds.SCALE` (`scale`, 0.25..4) sizes body flame, pillar (`SurgeC.y`), lashes and smoke; `BhfxCompat.lunebrisSurgeStart` takes it, `LunebrisSpell.SURGE_SCALE` = 1. `lunebris_flame.fsh`: soft alpha fade-out instead of eroding. `LunebrisManager.castLunebris`: vanilla large smoke on the body 80 → 15 when bhfx shows.
- result: `compileJava` OK, glslcheck OK (4 files). Round 3 not tested in game.

## 2026-11-10 — BHSpells `lunebris_surge` round 2: one body flame, big spires, yin ripples
- User (after seeing round 1 in game): the rest is fine, but the flames streaming round the body look odd → one flame the size of the body; the black lines thrown up should be bigger; the ground ink came out twice (it overlapped `lunebris_aura`'s floor) → make it yin spreading out like ripples, once.
- BHSpells `LunebrisSurgeFx`: `flames` batch and `flame()` removed; new pass `lunebris_body.fsh` (one flame round the caster's axis, follows `frame.position()`, fades out in first person); new `spires` batch (26 lashes, 0.7–1.3 wide, stretch 0.16); the 64-puff ground smoke ring removed; `WAVE_SECONDS` 0.8 → 0.7 to match the aura's 14-tick opening; `SurgeC.x` ripple drift. `lunebris_surge.fsh`: the ink tide is replaced by refracting yin ripples (spacing 2.6, cold, dark troughs, pale crests).
- result: `compileJava` OK, glslcheck OK. Round 2 not tested in game.

## 2026-11-10 — BHSpells `lunebris_surge`: Lunebris's held darkness breaking loose
- User: more bhfx for Lunebris, a black aura of darkness spreading everywhere, like power held for a long time and then released.
- BHSpells: new `LunebrisSurgeFx` (`BhfxIds.LUNEBRIS_SURGE`, slot `LUNEBRIS_SURGE_SLOT`), pass `lunebris_surge.fsh` (tide over surfaces, flattened smoke dome, pillar), materials `particle/lunebris_flame.fsh` and `particle/lunebris_lash.fsh`, reuses `haki_wave`. Phases: hold (drawn in) → release on the `ready` update → surge on the body. `BhfxCompat` / `BhfxLoadedBridge`: `lunebrisSurgeStart` / `Release` / `Cancel`; called from `LunebrisSpell.onServerPreCast`, `LunebrisManager.castLunebris` and the cancelled windup. Spec: `BHSpells/docs/spells/aqua/lunebris.md` "Dark Surge".
- result: `compileJava` OK, glslcheck OK (3 files). Not tested in game.
- next: in-game checklist in `testing.md`.

## 2026-11-10 — BHSpells `formation_array` round 9: ink removed, ring timing params
- User: take the ink out of the formation array (they will call `ink_spread` separately); the array should emerge slowly, or the ring timing should be settable.
- BHSpells: pass `formation_ink.fsh` renamed `ink_spread.fsh` (pass id = `BhfxIds.INK_SPREAD`); `BhfxIds.FORMATION_INK`, `INK_WORLD`, `INK_REACH`, `INK_SPEED` removed; `InkWash` used by `InkSpreadFx` only. `FormationArrayFx`: no ink, own `play`; new params `ring_delay` (1.2 s) and `ring_fade` (1.8 s) sent as `ArrC`, ring sounds and sparks at `layerTick`, default duration 20 s (at least 10 s after the last ring). `formation_array.fsh`: ring timing from `ArrC`, fade-in over the whole `ring_fade`, glow swelling `4 open (1 - open)` instead of the instant flash. Preview: `?ink` to add the ink pass, `?delay=` / `?fade=`.
- result: `compileJava` OK, glslcheck OK (both), preview checked (1.5 s .. 10 s, no ink). Not tested in game.

## 2026-11-10 — BHSpells ink world: whole view only while inside the ink
- User: the black-and-white view went past the ink's reach; wanted it only while the ink covers you, easing back to colour on the way out.
- BHSpells `formation_ink.fsh`: the "past the reach and the sky dyed together at the end" step removed; new `InkB.x` (viewer inside, 0..1) turns the whole view to ink; the edge line stays (softened) after the spread. `InkWash.updateInside`: camera within front − 1 → eases in (0.6 s) / out (1.5 s). Applies to `ink_spread` and `formation_array`.
- result: `compileJava` OK, glslcheck OK, preview checked outside (pool only) and inside (whole view). Not tested in game.

## 2026-11-10 — BHSpells `ink_spread`: the ink world as its own effect
- User asked to split the ground ink spread out of the formation array for reuse.
- BHSpells: new `compat/bhfx/client/InkWash` (ink timing with the shader's front formula, pass uniforms, sounds; hold < 0 = until stopped), used by `FormationArrayFx` (its own ink code removed, behaviour unchanged) and by new `InkSpreadFx` (`BhfxIds.INK_SPREAD`, registered in `BhfxClientBridge`): stays where it starts, spreads to `radius` (32) over 14 s x clamp(radius / 96, 0.35, 1.5) / `speed`, holds 3 s, washes away 7 s; `hold:true` keeps it until stopped; an early stop washes it away. Same pass `formation_ink.fsh` (header comment only). Notes: `BHSpells/docs/bhfx/ink_spread.md`.
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-10 — BHSpells `formation_array` round 7: sound, radius 120
- User asked for sound and said the array will be about radius 120.
- BHSpells `FormationArrayFx`: vanilla sound for every beat (temple bell + chime per ring, bell / conduit / amethyst for the yin-yang, beacon hum and soft chimes while standing, squid / sculk / underwater for the ink burst, front, pass-over and wash-away, beacon + bell on stop), each played at the array's nearest point to the viewer (`ctx.cameraPos()`) with volume raised for range. Big radius: stack unit `min(R, 8)` for layer heights and light reach (`ArrB.w`), bob ≤ 0.15, curtain ≤ 24 high, `ink_reach` default max(96, 1.6 R), particles scaled and half spawned round the viewer.
- result: `compileJava` OK, glslcheck OK, preview at r=120 (standing on it, and from 150 away). Sounds not heard yet (no in-game test).

## 2026-11-10 — BHSpells `formation_array` round 6: the real seam fix
- User saw round 5 in game: the dashed line was still there, across ring layers 1–4 only, at a fixed angle.
- Real cause: the ring texture's gradients came from screen derivatives of the atan angle (min of `u` and `fract(u + 0.5)`); fine in the browser, but some drivers still jump at the wrap (likely `dFdx(fract(x))` folded to `dFdx(x)`), so quads on the wrap line sampled the lowest mip. Round 5's curtain change was not the cause (old and new curtain render identically in the preview); this is corrected in round 5's notes.
- BHSpells `formation_array.fsh`: analytic angle gradients from the disc coordinates (`(-q.y, q.x) / |q|^2` dotted with `dFdx(q)` / `dFdy(q)`), `angleGrad` removed. `gen_formation_textures.py`: wrap-safe resize (three tiles, keep the middle); texture regenerated.
- result: glslcheck OK, preview unchanged (the browser GPU never showed the line, so the fix is unverified until seen in game). No Java change.

## 2026-11-10 — BHSpells `formation_array` round 5: seam line, sharp ink edge, `ink_speed`
- User saw round 4 in game from below the disc: a dashed line "where it doesn't quite join"; wanted adjustable ink speed and reach, and a clearer ink edge.
- ~~Cause of the line: the rim light curtain's noise~~ wrong, see round 6 above. The curtain noise was made loop-safe anyway.
- BHSpells `formation_array.fsh`: new `loopNoise` (value noise repeating in x) for the curtain. `formation_ink.fsh`: narrow band (`1.2 + 0.06 front`), dark ragged brush line ~0.4 block on the front, edge raggedness from (cos, sin) noise. `BhfxIds.INK_SPEED` (`ink_speed`, scales spread, hold and fade); `ink_reach` already existed. Preview: below-view panel 6, `?ispd=`.
- result: `compileJava` OK, glslcheck OK, preview checked (no seam from below; edge at 10 s). Not re-tested in game.

## 2026-11-10 — BHSpells `formation_array` round 4: ring-by-ring appearance, slow blinking, ink only at the start
- User saw round 3 in game: too bright (the ink world too), still too fast; ink should cover once at the start and then fade while the array stays; wanted the blinking back but very slow, plus a light fading over the array; rings should appear one whole ring at a time, not drawn round.
- BHSpells `formation_array.fsh`: one layer every 0.9 s from the rim in (no running head): fade-in, settle from 1.12x, flash, a ring of light running out of its edge; slow per-cell blinking on own 3..6 s cycles; whole-array light fade (5 s) and centre swell (7 s); all emission lower (glow 0.8). `formation_ink.fsh`: darker washes and paper. `FormationArrayFx`: ink from 4.6 s over 14 s, holds 3 s, washes away over 7 s (`inkStrength`), default 30 s; chime and sparks per layer.
- result: `compileJava` OK, glslcheck OK, browser preview checked (1.1, 4, 10, 17, 26, 30 s). Not re-tested in game.

## 2026-11-10 — BHSpells `formation_array` round 3: slow outer-first opening, shimmer, gradual ink dye
- User saw round 2 in game: the ink came out as green and white patches over the whole world; wanted the array to open slowly from the outer ring in to the yin-yang, a shimmer instead of blinking, and the ink to dye the world gradually.
- Cause of the patches: `formation_ink.fsh` clamped the distance of everything past the reach to the same value, so near the end of the spread the noisy edge decided every far pixel at once.
- BHSpells `formation_array.fsh`: layer i opens at (4 − i) · 0.5 s over 1.1 s, settling in from 1.25× and turning into place; random cell flicker replaced by a slow sheen and a smooth per-cell glint. `formation_ink.fsh`: no distance clamp; a wide soft band behind the front (colour drains to grey, then ink), faint tide mark, past-the-reach and sky dyed together at the end. `FormationArrayFx`: ink from 2.8 s over 8 s, default duration 20 s, rim sparks at 0 s and centre sparks + sound when the yin-yang lands (tick 40).
- result: `compileJava` OK, glslcheck OK, browser preview checked (1.2 s, 2.4 s, 5 s, 7.5 s, 14 s). Not re-tested in game.

## 2026-11-10 — BHSpells `formation_array` round 2: the world turns to ink
- User: when the array appears, the world should turn into black-and-white ink spreading everywhere.
- BHSpells: new pass `shaders/bhfx/formation_ink.fsh` (`BhfxIds.FORMATION_INK`, params `ink` / `ink_reach` in `BhfxIds`), added before the array pass in `FormationArrayFx.passes`: blotchy front from the array out to the reach (slow near, fast far), ink rim and spots ahead, then soft 4-level washes, ink outlines from depth folds and brightness jumps, misty paper far away and in the sky. Preview draws it too (`?noink` to compare).
- result: `compileJava` OK, glslcheck OK, checked in the browser preview (spreading at 0.9 s, fully inked at 4 s). Not tested in game.

## 2026-11-10 — BHSpells `formation_array`: floating yin-yang formation array
- User sent two reference images of Chinese formation arrays and asked for a floating, moving array; mid-way asked for real Chinese characters on life and death, merging, protection, opposites, black and white, yin and yang.
- BHSpells: `compat/bhfx/client/FormationArrayFx` (`BhfxIds.FORMATION_ARRAY`, new `BhfxIds.NORMAL`, registered in `BhfxClientBridge`), pass `shaders/bhfx/formation_array.fsh` (five ray-plane layers stacked along the normal, counter-rotating; yin-yang procedural; rings from an unrolled strip texture via `textureGrad` with a seam-safe angle gradient; word rings half light-on-ink, half ink-on-light; rim curtain; light on nearby surfaces), texture `textures/fx/formation_rings.png` from `docs/bhfx/tools/gen_formation_textures.py` (Noto Serif JP, OFL), preview `docs/bhfx/tools/preview_formation_array.html`. Notes: `BHSpells/docs/bhfx/formation_array.md`.
- result: `compileJava` OK, glslcheck OK, checked in the browser preview (3/4, top-down). Not tested in game.

## 2026-11-08 — BHSpells `plasma_clash`: meteor streaks, dome length param
- User: wanted long lines again, flowing like shooting stars, and an adjustable dome length.
- BHSpells: `PlasmaClashFx` streak lines 6 per side, each drawn for 0.32 s (long streak, head first) then reborn; `plasma_streak.fsh` bright white head (`exp(-14 u)`), long cooling, fraying tail. New param `length` (`BhfxIds.DOME_LENGTH`, 0.4..3) → `ClashC.x` in `plasma_clash.fsh` (depth and trail × L, marched sphere 1.9 + 0.9 (L − 1) R with a soft edge, steps 30..48), screen bounds and the streaks' backward push scale with it. Preview takes `?len=`.
- result: `compileJava` OK, glslcheck OK, dome length checked in the browser preview (len 1 vs 2). Not tested in game.

## 2026-11-08 — BHSpells `plasma_clash`: fire on the domes as repeating flakes
- User: wanted the fire lines to be born at the centre, flow to the end, then be born again, "like flakes flowing over the dome".
- BHSpells `PlasmaClashFx.peel()`: 10 lines per side, each draws a short flake (0.1 s of a 120/s stream at a fresh random angle), rests 0.08–0.3 s, `reset()` and draws the next; `along` set per point 0 (front) → 1 (tail). `particle/plasma_streak.fsh` rewritten for flakes (round front, fraying tail by `p.along`, cooling by `p.age`).
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-08 — BHSpells `plasma_clash` phases 3–4, streamers born at the centre
- User (phase 2 in game): the fire on the dome skin looked like it floated; asked for it to start at the centre and flow out, then phases 3 and 4.
- BHSpells `PlasmaClashFx`: streamers born at the seam centre, one batch per side whose gravity (set per frame) pushes back along that side's axis, with drag 2.2, so each path bends back over the dome. Phase 3: per beat two closed-ribbon shock rings in the seam plane (`punch_wind`, pulled out against drag 4, our / foe core colours), `screen.ripple`, and when ground is within 4 blocks (block collision scan in `tick`) a flat ribbon ring (`ribbonNormal` up, own orbit axis) plus `onGround` dust. Phase 4 (FINISH): domes shrink into a flaring seam, 90 fast sparks, 4 big rings + big ground ring, `impact_ring` ×2 and `impact_ripple` (not `impact`: it has impact frames), wide ripple and shake, no flash.
- result: `compileJava` OK. Not tested in game.

## 2026-11-08 — BHSpells `plasma_clash` phase 2: fire peeling off the domes, seam sparks, fixed position
- BHSpells: `PlasmaClashFx` — without a `target_entity` both ends are locked at the start (`lock()`; user: the clash should not turn with the caster); `peel()` streams 6 ribbons per side from the dome skin (same paraboloid as the shader) flying out and back; `sprayFromSeam()` sparks of both core colours in the seam plane (more on each beat). New ribbon material `particle/plasma_streak.fsh` (noise along `p.along`, hot head on the dome, thin cool tail).
- result: `compileJava` OK, glslcheck OK. Not tested in game.

## 2026-11-08 — BHSpells `plasma_clash` phase 1: two plasma domes meeting at a seam
- BHSpells: `compat/bhfx/client/PlasmaClashFx` (`BhfxIds.PLASMA_CLASH`, registered in `BhfxClientBridge`; positions, balance and fighter checks copied from `GoldenClashFx`), pass `shaders/bhfx/plasma_clash.fsh` (30-step march: paraboloid fronts with a bright skin and a glow trailing behind, 3D value-noise streaks; analytic seam disc), browser preview `docs/bhfx/tools/preview_plasma_clash.html`. Plan and rounds: `BHSpells/docs/bhfx/plasma_clash.md`.
- result: `compileJava` OK, glslcheck OK, tuned in the browser preview (4 rounds). Not tested in game. Phases 2–4 (streamers, beat ripples, finish) not started.

## 2026-11-07 — BHSpells `anime_punch`: first effect on the new ribbon features
- BHSpells: `compat/bhfx/client/AnimePunchFx` (`BhfxIds.ANIME_PUNCH`, registered in `BhfxClientBridge`), material `particle/punch_wind.fsh`; design notes `BHSpells/docs/bhfx/anime_punch.md`. Shock rings (closed ribbons, normal = punch direction), tilted wind arcs (per-point normal) orbiting the punch axis, speed lines, AXIS spike star, flash, puff, impact frames, ripple.
- Round 2 (user saw it in game: the impact frame hurt the eyes): impact frames off by default (`frames:true` brings them back), replaced by a dark edge `vignette` pulse; screen `flash` removed, aberration 0.4 → 0.25. `compileJava` OK, not re-tested.
- result: BHSpells `compileJava` OK against the 2026-11-07 bhfx jar (the deobf cache already held it: contains `SwirlTestFx`); glslcheck OK. Not tested in game.

## 2026-11-07 — Ribbons: smooth `p.along`, strips in a plane, `test_swirl`
- The user asked for anime wind/smoke swirls: one shared texture bent into rings, with depth (reference: a Roblox-style ground swirl). Two gaps found: no smooth coordinate along a ribbon drawn all at once (`p.age` is equal on every point, `p.data` is `flat` per segment), and ribbons always turned to the camera.
- bhfx API: `FxParticles.ribbonNormal`, `ParticleSpawn.along`, `ParticleSpawn.normal`; material fields `p.along`, `p.facing`. `Facing.RIBBON` javadoc.
- bhfx: `ParticleBatch` — ribbon points store along (axis.y; auto = previous + distance), own normal octahedral in axis.z/w (+ `NORMAL_SET`); `octEncode` / `octDecode`; batch `rnx/rny/rnz`. `PostRenderer.drawBatch` binds `PRibbonNormal`. `particle/vertex.vsh` — `Pt.along`, `Pt.normal` (turned with the orbit), `bhfxOctDecode`, side = dir × normal, `pAlong`, `pFacing`; `_header.glsl` / `_footer.glsl` fields.
- bhfx: new test effect `impl/client/test/SwirlTestFx` (`bhfx:test_swirl`, registered in `ClientBootstrap`) + material `particle/test_swirl.fsh` (noise_fbm streaks stretched by `p.along`, swept tail → head, tapered, lighter edge-on).
- `ParticleBatchTest` +3 cases (along, normal storage, oct round trip): 17 pass. `checkShaders` 17 OK. Build OK, jar copied to BHSpells and published to mavenLocal (still version 0.3.9: clear the deobf cache before `runClient`, see `bugs.md`).
- result: build + unit tests + glslang only; not tested in game. Checklist in `testing.md`.

## 2026-11-07 — Scale plan phase 1: known vs built effects, even particle thinning
- bhfx: `impl/client/FxSelection` (new, pure) — pinned → within `guaranteedRadius` → by distance up to `maxActiveFx`; 8-block margin and 2 s hold for shown effects; distance from the player only.
- bhfx: `ClientFxManager` — `Entry` is now a known effect with an optional `Fx`: start data, merged params, age from the client tick it arrived, learned default duration (`DEFAULT_TICKS`), distance, pinned. `add` builds at once when pinned, within 16 blocks, or within the 2 ms constructor budget of the tick; `select()` every tick builds the chosen ones nearest first and fades out the rest (10 ticks), freeing them; unbuilt entries expire by duration or a lost followed entity; a stop for an unbuilt entry just forgets it; updates merge into the stored params. Built on time (≤ 2 ticks) = as sent; later = at its age now, fading in. Up to 1024 known (farthest unbuilt forgotten). Counters: known / built / hidden / built late. `startLocal(…, mayHide)` for `/bhfx stress`.
- bhfx: `PostRenderer` — particle stride: when visible non-ribbon batches want more slots than the quality cap, every batch draws one slot in N (rises at once, falls after 30 frames); ribbons unthinned; `/bhfx status` says "particles thinned to 1 in N". `Presence` uniform for passes that declare it; `PMode.w` presence for particles. `ParticleRenderer.draw(b, instances)`. `PassListImpl.Entry.presence`.
- bhfx: shaders — `particle/vertex.vsh` slot = instance × `PRing.z`; `particle/_footer.glsl` `soft *= PMode.w`.
- bhfx: `BHFxConfig` — `maxActiveFx` 32 → 96, new `guaranteedRadius` 48; `ClientConfigMigration` step 4 moves a file still at 32 to 96.
- docs: `decisions.md`, `shaders.md`, `bugs.md`, `README.md`, phase 1 checklist in `testing.md`.
- result: `gradlew build` passes, 98 JUnit tests (9 new), glslang OK. Not run in game. The stale ForgeGradle deobf cache of 0.3.9 was deleted after the game closed; the next `runClient` remaps the phase 1 jar.
- next: the "Usable first" steps U1–U3 in [`roadmap.md`](roadmap.md) (in-game round, fixes, release 0.3.10); the rest of the scale plan waits. Committed as `1ec0427` on `feature/scale-phase0`.

## 2026-11-07 — Phase 0 first in-game round
- Read the user's `BHSpells/run/logs/latest.log` (04:18–04:23). Results and before numbers in [`testing.md`](testing.md): client burst of 70 cost 7.55 ms of constructors in one tick; GPU avg 1.75 / max 7.05 ms; server burst 64 of 70 (6 refused by `maxStartsPerTick`), ~86 B per start.
- The user saw flicker in the spread run of three particle effects; likely the per-frame particle slot budget skipping whole batches plus evictions (added to [`bugs.md`](bugs.md) Open).
- result: no code changed. Open: the spread server run, F3 heap / frame time, `/bhfx status` during a run and 30 s after.

## 2026-11-07 — Scale plan phase 0: stress commands and counters
- bhfx: `impl/FxStress` (new) — one stress run: count, ids in turn, all at once or spread over seconds, random points on a disc, `parseIds`. `impl/FxCommands.stress` — shared grammar `stress <count> [radius] [seconds] [fx ...]`.
- bhfx: `impl/client/ClientFxCommands` — `/bhfx stress` (local effects, registered in the `/bhfx stop` list, ticked from `ClientEvents`). `ClientFxManager` — counters: peak running, evicted by `maxActiveFx`, failed, unregistered, busiest tick's starts and constructor time, slowest constructor; particle batches, slots and heap, biggest batches' peak alive / capacity, batches never above 25%; peaks sampled once a second; shown in `/bhfx stats`, reset on world change and by `/bhfx stress`. `ParticleBatch.samplePeakAlive/peakAlive/heapBytes`, `Fx.bhfxParticleBatches` (internal).
- bhfx: `impl/net/NetMeter` (new) — `/bhfx_play net [seconds]`: per-player bytes (encoded message + 12 B, before Minecraft framing and compression), packets and starts, plus starts refused by `maxStartsPerTick` / `maxActivePerCaster`; no work unless running. `BHFxNetwork.sendTo/sendToDimension` replace `send`; `ServerFx` uses them and reports drops. `ServerFxEvents` — `/bhfx_play stress` and `/bhfx_play net`.
- bhfx: `README.md` commands table.
- result: `gradlew build` passes, 89 JUnit tests (13 new: `FxStressTest`, `NetMeterTest`), glslang OK. Not run in game. Version unchanged (0.3.9); the rebuilt jar has the same name as the old one, see the stale-jar note in [`bugs.md`](bugs.md) before testing in BHSpells.
- next: the phase 0 checklist in [`testing.md`](testing.md), then phase 1.

## 2026-11-06 — Assessment: ~70 players with different effects at once
- No code changed. Read `BHFxConfig` limits, `ClientFxManager.add`, `ParticleRenderer`, `ParticleBatch`, `ServerFx` (limits, outbox, tracker), `S2CFxStartBatch`, `FxParam`.
- bhfx memory is bounded on both sides: client `maxActiveFx` 32 (oldest evicted), render targets ~45–68 MB fixed, particles 112 B per slot per side with a per-frame slot cap; server keeps a small record per effect, `maxStartsPerTick` 64 per dimension, `maxActivePerCaster` 16, params ≤ 32 KB, 16 starts per batch packet.
- Weak spots for this scale: eviction is oldest first (your own effect can be dropped for a stranger's); every batch allocates a new `float[]` and GL buffer, so many starts in one tick allocate together; dead slots still draw; one global start cap per dimension can be used up by one caster; a join or respawn into many persistent effects sends them all in one tick.
- Outside bhfx (not part of this plan): BHSpells sends vanilla particles from the server one point at a time in loops (217 `sendParticles` calls in 69 files, e.g. `TigershadeTerrabreakSpell` ~800 packets per viewer per impact). This is the likelier cause of disconnects with many players.
- result: estimate from code only, nothing measured.
- next: scale plan in [`roadmap.md`](roadmap.md), starting with phase 0 (measure).
- Later the same day: side effects of each phase reviewed and added to the plan. The user then set a requirement: everyone in range sees the same effects, with even detail loss under load, and nothing blinks or vanishes when walking closer. Phase 1 was rewritten around this (light records, guaranteed radius, distance-only choice with hysteresis, fades). Checked: the seed comes from the server (`ServerFx` line 124), but `Fx.random()` is one sequence, so an effect built late matches only in shape.

## 2026-11-05 — bhfx `runClient` fixed (Carpet refmap)
- bhfx: `build.gradle` — run configs remap mixin refmaps (`mixin.env.remapRefMap`, `refMapRemappingFile`), so Carpet's mixins apply in dev (see [`bugs.md`](bugs.md)).
- result: `runClient` reaches the title screen; no world joined or effect tested yet.

## 2026-11-05 — Reference skills for what was built
- bhfx `.claude/skills/`: `bhfx-particles` (what the GPU particle system can do, API cheat sheet, rules, limits), `bhfx-effect-recipes` (lightning, sky tears, ground marks, grades, screen reactions, shader traps; `scripts/glslcheck.py` checks shaders of any mod), `bhfx-dev-loop` (build → BHSpells → runClient, stale jar cache, log reading, status / bench).
- BHSpells `.claude/skills/`: `dawn-cleaving-solar-flash`, `conqueror-haki`, `ef-animation-cut` (`scripts/cut_animation.py` analyze / build; reproduces the shipped dawn_cleaving charge / release files exactly).
- `.claude/` is git-ignored in both repos: the skills live on this machine only.

## 2026-11-05 — `bhspells:dawn_rend` rounds 2–5 and the spell Dawn Cleaving Solar Flash
- In-game rounds with the user: a white / grey sheet over the screen was NaN from `pow(negative, 2.0)` (now in `shaders.md`); claws made bigger, per-claw table (`DawnRendFx.CLAWS_TABLE` → uniform arrays `Claw` / `ClawB`), diagonal from the ground on the right up to the left, capsule distances and a soft fade where the tear plane meets the terrain (a rectangular frame before); lightning pass `dawn_rend_storm.fsh` (after `solar_eclipse_storm`) with gold tear bolts and pink bolts gathering into and bursting out of the caster; ground rings, smoke and the flame-spark helix removed.
- BHSpells: new Legendary lightning spell `bhspells:dawn_cleaving_solar_flash` (LONG 32 ticks) that starts the effect from the server (`BhfxCompat.dawnRendStart` / `dawnRendCancel`, `FxKey` per caster), plays Epic Fight `efn:biped/exsiliumgladius_reborn/exsiliumgladius_aaa` on release and deals the claw damage once. Notes: `BHSpells/docs/bhfx/dawn_rend.md`, spec `BHSpells/docs/spells/lightning/dawn_cleaving_solar_flash.md`.
- result: BHSpells `build` passed, shaders pass glslang (scratch script). Not seen in game. Not committed.

## 2026-11-04 — BHSpells showcase effect `bhspells:dawn_rend` (ประกายอรุณฉีกนภา)
- The user asked for one effect using every bhfx technique: the ground bursts into dawn fire, then a swipe rakes five claw marks tearing the sky.
- BHSpells: `compat/bhfx/client/DawnRendFx` (12 particle batches, per-frame ribbon / orbit streams, reuses `flame_field`), shaders `dawn_rend.fsh` (ray-plane tears with a parallax sky inside, bent image, cracks, ground furrows, tear light, dawn sky), `dawn_rend_grade.fsh`, `particle/dawn_haze.fsh`; `BhfxIds.DAWN_REND`, registered in `BhfxClientBridge`; `docs/bhfx/dawn_rend.md`.
- result: BHSpells `compileJava` and `build` passed; the three shaders pass glslang through a scratch script that expands includes like `checkShaders`. Not seen in game. Not committed.

## 2026-11-04 — Third in-game round passed; pass cap no longer counts particle batches
- User: the round-two fixes pass (helices, hole ring, water, comet head); several effects at once still lose parts.
- bhfx: `PassListImpl.drawList` caps screen passes only; `BHFxConfig` comment; new JUnit `PassListImplTest`. `bugs.md`, `decisions.md`, `testing.md`.
- result: `gradlew build` passed (JUnit, `checkShaders`); jar copied to `BHSpells/libs` and ~/.m2, deobf copy cleared by the build. Not re-tested in game.

## 2026-11-04 — Second in-game round; helices per frame, vortex hole follows, water counts as ground
- Read the user's second-round results (see `testing.md`).
- bhfx: `FlowTestFx` streams helices in `passes()`; `VortexTestFx` sets the hole centre per frame; `LevelGround` counts fluid surfaces; `particle/ribbon.fsh` soft head; javadoc `FxParticles.orbit`, `FxEmitter.Shape.onGround`; `decisions.md`, `API_PLAN.md`, `bugs.md`.
- result: `gradlew build` passed (JUnit, `checkShaders` 16 OK); jar copied; deobf copy not cleared yet (the user's client was open). Not re-tested in game.

## 2026-11-04 — First in-game round of the 0.3.9 particle features; three fixes
- Read the user's results, screenshots and `latest.log` (details and bench numbers in `testing.md`).
- bhfx: `PostRenderer.drawBatch` binds the ramp so it uploads; `particle/vertex.vsh` ribbon turbulence seeded per chain, orbiting particles placed round the current centre; `ParticleBatch` `Cursor.orbit` / `pull` store from the centre, cull sphere + |centre|; `FxParticles.orbit` javadoc; `ParticleBatchTest` 14 cases. See `bugs.md`.
- result: `gradlew build` passed (JUnit, `checkShaders` 16 OK); jar copied to `BHSpells/libs` and ~/.m2, but the deobf copy could not be cleared (the user's client was open). Not re-tested in game.
- next: close the client and clear the deobf copy, re-test (checklist in `testing.md`); water behaviour and the flicker to clarify with the user.

## 2026-11-04 — Particle memory and optimization plan (no code change)
- Answered: memory is fixed per batch, 112 bytes per slot on the GPU plus the same in the Java `float[]` mirror, plus one shared ~1.75 MB off-heap upload buffer (`test_flow` ≈ 0.42 MB each side). The real cost is GPU fill rate and the vertex work on dead slots.
- Optimization plan agreed and written into `roadmap.md` (live window, peak stats, LOD, near-camera limits; on hold until the 0.3.9 in-game checklists pass; release as 0.3.10).
- result: docs only. Nothing measured.

## 2026-11-04 — BHSpells runClient loaded a stale bhfx 0.3.9
- The user reported "run client ไม่ติด", then "no bhfx flow to run". BHSpells' `runClient` (19:45) reached the title screen with the 14:18 bhfx 0.3.9 from ~/.m2 (mavenLocal wins over `libs/` for the same version), without the 15:50 features (`bhfx:test_flow`, ribbons, ground, built-in materials). The 15:50 title-screen check had the same stale jar. See `bugs.md`.
- bhfx `build.gradle`: `copyToBHSpells` depends on `publishToMavenLocal` (which waits for `reobfJar`) and afterwards deletes ForgeGradle's deobf copy of `<mod_version>`, warning when a running client holds it. `docs/bugs.md`, `docs/integrations/bhspells.md`, `docs/testing.md` updated.
- result: ~/.m2 and `BHSpells/libs` jars have the same sha1; after clearing the deobf copy, `runClient` (19:56) loaded it (`.input` `orig=` matches; `FlowTestFx`, `LevelGround`, `particle/ribbon.fsh` present), title screen, no bhfx error. No world joined yet.
- next: the `test_flow` / `test_vortex` checklists in `testing.md`.

## 2026-11-04 — Turbulence, over-life curves, ribbons, ground snapping, built-in materials (0.3.9)
- The user asked for all four proposed additions so everything is tested in one go.
- bhfx API: `FxParticles.turbulence`, `colorOverLife` (keys / ramp), `alphaOverLife`, `sizeOverLife`, `Facing.RIBBON`, `ribbon()` + `Ribbon`, material constants `DOT` / `STREAK` / `SPRITE` / `RIBBON`; `FxEmitter.Shape.onGround(search[, lift])`, `groundSearch()`, `groundLift()`, `FxEmitter.GroundProbe` (internal).
- impl: `ParticleBatch` (curve and turbulence fields, ribbon links in axis.x / texel 6 w, streams keep a chain, cull sphere adds the size curve and turbulence), new `LevelGround`, `PostRenderer.drawBatch` uniforms (`PTurb`, `PRing`, `PCurves`, `PColorKey0..3`, `PAlphaKeys`, `PSizeKeys`, `PRamp`, `PRampRange`). `particle/vertex.vsh` rewritten round `bhfxPoint(slot)` (motion, turbulence, orbit, size curve) with a ribbon path; `pColor` smooth and new `pAge` (header/footer). New materials `particle/dot|streak|sprite|ribbon.fsh`. Test effect `FlowTestFx` = `bhfx:test_flow`. `ParticleBatchTest` 13 cases (5 new).
- result: `gradlew build` passed (all JUnit, `checkShaders` 16 OK); jar copied to `BHSpells/libs/`. Not seen in game. Not committed.

## 2026-11-04 — Emitters, streams and orbit motion (0.3.9)
- The user picked "emitter + orbit" from the "what else can the system do" list.
- bhfx: new `api/client/FxEmitter` (shapes, `EmitPoint`, `EmitAction`); `FxParticles.orbit(centre, axis)`, `emit`, `stream` + `Stream`; `ParticleSpawn.orbit(rad/s, spinUp)`, `pull`; `Fx.bhfxRandom()` (internal). `ParticleBatch`: 28 floats a slot, emit/stream impl, orbit centre relative to the origin, cull sphere + 2|c| when orbiting. `particle/vertex.vsh`: 7 texels, turning frame (`bhfxTurn`), pull, velocity with the turn; uniforms `POrbit`, `POrbitAxis` set in `PostRenderer.drawBatch`. Test effect `impl/client/test/VortexTestFx` registered as `bhfx:test_vortex`. `ParticleBatchTest`: 4 new cases (orbit storage + cull, even ring emit, cone angle, stream births/positions/gap/reset). Docs: CHANGELOG 0.3.9, README test table, API_PLAN 5.4, decisions.
- Version stays 0.3.9 (not released or committed yet); the jar in `BHSpells/libs/` was rebuilt.
- result: `gradlew build publishToMavenLocal` passed (JUnit `ParticleBatchTest` 8/8, `checkShaders` 12 OK); BHSpells `compileJava` passed against the new jar. Not seen in game. Not committed.

## 2026-11-04 — BHSpells conqueror_haki round 5 (no bhfx change)
- The user: the screen tone was too red; wanted grey-black, faint, with the world's colours showing.
- BHSpells `ConquerorHakiFx`: grade keeps 70% colour, crush 0.7, tint grey with a trace of red at 12%, strength 0.35 / 0.6 / settling 0.3; vignette `#060606`. Bolt and ring colours unchanged.
- result: BHSpells `compileJava` passed (no shader change). Not seen in game. Not committed.

## 2026-11-04 — BHSpells conqueror_haki round 4 (no bhfx change)
- The user saw round 3: wanted the screen sucking/ripple only at the opening, shake instead, and a slightly red, muted, darker screen tone.
- BHSpells: the distortion shell, ripple, zoom blur, aberration and FOV kick only on the first burst for 0.35 s; stronger, longer shake after it; new overlay pass `conqueror_haki_grade.fsh` (mute 55%, pull to dark red keeping brightness, crush blacks) replacing `desaturate`. Notes in `BHSpells/docs/bhfx/conqueror_haki.md`.
- Found while editing: the round 3 PowerShell `Get-Content`/`Set-Content` rewrite had decoded the Java file as cp874 (Thai ANSI), turning `—`, `–` and `°` into mojibake. Repaired with `encode('cp874').decode('utf-8')`; no other file affected. Don't round-trip source files through Windows PowerShell 5.1 text cmdlets.
- result: `checkShaders` 17 OK (copies removed); BHSpells `compileJava processResources` passed. Not seen in game. Not committed.

## 2026-11-04 — BHSpells conqueror_haki round 3 (no bhfx change)
- The user saw round 2: the ground looked like a black shadow, wanted deep red bolts, the red screen ripple only the first time, and expanding rings instead of noise on the ground.
- BHSpells: `conqueror_haki.fsh` rewritten as three clean red rings running out (no darkening, no noise, drawn only for 1.5 s after a burst); default glow `#8E0012`, birth boost on bolts cut; impact sequence is one red frame + one ink frame; impact, ripple, aberration and zoom blur only on the first burst. Notes in `BHSpells/docs/bhfx/conqueror_haki.md`.
- result: `checkShaders` 16 OK (copies removed); BHSpells `compileJava processResources` passed. A PowerShell `Set-Content -Encoding utf8` added a BOM and broke the Java compile once (fixed; use the edit tools or Python for writes). Not seen in game. Not committed.

## 2026-11-04 — Particles: `Facing.AXIS`, per-particle `axis(dir, length)`, batch `pivot`; conqueror_haki round 2
- The user saw `conqueror_haki` round 1 in game: sprites were separate stills, not an animation; asked for bolts that stretch long and large, in every direction, with clearly tilted depth (sketch: figure in the middle, jagged lines radiating out).
- bhfx: `FxParticles.Facing.AXIS` (long side along a per-particle axis, `rotation` tilts the quad round it away from facing the camera), `ParticleSpawn.axis(dir, length)`, `FxParticles.pivot(0..1)` (spawn point at the quad's base). Slot grew 20 → 24 floats (6 texels, 96 bytes); `vertex.vsh` builds every facing as centre + half extents. `ParticleBatchTest.axisLengthCountsForTheCullSphere`. A GLSL pitfall hit: `half` is a reserved word.
- BHSpells: new `particle/haki_strike.fsh` (AXIS, frame animation with hard cuts, grows out from its base); `haki_bolt.fsh` now animates; `ConquerorHakiFx` releases 22 chained strikes (2–3 bent segments), keeps striking while it settles, the 70 wave-front bolts are gone. Notes in `BHSpells/docs/bhfx/conqueror_haki.md`.
- result: `checkShaders` 16 OK (BHSpells copies removed), bhfx `build publishToMavenLocal` + JUnit (ParticleBatchTest 4) passed, BHSpells `compileJava processResources` passed. Round 2 not seen in game. Not committed.

## 2026-11-04 — BHSpells `bhspells:conqueror_haki` (first effect on bhfx particles); `BHFX_PREMULTIPLIED`
- The user gave a 14-bolt black/red lightning strip and asked for a Conqueror's-Haki feel. Design notes: `BHSpells/docs/bhfx/conqueror_haki.md`.
- bhfx: `particle/_footer.glsl` — a material may `#define BHFX_PREMULTIPLIED` and return premultiplied rgb (+ added light) with a = coverage, so one ALPHA particle can darken and glow. Part of 0.3.9 (unreleased), CHANGELOG and `API_PLAN.md` 5.4 updated.
- BHSpells: `compat/bhfx/client/ConquerorHakiFx`, `BhfxIds.CONQUEROR_HAKI`, registered in `BhfxClientBridge`; shaders `conqueror_haki.fsh`, `particle/haki_bolt.fsh`, `particle/haki_wave.fsh`; texture `textures/fx/haki_bolts.png` (4x4 grid of the user's bolts, R core / G glow).
- result: the 3 BHSpells shaders passed glslang through bhfx `checkShaders` (copied in, removed after; 15 OK); bhfx `build publishToMavenLocal` passed; BHSpells `compileJava processResources` passed. Not seen in game. Not committed.

## 2026-11-04 — "runClient fails" (user): not reproduced
- The user reported runClient failing, with no error text. `latest.log` was from 11:19, before the 0.3.9 build, so it had failed before the game logged. Likely cause (unconfirmed): it ran during the bhfx rebuild, while BHSpells `build.gradle` already asked for 0.3.9 but `libs/` had no 0.3.9 jar yet.
- Claude's own `runClient` run at 12:01 worked: menu reached, warmup clean, `bhfx:test_particles` played 7 times without errors (see [`testing.md`](testing.md)). No code changed.

## 2026-11-04 — 0.3.9: textures, GPU particles, distortion, held resolution steps
- The user asked to start the whole plan from the assessment below and to get a test effect.
- bhfx, textures: `api/client/FxTexture` (`of`, `packed`, `srgb`, `clamp`, `nearest`), `Uniforms.texture`, `BHFxClient.preload`/`textureReady`. `impl/render/FxTextures` decodes on `Util.backgroundExecutor()`, uploads on a later frame (gray → `R8` + swizzle, mipmaps, 8x aniso), sweeps after 60 s unused. `UniformBinder` binds them on units 6..15.
- bhfx, particles: `api/client/FxParticles` (+ `ParticleSpawn`), `Fx.createParticles`, `PassList.particles`, `PassOptions.PARTICLES` (50). `impl/render/ParticleBatch` (ring of 20-float slots, analytic motion, cull sphere, pending-run upload), `impl/render/ParticleRenderer` (one buffer texture per batch, `glDrawArraysInstanced` strips, idle sweep 5 s). `PostRenderer` draws runs of particle entries inside the pass chain; DISTORT batches write an RG16F target and `particle_resolve.fsh` bends the input. `GlStateGuard.touchBlend` saves/restores the blend func and equation. Shaders: `particle/vertex.vsh`, `particle/_header.glsl`, `particle/_footer.glsl`, `include/particle.glsl`.
- bhfx, resolution: `ResolutionController` rises at most one 1/8 step per 2 s (drops at once). The "allocate at the cap, use a smaller viewport" idea was dropped: see [`decisions.md`](decisions.md).
- bhfx, warmup: `ShaderWarmup` warms `particle/` files with `PostRenderer.warmupParticle` (they would otherwise have been compiled as passes and logged as broken).
- bhfx, test: `impl/client/test/ParticlesTestFx` = `bhfx:test_particles`, six `test_*.fsh` materials, textures `textures/fx/{noise_fbm,noise_cells,ramp_fire,burst_flipbook}.png` (generated procedurally by a scratch Python script, not kept).
- `gradle/glsl-check.gradle` also checks `particle/*.fsh` (header + material + footer) and `particle/*.vsh`. BHSpells has no copy of this file (see [`shaders.md`](shaders.md)).
- Version 0.3.9 (protocol 2): `gradle.properties`, CHANGELOG, README (test effect row), `API_PLAN.md` 4.2, 4.4, 4.8, 5.1, 5.3, new 5.4. BHSpells `build.gradle` → `net.deizyn:bhfx:0.3.9`; `libs/` now holds `bhfx-0.3.9.jar` (copy task). BHSpells `mods.toml` range `[0.3.7,0.4)` already covers it.
- Two recorded pitfalls hit during the work and were fixed before the build: reversed `smoothstep` edges in the new shaders, and particle colours (first written as linear like `Uniforms.color`, now display-encoded; see [`decisions.md`](decisions.md)). The footer runs the material before `discard` (implicit derivatives).
- result: bhfx `gradlew build publishToMavenLocal` passed: 64 JUnit tests (new `ParticleBatchTest` 3, `ResolutionControllerTest` 2), `checkShaders` 12 OK. BHSpells `compileJava` passed against 0.3.9. **Not tested in game.** Nothing committed.
- next: in-game checklist in [`testing.md`](testing.md); then a first BHSpells spell on particles.

## 2026-11-04 — Assessment: shader particles and textures in bhfx
- No code changed. Re-read the ArcaneVortex 0.6.8 particles (`GlowParticle` etc.: vanilla `Particle` + own `ParticleRenderType`, additive, `setShader`) and Shooting Star `07_PERFORMANCE.md` (`FxTextures`: off-thread decode, R8 + swizzle, channel packing, mipmaps), against bhfx 0.3.8.
- Already handled in bhfx (Shooting Star weak points 3 and 4): `GlStateGuard` restores only touched units; `PostRenderer.ensureTargets` rebuilds full-size and fx-size targets separately. Still left: fx targets are reallocated whenever `ResolutionController` steps (1/8), which can hitch mid-cast; allocating at the cap and drawing with a smaller viewport would avoid it.
- Proposed (the user picks, see [`roadmap.md`](roadmap.md)): (1) texture uniform with off-thread decode, R8/channel packing, mipmaps, 1D colour ramps; (2) a client-only bhfx particle renderer: instanced billboards with analytic motion (position from spawn data + age in the vertex shader, no per-frame CPU), a fragment "material" written like a pass, drawn at `AFTER_LEVEL` into the HDR chain before bloom with a test against the depth snapshot (works with shader packs the same way passes do; soft-particle fade from depth); (3) particles that write a distortion/emissive buffer read by passes; (4) later: half-res offscreen particles, flipbooks, mesh particles, GPU simulation by texture ping-pong (no compute shaders: GL 3.2 core / macOS 4.1).
- Cost notes: VRAM for small textures is tiny next to the targets (one 1080p RGBA16F ping target is 16.6 MB; 256² RGBA8 with mips ≈ 0.34 MB, R8 a quarter of that). Particle cost is overdraw, not count: keep quads tight, prefer additive/premultiplied (no sorting).
- A client-only renderer registers nothing, so bhfx stays removable; it changes the 2026-11-02 line "not bhfx's job: world particles" only for particles bhfx draws itself, not vanilla `ParticleType`s.

## 2026-11-03 — `solar_eclipse` round 10: no collapse ribbons, black sun swells
- The user asked to remove the ribbon-like lines at the second burst and keep the rest of the rays. They also asked for the eclipse to grow big as the ripple enters phase 2.
- `solar_eclipse.fsh`:
  - The pre-collapse corona no longer twists. Twisted, its streamers read as ribbons. Assumption: the user meant this, not the bending streamers of the black sun's hair-line corona.
  - The black disc swells `SWELL` = 2.4× over 0.9 s after the collapse; everything scaled by its radius grows with it.
  - `REACH` 13 → 24.
- `SolarEclipseFx`: `EFFECT_REACH` 14 → 25.
- result: `checkShaders` 7 OK (copies removed), BHSpells `compileJava` passed. Not seen in game. Not committed.

## 2026-11-03 — `solar_eclipse`: drip removed, look accepted
- The user saw round 9 in game. They asked to remove the molten drip and said everything else is OK. The drip block is removed from `solar_eclipse.fsh`, and the header and javadoc are updated.
- result: `checkShaders` OK (copies removed), BHSpells `compileJava` passed. Not committed.

## 2026-11-03 — `solar_eclipse` round 9: burning black sun, hair-line red corona
- The user sent two references:
  - a painting of a charred black sun in a ring of fire, with flames, embers and a molten drip falling to the ground;
  - an engraving-style red corona with fine streamers.
- They asked for the transformation to look like these instead of the marbled swirl.
- `solar_eclipse.fsh` black sun:
  - The disc is charred `fbm2` rock with lava veins (narrow `fbm2` ridges, AA'd from `aa`).
  - An uneven ring of fire that almost goes out in places.
  - Flame tongues flowing outward (`fbm2` with a radial-minus-time term).
  - The corona has streamers 0.5–4.5 horizon radii, a few bending with log distance. They are filled with 90 hair-lines per turn, which blend to their average below ~5 px spacing.
  - Embers.
  - A molten drip down the sky from the bottom edge.
  - Finish: the fire flares and blows outward.
- `SolarEclipseFx`: black-sun bloom is 0.5 again.
- result: `checkShaders` 7 OK (copies removed), BHSpells `compileJava` passed. Not seen in game. Not committed.

## 2026-11-03 — `solar_eclipse` round 8: marbled swirl, hybrid bolts
- The user sent a round-7 screenshot: the disc was clear, but a red ring and rays sat round it, and the ground bolts looked dotted like smoke. They also sent a reference painting of marbled ink swirling into a black hole. They asked for these changes:
  - No finish lightning round the eclipse.
  - Mix the old bolt style back in.
  - Remove the ring.
  - Make it feel like the painting, in the same colours.
- `solar_eclipse_storm.fsh`: the dots came from marching a thin core in steps. The core is now a crisp 16-segment polyline (`rayLine`) through the noise channel, with 2 forks; the sheath is still marched (24 steps) and exposed.
- `solar_eclipse.fsh`:
  - Removed: ring/halo, sparks, red corona and rays, finish tears.
  - New `marble()`: domain-warped `fbm2` in a log-twisted frame, slow. Crimson to red to pale gold; thick and bright on one side.
  - Gold contour filaments, AA'd from `aa` with no derivatives.
  - Star glints.
  - Finish: the swirl spins up and unwinds under a soft shock ring.
- `SolarEclipseFx`: finish sound is now beacon deactivate plus a soft explosion.
- result: `checkShaders` 7 OK (copies removed), BHSpells `compileJava` passed. Not seen in game. Not committed.

## 2026-11-03 — `solar_eclipse` round 7: dark calm black sun, noise-channel lightning
- The user sent an in-game screenshot of round 6. The centre was blown out white, the whole sky looked red, and the lightning was stiff. They asked for a clear black eclipse, no stiff lightning, and calm, floating rays rather than fast pulsing. They suggested another way to compute lightning: textures, or how grandsigil does it.
- grandsigil (studied for technique only):
  - `client/Bolt`: CPU midpoint displacement with branches.
  - `fx_bolt_column.fsh`: the channel's offset is noise of height in three layers, and the view ray is marched through the column. Light is summed and exposed with `1 - exp(-x)`. One hit plus two re-lights.
- `solar_eclipse_storm.fsh`: bolts rebuilt that way, with 40 steps and a fork; no texture needed.
- `solar_eclipse.fsh`:
  - Removed: cracks (`rifts`), pulsing rings, fast sparks.
  - The black sun is a black disc with a thin rim, a red corona and fine rays drifting slowly, and a faint breathing halo.
  - Finish tears are 2D noise channels (`tear`).
- Sky darker: `pow 2.0`. Strike flash 0.2 (it tinted the whole view at 7.5 strikes/s). Black-sun bloom 0.35.
- result: `checkShaders` 7 OK (copies removed), BHSpells `compileJava` passed. Not seen in game. Not committed.

## 2026-11-03 — `solar_eclipse` round 6: black sun like the reference, black sky, finish tears
- The user sent a reference picture of an eclipse: a black disc with a white rim, fine rays, a sharp-edged disc of light and cracks. They asked for these changes:
  - Remove the black-hole look, including the disk lines across the middle.
  - Make the background black so the red light stands out.
  - Add red cracks and waves radiating out.
  - At the finish, add a ripple of lightning tears.
- `solar_eclipse.fsh` rewritten for the black sun:
  - Removed: disk, arch, lens, photon ring.
  - A black disc with a white-hot rim and fine rays.
  - A red halo disc (3× the black disc) that swells out at the collapse, with a sharp edge and sparks.
  - 12 jagged red cracks with branches (`rifts`, 2D `sdSegment`).
  - Pulsing red rings.
  - Finish: 16 lightning tears re-cut 12×/s racing out across the sky, a shock ring and a flash (`EclD.x`).
- `solar_eclipse_sky.fsh`:
  - The sky is black-first: `DEEP` near black, `pow 1.6`, 12% red pull.
  - No swirl round the star; no red grade on the sky (the ground keeps it).
  - The `Star` uniform is dropped.
- `SolarEclipseFx`:
  - `finishSeconds`, and `finishCue` (thunder, explosion, shake) on a FINISH/CANCEL stop or at the start of the last 40 ticks.
  - A red flash, aberration and zoom blur, plus an FOV push.
  - The eclipse pass fades only in the second half of the ending.
- result: `checkShaders` 7 OK (copies removed), BHSpells `compileJava` passed. Not seen in game. Not committed.

## 2026-11-03 — `solar_eclipse` round 5: storm from the sky, red-black sky
- The user asked for these changes:
  - Remove the lightning round the eclipse.
  - Many more bolts, striking down from the sky rather than from the eclipse.
  - Move the eclipse higher.
  - Keep the sky red, and make it blacker.
- `solar_eclipse.fsh`: `starBolts` and its helpers removed.
- `solar_eclipse_storm.fsh`:
  - `MAX_STRIKES` 16 → 32; bolts start 80 blocks up with a random lean of ±0.35 × that.
  - Visible 0.5 s, with flickering return strokes; 3 forks.
  - `SkyDir` uniform dropped.
- `SolarEclipseFx`:
  - Strike rate 3 + 9 × `density` per second (7.5 at default).
  - Default `elevation` 60.
  - Thunder on 35% of strikes.
  - Strike glow goes to the sky pass (`SkyC.w`).
- `solar_eclipse_sky.fsh`: from the space phase on the sky stays red and black:
  - `DEEP` haze replaces `INDIGO`; `NEB_DUST` 0.42; red-weighted nebula; blacks pushed down; 35% pull to `BLOOD`.
  - Red-orange rim; red flash on strikes.
- result: `checkShaders` 7 OK (copies removed), BHSpells `compileJava` passed. Not seen in game. Not committed.

## 2026-11-03 — `solar_eclipse` round 4: red lightning round the star, red grade
- The user asked to "adjust the screen colour too". They also said the things flying out of the eclipse should not be streaks; they want a lot of red lightning instead.
- `solar_eclipse.fsh`:
  - `strokes`, `splatter` and `paint` are replaced by `starBolts`, drawn twice:
    - 26 long bolts (4–10 horizon radii);
    - 18 short crackles (0.8–2.2).
  - Each bolt re-strikes in a new direction every 0.22–0.52 s and has 2 forks.
  - Bolts are 2D `sdSegment` on the star plane, with a corridor reject.
- `solar_eclipse_sky.fsh`: new `SkyC.z` red grade (`BLOOD`). Under the black star the umbra goes from cold to red, and the whole view from inside is graded red (ground 55%, sky 25%).
- `SolarEclipseFx`: the vignette turns blood red (`#4A0504`) under the black star, with no desaturation.
- result: `checkShaders` 7 OK (copies removed), BHSpells `compileJava` passed. Not seen in game. Not committed.

## 2026-11-03 — `solar_eclipse` round 3: world-fixed layout
- The user reported that the eclipse turns when the camera turns, and is sometimes skewed.
- Cause: round 2 laid the eclipse out in screen pixels round its projected centre, so its axes followed the screen, and perspective stretched it near the screen edges.
- Fix: `solar_eclipse.fsh` works on the plane square to the star's direction, with world-fixed axes (`E = cross(D, up)`, `N = cross(E, D)`). Each pixel uses its own view ray: `q = (ray·E, ray·N) / (ray·D · tanS)`. The lens maps the bent `q` back to a direction and then to the screen with `projectUv`.
- result: `checkShaders` 7 OK (copies removed). No Java change. Not seen in game. Not committed.

## 2026-11-03 — bhfx 0.3.8 fog uniforms; `solar_eclipse` round 2
- Why: in BHSpells `runClient` the user saw round 1 and reported three things. The space sky was good, but a pale band with stepped edges ran along the horizon. The eclipse looked like it was floating. They asked for a black star like a painted black hole, in reds, using every technique, and to check what bhfx needs.
- bhfx (0.3.8):
  - `ShaderManager.HEADER` and `glsl-check.gradle` gain `FogColor` and `FogRange` (start, end, shape).
  - `PostRenderer.captureFog()` reads `RenderSystem` fog after the solid blocks, through `ClientEvents` → `ClientFxManager.captureFog()`, and binds it in `begin()`.
  - New include `fog.glsl`: `fogDistance`, `fogAmount`, `refog`.
  - Updated CHANGELOG, README, `API_PLAN.md` 5.2/5.3 and `shaders.md`.
- BHSpells: `build.gradle` and `mods.toml` move to bhfx 0.3.8 (`[0.3.8,0.4)`); the bhfx build copied `libs/bhfx-0.3.8.jar` and removed 0.3.7.
- `SolarEclipseFx`:
  - The sun now hangs at infinity: camera + `skyDir` × 600. `skyDir` points towards the spawn target, or comes from the seed.
  - New params `size` and `elevation` (`BhfxIds.SIZE` / `ELEVATION`) replace `height`; the gold default is now `#FFD27A`.
  - Bolts come from `SkyDir` × 70.
  - The cloud height is passed to the shaders.
- Shaders:
  - Sky: far-terrain fog and the cloud layer are replaced with the new sky (`skyAt`).
  - `solar_eclipse.fsh` is rewritten for the black star: accretion disk (near and far halves), a lensed arch over and under the hole, three layers of paint strokes plus splatter that burst out at the collapse, a shock ring, and a red haze.
  - Storm: bolts come from the sky direction.
- result: bhfx `gradlew build` passed. With the 3 eclipse shaders copied in, bhfx `checkShaders` passed (7 OK; copies removed). BHSpells `compileJava` passed. Not seen in game. Not committed.
- next: in-game check of the fog swap at different render distances and in rain; the black-star look; GPU cost.

## 2026-11-02 — BHSpells: standalone `bhspells:solar_eclipse` effect
- BHSpells: new `compat/bhfx/client/SolarEclipseFx` + `solar_eclipse.fsh` (sun, moon, corona, diamond ring, black star lens; order 10, `within(star, 7R)`), `solar_eclipse_sky.fsh` (twilight sky, horizon rim, umbra darkening, shadow bands; full screen inside the umbra), `solar_eclipse_storm.fsh` (seeded red lightning + scorch; `Strikes[16]` vec4). `BhfxIds.SOLAR_ECLIPSE`, registered in `BhfxClientBridge.onRegister`. Not tied to any spell; `SolarEclipseSpell` untouched. Design notes in `BHSpells/docs/bhfx/solar_eclipse.md`.
- Same day, on request ("change the sky itself, like ArcaneVortex"): from totality on, `solar_eclipse_sky.fsh` replaces sky pixels with open space instead of twilight: a volumetric kaliset nebula along the view ray (`space()`, 14 samples × 14 folds, red near / gold far / indigo haze), fixed to world directions, swirling round the star once it forms. New uniforms `Star`, `SkyB.w` (space), `SkyC.x` (swirl), `Tint2`. Only the idea comes from ArcaneVortex (`partial_darkness`); the code is new. Brightness constants are guesses.
- Lens samples `DiffuseSampler` in a pass drawn last, so the darkened world and the bolts bend too (`SceneSampler` would bring back the un-darkened world).
- result: BHSpells `compileJava` + `build` passed; the 3 shaders passed glslang through bhfx `checkShaders` (copied in, then removed). Not seen in game. Not committed.
- next: in-game checklist in [`testing.md`](testing.md).

## 2026-11-02 — Fix: floor effects dropped out in horizontal lines
- bhfx: `include/scene.glsl` `sceneAt` unprojects at the centre of the depth texel it reads (cause and the first, unhelpful attempt in [`bugs.md`](bugs.md)). `PostRenderer`: outside a bounded pass's rect, copy `sceneLow` 1:1. `BHFxConfig`: new client option `quality.passBounds` (default true) to turn bounds off for A/B checks.
- result: bhfx `gradlew build` passed (`checkShaders` 4 OK). The user confirmed in BHSpells `runClient` that the lines on `lunebris_aura` are gone. BHSpells shaders were not glslang-checked (this branch has no `checkShaders`); they loaded in game. Committed 2026-11-02: bhfx `1d09489`, BHSpells `f71d9f6` (with the bounds work and the rebuilt jar).

## 2026-11-02 — BHSpells uses pass bounds; bhfx declared again in BHSpells `mods.toml`
- BHSpells (not committed): new `compat/bhfx/client/FxBounds`; `FlameFieldFx`, `ShadowDomainFx`, `LunebrisAuraFx`, `GroundBeatFx`, `GroundPulseFx`, `ImpactRingFx`, `ScorchedGroundFx`, `GoldenAuraFx`, `PurpleFlameFx`, `SmokeAuraFx` pass `FxBounds.cylinder(...)` to `out.add`. `mods.toml` declares `bhfx` again (`mandatory=false`, `[0.3.7,0.4)`): branch `feature/deizy-integrate-1.8.0` had no bhfx entry (the old one is on `bhfx-squash-1.6.5`). BHSpells docs (gitignored, local): `docs/bhfx/reusable_effects.md` table of bounds, lines in `lunebris`, `heavenly_combo`, `tempest_combo`, `vengeful_pressure`, `tempest_reiatsu`.
- Notes: [`integrations/bhspells.md`](integrations/bhspells.md), [`release.md`](release.md) (the required-mod list is also missing on that branch), [`testing.md`](testing.md).
- result: BHSpells `compileJava processResources` passed; expanded `mods.toml` has the bhfx entry. Not tested in game.

## 2026-11-02 — Version 0.3.7; test results from the user; test setup moves to BHSpells runClient
- bhfx: `mod_version` 0.3.7 (protocol stays 2) for `PassOptions.within`; CHANGELOG, README and `API_PLAN.md` 4.4 say 0.3.7. BHSpells `build.gradle` already points at `net.deizyn:bhfx:0.3.7`.
- testing.md: the user reports the 2026-11-02 list tested: Mirror block, Mirror Veil, `purple_flame`, Heavenly Combo after the bridge change, window-resize fix, settings screen at GUI scale 4, and the multiplayer items (Annihilation Arrow checklist, tracker, `to @s`). No logs or per-item details were given. Only the `followSeen` in-game check is left (steps added).
- The Mingyue Eclipse modpack is no longer used; in-game tests run in the BHSpells dev client (noted at the top of [`testing.md`](testing.md)).
- result: see the build lines in the commit; pass bounds still not tested in game.

## 2026-11-02 — Per-pass screen bounds (`PassOptions.within`)
- bhfx: `api/client/PassOptions` gains an optional `Bounds` (`within(center, radius)`, `@ApiStatus.Experimental`; `new PassOptions(int)`, `DEFAULT`, `OVERLAY` kept, so 0.3.6 callers still link). `impl/client/PassListImpl` stores the bounds camera-relative (`clear(Vec3 camera)`). New `impl/render/ScreenRect` projects the bounding cube's 8 corners into a uv rect. `PostRenderer.render` skips a pass whose sphere is off screen and scissors one whose rect covers < 85% of the screen, after a blit makes the rest of the target equal to the input. `/bhfx status` shows the skipped count.
- Per-pass resolution was not added: see [`decisions.md`](decisions.md).
- No BHSpells pass uses bounds yet; behaviour without bounds is unchanged.
- result: `gradlew build` passed (tests incl. 8 new in `ScreenRectTest`, `checkShaders`). Not tested in game. Released as 0.3.7 (see the next entry).
- next: in-game check ([`testing.md`](testing.md)); then add bounds to BHSpells passes that stay inside a radius.

## 2026-11-02 — Review: what could be improved in bhfx
- No code changed. Read `PostRenderer.render`, `PassListImpl` and `UniformBinder`, plus bugs/testing/shaders notes.
- ~~Found: `PassOptions.Resolution` is ignored~~ Wrong: the code never had `resolution`. `README.md` "Where the code differs from the plan" already says `PassOptions` is `order` only; only `API_PLAN.md` 4.4 still showed it (fixed the same day, see the next entry).
- Proposed (not scheduled; the user picks): per-pass screen bounds with scissor (done the same day); a texture uniform; a previous-frame history target; an sRGB colour helper for the `Uniforms.color` pitfall; a dev-only warning for uniforms the shader doesn't declare; fix the stale GPU time in `/bhfx status`; clear the ⏳ backlog in [`testing.md`](testing.md).

## 2026-11-02 — Assessment: can bhfx do ArcaneVortex's effects?
- No code changed. I compared the unpacked ArcaneVortex 0.6.8 jar (All Rights Reserved) with the bhfx 0.3.6 API. Only ideas carry over; any shader would be written from scratch.
- **Works with today's API** (a custom `PassList` pass, `scene.glsl` + `shapes.glsl`): a black hole with lensing (ray-march in the pass, then sample `SceneSampler` at the bent `projectUv`; scene occlusion by comparing with `sceneDepth`), a volumetric darkness/starfield dome, an expanding inverted/scanline/mosaic screen wipe, and shockwave distortion (`ScreenParams.ripple` + `ground.glsl` `shockRing`). Negative/inverted flash already exists as `ImpactStyle`.
- **Works, but limited**: a Mandelbulb "sun" (fits only at low resolution within `gpuBudgetMs` 4.0); a distortion trail behind a sword arc (only as analytic segments passed through `Uniforms.floats`, with no ribbon geometry).
- **Gaps in bhfx**: no texture uniform (removed in 0.3.2), so texture-driven parts such as an accretion disk or colour ramps must be procedural. Passes run at `AFTER_LEVEL`, so translucents and particles have no depth and get lensed/covered (ArcaneVortex has the same issue). Post is off when a shader pack is on in `AUTO`.
- **Not bhfx's job** (it registers no RenderTypes/items/particles): the cosmic item/block shader, trail ribbon geometry, world particles, damage numbers, GUI particles. These belong in BHSpells (custom `RenderType` + core shader); see the `bhspells-vfx` skill.
- next: backlog item for a texture uniform (see [`roadmap.md`](roadmap.md)).

## 2026-10-24 — Version 0.3.6
- bhfx: `mod_version` 0.3.5 → 0.3.6 (protocol stays 2) for `BHFxClient.drawingMirrorReflection()`; CHANGELOG, README and `API_PLAN.md` 4.8 say 0.3.6.
- BHSpells (working tree only, not committed): `build.gradle` → `net.deizyn:bhfx:0.3.6`; `libs/` holds `bhfx-0.3.6.jar` (copy task removed 0.3.5). The `[0.3.2,0.4)` range already covers it. BHSpells now needs 0.3.6 at runtime for Mirror Veil's mirror reflection (older bhfx: linkage error is caught and the veil hides everywhere).
- Modpack `mods/` not updated: still 0.3.5.
- result: bhfx `gradlew build` passed (tests + `checkShaders`, copy task wrote `bhfx-0.3.6.jar`); BHSpells `gradlew build` passed against it; not tested in game. Nothing committed.

## 2026-10-24 — `BHFxClient.drawingMirrorReflection()` for BHSpells Mirror Veil
- bhfx: `MirrorRenderer` (flag `drawing` around the player draws in `drawPlayers`), `ClientFxManager`, `api/client/BHFxClient` — new public query, listed in `API_PLAN.md` 4.8 and the 0.3.6 changelog.
- BHSpells: effect `bhspells:mirror_veil` — not drawn in the world (body, armour, held items, name tag, shadow, hitbox, Epic Fight trails), drawn in mirror reflections. Helper `client/InvisibilityRender`, bridge `BhfxClientCompat.drawingMirrorReflection()`, new `EntityRenderDispatcherMixin` (shadow/hitbox), existing invisibility mixins now go through the helper.
- result: bhfx `build` and BHSpells `build` passed; not tested in game.
- next: in-game check in [`testing.md`](testing.md).

## 2026-10-24 — Version 0.3.5
- bhfx: `mod_version` 0.3.4 → 0.3.5 (protocol stays 2); CHANGELOG "Unreleased" → 0.3.5 (mirror block, `offWithShaderpack` default, mirror settings); README version lines.
- BHSpells (working tree only, not committed): `build.gradle` → `net.deizyn:bhfx:0.3.5`; `libs/` now holds `bhfx-0.3.5.jar` (copy task). Its accepted range `[0.3.2,0.4)` already covers it.
- Mingyue Eclipse Production Client 0.1.3 `mods/`: `bhfx-0.3.4.jar` → `bhfx-0.3.5.jar` (old jar backed up in the session scratchpad).
- result: bhfx `gradlew build` passed (tests + `checkShaders`); BHSpells `compileJava` passed; not launched in the modpack with 0.3.5. Nothing committed.

## 2026-10-24 — Mirror settings page; defaults distance 16, maxPlanes 6
- bhfx: `MirrorSettingsScreen` (new), "Mirror..." button in `BHFxSettingsScreen`, `BHFxConfig` defaults, `ClientConfigMigration` step 3 (`CURRENT = 3`), `ClientConfigMigrationTest`, `README.md`, `CHANGELOG.md`.
- why: in a mirror room (floor, ceiling, both walls, far wall) the far wall showed nothing; 5 surfaces in view, `maxPlanes` was 4.
- result: `build` (with tests) passed; jar copied to the Mingyue Eclipse Production Client 0.1.3 instance; not tested in game.
- next: the cap still counts surfaces with nobody reflected in them; counting only surfaces that draw a player would waste fewer slots.

## 2026-10-24 — Mirror: hidden surfaces no longer wipe the reflection in front
- bhfx: `shaders/core/mirror_area.{fsh,json}` (new), `MirrorRenderer.setArea`/`drawReflections`/`drawFaceDepth` (new), `ClientBootstrap.registerShaders` — surface areas in the reflection target are opened/closed only where no nearer mirror face hides them (see `bugs.md` 2026-10-24). First version used the main depth; the user's in-game screenshot with a shader pack still showed the cut, so it now uses its own depth pass of the faces.
- result: `build` passed; `mirror_area.fsh` passed glslang by hand (core shaders are not in `checkShaders`); jar copied to the Mingyue Eclipse Production Client 0.1.3 instance; not yet re-tested in game.
- next: in-game check with and without a shader pack (mirror floor + mirror wall, stacked walls, stepping back).

## 2026-10-24 — BHSpells: `bhspells:purple_flame` effect
- BHSpells: new `PurpleFlameFx` + `shaders/bhfx/purple_flame.fsh` (one flame from `flame_field`'s `flameShape` on a single camera-facing sheet; cyan tips → violet body → magenta-pink root by heat; halo, floor pool, 18 embers; bloom only), `BhfxIds.PURPLE_FLAME` / `COLOR3`, registered in `BhfxClientBridge`; `docs/bhfx/reusable_effects.md`.
- result: `compileJava` passed. Shader checked with glslang 16.6 through bhfx's `checkShaders` (file copied in temporarily, removed after). Not seen in game.
- Same day: ember count is a param, `density` 0..1 → 0..64 embers (`MAX_EMBERS` in the shader, passed as `FlameB.z`), default 0.3 ≈ 19. `compileJava` + glslang passed again.
- Found: BHSpells no longer has `gradle/glsl-check.gradle` / `checkShaders` (gone since at least `3770530`), see [`shaders.md`](shaders.md).

## 2026-10-24 — Mirror: six faces, built for 20k-block mazes
- Reported (user): many mirrors (aiming at a ~20,000-block mirror maze) drop the frame rate a lot; asked for all six faces to reflect.
- bhfx: `MirrorBlock` is a plain full block now (no `FACING`; `cube_all` with `mirror_front`; `mirror_side.png` removed). `MirrorTracker` rewritten: set of mirror positions; a face exists unless the neighbour that way is a mirror (add/remove hide and restore touching faces); clusters indexed by chunk section (`forEachNear`); each cluster greedily merges its faces into rectangles (`rects()`), bounds and rects rebuilt lazily. `MirrorRenderer`: `collect` only visits sections around the camera; quads drawn per rectangle (one quad for a whole wall tile instead of one per block); pack composite reuses `Vector4f`s and splits rectangles into 0.5-block cells; per-frame player-draw budget `mirror.maxReflections` (default 6, `BHFxConfig`); fallback visibility ray only within 1 block of the mirror. `MirrorTrackerTest` rewritten (8 tests).
- Measured (JUnit, throwaway bench, removed): 20,001-block maze, 1,563 clusters; adding all = 538 ms cold / 17 ms warm; finding the clusters within 32 blocks of the camera (608) = 0.006–0.03 ms per frame. Not measured in game (GPU side, Figura avatars).
- result: `gradlew build` passed. Not tested in game.
- Old worlds: mirror states placed before this lose their `facing` property (dev-only so far).

## 2026-10-23 — `offWithShaderpack` default false
- bhfx: `BHFxConfig` default false; `ClientConfigMigration` step 2 (`CURRENT` 1 → 2) moves an existing `true`; `ClientConfigMigrationTest` checks the default and `CURRENT`; README, CHANGELOG.
- result: `gradlew build` passed (`ClientConfigMigrationTest` 2/2). Migration not tested in game.

## 2026-10-23 — Mirror works with shader packs
- Reported (user): with a shader pack on, mirrors showed nothing (they were switched off on purpose while a pack is active).
- bhfx: with a pack active, `MirrorRenderer.drawReflectionsBeforeLevel` draws the reflection from `ViewportEvent.ComputeCameraAngles` (new `ClientEvents.onCameraAnglesFinal`, priority LOWEST), where the camera is set but Iris has not begun the level, so vanilla shaders still draw into our target; view rotation rebuilt from the final roll/pitch/yaw like `GameRenderer.renderLevel`, own `Frustum`. Step 2 (`compositeForPack`) draws the mirror faces through `RenderType.entityCutoutNoCull(bhfx:mirror_reflection)` (a `ReflectionTexture` that hands out the target's colour id), UV = vertex screen position under the drawn matrices, faces split 4x4, full-bright light, so the pack shades them like an entity. Without a pack nothing changed.
- result: `gradlew build` passed. Not tested in game (with or without a pack).

## 2026-10-23 — Mirror: reflect before the entity pass, Figura support, looser visibility
- Reported (user, in game): in F5 the mirror in front sometimes showed nothing, the floor mirror under your feet showed nothing, and Figura hair physics in the mirror were slightly off. Causes and fixes in [`bugs.md`](bugs.md).
- bhfx: `MirrorRenderer` split into `drawReflections` (`AFTER_SOLID_BLOCKS`, before entities: per surface, farthest first, open its screen area in our target with depth 1 + colour clear, draw players, close with depth 0; calls `Lighting.setupLevel` itself) and `composite` (`AFTER_BLOCK_ENTITIES`, one draw for all surfaces). Visibility: front-side test on the box centre; 8 sample points; fallback ray from the mirror point nearest the middle sample. New `FiguraMirror` (reflection): for the mirror pass sets `Avatar.culled = false` and `Avatar.renderScriptsSkipped = true`, then restores them.
- Figura source checked: `figura-0.1.6-bhbuild.32+1.20.1-forge-mc.jar` (Mingyue Eclipse 0.1.3). It throttles render scripts with `renderScriptsSkipped` (`AvatarCulling.throttleRenderScripts`; skips RENDER/POST_RENDER and part scripts when `renderMode == RENDER`), shares shadow-pass scripts per `levelFrame`, and culls avatars outside the camera frustum (`AVATAR_VIEW_CULLING` → `culled`, and `getAvatarForRender` returns null).
- result: `gradlew build` passed. Not tested in game.

## 2026-10-23 — Carpet: NeoForged as a dev dependency
- bhfx: `build.gradle` adds the Modrinth maven (only group `maven.modrinth`) and `implementation fg.deobf("maven.modrinth:XqqOkvZz:XMYeNIOx")` (Carpet: NeoForged 1.0.8, Forge 1.20.1), at the user's request. It is on the compile and dev-run classpath only; `mods.toml` does not declare it, so players don't need it.
- result: `gradlew build` passed (the dependency resolved).

## 2026-10-23 — Mirror: offscreen reflection instead of stencil
- Symptom (user screenshot, floor mirror in rain): the reflected player was drawn far outside the mirror and the area showed clouds, caves and mob items through the ground. See [`bugs.md`](bugs.md).
- bhfx: `MirrorRenderer` no longer uses the stencil buffer or `glDepthRange`. Per plane it clears a screen-sized `TextureTarget` (transparent), draws the mirrored players into it, binds the main target and draws the mirror faces with the new core shader `bhfx:mirror_composite` (`assets/bhfx/shaders/core/`, registered in `ClientBootstrap` via `RegisterShadersEvent`), which samples the target at `gl_FragCoord / ScreenSize` and discards empty pixels; main depth test `LEQUAL`, no depth writes. `tick()` frees the target when no mirror is loaded; `enableStencil()` removed.
- result: `gradlew build` passed; both new shaders pass glslang 16.6 by hand (`-S vert` / `-S frag`; `checkShaders` only covers `shaders/bhfx/`). Not tested in game.

## 2026-10-23 — Mirror: full block, players only, wall check, clusters
- bhfx: `MirrorBlock` is now a full solid cube (`minecraft:block/cube` model, front on `up` rotated by `FACING`), facing the placer (`getNearestLookingDirection().getOpposite()`); recipe 8 nuggets + glass → 1. `MirrorTracker` keeps mirrors in clusters (facing, layer, 16x16 tile) with bounds; `MirrorBlockEntity.setBlockState` re-files rotated mirrors. `MirrorRenderer` culls by cluster (distance to bounds, back side, frustum), merges visible clusters of one plane, draws only `level.players()` (no spectators), skips a plane with no visible player, and checks line of sight per player (eyes/middle/feet: camera ray → mirror point must be on this plane, then `ClipContext.Block.VISUAL` clip to the player), cached per game tick. Glass sheen dropped. New client config `mirror.distance/maxPlanes/playerRange` (`BHFxConfig`); README config list. New `MirrorTrackerTest` (5 tests).
- Figura: checked the Figura 0.1.5 forge jar; it hooks `LivingEntityRenderer`/`PlayerRenderer`/`EntityRenderDispatcher.render`, which is what the mirror calls, so avatars should render without extra code. Not seen in game.
- result: `gradlew build` passed (all tests incl. `MirrorTrackerTest`). Not tested in game.
- next: in-game checklist in [`testing.md`](testing.md).

## 2026-10-23 — Mirror block (entity-only reflection)
- bhfx: new `impl/block/` (`BHFxBlocks` registers block, item, block entity and the Functional Blocks tab entry; `MirrorBlock` 2 px plate with `FACING` = clicked face; `MirrorBlockEntity` only feeds `MirrorTracker` on the client); `impl/client/MirrorRenderer` (stencil clip + mirrored entity draw at `AFTER_BLOCK_ENTITIES`; asks for the stencil buffer from the client tick); hooks in `BHFxMod` and `ClientEvents`; assets (blockstate, models, two generated 16x16 textures, `en_us`), loot table, recipe; CHANGELOG "Unreleased".
- result: `gradlew build` passed (tests + `checkShaders`). Not tested in game. The build copied the new jar over `BHSpells/libs/bhfx-0.3.4.jar` (same version number as the release).
- next: in-game checklist in [`testing.md`](testing.md); version bump before shipping (see [`roadmap.md`](roadmap.md)).

## 2026-10-22 — Release 0.3.4
- bhfx: `mod_version` 0.3.3 → 0.3.4 (protocol stays 2); CHANGELOG "Unreleased" → 0.3.4 (config migration to Medium, slider rounding fix); README version lines.
- BHSpells (working tree only, not committed): `build.gradle` → `net.deizyn:bhfx:0.3.4`; `libs/` now holds `bhfx-0.3.4.jar` (copy task).
- Mingyue Eclipse `mods/`: `bhfx-0.3.3.jar` → `bhfx-0.3.4.jar` (old jars backed up in the session scratchpad). Its toml is already Medium, so on the next launch only `settingsVersion = 1` is added.
- result: bhfx `gradlew build` passed (43 tests); BHSpells `compileJava` passed; the same jar hash in all three places. Not launched in the modpack with 0.3.4.

## 2026-10-22 — One-time migration of old-default configs to Medium
- bhfx: new `impl/ClientConfigMigration` (run from `BHFxMod.configLoaded` on `ModConfigEvent.Loading` of `CLIENT_SPEC`); new top-level key `BHFxConfig.SETTINGS_VERSION` (`settingsVersion`, default 0). New `ClientConfigMigrationTest` (2 tests; 43 in all).
- result: `gradlew build` passed. Dev check: `bhfx/run/config/bhfx-client.toml` (HIGH/2.3/6, backup in the session scratchpad) became MEDIUM/1.6/4 with `settingsVersion = 1` on `runClient`, and the log printed the migration line; client closed right after loading. Not committed; not in the modpack (its toml is already Medium, so only `settingsVersion` would be added).
- next: released as 0.3.4 the same day.

## 2026-10-22 — Why the Medium default did not show up; slider rounding
- The user saw HIGH after the default change. Every existing `bhfx-client.toml` keeps its values: `BHSpells/run`, `BHSpells/run-client2` and `bhfx/run` still say HIGH / 2.3 MP / 6 ms; only the Mingyue Eclipse one is Medium (set by hand in the screen at 00:36).
- bhfx: `BHFxSettingsScreen.Slider.current()` rounds away float noise (see `bugs.md`).
- result: `gradlew build` passed (jar `bhfx-0.3.3.jar` rebuilt and copied to `BHSpells/libs`; the modpack still has the 00:40 build). Not committed.

## 2026-10-22 — Release 0.3.3
- bhfx: `mod_version` 0.3.2 → 0.3.3 (protocol stays 2); CHANGELOG "Unreleased" → 0.3.3 (settings screen, Medium defaults, resize fix, `seconds` → `duration`); README version and dependency lines.
- BHSpells (working tree only, not committed): `build.gradle` depends on `net.deizyn:bhfx:0.3.3`; the build's copy task replaced `libs/bhfx-0.3.2.jar` with `libs/bhfx-0.3.3.jar`. `mods.toml` range `[0.3.2,0.4)` already accepts it.
- result: bhfx `gradlew build` passed; BHSpells `compileJava` passed. Mingyue Eclipse `mods/`: `bhfx-0.3.2.jar` removed, `bhfx-0.3.3.jar` copied (2026-10-22 00:40). Not yet launched with 0.3.3.

## 2026-10-22 — Default settings = Medium preset
- bhfx: `BHFxConfig` defaults `quality` HIGH → MEDIUM, `gpuBudgetMs` 6 → 4, `maxFxMegapixels` 2.3 → 1.6; `BHFxSettingsScreen` comment, README, CHANGELOG, `API_PLAN.md` config table, `decisions.md`.
- result: `gradlew build` passed. Existing toml files (e.g. the Mingyue Eclipse one) keep their values, so in-game behaviour only changes on a fresh config. Not tested with a fresh config.

## 2026-10-22 — In-game settings screen
- bhfx: new `impl/client/BHFxSettingsScreen` registered as the Forge config screen in `ClientBootstrap` (Mods → bhfx → Config). Presets Low/Medium/High/Max set quality + `gpuBudgetMs` + `maxFxMegapixels`; also quality, bloom, budget slider (1–16 ms), resolution cap slider (0.3–8.3 MP, shows the effect size for the current window), auto-lower, offWithShaderpack, shake, flashes, camera control, impact frames. Live line at the bottom (fx count, effect size, GPU ms).
- `ClientFxManager.settingsChanged()` drops a session auto-downgrade and the resolution history on every change; `liveLine()` for the screen. `PostRenderer.effectSize()`.
- result: `gradlew build` passed (tests + shader check). Not tested in game. Jar (built from the working tree, so it also has the 2026-10-20 resize fix and `duration` rename) copied to the Mingyue Eclipse `mods/` on 2026-10-22, replacing the 2026-10-19 build of the same name.
- Tested in the modpack the same night: settings save and apply live; LOW vs HIGH with two effects 0.82 vs 5.63 ms GPU; resolution dropped to 88% on HIGH (see `testing.md`).
- next: GUI scale 4 fit and dedicated server start, see `testing.md`.

## 2026-10-22 — 1.0 step 5: second performance run in the modpack
- Read the Mingyue Eclipse logs (user ran `/bhfx bench`, `/bhfx status`, `/bhfx test` with `flame_field`/`shadow_domain`). Results in `testing.md`; stale GPU number in `/bhfx status` added to `bugs.md`.
- result: idle cost, bench cost, VRAM and per-effect GPU pass at 2560x1369 with a shader pack. Not 1080p mid-range; Heavenly Combo while casting not measured.
- Follow-up the same night: `shadow_domain` + `flame_field` together measured 5.32–5.93 ms GPU (budget 6 ms).
- next: see the open items under the 2026-10-22 entry in `testing.md`.

## 2026-10-21 — 1.0 step 4: Embeddium + Oculus OK
- The user reports bhfx works with Embeddium and Oculus. Recorded in `testing.md` and `roadmap.md` (step 4).
- result: user report, no log or versions captured.
- next: Epic Fight 1st/3rd person, dedicated server with 2 players; step 5 at 1080p mid-range plus a Heavenly Combo number while really casting.

## 2026-10-21 — BHSpells: `heavenly_combo` from a command honours `hold`
- The user: `/bhfx_play fx bhspells:heavenly_combo attach @s {hold:true}` would not hold.
- Cause: `HeavenlyComboFx.tick` ends itself 50 ticks (`IDLE_END_TICKS`) after the last cue (blow/slam/keepAlive from `BhfxClientBridge`). The spell spawns it `UNTIL_STOPPED` and relies on that, so `hold` could not override it; a command never sends cues, so it always ended after ~2.5 s.
- BHSpells: `HeavenlyComboFx` — new `cued` flag set by the first cue; the idle end only applies once cued. Every bridge spawn is cued in the same call, so the spell is unchanged.
- result: `compileJava` passed. Not tested in game. From a command only the aura shows (blows and slam come from the spell's cues).

## 2026-10-21 — BHSpells: `shadow_domain` aurora shows through dense mist
- The user: at higher `density` the mist got so thick the aurora curtains were hard to see.
- Cause: aurora light was attenuated by the same transmittance (`trans`) that hides the scene behind the mist, and the march stopped once `trans` < 0.03.
- BHSpells: `shadow_domain.fsh` — the aurora now uses its own `veil` transmittance with the mist's opacity scaled by `VEIL` (0.2); the march stops on `veil` < 0.03. The scene behind is still dimmed by the full mist.
- result: glslang 16.6.0 by hand OK. Not tested in game; WebGL preview not updated.
- next: if the mist itself still reads too thick at `density` near 1, soften `thresh = mix(0.6, 0.3, density)` or the 0.22 extinction.
- Follow-up the same day (the user wants aurora over smoke, many long continuous curtains): new tuning constants in `shadow_domain.fsh` — `CURTAINS` 3.5 → 6 (sheet a bit wider, 0.03..0.17), ray floor 0.12 → 0.4 so curtains read unbroken, `GLOW` 0.5 → 0.8, `VEIL` 0.2 → 0.1, mist `MIST` extinction 0.22 → 0.12, `thresh` mix(0.6, 0.3) → mix(0.65, 0.42). glslang OK; not tested in game (watch for curtain aliasing at height 20 with the closer spacing).

## 2026-10-20 — BHSpells: GPU cost of `flame_field` / `shadow_domain`, distance LOD
- Measured by the user (`/bhfx bench`, dev client, default radius 24): flame_field 2.80 ms, shadow_domain 4.69 ms bhfx GPU (budget 6 ms); together ~7.5 ms, which is why the earlier run showed effect resolution at 75%. Real use is radius 120, heavier still.
- BHSpells: `flame_field.fsh` per-layer draw distances (small 45, mid 80, big 140 blocks, × height/6 above 6) with a fade, pools cut the same way; `shadow_domain.fsh` 24 steps instead of 32, no marching past 110 blocks (fades from 77), mist noise 3 → 2 octaves. Cost per pixel no longer grows with the radius.
- result: `compileJava`, glslang OK; preview at radius 120 looks the same. Not re-measured in game.
- The user's real commands use `height:20, density:0.3, radius:120` for both. At height 20 the aurora/mist pattern stayed at its height-7 size while the steps were ~3 blocks apart, so curtains would alias; `shadow_domain.fsh` now scales the aurora and mist pattern by `height/7`. glslang OK, preview at the real params checked.
- Re-measured in game with the real commands after a client restart: both together avg 4.68 ms, max 5.97 ms (was max 12.21 ms); shadow_domain alone avg 3.46, max 8.96 (one spike); flame_field alone avg 2.57, max 4.93. Resolution stayed at 100%. See [`testing.md`](testing.md).

## 2026-10-20 — BHSpells: `flame_field` light on the ground at each fire's root
- BHSpells `flame_field.fsh`: `poolLayer` back, but only as a small flickering pool on upward-facing floor round each fire's roots (no light over the gaps, so no circle); pink like the flames with pale gold at the heart, dyed nearly fully near the root (half-dyed pink over grass read beige).
- result: `compileJava` passed, glslang OK, checked in the WebGL preview. Not tested in game.

## 2026-10-20 — BHSpells: `ground_pulse` (whole floor thumps at once)
- BHSpells: `GroundPulseFx` + `ground_pulse.fsh`: on every beat every block top in the area kicks up at once (each a little early/late and higher/lower), then settles, under a soft tinted light; not a wave from the centre (`ground_beat` stays as a separate effect). Block edges found from the centre's world x/z mod 256 passed in `PulseB.zw`. `BhfxIds.GROUND_PULSE`. Preview `BHSpells/docs/bhfx/tools/preview_ground_pulse.html`.
- Then, at the user's request (no square blocks; "aura streaks like pressure, gravity rising from the ground"), `ground_pulse` was rewritten: thin vertical streaks of pink light shoot up from all over the area at once on each beat (others drifting up faintly between), soft floor glow, nothing bent. Params now `color`, `radius`, `height`, `beat`, `density` (`amplitude` and the world-offset uniform dropped). `compileJava`, glslang OK, preview checked.
- Then rewritten again (the user wanted no streaks, just colour stuck to the floor as "power covering the area"): every upward-facing surface within ±6 blocks of the effect's height is dyed towards the tint keeping its brightness, with slowly flowing brighter patches and thin glowing veins, all swelling together on the beat; nothing bent or rising. Params `color`, `radius`, `beat`, `density`. `compileJava`, glslang OK, preview checked.
- Then the white veins were removed and the beat softened to a gentle cosine swell that never fades the colour away. glslang OK, preview checked.
- result: `compileJava` passed, glslang OK, checked in the WebGL preview. Not tested in game.

## 2026-10-20 — BHSpells: `ground_beat` and `smoke_aura`
- BHSpells: `GroundBeatFx` + `ground_beat.fsh` (the image of upward-facing ground near the effect is bent as if the surface rose and fell, a crest rolling out on every beat; soft pink light on crests, a glow at the centre on the beat); `SmokeAuraFx` + `smoke_aura.fsh` (green smoke sleeve swirling up round a body, attachable). `BhfxIds.GROUND_BEAT`, `SMOKE_AURA`, `AMPLITUDE`, `BEAT` (and `COLOR2`'s misplaced javadoc fixed). Preview `BHSpells/docs/bhfx/tools/preview_beat_aura.html`.
- result: `compileJava` passed, glslang OK, checked in the WebGL preview. Not tested in game.

## 2026-10-20 — Effects vanish after a window resize
- bhfx: `impl/render/PostRenderer.java` — `render()` remembers the main target (`frameBufferId`, colour/depth ids, size); when any of them changes it calls `release()` and `resolution.reset()` so every target and the `outFbo` attachment are rebuilt. See bugs.md.
- result: `compileJava` passed; not tested in game.
- next: in game, resize, maximize/restore, minimize/restore and toggle fullscreen while an effect is running.


## 2026-10-20 — BHSpells: `flame_field` pink-gold
- BHSpells: `flame_field.fsh` takes a second colour `Tint2` (pink): cooler tongues pink, body gold, hottest white-gold; how much is pink varies per fire. `FlameFieldFx` reads `color2` (`BhfxIds.COLOR2`, default `#FF5FA2`); `color` stays the gold. Preview `preview_domains.html?set=fire`.
- Then, at the user's request, pink became the main colour and gold the minority (only the hot lower body and core): boundary `mix(0.6, 0.82, pink)`, white-gold above heat 0.95. glslang OK, preview checked.
- Then the gold was lightened to a pale, almost white gold where it meets the pink (`mix(color, (1, 0.97, 0.9), 0.55)`). glslang OK, preview checked.
- Then the colours were made to melt together (wide pink→gold and gold→white transitions) and the pink lightened (`mix(color2, (1, 0.94, 0.97), 0.3)`). glslang OK, preview checked.
- Then the pink was switched to Lightning Strike's (`LightningStrikeSpell` shockwave: centre `#FF66CC`, edge `#E61A99`): default `color2` = `#FF66CC`, no white mixed in, coolest tongues deepen to `color2 * (0.9, 0.25, 0.75)`. `compileJava`, glslang OK, preview checked.
- result: `compileJava` passed, glslang OK, checked in the WebGL preview. Not tested in game.

## 2026-10-20 — Command option `seconds` renamed `duration`
- bhfx `impl/FxCommandArgs`: `{duration:N}` (seconds) replaces `{seconds:N}`; `{seconds:..}` now fails with "seconds was renamed to duration" instead of silently becoming an effect param. `FxCommandArgsTest` updated (also checks `duration` never reaches the effect). README, CHANGELOG (Unreleased), decisions, BHSpells `reusable_effects.md` and the Fx javadocs updated. Version stays 0.3.2.
- result: bhfx `gradlew build` passed (41 tests, shader check); jar copied to `BHSpells/libs` (same file name `bhfx-0.3.2.jar`, overwritten). The Mingyue Eclipse modpack jar was not replaced. Not tested in game.

## 2026-10-20 — BHSpells: `bhspells:shadow_domain` (black-green qi domain in the air)
- BHSpells: `ShadowDomainFx` + `shadow_domain.fsh` (raymarched smoke slab hanging at `height`, streaks pouring outward, green fringes and qi veins, pressure waves every 1.4 s with screen ripple, green motes; dark flash, ink frame, desaturate and vignette on screen). Meant as the counterpart of `flame_field` for two masters fighting. `BhfxIds.SHADOW_DOMAIN`; `FLAME_HEIGHT` / `FLAME_DENSITY` renamed `HEIGHT` / `DENSITY` (same keys). Preview `BHSpells/docs/bhfx/tools/preview_domains.html` shows both alone and together.
- result: `compileJava` passed, glslang OK (both shaders), checked in the WebGL preview. Not tested in game; GPU cost unknown (28 steps x ~8 noise per pixel inside the domain).
- Follow-up the same day: the smoke was replaced by green aurora curtains (contour lines of a drifting noise field, sharp bright lower edge, vertical rays, flare on each pressure wave) over a soft black-green mist that glows near the curtains; same colours and params (`density` now drives the mist). glslang OK, preview checked alone and with `flame_field`.
- Then the screen ripple on each pressure wave was removed at the user's request (shake/aberration as the front passes kept). `compileJava` OK.
- next: in-game check of both together ([`testing.md`](testing.md)).

## 2026-10-20 — BHSpells: `bhspells:flame_field` (golden domain of fire)
- First built as `bhspells:inferno` (one raymarched wall of fire); the user wanted a domain with fires big and small scattered over a wide area, so it was replaced: `FlameFieldFx` + `flame_field.fsh` (three grid layers of flame sheets found by a 2D DDA, glowing pools, gold rim, embers, haze), `BhfxIds.FLAME_FIELD` / `FLAME_HEIGHT`. `InfernoFx`, `inferno.fsh` and `BhfxIds.INFERNO` deleted. Preview `BHSpells/docs/bhfx/tools/preview_flame_field.html` + `assemble_single.py`. Notes in `BHSpells/docs/bhfx/reusable_effects.md`.
- Found: `Uniforms.color` is linear but the frame is display-encoded (see [`shaders.md`](shaders.md)); `impact_ring.fsh` and `scorched_ground.fsh` still use the linear tint.
- result: `compileJava` passed, glslang OK, checked in the WebGL preview (6 views). Not tested in game; GPU cost unknown.
- Follow-up the same day: too dense → `density` param (`BhfxIds.FLAME_DENSITY`, default 0.5, scales each layer's share of cells); fire width now follows `height` (`layerFor` in `flame_field.fsh`; cells grow above height 6, never shrink). `compileJava`, glslang and the preview checked.
- Then the gold rim at the domain's edge was removed at the user's request (`compileJava`, glslang OK).
- The user still saw a circle and asked for only the fires: ground shimmer, pools, light outside and heat haze removed; the area's edge is now ragged (noise on the radius); DDA cell limits raised to 32/56/96 so a large `radius` (40+) keeps its far side. glslang OK, preview checked at radius 24 and 40.
- next: in-game check and `/bhfx stats` while it burns ([`testing.md`](testing.md)).

## 2026-10-20 — BHSpells: impact, impact ring, impact ripple and scorched ground split out of `bhfx:test`
- BHSpells (branch `feature/integrate-deizy`): `ImpactFx` + `impact.fsh`, `ScorchedGroundFx` + `scorched_ground.fsh`, ids in `BhfxIds`, registered in `BhfxClientBridge.onRegister`. Both take `color` and `radius`. Notes: `BHSpells/docs/bhfx/reusable_effects.md`. No spell uses them yet; bhfx unchanged.
- result: BHSpells `compileJava` passed. This branch has no `checkShaders` task, so both shaders were checked by hand with glslang 16.6.0 (bhfx header + includes inlined): OK. Not tested in game.
- Follow-up the same day: all particles removed from `ImpactFx` (lava first, then the coloured dust). The ground ring and screen ripple were split into their own ids, `bhspells:impact_ring` (`ImpactRingFx`, shader renamed to `impact_ring.fsh`) and `bhspells:impact_ripple` (`ImpactRippleFx`); `bhspells:impact` keeps the bang only. A short-lived `ground`/`ripple` param version was replaced by this. `compileJava` and glslang passed.
- next: in-game check in [`testing.md`](testing.md).

## 2026-10-17 — 1.0 step 5: first performance numbers
- The user ran `/bhfx off|on`, `/bhfx stats`, `/bhfx status` and `/bhfx bench` in the BHSpells dev client (bhfx 0.3.2). Results in [`testing.md`](testing.md): idle = no work, `bhfx:test` 0.33-0.37 ms GPU, VRAM ~11 MB and released after 30 s.
- The user reports no stutter on the first cast.
- BHSpells: the bhfx 0.3.2 work was squashed into one commit (`f0be802`) on Yumeko-1234/BHSpells `feature/deizy-integrate-bhfx`, so friends can build it and review it as one change.
- next: `/bhfx stats` while actually casting Heavenly Combo; repeat at 1080p.

## 2026-10-17 — Work that needed no game: tests, changelog, bridge
- bhfx: `impl/client/SelectorSpec` (new, parsing pulled out of `ClientSelector`); `ScreenParamsImplTest`, `SelectorSpecTest`; 41 tests in all. `CHANGELOG.md` (0.1.0 to 0.3.2).
- BHSpells: `BhfxLoadedBridge.annihilationChargeEnd` makes one `BHFx.stop` instead of looping over every level; `mods.toml` bhfx range `[0.3.2,0.4)`. No spell code changed.
- Dedicated server: the world opens (log from a 12:45 dev server, bhfx 0.3.0). That server (PID 19392) was still running and holds `run/world`; left running for the user to decide.
- result: bhfx `gradlew build` (41 tests, shader check) and BHSpells `gradlew build` passed. bhfx jar refreshed in `BHSpells/libs` and the Mingyue Eclipse `mods/`; the modpack's BHSpells jar was not replaced. Not tested in game.

## 2026-10-17 — API lock (0.3.2)
- bhfx API removed: `ScreenParams` speedLines/contrast/grain/freeze/afterimage/stepFps/synthetic (with `PostRenderer` hold/step targets and the `composite.fsh` / `bloom_down.fsh` code), `CameraParams` hideHand/roll/hideHud/override/FULL, `CameraPose`, `CameraMover`, `Uniforms.points/texture` (with `UniformBinder` texture loading), `FxParam.EMPOWERED`, `BHFxClient.register/reloadShaders`.
- bhfx `ServerFx`: `FxKey` is server-wide. `Audience` keeps player UUIDs. `@ApiStatus.Internal` on `Fx.bhfx*`, `ScreenPulse.write/read`, `Audience.kind/playerIds`. Javadoc filled in (`FxSettings`, `FrameContext`, `FxContext`, `Uniforms`, `BHFxClient`).
- BHSpells: `build.gradle` → bhfx 0.3.2; no spell code changed (`desaturate` kept for Heavenly Combo).
- result: bhfx `gradlew build` (31 tests, required shader check) and BHSpells `compileJava` + `checkShaders -PrequireGlslCheck` passed. Jar 0.3.2 in `BHSpells/libs` and the Mingyue Eclipse `mods/` (0.3.1 removed). Tested in game by the user: works as before.
- next: 1.0 steps 4-5 (multiplayer, other mods, performance).

## 2026-10-17 — 1.0 steps 2 and 3: tests and required shader check
- bhfx `gradle.properties`: `requireGlslCheck=true`; `gradle/glsl-check.gradle` (both repos, identical) honours `-PrequireGlslCheck=false`. `checkShaders`: bhfx 4 OK, BHSpells 2 OK (with the flag).
- bhfx `impl/server/Tracking` (new): expiry, follow-lost and enter/leave rules pulled out of `ServerFx` (behaviour unchanged).
- bhfx tests: `WireTest`, `TimelineTest`, `impl/server/TrackingTest` added; 31 tests in all.
- result: `gradlew build` passed (tests + required shader check); jar copied to `BHSpells/libs` and the Mingyue Eclipse `mods/`. Not re-tested in game (only `ServerFx` internals moved).
- next: `ScreenParamsImpl` merge tests; 1.0 step 1 (API lock).

## 2026-10-17 — Effect commands (0.3.1)
- bhfx `impl/FxCommands` (new): one grammar for `/bhfx test` and `/bhfx_play`: `<fx> [at <pos> | attach <who>] [to <players>] [{...}]`, `stop [<who>] [finish]`; `Started` tracks what a command started (same effect on the same entity replaces).
- bhfx `impl/FxCommandArgs`: rewritten for SNBT `{...}`; `hold`/`seconds`/`range` are command options and are not passed to the effect.
- bhfx `impl/client/ClientSelector` (new): `@s @p @a @r @e`, names, UUIDs with `type name distance limit sort` for client commands (vanilla selectors need the server).
- bhfx `impl/client/ClientFxCommands` (new, from `ClientEvents`); `ServerFxEvents`: `/bhfx_test` removed, `/bhfx_play fx <fx> ...` with `to`, plus `stop` and `pulse` (op 2, release jars too). Ids are suggested on an integrated server (it reads `ClientFxManager.registeredIds()`, guarded by `FMLEnvironment.dist`).
- bhfx `FxParams.get/has`: an int fills a float key, a whole float an int key.
- bhfx `TestFx`, `TrackerTestFx`: draw at `position()` so `attach` makes them follow; the tracker no longer reads a `range` param.
- bhfx: JUnit set up (`src/test`, `FxCommandArgsTest`, `FxParamsTest`); version 0.3.1.
- BHSpells: only `build.gradle` (bhfx 0.3.1) and `libs/` (jar swapped by `copyToBHSpells`); no spell code touched (see [`decisions.md`](decisions.md), "Spell effects and command effects are separate").
- result: bhfx `gradlew build` passed with 10/10 tests; BHSpells `compileJava` passed. Jar 0.3.1 in the Mingyue Eclipse `mods/` (0.3.0 removed). Not tested in game.
- next: in-game checklist in [`testing.md`](testing.md).

## 2026-10-17 — Modpack test passed
- After restarting with the fixed jar and setting `offWithShaderpack = false`, the user confirmed effects work in Mingyue Eclipse, with and without Complementary. Log numbers are in [`testing.md`](testing.md); the earlier "not visible" report is closed in [`bugs.md`](bugs.md).
- Next: check that world-space parts line up with a shader pack on. If they don't, add a mode that keeps screen-only effects and skips depth-based passes while a pack is on (see [`roadmap.md`](roadmap.md)).

## 2026-10-17 — Modpack test: effects not visible
- Read the Mingyue Eclipse Production Client 0.1.3 log: bhfx loads, warms up 6 shaders, BHSpells detects it. `/bhfx bench` ran while an Oculus pack was on, so post was off. After the pack was turned off, the auto-downgrade fired. Cause of the invisible effects still unknown (see [`bugs.md`](bugs.md)).
- bhfx `ClientFxManager.checkBudget`: fixed the "-1.0 ms" downgrade message. Rebuilt; the jar was copied to `BHSpells/libs` and into the modpack's `mods/`.
- Next: the user tests in the modpack with the shader pack off, running `/bhfx status` while charging Annihilation Arrow.

## 2026-10-17 — Docs translated to English
- bhfx: `README.md` and every file in `docs/` translated from Thai to English. Recording rule 3 in [`README.md`](README.md) and the `bhfx-notes` skill now say the notes are written in English.

## 2026-10-17 — Gitignore Claude files, keep docs in git
- bhfx `.gitignore`: `.claude/`, `CLAUDE.md`, `.mcp.json`, `.agents/`, `AGENTS.md`. **`docs/` and `README.md` stay in git** (the user wants them kept in Dezameria/BHFX).
- BHSpells `.gitignore`: added `*.md` and `run-data` (BHSpells keeps no markdown in git at all).
- Removed the `Co-Authored-By: Claude` line from every commit:
  - bhfx `main`: force pushed to Dezameria/BHFX.
  - BHSpells: squashed into one commit, `b18767c`, on Yumeko-1234 `feature/deizy-integrate-bhfx`. `feature/deizy-integrate-1.6.2` was not touched.
- Local backup branches: bhfx `backup/main-before-strip`; BHSpells `backup/feature-fx-before-squash` and `backup/feature-fx-before-resquash`.

## 2026-10-17 — bhfx jar in `BHSpells/libs`
- bhfx `build.gradle`: task `copyToBHSpells` (runs after `reobfJar`, finalizes `build`) copies the jar into `../BHSpells/libs`, replacing the old one.
- BHSpells: committed `libs/bhfx-0.3.0.jar` so that a friend who clones it can build.
- Result: after a bhfx build the jar lands in `libs/` correctly. With bhfx moved out of mavenLocal temporarily, BHSpells `compileJava --refresh-dependencies` passed (exit 0), so a friend without mavenLocal can build from `libs/`. mavenLocal was restored afterwards.

## 2026-10-17 — Permanent glslang
- `D:\Minecraft\Dev\ironspell_more\tools\glslang\bin\glslang.exe` (16.6.0, 4.9 MB) plus `VERSION.txt`.
- bhfx and BHSpells `gradle/glsl-check.gradle`: finds `<repo>/../tools/glslang/bin` automatically. The file is still identical in both repos.
- Result: `checkShaders -PrequireGlslCheck` with no path given passes in both repos (bhfx 4, BHSpells 2).

## 2026-10-17 — Preparing for real use
- BHSpells `mods.toml`: the 15 mods BHSpells calls directly are declared `mandatory=true` (list in [`release.md`](release.md)).
- bhfx: [`release.md`](release.md) (build, install, checklist).
- Result:
  - Both repos build with `-PrequireGlslCheck`.
  - Jars checked: bhfx has 10 shader files, BHSpells has 2 shaders and 17 dependency entries.
  - BHSpells `runServer` loaded every mod (dependency check passed, bhfx detected, registries frozen), but could not open the world because another dev server in `run/` held the world lock. Joining a world on a dedicated server is not confirmed for this round.
- Next: start the dedicated server again when nothing else is running; multiplayer test with a friend.

## 2026-10-17 — Markdown knowledge base + `bhfx-notes` skill
- bhfx: created `docs/README.md` (index), `worklog.md`, `roadmap.md`, `testing.md`, `bugs.md`, `decisions.md`, `shaders.md` and `integrations/bhspells.md`. Moved "test status" and "used in BHSpells" out of `README.md` into these categories.
- bhfx: `.claude/skills/bhfx-notes/SKILL.md` and `CLAUDE.md` point to `docs/README.md`.

## 2026-10-17 — Shader check with glslang
- bhfx and BHSpells: `gradle/glsl-check.gradle` (same file in both) and task `checkShaders`, wired into `check`/`build`. Details in [`shaders.md`](shaders.md).
- Result: glslang 16.6.0 passes all 6 shaders (bhfx 4: `test`, `composite`, `bloom_down`, `bloom_up`; BHSpells 2: `heavenly_combo`, `annihilation_charge`). A deliberately broken copy was caught with the right line number.
- Next: ~~install glslang permanently~~ done (see "Permanent glslang").

## 2026-10-17 — Housekeeping 1–3
- BHSpells `mods.toml`: accepted bhfx versions `[0.3,)` → `[0.3,0.4)`.
- bhfx `ServerFxEvents`: `/bhfx_test` is registered in dev only (`!FMLEnvironment.production`).
- bhfx `BHFxClient`: added `update(LocalFxHandle, FxParams)`, `fx(LocalFxHandle, Class)` and `isAlive(LocalFxHandle)`. BHSpells `BhfxClientBridge` dropped the static `created` field and uses `BHFxClient.fx(...)`.
- Result: both repos build, `publishToMavenLocal` done. Heavenly Combo after the bridge change is not tested in game.

## 2026-10-17 — Fix ServerFx `followSeen` leak + doc updates
- bhfx `ServerFx.start`: sets `followSeen` immediately when the followed entity exists (see [`bugs.md`](bugs.md)).
- bhfx `README.md`, `build.gradle`, `docs/API_PLAN.md`: fixed stale versions and protocol number.
- Result: build passed, `publishToMavenLocal` done.

## 2026-10-17 — Annihilation Arrow: first real user of `BHFx.start/update/stop`
- BHSpells: `compat/bhfx/BhfxIds`, `BhfxCompat`/`BhfxLoadedBridge` (3 methods), `client/AnnihilationChargeFx`, shader `annihilation_charge.fsh`, hooks in `AnnihilationArrowSpell`, doc `docs/spells/fire/annihilation_arrow.md`.
- Reviewed with `bhspells-reviewer`: fixed 3 IMPORTANT and 3 OPTIONAL findings (see [`integrations/bhspells.md`](integrations/bhspells.md)).
- Result: build passed; the user tested it in game and it passed, except multiplayer.
