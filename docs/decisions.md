# Design decisions

## 2026-11-17 — License: All Rights Reserved, held by Dezameria
The user chose to keep All Rights Reserved (over MIT, LGPL-3.0 and ARR with a modpack exception) and Dezameria as
the copyright holder. `LICENSE` at the repo root states it; `mod_license` in `gradle.properties` already said so.
Effect: nobody else may copy, change or redistribute bhfx (including in a modpack) without written permission. The
`LICENSE` file is not packed into the jar.

## 2026-11-17 — Keyed random is a stateless helper beside `Fx.random()`, not a replacement
`FxRandom` (in `api`, common) mixes `(seed, stream, index)` into a seed; `Fx.random(stream, index)` wraps it.
`Fx.random()` stays one sequence. Reason: existing effects keep their looks; a keyed value needs no state, so the
server can compute the same strike points from the seed it chose, and a client that builds the effect late gets the
same numbers. Cost: the effect's author must pick streams and indexes; each `source(...)` call allocates a small
`RandomSource` (use `unit` / `range` for single values).

## 2026-11-17 — The overdraw view reuses the particle draw, no counting target
`/bhfx overdraw` sets `PMode.x` to 3 (past the blend modes); the particle footer then writes a flat colour with a
small alpha, blended like an ALPHA particle, so n layers leave `0.93^n` of the image. Reason: no new target, pass or
uniform, and it covers every material of every mod through the shared footer. Cost: it is a saturating tint, not an
exact count, and only particles show (models and passes draw as usual). Dev clients only.

## 2026-11-17 — Particle LOD thins what is drawn, not what is spawned
`FxParticles.lod` raises the batch's draw stride by distance instead of scaling `emit` / `stream` counts. Reason:
it reuses the frame's thinning (same slots every frame), cannot change an effect's logic or leave holes when the
camera comes back, and needs no opt-out per emit. Cost: upload and memory are not reduced, and measured GPU time
did not drop for small far particles; what it gives is slot budget for near effects.

## 2026-11-17 — Model distortion reads the image in the material, no distortion target
`bhfx:distort` samples the model run's copy of the image at a shifted uv (`bhfxBehind`), instead of writing offsets
into the particles' distortion target and resolving. Reason: models already get a copy when a material declares
`DiffuseSampler`, so it needs no new target or pass, and the depth check per pixel is free. Cost: a model run is
split before such a material (so it sees earlier models), one more full copy a frame.

Format: **topic — decision — reason**, with the date. If a decision changes later, add a "Replaced by …" line instead of deleting the old one.
Decisions made during the original planning are in [`API_PLAN.md`](API_PLAN.md) section 10 and in "Where the code differs from the plan" in [`../README.md`](../README.md).

## 2026-11-17 — Lights are one shared pass, fed by a uniform array
- **Decision**: `PassList.light` only records a light; `PassListImpl.drawList` adds one `bhfx:lights` entry (ordinary
  pass machinery: bounds, scissor, copy) in front of the list, outside the pass cap. Lights go to the shader as
  `uniform vec4 Lights[128]` (64 lights), not a data table. No shadows.
- **Reason**: a custom pass per effect costs a screen pass each and only 8 fit a frame; lights from 70 effects must
  cost about the same as from one. A uniform array needs no buffer object and no per-frame table upload path; 512
  floats is within the GL 3.2 minimum (1024 fragment uniform components). Shadows were dropped on 2026-11-16.

## 2026-11-17 — Uniform values are cached per program
- **Decision**: `ShaderProgram.set1` .. `setMatrix` remember the last value sent and skip the GL call when it is the
  same (uniforms stay in a program between draws and frames). A uniform goes either through these (everything in
  `Uniforms.f1` .. `f4`, the frame constants, the 2D sampler units) or directly by location (`floats`, textures and
  data tables, `PData`, `MBones`, `MAxes`), never both, or the cache goes stale.
- **Reason**: about half of the GL calls per batch repeat the same value. Kept although it measured no gain on the dev
  machine (2026-11-17, [`testing.md`](testing.md)): it is small and safe.

## 2026-11-16 — Next feature is `FxData` (runtime data tables), not world warp, the block atlas or half-res stages
- **Decision**: after comparing bhfx with `../orbital-laser` (a Horizon mod whose effects are one big post pass), build
  `FxData` first: a `GL_TEXTURE_BUFFER` (`RGBA32F`, up to 65,536 texels) that an `Fx` fills in Java and a pass reads as
  a `samplerBuffer`. Plan: [`plan-fx-data.md`](plan-fx-data.md).
- **Reason**: it is the one gap that blocks a whole class of effects (per-piece tables, bone tables for SDF bodies,
  ground heights looked up per piece), and the mechanism already exists inside bhfx for particles. The block atlas is
  small but only helps block-looking debris. Half-res stages only save GPU time. Bending the world itself needs
  chunk-renderer mixins that would clash with Sodium-type mods and shader packs, so it is not planned.

## 2026-11-16 — Dev remote control through files, dev client only
- **Decision**: `DevControl` reads commands from `<run>/bhfx_dev/inbox/*.txt` (allowlisted first words: `bhfx`, `tp`,
  `time`, `weather`, `fill` ...; plus `wait`, `camera`, `quit`), writes `status.txt` and `outbox/*.log`. Only when
  `FMLEnvironment.production` is false and the `bhfx_dev` folder exists. With `runClient -PquickPlay` a tool can start
  the game into a world, set the camera by `/tp`, shoot, and read `sheet.png` without the user.
- **Reason**: every check needed the user to type and report back; files need no port or setup, work in both dev
  folders, and the allowlist plus dev-only gate keep it from being a general remote shell. Details: [`dev-control.md`](dev-control.md).

## 2026-11-16 — No shadows (sun direction, blob, SDF or model shadows) for now
- **Decision**: not planned. The user judged shadows more than effects need. `sdf_march.glsl` keeps `sdfShadow` for an
  effect that wants it in its own pass.
- **Reason**: glowing, mostly brief spell effects read fine without them; the options weighed were blob shadows (near
  free), SDF soft shadows (about 0.2 ms bounded, up to 1–3 ms full screen) and model shadow maps (about twice the model
  cost). Reopen only when a specific effect looks wrong without one.

## 2026-11-16 — Animations (M3) sampled on the CPU into `FxPose`, Blockbench's interpolation rules
- **Decision**: `FxAnimation` parses `.animation.json` itself (no GeckoLib) into arrays and writes the sampled values into
  the same `FxPose` M2 uses; the GPU side is unchanged. Interpolation follows Blockbench (what the artist saw:
  catmullrom / bezier when either end has it, step holds), easing follows GeckoLib (the later key's). Only a Molang
  subset, compiled once to a tree. Non-looping clips hold their last frame (effects usually end on a pose, unlike
  GeckoLib's reset).
- **Reason**: sampling is tiny next to drawing (0.17 µs per clip apply), so no GPU keyframe textures; one pose path for
  hand-written and file-driven motion means blending (`weight`) and code overrides mix freely. A full Molang VM is not
  worth it: none of BHSpells' 15 animation files use Molang at all.

## 2026-11-16 — Model bones posed on the GPU by uniform matrices (M2), Blockbench numbers
- **Decision**: `FxPose` (by bone name, Blockbench animation units) → `BoneRig` makes one 3x4 matrix per bone on the
  CPU (posed × rest⁻¹) → a `vec4 MBones[192]` uniform → the vertex shader moves each vertex by its bone. Not an
  `FxData` table, not re-baking, not one draw per bone. Instanced copies share one pose. Max 64 bones.
- **Reason (performance)**: the baked vertex buffer never changes (no re-upload); a bone costs a few matrix products
  on the CPU and 3 vec4 to upload once per draw; one draw call as before; untouched poses upload nothing; hiding is
  scale 0 (collapsed triangles cost almost nothing to raster). A uniform array is faster to read than a buffer texture
  and 64 bones fit GL 3.2's minimum of 1024 vertex uniform components. Blockbench / GeckoLib units so M3 (playing
  `.animation.json`) only has to feed keyframes into the same `FxPose`. Per-copy poses for instances would cost
  `copies × bones` matrices a frame; vary copies in the material (`s.instance`) instead until an effect needs more.

## 2026-11-16 — bhfx draws models itself (M1: static), baked like GeckoLib, without depending on it
- **Decision**: `FxModel` + `PassList.model`: bhfx bakes `.geo.json` itself (`GeoModelLoader`, a copy of GeckoLib 4.8.3's
  rules) and draws it as its own geometry group in the pass chain with a depth buffer of its own, material shaders
  like particles (`model/*.fsh`). Steps after this: M2 bones from Java (skinning by `MBone` with matrices from
  `FxData` / `FxFrame`), M3 `.animation.json` playback, M4 instancing and distortion.
- **Reason**: the user wants model effects that are cheap: no entity (no server tick, no network, no per-entity
  GeckoLib bone work), bhfx materials (glow, dissolve, bloom) on the real shape. Copying the bake rules keeps bhfx free
  of a GeckoLib dependency while textures line up the same; checked against the decompiled classes and all 17 BHSpells
  models. Each vertex already carries its bone index, so M2 needs no format change. A separate depth renderbuffer, not
  the world's, because bhfx draws at FX resolution; the world's depth is only sampled to hide the model.

## 2026-11-16 — Named points on entities, apart from body parts; GeckoLib bones reported by BHSpells
- **Decision**: a second, open-ended set of points (`EntityPoints`, `BHFxClient.point` / `reportPoint`, string names)
  next to the fixed `BodyPart` enum, sharing only its capture window (entity pass, no mirror reflections). Points are
  never estimated. bhfx keeps no GeckoLib dependency: BHSpells adds a draw-nothing `GeoRenderLayer` to every
  `GeoEntityRenderer` through GeckoLib's `GeoRenderEvent.Entity.CompileRenderLayers` (Forge bus, fired lazily on a
  renderer's first `getRenderLayers()`), reporting each bone's pivot from `renderForBone` (GeckoLib hands layers the
  bone matrix translated back from the pivot, so the layer translates by `pivot / 16`).
- **Reason**: the user asked that nothing existing change. A separate store means asking for a bone never turns on the
  vanilla body-part layer and the reverse; the event means no renderer file is touched; points only exist when an
  effect asks (one `wantsPoints` check per bone otherwise). Bones have arbitrary names per model, which an enum can't
  hold. No network change: an effect that rides a bone reads it in `passes()`; `FxSpawn.part` stays body parts only.

## 2026-11-16 — 3D shapes: SDF includes and Java-built bones now, mesh-baked shapes later
- **Decision**: add `sdf3d.glsl`, `sdf_march.glsl` and `FxFrame` + `FxData.setFrame` (API_PLAN 5.6). Do not build
  shapes baked from meshes into 3D distance textures yet.
- **Reason**: shapes and bones cover glowing weapons, limbs, crystals and constructs, cost no new GL path, and leave
  every existing effect alone. Unlike `../orbital-laser` (the Sky Breaks hand: poses sampled 30 times a second into a
  table ahead of time, because its WebAssembly has a fuel limit), bhfx's `passes()` runs every frame in Java, so
  bones are built live and can follow `BodyPose`. Mesh baking (a `sampler3D` loader, a bake script, blending pieces at
  the joints) is the biggest part and no effect asks for a sculpted shape yet; solid animated models are GeckoLib's
  job. The march include relies on bhfx pasting includes in place: `map` must be defined above it (GLSL 150 can't pass
  functions).

## 2026-11-16 — 1.21.1 NeoForge port: one repo, a long-lived branch, 1.21.1 only
- **Decision** (prepared, not started): port to 1.21.1 NeoForge on a branch `mc/1.21.1-neoforge` with features
  cherry-picked from `main`; separate artifact `bhfx-neoforge`; no multi-version tooling. Plan:
  [`plan-neoforge-1.21.1.md`](plan-neoforge-1.21.1.md).
- **Reason**: bhfx has no mixins and a raw-GL renderer, so 1.21.1 (still classic blaze3d, GL 3.2) is mostly a loader
  API port. 1.21.2 removes `ShaderInstance` and 1.21.5 replaces the GL layer, so those are not targets.

## 2026-11-13 — Body parts: a render layer for vanilla models, a report hook for everything else
- **Decision**: body part positions come from what the client actually drew last frame. A draw-nothing `RenderLayer` on every `LivingEntityRenderer` reads the posed `ModelPart` matrices (layers run after `setupAnim`, already in model space, so still no mixin), multiplied by the inverse of the camera rotation captured at `AFTER_SOLID_BLOCKS`. Captures are written only between `AFTER_SOLID_BLOCKS` (after mirror reflections) and the next stage after the entity pass, and only for entities something asked about in the last 200 frames. Without a fresh capture (not drawn, first person, unknown model) the point is estimated from the bounding box and body yaw; first-person hands sit where the view shows them. Renderers that replace the vanilla model report joints through `BHFxClient.reportBodyPart`; reports win over the layer for that frame. The Epic Fight reporter lives in BHSpells (`EpicFightBhfxBodyParts`), not in bhfx. `FEET` is the midpoint of both soles at query time.
- **Reason**: a server has no model pose, so the client is the only place a hand position exists; reading the drawn model means walking, swinging, crouching and other mods' animations come for free. bhfx stays free of compile dependencies (Epic Fight's joint API has been renamed between versions; BHSpells already pins one). Protocol 3 because `S2CFxStart` carries the part. Cost when nobody asks: one map lookup per drawn living entity.

## 2026-11-12 — TUMBLE particles instead of mesh particles
- **Decision** (for BHSpells `glass_shatter`, from the user's "fantasy glass shatter" brief that asked for real 3D shards; `glass_shatter` was removed the same day and its techniques moved into `sky_shatter`, which keeps TUMBLE): add one facing, `TUMBLE`, a flat pane in a real world plane that turns round a per-particle axis, instead of mesh particles with front, back and side faces. Shapes stay in the material (it cuts the quad, e.g. to a Voronoi cell from uniforms). The face normal is packed (octahedral, 2 x 11 bits, `ParticleBatch.packUnit`) into the rotation float and the tumble axis into the link float (unused outside RIBBON); the rate is the spin. `rotation()` is ignored for TUMBLE so it can't clobber them.
- **Reason**: a shard's thickness (0.01–0.03 blocks) is below a pixel at any distance that matters, so side faces add cost and a new geometry path for nothing visible; a pane with true orientation already gets depth, correct occlusion, fresnel and edge-on thinning. Memory per slot stays 112 bytes. Not done: no side faces (edge on it thins to nothing), no orbit turning of the pane, no sorting between overlapping ALPHA panes. Mesh particles stay in the backlog.

## 2026-11-12 — Mirror panel: a separate thin block, front only
- **Decision** (the user's request): `bhfx:mirror_panel` is its own block (1/16 plate, facing the clicked face, all six directions) next to the full `bhfx:mirror`, sharing its block entity type and tracker. Only the front reflects. Panels are their own surfaces (`Key.panel`), never merged with block faces, since they lie 1/16 off the block grid. No frame and no support check: plates on one wall already join seamlessly, and a plate stays put if the wall behind is broken.
- **Reason**: a full block makes a mirror wall one block thick; a plate can go on any existing wall. Keeping the panel separate leaves the tested mirror block untouched. A connected frame (border only on the outer edges) would need neighbour states and was left out until asked for.

## 2026-11-07 — Ribbon along / plane data in the unused axis texel
- **Ribbon points keep `along` in axis.y and their own normal (octahedral) in axis.z / axis.w + 3**, since a ribbon never uses the AXIS axis: memory per slot stays 112 bytes. axis.w = 0 means "no normal of its own" (the encoded y is offset by 3 so 0 can't be a real value); the batch normal is a uniform. Reason: per-point normals let one batch hold rings at different tilts (and twist a strip), without a new texel.
- **`along` defaults to distance in blocks**, not a 0..1 fraction: a streamed trail doesn't know its final length, and a distance keeps a trail's texture fixed in the world. Arcs drawn at once set 0..1 themselves.
- **The normal turns with the point's orbit**, like its velocity: a tilted ring spinning round the batch axis precesses as a rigid body would; a normal along the orbit axis is unchanged.
- **`p.facing` instead of a normal in the material**: one smooth value covers the common uses (fade or thicken edge-on strips) without another varying vector.

## 2026-11-07 — Known effects vs built effects; even particle thinning (scale plan phase 1)
- **The client remembers every effect it is sent** (start data, merged params, stop) and builds an `Fx` only for the ones `FxSelection` shows; a hidden one fades out and is freed, and is built again at its current age when chosen again. Replaces "drop the oldest past `maxActiveFx`" (2026-11-06 user requirement: everyone in range sees the same effects, nothing blinks or vanishes when walking closer). The server needs no change and resends nothing.
- **Selection by distance from the player only** (not the view direction), pinned first (local API effects, your own, effects following you), then everything within `guaranteedRadius`, then the rest by distance up to `maxActiveFx`. Shown effects keep their place with an 8-block margin and a 2 s minimum hold. Reason: turning the camera must never swap effects, and an effect at an edge must not blink.
- **Local effects started through the API are never hidden**, because callers keep their `Fx` (`BHFxClient.fx`, e.g. BHSpells `BhfxClientBridge` feeds cues to it); a rebuilt instance would leave them holding a dead one. `/bhfx stress` effects are local but hideable (nobody holds them).
- **On time vs late**: built within 2 ticks of arriving it starts as sent (keeps `onStart`); later it starts at its age now (no `onStart`, like joining mid-effect), fading in over 10 ticks. Missed ticks are not replayed (that would bring the CPU spike back), so their spawns are missing; `random()` restarts from the seed, so single particles differ from clients that built it on time (same shape).
- **Too many particle slots → every non-ribbon batch draws one slot in N** (the same slots every frame; N rises at once and falls after 30 settled frames) instead of whole batches being skipped. Ribbons are not thinned (each point links to the previous one); past the cap they are still skipped. Reason: the 2026-11-07 flicker came from batches taking turns at the slot cap.
- **`Presence` is opt-in for passes**: a pass shader that declares `uniform float Presence;` gets 0..1; old shaders just appear (not blink). Not added to the pass header, so no existing shader can clash with a second declaration. Particles get it in `PMode.w` through `_footer.glsl`, no material change.

## 2026-11-04 — Curves in the vertex shader; ribbons by back-links; ground snapping on the CPU (0.3.9)
- **Over-life curves** are batch uniforms evaluated in `particle/vertex.vsh` and folded into `pColor` (now a smooth varying, like the new `pAge`), not a footer step: every material, BHSpells' included, follows them with no change, and they cost nothing per pixel. Keys are evenly spaced (1–4) to stay in uniforms; a ramp texture covers anything finer.
- **Ribbons** link each point to the previous point's slot (texel 6 w) with a chain id (axis.x), instead of "the next ring slot": several streams can share one batch without breaking each other. Each end of a segment uses its own point's backward tangent, which both segments meeting there compute the same way, so there are no cracks; a central tangent would need the next point, which a point doesn't know. Memory per slot is unchanged.
- **Turbulence** is a sum of sines minus its value at birth: analytic, smooth, starts at no offset. Not curl noise: no divergence-free guarantee, which particles that don't interact don't need.
- **Ground snapping** is a CPU block probe at emit time (`LevelGround`), not a depth-buffer lookup: it works off screen and before the frame, and emits are rare compared with frames.
- **`maxPassesPerFrame` counts screen passes only** (2026-11-04): particle batches are not full-screen passes and already have a per-frame slot cap; counting them let a few particle effects push each other out (`bugs.md`).
- **Fluid surfaces count as ground** (2026-11-04, after the user's test): a point over water first landed on the bed, where the water surface's depth hid it. Replaces the first rule "water doesn't count".

## 2026-11-04 — Orbit as a turning frame round a batch axis; streams spread births in time (0.3.9)
- **Decision**: orbit motion is analytic: the usual motion runs in a frame that turns round one axis per batch (`FxParticles.orbit`, a uniform), by `ωt + αt²/2` per particle, with `pull` towards the axis along the spawn radius (same drag curve as the velocity, stopping on the axis). Per particle it costs one texel (7 instead of 6, 112 bytes a slot).
- **Reason**: keeps "no CPU after spawn" and no GPU simulation. A batch axis that can move each frame carries a whirlwind with its caster at no cost. Angular speed that rises as a particle falls in (true angular momentum) has no closed form with drag, so spin-up `α` stands in for it.
- **Rejected**: pulling towards a point (a sphere black hole) — not analytic together with the turn; use a ring/disc across the axis instead. Per-particle orbit centres — 1 more texel for a case no effect needs yet.
- **Streams**: `Stream.emit` spawns what is due since its last call with births spread over the interval (`birth` set directly, possibly before "now") and positions along the line from the last centre, so a trail behind something fast is even at any tick or frame rate. Gaps over 0.25 s are dropped instead of caught up. Emitters use the effect's random, so they are deterministic from `tick` only.

## 2026-11-04 — GPU particles live in bhfx, drawn inside the pass chain (0.3.9)
- **Decision** (the user asked for shader-mixed particles in bhfx): bhfx draws its own particles, client-only, with no `ParticleType` or registry entry. A batch belongs to an `Fx` (`createParticles`) and is drawn among that frame's passes by order (`PassList.particles`), into the effect-resolution HDR chain at `AFTER_LEVEL`, depth-tested in the shader against the depth buffer.
- **Reason**: no registry keeps bhfx removable; drawing in the chain reuses the dynamic resolution, bloom and the shader-pack path that already works for passes (drawing mid-level breaks under packs, see the 2026-10-23 mirror entries); passes ordered after particles can read them. Motion is analytic, so CPU cost is per spawn, not per frame.
- **Storage**: one buffer texture per batch (GL 3.1) instead of instanced attributes (`glVertexAttribDivisor` is GL 3.3); a ring that overwrites the oldest instead of compaction, so uploads are only the newly spawned run.
- **Colours are display-encoded** (`color(0xRRGGBB, a)` passes through), unlike `Uniforms.color`: the frame is display-encoded and the linear conversion is a recorded pitfall ([`shaders.md`](shaders.md)). `FxTexture` likewise reads as stored unless `.srgb()`.
- **Costs/limits**: particles draw over water and glass like passes; no sorting for ALPHA; per-frame slot caps 4096/16384/32768 by quality.
- Changes the 2026-11-02 assessment line "not bhfx's job: world particles" for particles bhfx draws itself; vanilla/Forge particle types stay out of bhfx.

## 2026-11-04 — Resolution steps: hold the way up, don't allocate at the cap
- **Decision**: `ResolutionController.share()` drops at once but rises at most one 1/8 step per 2 s. Effect targets are still sized to the effect resolution.
- **Reason**: allocating at the cap and drawing into a smaller viewport would change the uv range of every texture a pass samples (`DiffuseSampler` at `texCoord`, `projectUv` lookups, bloom), so every pass shader in bhfx and BHSpells would need a uv scale. Holding the upward steps removes the back-and-forth resizes mid-cast, which were the hitch, without touching shaders.

## 2026-11-03 — Expose the terrain fog to passes (0.3.8), instead of overriding the fog
- **Decision**: bhfx captures the terrain fog (`RenderSystem` fog colour, start, end, shape) after the solid blocks and gives it to every pass as `FogColor` / `FogRange`, with `fog.glsl` (`fogAmount`, `refog`). A pass that replaces the sky swaps the fog on far terrain for its own sky colour in post.
- **Reason**:
  - Far terrain is mixed with the day fog colour, so a post pass that replaces only depth-1 pixels leaves a pale band at the horizon (`solar_eclipse` round 1).
  - Re-fogging in post is exact for the vanilla formula: `col + k × (newSky − FogColor)`.
  - It needs no new API surface on `Fx`.
  - It changes nothing for effects that don't use it.
- **Rejected for now**: an `Fx` hook that overrides the fog colour (`ViewportEvent.ComputeFogColor`). That would also tint water and every other mod's fog, and the fog would then lag a frame behind the pass.

## 2026-11-02 — Pass bounds by scissor + copy; no per-pass resolution
- **Decision**: a pass may declare a world-space sphere (`PassOptions.within`). bhfx copies the input into the target with a blit, then draws the pass only inside the sphere's screen rect (scissor). If the sphere is off screen the pass is skipped; if the rect covers ≥ 85% of the screen the pass is drawn full. Per-pass resolution (`Resolution.FULL`, in the original plan) stays out.
- **Reason**: the expensive part of a ray-marched pass is shading, which scales with the shaded area; a blit costs little in comparison. From the snapshot the copy uses the same LINEAR blit as the composite's `SceneLowSampler`, so outside the rect the detail restore gives back the exact world. Mixed resolutions would break the single-size ping-pong chain and the composite's ratio-based detail restore, and no effect has needed it.

## 2026-10-24 — Mirror defaults: distance 16, maxPlanes 6; own settings page
- **Decision** (the user's request): `mirror.distance` 32 → 16 and `mirror.maxPlanes` 4 → 6; the four mirror values get their own page, `MirrorSettingsScreen`, opened by "Mirror..." on the bhfx settings screen. `ClientConfigMigration` step 3 moves each value once if it is still the old default.
- **Reason**: a closed mirror room shows up to five surfaces facing the camera; with 4 the farthest one (the far wall) was dropped. The planes are ordered nearest first, so the cut fell on the wall straight ahead.

## 2026-10-24 — Mirror reflects on all six faces; built for large mazes
- **Decision** (the user's request): no facing; every face reflects except one touching another mirror. Tracking is per face in section-indexed clusters with merged rectangles; reflections are capped per frame by surfaces (`maxPlanes`) and by player drawings (`maxReflections`).
- **Reason**: a mirror maze needs both sides of every wall. The per-frame cost must not grow with the number of mirrors in the world: only sections near the camera are visited, and drawing cost follows rectangles, not blocks. The draw budget bounds the expensive part (each player drawing, worse with Figura).
- Replaces the "full block facing the placer" part of the entries below.

## 2026-10-23 — `offWithShaderpack` defaults to false
- **Decision** (the user's request): the default is false, and `ClientConfigMigration` step 2 (`CURRENT = 2`) sets an existing `true` to false once.
- **Reason**: the user runs packs and has had post forced on since 2026-10-17 with effects visible (see [`testing.md`](testing.md)). Unlike step 1 there is no way to tell the old default from a hand-picked `true`, so step 2 moves every `true` once; a player who wants it back turns it on again and it stays.

## 2026-10-23 — Mirrors under a shader pack: reflect before the level, show as an entity surface
- **Decision**: while an Iris/Oculus pack is active the reflection is drawn from the camera-angles event (before the level render) and put on the mirrors with a vanilla entity render type textured with it, instead of switching mirrors off.
- **Reason**: during the level render the pack swaps every vanilla program for its own and binds its own buffers, so neither an offscreen draw nor our composite shader works there. Before the level render vanilla programs still apply, and an entity render type is something every pack already handles. Costs: the reflection is unlit by the pack's own light model (it carries vanilla lighting, then the pack shades the mirror surface); a strip crossing the camera plane is skipped.
- Replaces "Off while an Iris/Oculus shader pack is active" in the entries below.

## 2026-10-23 — Mirror pass runs before the entity pass and reuses Figura's render scripts
- **Decision**: reflections are drawn offscreen at `AFTER_SOLID_BLOCKS` and put on the mirrors at `AFTER_BLOCK_ENTITIES`. For Figura avatars the mirror pass uses Figura's own throttle switch (`renderScriptsSkipped`) so it reuses the last part state instead of running scripts, and overrides view culling (`culled`). Both by reflection; missing fields are skipped.
- **Reason**: the same idea the BeeHouse Figura build uses to share scripts between passes; scripts run once per frame (cheaper, physics stay stable) and the reflection lags one frame at most. Drawing before the entity pass keeps the real render as the last writer of the part matrices that scripts read.

## 2026-10-23 — Mirror reflection drawn offscreen, not with the stencil buffer
- **Decision**: the reflected players go into a bhfx-owned screen-sized `TextureTarget`, then a core shader copies it onto the mirror faces under the main depth test. Replaces the stencil + `glDepthRange` approach in the two entries below.
- **Reason**: the stencil buffer is not reliably there (other mods, drivers), and without it the old approach leaked through the world (see [`bugs.md`](bugs.md)). The offscreen way never writes the world's depth, so the worst failure is a wrong reflection, not a hole in the world. Cost: one RGBA+depth target at screen size while mirrors are loaded.

## 2026-10-23 — Mirror: full block, players only, line-of-sight per player
- **Decision** (the user's request): the mirror is a full solid block facing its placer; it reflects players only (mobs and things like spell cloud entities were noise); a player shows only if the path from the mirror point to them is clear of blocks (visual shapes, so glass does not block). Checked per player with 3 sample points, not per pixel. Mirrors are tracked in 16x16-tile clusters per plane for many-mirror builds; reach and plane count are client config.
- **Reason**: per-pixel occlusion would need the world rendered from the mirrored view, which the entity-only design avoids on purpose. Per-player rays cost at most planes × nearby players × 3 clips per tick.
- Refines the entry below (block shape and "entities" → "players").

## 2026-10-23 — bhfx ships a Mirror block; it reflects entities only
- **Decision** (the user's choices): the mirror block, item and block entity live in bhfx itself (`impl/block/`), not in BHSpells. The reflection draws only entities, mirrored across the plate's plane and clipped with the stencil buffer; the block texture is the backdrop. The stencil buffer is enabled on the main target only once a mirror loads. Off while an Iris/Oculus pack is active.
- **Reason**: entity-only is cheap (no second world render) and does not fight Embeddium's terrain renderer; a full world reflection was judged too heavy. Consequence: bhfx is no longer content-free — removing bhfx from a world that has mirrors deletes them (Forge missing-registry warning), which weakens "bhfx must stay removable" (API_PLAN section 0) for worlds that use mirrors. bhfx was already required on both sides (network protocol), so joining servers is unchanged.

## 2026-10-22 — Migrate old-default config files to Medium once
- **Decision**: `ClientConfigMigration` runs on `ModConfigEvent.Loading` of the client config. If `settingsVersion` < 1 and the file still has exactly the pre-0.3.3 defaults (HIGH, 2.3 MP, 6 ms), it sets the Medium preset; then it sets `settingsVersion = 1` and saves. Later default changes add a step and raise `ClientConfigMigration.CURRENT`.
- **Reason**: Forge never rewrites values already in a file, so without this the Medium default only reached new installs. Matching the exact old defaults leaves every hand-made choice alone, and the version key makes it run once, so a player who picks High again keeps it.

## 2026-10-22 — Default preset is Medium
- **Decision**: the config defaults are the Medium preset: `quality = MEDIUM`, `gpuBudgetMs = 4.0`, `maxFxMegapixels = 1.6`. High (6 ms, 2.3 MP) is one click away in the settings screen.
- **Reason**: on the user's machine two effects at once cost 5.6–5.9 ms GPU on HIGH (fps 117 → 106) and under 1.4 ms on LOW; a mid-range GPU should not start at the 6 ms ceiling. Existing `bhfx-client.toml` files keep their values; only new installs get the new defaults.

## 2026-10-22 — Own settings screen, presets
- **Decision**: bhfx ships its own small config screen instead of depending on Cloth Config / Configured. Presets: Low = LOW, 2 ms, 1.0 MP; Medium = MEDIUM, 4 ms, 1.6 MP; High = HIGH, 6 ms, 2.3 MP (the defaults until replaced the same day, see "Default preset is Medium"); Max = HIGH, 10 ms, 8.3 MP. Any change clears the session auto-downgrade.
- **Reason**: no extra required mod for players; the 2026-10-22 modpack run showed 2 effects at 5.9 ms GPU, so players on weaker GPUs need an easy way to lower the budget without editing the toml.

## 2026-10-17 — Notes are written in English
- **Decision**: `README.md` and everything in `docs/` are in English. Code names stay as they are, in backticks.
- **Reason**: the docs are in git (Dezameria/BHFX) and are read by more people than the original author.

## 2026-10-17 — BHSpells uses bhfx from `libs/` (jar committed)
- **Decision**: commit `bhfx-<ver>.jar` in `BHSpells/libs` (like `bhweapons`), and have bhfx copy it there after each build. No jarJar for now.
- **Reason**: friends who clone BHSpells don't have the bhfx repo or mavenLocal. No jarJar because bhfx must stay removable (API_PLAN section 0).

## 2026-10-17 — BHSpells declares the mods it uses directly as mandatory
- **Decision**: mods imported outside `compat/` (15 of them) are declared `mandatory=true`, with the lower bound set to the version BHSpells is built against. `bhfx` and `epic_fight_avalon` stay optional.
- **Reason**: before, only Forge and Minecraft were declared, so a missing mod crashed mid-game with a hard-to-read error. Declaring them makes Forge say what is missing at the loading screen. Behavior is unchanged when all mods are present.

## 2026-11-10 — Scoped stop for the test commands
- **Decision** (the user's choice of scopes): `stop` takes `fx <id>`, `near <radius>` or `name <name>` besides `<who>`; `{name:...}` at start is a label, and several effects may share it (a group to stop together) instead of a new effect replacing the old one of the same name. `near` uses the attached entity's current position, else the start point, and only the runner's dimension. A "stop the latest" scope was offered and not chosen.

## 2026-10-17 — `/bhfx test` + `/bhfx_play` replace `/bhfx_test`
- **Decision**: one grammar for both, built once in `impl/FxCommands`: `<fx> [at <pos> | attach <who>] [to <players>] [{...}]` and `stop [<who>] [finish]`. Client `/bhfx test` plays locally (`ClientFxManager.startLocal`); server `/bhfx_play fx` uses `BHFx.start` (op 2, registered in release jars too). The `fx` keyword keeps effects apart from `stop` and `pulse` in tab completion (the user's choice); ids are suggested only where the client shares the game (single player, LAN host). Params are SNBT (`impl/FxCommandArgs`); `hold`, `seconds` and `range` are command options and are **not** passed to the effect, so they can never clash with an effect's own param names. (2026-10-20: `seconds` renamed `duration` at the user's request, still in seconds; `{seconds:..}` is refused with a hint rather than passed to the effect as a param.) Stop fades out (`CANCEL`) unless `finish`. `FxParams.get` accepts an int for a float key and a whole float for an int key.
- **Reason**: one generic command plays any effect, instead of one subcommand per test effect. No new packet or protocol bump. Op 2 is enough protection for release jars. SNBT is the syntax players already know from `/summon`.
- **Client selectors**: vanilla selectors need the server (Forge's `ClientCommandSourceStack.getServer()` throws), and tags/teams/scores exist only on the server, so `/bhfx test` evaluates a subset itself (`impl/client/ClientSelector`). `/bhfx_play ... to @s` covers the rest.
- **Not done on purpose** (keep it simple): per-effect param declarations, `/bhfx info`, misspelled-name warnings, short effect ids (they would work in `test` but not in `_play`), list values in `{...}`. Revisit at the 1.0 API lock.

## 2026-10-17 — API lock for 1.0 (applied in 0.3.2)
- **Decision** (the user's choices): `FxKey` becomes server-wide (simplest for servers: no new method, stop works from any level); remove every API that no effect uses (instead of marking it Experimental); remove `FxParam.EMPOWERED`, `BHFxClient.register`, `BHFxClient.reloadShaders`; no JSON timelines and no `ctx.effek` in 1.0; version policy as in [`API_PLAN.md`](API_PLAN.md) section 0.1.
- **Reason**: a smaller API is easier to keep stable; the removed parts can return in a 1.x minor once a real effect needs them. `ScreenParams.desaturate` stayed because Heavenly Combo uses it (spell effects are not changed for the API).
- **How usage was checked**: grep of BHSpells and the bhfx test effects. A first count missed a chained `.desaturate(` call; BHSpells' compile caught it. Always compile BHSpells after removing API.

## 2026-10-17 — Spell effects and command effects are separate
- **Decision**: spell effects (`bhspells:...` started by a spell) follow the spell's own process: the spell starts, updates, cues and stops them, and their params serve the spell. They are not changed to suit commands. Effects meant for commands play on their own and use the common param names (`radius`, `color`, ...). To preview a spell effect by command, add a separate preview effect (it may reuse the spell effect's code) in the mod that owns it.
- **Reason**: spell effects depend on gameplay (how long the player holds, where each blow lands); forcing them into a command-friendly shape risks the spells and limits how they are written.
- **Where an effect lives**: in bhfx if it still makes sense without BHSpells (generic, parameterised: shockwave, ring, flash); in BHSpells if it is a spell's own look or needs Iron's/BHSpells classes. When unsure, start in BHSpells and move it to bhfx once it is reused; moving out of a library that others call is the hard direction.

## 2026-10-17 — Access to the Fx behind a local handle
- **Decision**: add `BHFxClient.fx(handle, Class)`, `BHFxClient.update(handle, params)` and `BHFxClient.isAlive(handle)` instead of changing `LocalFxHandle` (it stays a record holding only an id).
- **Reason**: effects driven by client-side cues (for example "a blow landed here") are awkward to feed through params. Heavenly Combo used to grab the instance from its factory into a static field, which was fragile. Adding methods doesn't break existing code, so it can go into 0.3.x.

## 2026-10-17 — `/bhfx_test` in dev only
- **Decision**: register it only when `!FMLEnvironment.production` (not as a config option).
- **Reason**: it's a developer test tool with no use on a real server, and it doesn't need another config value.
- Replaced by "`/bhfx test` + `/bhfx_play`" (2026-10-17).

## 2026-10-17 — BHSpells accepts bhfx `[0.3,0.4)` only
- **Decision**: lock the version range to a single minor version until 1.0 (then change it to `[1.0,2.0)`).
- **Reason**: the API can still change during 0.x. With an open range, an incompatible bhfx would load and then break mid-game. With a locked range, Forge reports it at the loading screen.
- 2026-10-17: raised to `[0.3.2,0.4)`, because the bridge now relies on server-wide `FxKey`s.

## 2026-10-17 — Check shaders at build time with glslang
- **Decision**: the `checkShaders` task assembles each shader the same way `ShaderManager` does and runs glslang on it. If glslang isn't found it warns and skips; `-PrequireGlslCheck` makes it fail instead.
- **Reason**: before, a broken shader only showed up in game. glslang isn't required, so people without it can still build. Both repos use the same script file, so shaders are assembled identically.
