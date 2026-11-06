# Design decisions

Format: **topic — decision — reason**, with the date. If a decision changes later, add a "Replaced by …" line instead of deleting the old one.
Decisions made during the original planning are in [`API_PLAN.md`](API_PLAN.md) section 10 and in "Where the code differs from the plan" in [`../README.md`](../README.md).

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
