# Roadmap

Open work and plans. When something is done, tick it as `[x]` with the date, and record the details in [`worklog.md`](worklog.md).

## Now (0.3.x)

- [ ] Body parts (added 2026-11-13, 0.3.12): test in game, vanilla and Epic Fight ([`testing.md`](testing.md)); tune the Epic Fight joint offsets in BHSpells `EpicFightBhfxBodyParts`. Later: baby models (head scale), GeckoLib models, slim arms.
- [ ] Test pass bounds (`PassOptions.within`, 0.3.7) in game ([`testing.md`](testing.md)).
- [ ] Optional: `followSeen` in-game check (JUnit already covers it; steps in [`testing.md`](testing.md)).

- [x] 2026-11-02 Test Annihilation Arrow in multiplayer with a friend (checklist in [`testing.md`](testing.md)).
- [x] 2026-11-02 Test Heavenly Combo after the bridge switched to `BHFxClient.fx(...)` (2026-10-17).
- [x] 2026-10-17 Permanent glslang at `ironspell_more/tools/glslang`; the build finds it on its own (see [`shaders.md`](shaders.md)).
- [x] 2026-10-17 Commit pending work (the user committed it).
- [x] 2026-10-17 Declare the mods BHSpells requires in `mods.toml` (see [`release.md`](release.md)).
- [x] 2026-10-17 Confirm a dedicated server can open a world with the real mod set (see [`testing.md`](testing.md)).
- [x] 2026-10-17 Fix the `followSeen` leak in `ServerFx`.
- [x] 2026-10-17 Lock the bhfx version range in BHSpells `mods.toml` to `[0.3,0.4)`.
- [x] 2026-10-17 `/bhfx_test` in dev only (replaced by `/bhfx_play` the same day).
- [x] 2026-10-17 `/bhfx test` / `/bhfx stop` / `/bhfx_play` effect commands with `at`/`attach`/`to`, SNBT params and fade-out stop, 0.3.1 (see [`decisions.md`](decisions.md)).
- [x] 2026-10-17 Test the effect commands in game, single player (the second-player items go with the multiplayer test).
- [ ] Optional: preview effects for spells (e.g. `bhspells:preview/heavenly_combo` playing a scripted combo), as separate classes that leave the spell effects alone.
- [ ] Optional: packet that sends the client's registered effect ids to the server, so `/bhfx_play` can suggest and check ids (needs protocol 3).
- [x] 2026-10-17 API `BHFxClient.fx/update/isAlive` for local handles.
- [x] 2026-10-17 `checkShaders` with glslang.
- [x] 2026-10-17 Docs translated to English.
- [x] 2026-11-02 Mirror block (added 2026-10-23): tested in game (user report) ([`testing.md`](testing.md)). Version bumped to 0.3.5 on 2026-10-24.
- [ ] Mirror panel (added 2026-11-12): test in game ([`testing.md`](testing.md)); ships in 0.3.11 (bumped 2026-11-12). Optional later: a frame drawn only on the outer edge of joined panels.
- [x] 2026-10-22 In-game settings screen with Low/Medium/High/Max presets; tested in the modpack the same day (see [`testing.md`](testing.md)).

## Performance and new effect forms (plan proposed 2026-11-16 after a code review; not started)

Rules for every step: bhfx only; a performance step must not change the look (same `/bhfx shoot` sheets on old and new
code, through dev control) and is measured before / after; anything that can change a look is opt-in and off by
default; one version bump per finished stage. Review notes: [`worklog.md`](worklog.md) 2026-11-16.

- [x] 2026-11-17 **A. Measure properly first (0.3.17).** Done: `/bhfx stats` already had render / tick CPU and peak alive vs capacity (A1, A3); added fps, longest frame, slots and thinning, a time window, dev-control `stats` and `fps`; baseline in [`testing.md`](testing.md). Finding: render CPU is as large as GPU time with many effects, so B3 / B4 are not optional. Planned text: The 2026-11-16 stress run could not show frame cost (fps at the 119 cap,
  single-frame GPU readings, no CPU number).
  - [ ] A1. CPU ms of bhfx per frame (tick part and render part) in `/bhfx status` and the dev-control `status` line.
  - [ ] A2. Dev-control line to lift the fps cap (`fps unlimited|<n>`), and GPU / CPU ms averaged over the time between
    two `status` lines instead of one frame.
  - [ ] A3. `/bhfx stats`: peak alive vs capacity per batch (old optimization item 4).
  - [ ] A4. Baseline with A1–A3: `/bhfx stress 70` and the four particle test effects, numbers and sheets into
    [`testing.md`](testing.md).
- [x] 2026-11-17 **B. Free wins, no look change (0.3.18).** B1–B3 done and checked in game (same look); **no measurable gain on the dev machine** ([`testing.md`](testing.md)): copies are cheap there and the CPU cost per batch is not the uniform calls. B4 not done (the string lookups stay). Follow-up: split render CPU into collect / stride / draw in `/bhfx stats` before any more CPU work; for comparable runs, a stress with fixed spots (seeded) and a fixed effect resolution. Planned text, found in `PostRenderer`:
  - [ ] B1. Draw particle and model runs in place. Now every run first copies the whole input target
    (`blit(from, pingFbo[ping], ...)`), and runs split whenever passes and particles of different effects interleave.
    When the input is already a ping target and no material in the run reads `DiffuseSampler` (uniform not active),
    draw onto the input and skip the copy and the ping swap. Models keep their per-target depth buffer.
  - [ ] B2. Bounded passes copy only what differs. A scissored pass now copies the full target before shading its rect.
    The other ping target differs from the input only inside the previous pass's rect, so copy that rect (padded); a
    full copy only on the first use in a frame or after a full-screen pass / particle run.
  - [ ] B3. Frame-constant uniforms once per program per frame. `begin()` sets three matrices, `Eye`, fog, `ScreenSize`,
    `GameTime` and three sampler units on every draw; many batches share `DOT` / `STREAK` / `SPRITE`. Keep a frame
    stamp per `ShaderProgram`; set sampler units once after link. `OutSize` stays per draw.
  - [ ] B4. Built-in particle / model uniforms through locations resolved once per program, not about 20 `String` map
    lookups per batch (`ShaderProgram.location`). Only if A1 shows the CPU part matters.
- [x] 2026-11-17 **B+. Where render CPU goes (0.3.19).** `/bhfx stats` phase split, seeded `/bhfx stress`; result in
  [`testing.md`](testing.md): the cost is the driver's work per draw call (2–3 µs per batch), not bhfx's own code.
  Not planned unless a real scene needs it: merging batches of one material across effects into one draw.
- [ ] **C. New forms that are shared by design (0.3.19–0.3.2x).** A custom pass costs one screen pass per effect and
  only 8 fit a frame (`maxPassesPerFrame`); these collect every effect's items into one built-in pass or reuse the
  particle path, so they stay cheap with many players. All new API, `@ApiStatus.Experimental`, each with a test effect.
  - [x] 2026-11-17 C1 done as `PassList.light` (0.3.20), checked in game ([`testing.md`](testing.md), [`API_PLAN.md`](API_PLAN.md) 5.8). Later if wanted: a config switch, lights from particles (one call per batch), spot / line lights. Planned text: `FxLights`: point lights (position, colour, radius, falloff) from any effect, gathered into one data table
    and drawn by one pass that lights the world from depth. Spells light the ground and walls round them.
  - [x] 2026-11-17 C2 done as `PassList.decal` / `FxDecal` (0.3.21), checked in game ([`testing.md`](testing.md), [`API_PLAN.md`](API_PLAN.md) 5.9). Later if wanted: a soft edge option, a ramp over the mask, more than 8 textures (an atlas). Planned text: `FxDecals`: textured marks on the ground and walls (magic circles, scorch, cracks; rotation, fade, flipbook,
    ramp), all in one pass, built on `groundDecal`. No shader to write for the common case.
  - [x] 2026-11-17 C3 done as `FxBolt` and `FxParticles.BEAM` (0.3.22), checked in game ([`testing.md`](testing.md), [`API_PLAN.md`](API_PLAN.md) 5.10). Later if wanted: a beam that follows two moving points without redrawing, travel along the bolt (`delay` per point). Planned text: Bolts and beams: a Java builder for lightning / beam polylines (jitter, branches, travel) on the existing
    ribbons, plus the built-in textured-ribbon material from the backlog. No new renderer.
  - [x] 2026-11-17 C4 done as `PassList.modelParticles` (0.3.24), checked in game ([`testing.md`](testing.md), [`API_PLAN.md`](API_PLAN.md) 5.12). Later if wanted: turbulence, a choice of the leading axis for `VELOCITY`, light per piece. Planned text: Mesh particles: a particle batch whose pieces are an `FxModel` (shards, debris, blades) moved by the GPU
    particle motion. Uses the particle data buffer with the model draw.
  - [x] 2026-11-17 C5: afterimages as `FxAfterimage` (0.3.25) and the `DISTORT` model material (0.3.26), both checked in game ([`testing.md`](testing.md), [`API_PLAN.md`](API_PLAN.md) 5.13, 5.14). Planned text: Model afterimages (ghost copies with their own pose and fade: needs per-copy poses) and a `DISTORT` model
    material (old M4).
- [x] 2026-11-17 **D dropped for now**: measured, no hitch to fix (70 starts in one tick: 2.2 ms, longest frame 15 ms; [`testing.md`](testing.md)). Only worth doing for memory. Planned text: **D. Memory and hitches (scale phase 2).** Pool `ParticleBatch` storage and GL buffers by power-of-two size, a
  ceiling on slots allocated. Measured with `/bhfx stress` staggered starts (frame over 50 ms count).
- [ ] **E. Opt-in quality knobs, default off.** Done 2026-11-17 (0.3.23): the half-resolution stage, as `PassOptions.halfRes()` layers ([`API_PLAN.md`](API_PLAN.md) 5.11), with a mist volume as its test (`bhfx:test_volume`, part of F). Done 2026-11-17 (0.3.27): draw-time distance LOD per batch, `FxParticles.lod` ([`API_PLAN.md`](API_PLAN.md) 5.15), in place of the count LOD for `emit` / `stream` (old item 2; spawn counts are not scaled). Left: near-camera fade and max
  on-screen size (old item 3); `PassOptions` half-resolution stage with a depth-aware upsample for heavy passes
  (`sdf_march`, volumes); dev-only overdraw view.
- [ ] **F. Needs E's half-res stage.** Done 2026-11-17 (0.3.28): light shafts as `FxShafts` ([`API_PLAN.md`](API_PLAN.md) 5.16; no shadowing by terrain). Planned text: Light shafts from a world point; fog / smoke volumes; later per-stream seeded
  random (`FxRandom`).
- Checked and left alone: bloom (5 levels from half of effect resolution), the full-resolution snapshot copy (it is
  `SceneSampler` and the composite's sharp source), particle upload (only new slots, `glBufferSubData`), the GPU timer
  (no wait). Still not planned: evaluate-once particle pre-pass, half-float packing, compute shaders.

Suggested order: A → B → C1 → C2 → C3 → D → E → C4 / C5 → F.

## Next feature: `FxData` (chosen 2026-11-16, code done the same day)

- [ ] `FxData`: tables of `vec4` that an `Fx` fills in Java and a pass reads as a `samplerBuffer`. Code done 2026-11-16
  in the unreleased 0.3.12 (build + JUnit + glslang); left: the in-game checklist in [`testing.md`](testing.md), then
  one BHSpells effect using it. Plan and what changed from it: [`plan-fx-data.md`](plan-fx-data.md).
- [ ] 3D shapes from signed distances (`sdf3d.glsl`, `sdf_march.glsl`, `FxFrame`, `FxData.setFrame`, `bhfx:test_sdf`):
  code done 2026-11-16 in 0.3.12 (build + JUnit + glslang); left: the in-game checklist in [`testing.md`](testing.md),
  then a BHSpells spell using it (a weapon or limb of light on the caster's arm). Later, only when an effect needs a
  sculpted shape: meshes baked into 3D distance textures ([`decisions.md`](decisions.md) 2026-11-16).
- [ ] Models drawn by bhfx (`FxModel`, `PassList.model`, `bhfx:test_model`): M1 (static, three materials) code done
  2026-11-16 in 0.3.12 (build + JUnit + glslang); left: the in-game checklist in [`testing.md`](testing.md). Next steps
  ([`decisions.md`](decisions.md) 2026-11-16): M2 bones from Java (skinning), M3 `.animation.json` playback, M4
  distortion. Instancing (`PassList.models`) done 2026-11-16 for BHSpells `thousand_swords`. M2 (`FxPose`, bones
  posed on the GPU) and M3 (`FxAnimation`: `.animation.json` → `FxPose`) code done 2026-11-16 (build + JUnit +
  glslang); left: in-game check. Next, when wanted: model LOD, per-copy poses for instances, M4 distortion.
- [ ] Travelling waves (`soundWave`, `shakeWave`, `waveDelay`) and `FxGround` (surface + paced batch), from the
  orbital-laser review (items 2 and 3): code done 2026-11-16 (build + JUnit); left: [`testing.md`](testing.md). Next from
  that review, when wanted (shadows dropped 2026-11-16, see [`decisions.md`](decisions.md); `/bhfx shoot` done 2026-11-16, 0.3.14; model LOD not needed: `thousand_swords` 1.1 ms GPU): per-stream seeded
  random (`FxRandom`) for server/visual sync, half-res stages, model smear (motion blur).
- [ ] Named points / GeckoLib bones (`BHFxClient.point`, `reportPoint`, `bhfx:test_points`; BHSpells `GeckoBhfxPoints`):
  code done 2026-11-16 (bhfx build + BHSpells `compileJava`); left: the in-game checklist in [`testing.md`](testing.md),
  then a spell whose effect rides a bone of its GeckoLib model. Later if wanted: GeckoLib armour / item / replaced
  renderers.
- [ ] After that, maybe: block textures in a pass (block atlas + sprite UV rect); first check whether `FxTexture.of`
  on a vanilla block texture already works. Notes in [`plan-fx-data.md`](plan-fx-data.md) under "Not in this plan".

## 1.21.1 NeoForge port (prepared 2026-11-16, not started)

- [ ] Plan ready: [`plan-neoforge-1.21.1.md`](plan-neoforge-1.21.1.md). Waiting on the user: does BHSpells move too, and
  is 1.20.1 kept updated after the port? Optional prep on 1.20.1: route the 14 `new ResourceLocation(...)` sites
  through one helper.

## 1.0 plan

| Step | Work | Done when |
| :--- | :--- | :--- |
| 1. Lock the API | ~~Go through every class in `api.*` and mark it stable, `@ApiStatus.Experimental`, or removed. Decide on `stopEverywhere(server, key)`, `ctx.effek(...)` and Timeline loaded from JSON. Complete the javadoc.~~ done 2026-10-17 in 0.3.2 (see [`API_PLAN.md`](API_PLAN.md) section 0.1). Checked in game the same day. | There is a list of APIs promised not to change throughout 1.x |
| 2. Automated tests (JUnit) | ~~`FxParams` (encode/decode, merge, size), `Timeline`/`Ease`, `ScreenPulse`, the `ServerFx` tracker rules (`impl/server/Tracking`) incl. an entity vanishing in the first 10 ticks~~ done 2026-10-17 (31 tests); ~~the merge rules of `ScreenParamsImpl`, `ClientSelector` parsing (`SelectorSpec`)~~ done the same day (41 tests). Optional: Forge GameTest. | `gradlew test` passes, and past bugs are covered by tests |
| 3. Real shader checks | ~~Permanent glslang, and the build uses `-PrequireGlslCheck`~~ done 2026-10-17: bhfx requires it in `gradle.properties`; BHSpells stays optional for friends (see [`shaders.md`](shaders.md)) | Every shader in both mods passes (bhfx 4, BHSpells 2 on 2026-10-17) |
| 4. Test with other mods and several players | ~~Embeddium, Oculus (pack on/off)~~ user reports OK 2026-10-21; Epic Fight in 1st/3rd person, a dedicated server with 2 players | No ⏳ left in [`testing.md`](testing.md) |
| 5. Measure performance (2026-10-17: idle cost, GPU time, VRAM and first cast pass at 854x480; 2026-10-22: pass again in the modpack at 2560x1369 with a shader pack, `shadow_domain` up to 4.82 ms GPU, with `flame_field` together 5.93 ms; a 1080p mid-range run and a Heavenly Combo number while casting are left, see [`testing.md`](testing.md)) | With no Fx, under 0.1 ms difference from not having bhfx; bhfx GPU time ≤ 6 ms; first cast hitches < 50 ms; VRAM ≤ 80 MB and freed after 30 seconds (`/bhfx bench`, `/bhfx stats`) | Every criterion passes on a mid-range machine at 1080p |
| 6. Release 1.0 | Version 1.0.0, changelog, BHSpells accepts `[1.0,2.0)` | — |

Suggested order: 1 → 2 → 3 can be done locally right away (3 done, 2 mostly done on 2026-10-17). 4–5 need time in game and a friend; do them together with the multiplayer test.

## Scale plan: ~70 players with different effects at once (proposed 2026-11-06, not started)

Goal: about 70 players casting different effects at the same time, with no client or server running out of memory, no long CPU hitch and no network flood from bhfx. bhfx only; the existing BHSpells effects stay as they are (their server `sendParticles` loops are a separate BHSpells problem, found 2026-11-06). Assessment in [`worklog.md`](worklog.md) 2026-11-06. Merges the particle optimization plan below; that plan's hold (the `test_flow` / `test_vortex` checklists) still applies to phase 2.

Done when (measured with the phase 0 tool): 70 different effects starting within one second give no frame over 50 ms; client heap and VRAM stop growing (same numbers after a 10-minute loop, back to "post idle" 30 s after the end); bhfx GPU stays within `gpuBudgetMs`; bhfx traffic per player stays bounded (target to set from the phase 0 numbers).

- [ ] **Phase 0, measure first.** Code done 2026-11-07 (build + JUnit, not run in game): `/bhfx stress`, `/bhfx_play stress`, `/bhfx_play net`, new `/bhfx stats` lines (see [`testing.md`](testing.md)). Left: run it in game and record the before numbers. Planned text: Dev command `/bhfx stress <count> [ids…]`: starts N effects round the player from fake caster ids, at random spots, staggered or all in one tick. `/bhfx status` gains peak alive vs capacity per batch (old item 4 below), effects refused or evicted, particle slots allocated. A server-side counter of bhfx bytes and starts sent per player per second. Record before numbers in [`testing.md`](testing.md).
**Usable first (agreed 2026-11-07).** Phases 0 and 1 are on branch `feature/scale-phase0` (`15eb30f`, `1ec0427`). The user goes back to making effects next, so only these steps come before that; everything else in this plan waits until a real need shows up.
- [ ] U1. In-game round of the phase 1 checklist in [`testing.md`](testing.md). Must pass: item 1 (no flicker under the stress that flickered), item 3 (walk away and back: fade out / in, nothing near blinks, turning the camera changes nothing), item 5 (Heavenly Combo, Annihilation Arrow, Conqueror Haki and Dawn Rend still work). Items 2, 4 and 6 are nice to have.
- [ ] U2. Fix what U1 finds, rebuild, re-check only the failed items.
- [ ] U3. Release 0.3.10. ~~`gradle.properties` `mod_version`, BHSpells `build.gradle` `fg.deobf("net.deizyn:bhfx:0.3.10")` (lines 254–255), the jar in `BHSpells/libs/`, `CHANGELOG.md`~~ done 2026-11-10 (version bumped before U1, at the user's request); BHSpells `mods.toml` raised to `[0.3.10,0.4)` because its effects already use the 0.3.10 ribbon calls. Left: merge the branch into `main` (after U1/U2).
- For effects made from now on: pass shaders may declare `uniform float Presence;` and multiply their output by it (fade in / out when far effects are built late or hidden); particle materials need nothing. Under load particles are thinned evenly, so density should still read when one in two or three shows. Size batch capacities with the `/bhfx stats` "never above 25%" line.
- Waits (not needed to be usable): far draw distance step; phase 2 (pool, live window, slot ceiling); a lower quality floor for GTX 1050 Ti class GPUs; phase 3 LOD API; phase 4 server fairness (`maxStartsPerTick` 64 per dimension dropped 6 of 70 in one tick on 2026-11-07); phase 5 retest; the separate BHSpells server `sendParticles` loops (not bhfx).

Requirement (user, 2026-11-06): everyone in range sees the same effects. Under load, lower the detail of all effects evenly instead of dropping some. Nothing may blink or vanish when you walk closer or turn round. Equal detail on every machine is not required.

- [ ] **Phase 1, show every effect in range, steadily.** Code done 2026-11-07 (build + JUnit, not run in game), details in [`worklog.md`](worklog.md); the even particle thinning comes first, the far draw distance step is not done. Planned text:
  - [ ] Keep a light record of every effect the client was told about (start data, current params from `S2CFxUpdate`, stop state), apart from the constructed `Fx`. The cap on records is far higher than on constructed effects. An effect is built from its record at its current age when it is shown, so walking closer brings back an effect that was not built.
  - [ ] Guaranteed radius (config, e.g. 48 blocks): every effect inside it is always built and drawn. Under load, all of them lose detail evenly (resolution steps as now, then a particle count scale shared by all, then shorter far draw distances), never one effect dropped. Past the radius, the budget is shared by distance.
  - [ ] Choose which effects are shown by distance from the local player (own effects and effects aimed at the player first), not by view direction, so turning the camera never swaps effects. Hysteresis: an effect stays shown until it is clearly past the radius plus a margin, held at least ~2 s. Off-screen effects keep their place.
  - [ ] Fade instead of popping when an effect enters or leaves the shown set: particles by an alpha multiplier in the vertex shader; passes by a `Presence` uniform that new shaders may read (old shaders ignore it and just appear, without blinking).
  - [ ] Order the particle slot budget and `maxPassesPerFrame` by the same shown set (now: latest-ordered, then farthest), and raise `maxActiveFx` (32) to fit the radius; passes inside the radius use `PassOptions.within` bounds so many of them stay cheap.
  - Same look on every client: `Fx` already takes time only from `age()` and randomness from `random()` with the server's seed (`ServerFx` line 124). But `random()` is one sequence, so an effect built late draws different numbers from one built at tick 0: same shape, different single particles. For an exact match: an opt-in `random(tick)` keyed by seed and tick. Particles born before a late build are missing until their lifetime would have ended.
  - [ ] Spread effect construction: at most N `Fx` constructed per frame (own and nearby effects exempt); the rest wait in a queue and catch up with the start age already in `S2CFxStart`. Stops a tick with 64 starts from allocating every batch in one frame.
  - Side effects to handle (reviewed 2026-11-06):
    - Rank before constructing. `ClientFxManager.add` runs `factory.create` before the cap check, so a refused effect still allocates its batches. BHSpells `BhfxClientBridge.comboFor` plays again on every cue when bhfx refused, so a refused far combo would construct on every cue.
    - An evicted or refused effect never comes back today: the server marks it as sent (`Tracking.Reach`). The record above fixes this on the client, with no resend.
    - A queued start that waits longer than the effect's duration is dropped. A stop for a queued start removes it from the queue. Catch-up only sets the age; it does not run the missed ticks (that would bring the CPU spike back), so spawns from those ticks are lost.
- [ ] **Phase 2, memory without spikes (after the hold above).**
  - [ ] Pool particle batch storage (`ParticleBatch.data` and its GL buffer in `ParticleRenderer.bind`) by power-of-two capacity, reuse across effects, cap the pool, free after idle. Now every batch allocates a new `float[]` and a new buffer on its first draw. A reused slot must start empty (`filled` 0, full upload), or the last effect's particles flash for a frame. The ring keeps the capacity the effect asked for, not the rounded size, so trail lengths don't change. Held memory then drops to idle a little later than now.
  - [ ] A ceiling on particle slots allocated across all effects; past it, new batches are refused or get a smaller capacity, and `/bhfx status` says so.
  - [ ] Old item 1 below: draw only the live window, not every `filled` slot.
- [ ] **Phase 3, opt-in LOD for new effects (new API, existing effects untouched).** `ctx.lod()` / `frame.lod()` tier from distance, owner and quality (`FULL` / `REDUCED` / `MINIMAL`), and an opt-in count scale for `emit` / `stream` (old item 2 below). The default is off, so effects that never ask look the same.
- [ ] **Phase 4, server fairness.**
  - [ ] Replace the single `maxStartsPerTick` per dimension with a per-caster rate (token bucket), so one caster can't use up everyone's starts. Keep a global safety cap.
  - [ ] Per-player send budget in `ServerFx.flush`: what doesn't fit waits for the next tick, nearest first, so joins, respawns and teleports into many persistent effects don't send everything at once.
  - Side effects to handle (reviewed 2026-11-06): one cast that starts several effects in the same tick needs a burst big enough for it; mobs (`casterId` < 0) need their own bucket; a start still waiting in the outbox when its stop or update is sent must be dropped or sent first (now `flush` runs before every stop and update); keep `maxStartsPerTick` as the global cap so old server configs still load.
- [ ] **Phase 5, retest** with `/bhfx stress` and the same numbers as phase 0, then a real multiplayer round. Release as a new version (not the same version number, [`bugs.md`](bugs.md) 2026-11-04).
- Not needed at this scale (checked 2026-11-06): the server tracker scan (`ServerFx`, every 10 ticks, about 1,120 effects × 70 players), the packet size limits (params ≤ 32 KB, 16 starts per batch packet), the client per-frame paths (shared contexts, no allocation per frame except `PassListImpl` entries).

## Waiting on the user

- Where to distribute bhfx: only your own modpack, or Modrinth/CurseForge.
- License: keep All Rights Reserved if other mods are going to import it?

## Backlog (not scheduled)

- Shader particles and textures (2026-11-04 assessment in [`worklog.md`](worklog.md)):
  - [x] 2026-11-04 Texture uniform (`Uniforms.texture`, `FxTexture`): off-thread decode, R8 + channel packing, mipmaps, ramps. 0.3.9, not tested in game.
  - [x] 2026-11-04 Particle renderer v1 (`Fx.createParticles`, `PassList.particles`): instanced, analytic motion, pass-style material, in the chain with depth test and soft fade. 0.3.9, not tested in game.
  - [x] 2026-11-04 DISTORT particles (RG16F target + `particle_resolve`). Emissive needs no buffer: HDR particle light already feeds the bloom prefilter.
  - [x] 2026-11-04 Flipbooks (`flipbook` in `particle.glsl`). Particles already draw at effect resolution (the same dynamic resolution as passes), so a separate half-res buffer was not added.
  - [x] 2026-11-04 Resolution-step hitches: held upward steps instead of allocate-at-cap (see [`decisions.md`](decisions.md)).
  - [x] 2026-11-04 Emitter shapes (`FxEmitter`, `FxParticles.emit`), rate streams (`FxParticles.stream`) and analytic orbit / pull round a batch axis. 0.3.9, not tested in game.
  - [ ] In-game test of all of the above ([`testing.md`](testing.md)), then `/bhfx bench bhfx:test_particles` numbers.
  - [x] 2026-11-04 Turbulence, over-life curves (colour keys / ramp, alpha, size), ribbons (`Facing.RIBBON`, `FxParticles.ribbon()`, streams), `Shape.onGround`, built-in materials `DOT` / `STREAK` / `SPRITE` / `RIBBON`. 0.3.9, not tested in game.
  - [x] 2026-11-07 Ribbons drawn along curves with one texture: smooth `p.along`, strips in a plane (`ribbonNormal`, per-point `normal`), `p.facing`; test effect `bhfx:test_swirl`. Not tested in game.
  - [ ] Optional: a built-in textured-ribbon material (`Sprite` stretched by `p.along`, scale / scroll in uniforms), once a BHSpells effect needs the same thing twice.
  - [ ] Presets for repeated pieces (opening sequence, ground rings), from the 2026-11-04 "what else" list.
  - [ ] BHSpells: move `ConquerorHakiFx` spawn loops onto `emit` where it reads simpler (optional).
  - [ ] First BHSpells spell on particles (e.g. Fiery Dance embers + heat haze).
- Particle optimization plan (agreed 2026-11-04, **on hold until the `test_flow` / `test_vortex` checklists in [`testing.md`](testing.md) pass**: they did on 2026-11-16 by `/bhfx shoot`, so the hold is lifted; so bugs from the new features and from the optimization don't mix). Release as a new version after 0.3.10 (a new version also avoids the stale same-version jar, [`bugs.md`](bugs.md) 2026-11-04). Before and after: `/bhfx bench` on `bhfx:test_flow`, `bhfx:test_vortex`, `bhfx:test_particles` and `bhspells:conqueror_haki`, numbers into `testing.md`.
  - [x] 2026-11-16 1. Draw only the live window (0.3.16): `ParticleBatch.live` / `advanceWindow` / `windowRuns`, `PWindow` in the particle vertex shader; JUnit `ParticleWindowTest` (4); in game (dev control, 30 s runs) the slots drawn at 25 s fell 73–91 % with the same look. Original plan text: Now every batch draws `filled` instances (`ParticleRenderer`, = capacity once the ring wraps) and dead slots still run the vertex shader. Track write times, draw only the newest K slots that can still be alive (max life + max delay), pass the start slot as a uniform (no base instance in GL 3.3). The particle budget in `PostRenderer` counts that K, not `filled`. Internal only, no API change, no visual change. Risks: `delay()` births out of write order (window must include max delay); ribbon links stay inside the window. JUnit for the window with delays and wrap.
  - [ ] 4. `/bhfx stats`: peak alive vs capacity per batch, to size capacities in bhfx and BHSpells. Display only.
  - [ ] 2. Quality / distance LOD for `emit` and `Stream` counts (e.g. LOW ×0.5, fewer far away). Changes looks: evenly counted shapes change spacing, streamed ribbons get coarser. Needs a per-batch / per-emit opt-out; default **off** so existing BHSpells effects don't change.
  - [ ] 3. Max on-screen size and near-camera fade per batch, for fill rate (big ALPHA smoke). Changes looks of effects round the player (aura, `attach @s`, first person); default **off**.
  - Not planned now: evaluate-once pre-pass (positions computed 4× per particle, 8× for ribbons; worth it at tens of thousands), half-float packing (112 → ~64 bytes; position/time precision risk), a half-res buffer (particles already draw at effect resolution), ALPHA sorting, compute shaders (GL 4.3, not on macOS), persistent mapped buffers.
  - [x] 2026-11-12 `TUMBLE` facing (tumbling panes in real world planes) covers flat shards; see [`decisions.md`](decisions.md). Version 0.3.11 (bumped 2026-11-12); BHSpells `build.gradle` and `mods.toml` `[0.3.11,0.4)` raised with it.
  - [ ] Later, only when an effect needs it: mesh particles (thick pieces with side faces); GPU simulation by texture ping-pong (no compute: GL 3.2 / macOS 4.1); particle sorting for ALPHA; a dev-only overdraw view.

- Shader pack mode: while a pack is on, keep screen-only effects (flash, vignette, ripple, impact, bloom, pulses) and skip only depth-based world passes, instead of the all-or-nothing `offWithShaderpack`. Only needed if world-space parts look wrong with a pack (2026-10-17: the user reports effects visible with Complementary forced on).

- `AaaParticlesBridge` (`ctx.effek(...)`): when there is a real use case.
- ~~`Uniforms.texture(name, ResourceLocation)`~~ done 2026-11-04 as `Uniforms.texture(name, FxTexture)` (0.3.9).
- BHSpells spells that would suit bhfx next: Indigo Lightning, Fiery Dance, Venomous Blossomfall (LONG/CONTINUOUS with all the hooks), and long-lasting zone spells (to test the tracker with long effects).
