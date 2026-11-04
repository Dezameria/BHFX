# Worklog

Daily work log, newest at the top. Format:

```
## YYYY-MM-DD — short title
- repo: files touched — what changed
- result: build / tested in game / not tested
- next: what remains (move it to roadmap.md if it is longer)
```

---

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
