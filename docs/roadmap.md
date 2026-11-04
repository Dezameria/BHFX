# Roadmap

Open work and plans. When something is done, tick it as `[x]` with the date, and record the details in [`worklog.md`](worklog.md).

## Now (0.3.x)

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
- [x] 2026-10-22 In-game settings screen with Low/Medium/High/Max presets; tested in the modpack the same day (see [`testing.md`](testing.md)).

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
  - [ ] Presets for repeated pieces (opening sequence, ground rings), from the 2026-11-04 "what else" list.
  - [ ] BHSpells: move `ConquerorHakiFx` spawn loops onto `emit` where it reads simpler (optional).
  - [ ] First BHSpells spell on particles (e.g. Fiery Dance embers + heat haze).
- Particle optimization plan (agreed 2026-11-04, **on hold until the `test_flow` / `test_vortex` checklists in [`testing.md`](testing.md) pass**, so bugs from the new features and from the optimization don't mix). Release as 0.3.10 (a new version also avoids the stale same-version jar, [`bugs.md`](bugs.md) 2026-11-04). Before and after: `/bhfx bench` on `bhfx:test_flow`, `bhfx:test_vortex`, `bhfx:test_particles` and `bhspells:conqueror_haki`, numbers into `testing.md`.
  - [ ] 1. Draw only the live window. Now every batch draws `filled` instances (`ParticleRenderer`, = capacity once the ring wraps) and dead slots still run the vertex shader. Track write times, draw only the newest K slots that can still be alive (max life + max delay), pass the start slot as a uniform (no base instance in GL 3.3). The particle budget in `PostRenderer` counts that K, not `filled`. Internal only, no API change, no visual change. Risks: `delay()` births out of write order (window must include max delay); ribbon links stay inside the window. JUnit for the window with delays and wrap.
  - [ ] 4. `/bhfx stats`: peak alive vs capacity per batch, to size capacities in bhfx and BHSpells. Display only.
  - [ ] 2. Quality / distance LOD for `emit` and `Stream` counts (e.g. LOW ×0.5, fewer far away). Changes looks: evenly counted shapes change spacing, streamed ribbons get coarser. Needs a per-batch / per-emit opt-out; default **off** so existing BHSpells effects don't change.
  - [ ] 3. Max on-screen size and near-camera fade per batch, for fill rate (big ALPHA smoke). Changes looks of effects round the player (aura, `attach @s`, first person); default **off**.
  - Not planned now: evaluate-once pre-pass (positions computed 4× per particle, 8× for ribbons; worth it at tens of thousands), half-float packing (112 → ~64 bytes; position/time precision risk), a half-res buffer (particles already draw at effect resolution), ALPHA sorting, compute shaders (GL 4.3, not on macOS), persistent mapped buffers.
  - [ ] Later, only when an effect needs it: mesh particles; GPU simulation by texture ping-pong (no compute: GL 3.2 / macOS 4.1); particle sorting for ALPHA; a dev-only overdraw view.

- Shader pack mode: while a pack is on, keep screen-only effects (flash, vignette, ripple, impact, bloom, pulses) and skip only depth-based world passes, instead of the all-or-nothing `offWithShaderpack`. Only needed if world-space parts look wrong with a pack (2026-10-17: the user reports effects visible with Complementary forced on).

- `AaaParticlesBridge` (`ctx.effek(...)`): when there is a real use case.
- ~~`Uniforms.texture(name, ResourceLocation)`~~ done 2026-11-04 as `Uniforms.texture(name, FxTexture)` (0.3.9).
- BHSpells spells that would suit bhfx next: Indigo Lightning, Fiery Dance, Venomous Blossomfall (LONG/CONTINUOUS with all the hooks), and long-lasting zone spells (to test the tracker with long effects).
