# Changelog

All notable changes to bhfx. Versions follow the policy in [`docs/API_PLAN.md`](docs/API_PLAN.md) section 0.1
(0.x may still break the API; 1.x keeps the Stable API). "Protocol" is the network protocol version; client and
server must use the same one.

## 0.3.10 — 2026-11-10 (protocol 2)
- **Changed**: the client remembers every effect it is sent and builds only the ones it shows. Every effect within
  `guaranteedRadius` (new client setting, default 48 blocks) is always built; past that, the nearest ones up to
  `maxActiveFx` (default 32 → 96; a config file still at 32 is moved to 96 once). Choice by distance from you only,
  with a margin and a 2 s hold, so turning the camera or walking a little never swaps effects. A hidden effect fades
  out and is freed; walking closer builds it again at its current age and fades it in. Server unchanged.
- **Changed**: under load, particles are thinned evenly over all batches (one slot in N; ribbons are not thinned)
  instead of whole batches being skipped. `/bhfx status` shows "particles thinned to 1 in N".
- **Added**: passes may declare `uniform float Presence;` (0..1) and multiply their output by it to fade in and out
  with the above; particles fade on their own.
- **Added** (Experimental): ribbons laid in a plane and stretched along a curve: `FxParticles.ribbonNormal`,
  `ParticleSpawn.along`, `ParticleSpawn.normal`; material fields `p.along` (smooth along the whole ribbon) and
  `p.facing` (1 face-on, 0 edge-on). Test effect `bhfx:test_swirl`.
- **Added**: `/bhfx stress` and `/bhfx_play stress` (load tests), `/bhfx_play net` (traffic per player), and load
  counters in `/bhfx stats` (effects known / built / hidden, busiest tick, particle batches and capacity use).
- **Added**: `/bhfx stop` and `/bhfx_play stop` take a scope: `fx <id>`, `near <radius>` or `name <name>` (start
  with `{name:a}`; several effects may share a name).
- A new version number also means BHSpells' dev client loads the new jar (ForgeGradle kept the first 0.3.9 copy).

## 0.3.9 — 2026-11-04 (protocol 2)
- **Added** (Experimental): textures. `Uniforms.texture(name, FxTexture)` binds an image to a `sampler2D` in a pass
  or particle material (up to 10). `FxTexture.of(file)`, `FxTexture.packed(r, g, b, a)` (four grayscale masks in one
  texture), `.srgb()`, `.clamp()`, `.nearest()`. Decoded on a background thread, so the frame never waits (the sampler
  reads zero until it has loaded; `BHFxClient.preload` starts early, `BHFxClient.textureReady` asks). Grayscale images
  use a quarter of the memory. Mipmapped, freed after 60 s unused and on resource reload.
- **Added** (Experimental): GPU particles. `Fx.createParticles(material, capacity)` makes an `FxParticles` batch;
  `PassList.particles(batch, options, setup)` draws it among the passes (default order `PassOptions.PARTICLES` = 50).
  Materials live in `assets/<ns>/shaders/bhfx/particle/<name>.fsh` and define `vec4 material(Particle p)` (helpers in
  `bhfx:particle.glsl`: fades, sphere normals, flipbooks, erosion; `#define BHFX_PREMULTIPLIED` lets a material return
  premultiplied colour plus added light, so one particle can both darken and glow). Particle colours are display-encoded like the frame
  (`color(0xRRGGBB, a)` shows as given; unlike `Uniforms.color`). Motion is computed on the GPU (velocity, gravity,
  drag), so a particle costs no CPU after its spawn. Blends `ADD`, `ALPHA` (premultiplied) and `DISTORT` (bends the
  image behind it); facings `CAMERA`, `VELOCITY` (streaks), `FLAT` (ground decals) and `AXIS` (each particle's own
  direction and length via `ParticleSpawn.axis`, tilted round it in depth: bolts, beams); `pivot` puts the spawn point
  at the quad's base so it grows out from there. Hidden by terrain in front, soft
  where they meet it, bright ones bloom. At most 4096 / 16384 / 32768 particle slots per frame on Low / Medium / High.
- **Added** (Experimental): emitters and orbit motion. `FxEmitter` shapes (`point`, `sphere`, `shell`, `ring`,
  `disc`, `cylinder`, `cone`, `line`, `box`; `.axis(dir)` turns them, `.even()` spaces one emit evenly) give each
  particle an offset and a direction. `FxParticles.emit(shape, centre, count, speed, each)` spawns a burst from one;
  `FxParticles.stream(perSecond)` makes a `Stream` that spawns at a steady rate, spreading births in time and along
  the way the centre moved between calls, so trails stay even. Orbits: `FxParticles.orbit(centre, axis)` sets a batch
  axis (can move every frame, carrying a whirlwind along), and per particle `ParticleSpawn.orbit(rad/s, spinUp)` turns
  round it and `pull(blocks/s)` moves towards it (negative = away), all on the GPU: vortices, black holes.
- **Added** (Experimental): turbulence, over-life curves, ribbons, ground snapping and built-in materials.
  `FxParticles.turbulence(blocks, frequency[, scale])` wobbles particles smoothly (neighbours alike).
  `colorOverLife(colours...)` / `colorOverLife(ramp, from, to)`, `alphaOverLife(keys...)`, `sizeOverLife(keys...)`
  (1–4 keys) shape every material from Java. `Facing.RIBBON` draws connected strips through the points of each
  `FxParticles.ribbon()` / `Stream` (several per batch). `Shape.onGround(search[, lift])` drops emitted points onto
  the terrain. Built-in materials `FxParticles.DOT`, `STREAK`, `SPRITE` (texture + optional flipbook) and `RIBBON`,
  so simple particles need no shader.
- **Added**: test effects `bhfx:test_flow` (smoke, embers, helix ribbons, a comet ribbon, ground rings on uneven
  terrain, all on built-in materials), `bhfx:test_particles` (`/bhfx test fx bhfx:test_particles`), a bonfire that uses every
  particle and texture feature, and `bhfx:test_vortex`, a whirlwind, black hole and comet trail that use the emitters,
  streams and orbits.
- **Changed**: effect resolution now rises at most one step (1/8) every 2 s; it still drops at once. Each step resized
  the effect targets, so a spell whose cost swung could resize them back and forth mid-cast.
- Shader warmup also warms particle materials (as particle programs, not as passes).
- `checkShaders` also checks particle materials (between bhfx's particle header and footer) and `particle/*.vsh`.

## 0.3.8 — 2026-11-02 (protocol 2)
- **Added**: header uniforms `FogColor` and `FogRange` (start, end, shape) with the terrain fog of the frame, captured
  after the solid blocks, and the include `bhfx:fog.glsl` (`fogDistance`, `fogAmount`, `refog`). A pass that replaces
  the sky can now swap the fog on far terrain for its own sky, so the horizon no longer shows a band of day fog.

## 0.3.7 — 2026-11-02 (protocol 2)
- **Added** (Experimental): `PassOptions.within(center, radius)`. A pass that stays inside a world-space sphere is
  shaded only inside that sphere's screen rect, and skipped while the sphere is off screen. Passes without bounds work
  as before. `/bhfx status` shows how many passes were skipped.

## 0.3.6 — 2026-10-24 (protocol 2)
- **Added**: `BHFxClient.drawingMirrorReflection()`, true while the mirror draws players into its reflection, so a
  renderer hook can show someone only in mirrors (BHSpells' Mirror Veil effect uses it).

## 0.3.5 — 2026-10-24 (protocol 2)
- **Changed**: `compat.offWithShaderpack` defaults to false, so post effects stay on with a shader pack. An existing
  `bhfx-client.toml` that still has `true` moves to `false` once (`ClientConfigMigration` step 2, `settingsVersion` 2);
  the log says when it happens.
- **Added**: Mirror block (`bhfx:mirror`, Functional Blocks tab, 8 iron nuggets + 1 glass → 1). A full block that
  reflects on all six faces (faces touching another mirror don't), built for large mirror mazes; it reflects nearby players (Figura avatars included), not mobs, other entities
  or blocks. Nobody shows through a wall: a player is reflected only when the way from the mirror to them is clear.
  New client config section `mirror` (`distance` 16, `maxPlanes` 6, `playerRange`, `maxReflections`), also on its own settings page (Config > Mirror...); files still on the first defaults (32 / 4) move once. Works with Iris/Oculus shader packs
  (the mirror surface is drawn like an entity there, so the pack shades it). The reflection is drawn into its own screen-sized buffer (freed when no mirror is loaded) and copied onto the
  visible mirror faces; the world's depth and stencil are not touched.

## 0.3.4 — 2026-10-22 (protocol 2)
- **Changed**: an existing `bhfx-client.toml` that still has the old defaults (HIGH, 2.3 MP, 6 ms) moves to the Medium
  preset once, on first launch (`ClientConfigMigration`, new key `settingsVersion`). Files the player changed are left
  alone. The log says when it happens.
- **Fixed**: settings sliders wrote float noise to the toml (`shakeScale = 0.30000000000000004`).

## 0.3.3 — 2026-10-22 (protocol 2)
- **Added**: in-game settings screen (Mods → bhfx → Config): Low / Medium / High / Max presets, quality, GPU budget,
  resolution cap, bloom, auto-lower, shader-pack switch, shake, flashes, camera, impact frames. Changes apply live
  and are saved to `bhfx-client.toml` when the screen closes.
- **Changed**: default settings are the Medium preset (`quality = MEDIUM`, `gpuBudgetMs = 4`, `maxFxMegapixels = 1.6`).
  Existing `bhfx-client.toml` files keep their values.
- **Fixed**: effects vanished after the window was resized, maximized/restored, minimized or switched to fullscreen.
- **Changed**: the command option `seconds` is now `duration` (`{duration:10}`); the old name is refused with a hint.
- Tests for the `ScreenParams` merge rules and the `/bhfx test` selector parsing (`SelectorSpec`, pulled out of
  `ClientSelector`); 41 JUnit tests in all.

## 0.3.2 — 2026-10-17 (protocol 2)
API lock for 1.0.
- **Changed**: `FxKey` is server-wide. `BHFx.stop(level, key, mode)` and `BHFx.handle(level, key)` find the effect in
  any dimension, so stopping or updating after the owner changed dimension works.
- **Removed** (no effect used them): `ScreenParams` speedLines, contrast, grain, freeze, afterimage, stepFps, synthetic;
  `CameraParams` hideHand, roll, hideHud, override and level `FULL`; `CameraPose`; `Uniforms` points and texture;
  `FxParam.EMPOWERED`; `BHFxClient.register` (use `RegisterFxEvent`) and `BHFxClient.reloadShaders` (use `/bhfx reload`).
  The `camera.control` config now takes NONE or SUBTLE; Forge rewrites an old file once on load.
- `Audience` stores player UUIDs instead of `ServerPlayer` objects (a respawn replaces the object).
- `@ApiStatus.Internal` on hooks that are public only for bhfx itself; javadoc filled in.

## 0.3.1 — 2026-10-17 (protocol 2)
- **Added**: `/bhfx test <fx> [at <pos> | attach <who>] [{...}]` and `/bhfx stop [<who>] [finish]` (client, only you
  see it); `/bhfx_play fx ... [to <players>]`, `/bhfx_play stop`, `/bhfx_play pulse` (server, op 2). Params are SNBT;
  `hold`, `seconds` and `range` are command options. Replaces the dev-only `/bhfx_test`.
- **Changed**: `FxParams.get/has` accept an int for a float key and a whole float for an int key.
- **Fixed**: the auto-downgrade chat message always said "-1.0 ms".
- Build: JUnit tests; the shader check (`checkShaders`, glslang) is required for bhfx builds; the jar is copied into
  `../BHSpells/libs` after every build.

## 0.3.0 — 2026-10-16 to 2026-10-17 (protocol 2, not compatible with 0.2)
- **Added**: shader warmup while the world loads; Oculus shader pack detection (`offWithShaderpack`); `/bhfx stats`
  and `/bhfx bench`; several starts per packet; `CameraParams` with per-player permission; `ScreenPulse` / `BHFx.pulse`;
  `BHFxClient.fx/update/isAlive` for local effects.
- **Fixed**: GPU timer clashing with F3; bench drift; effects that follow an entity could stay on the server forever
  when the entity vanished in the first 10 ticks.

## 0.2.0 — 2026-10-16 (protocol 1)
- **Added**: bloom; the full set of screen effects (flash, vignette, aberration, zoom blur, impact frames, ripples);
  `FxKey` and `BHFx.update`; the tracker that re-sends long effects to players who arrive late; GPU budget with
  resolution scaling and automatic quality downgrade.

## 0.1.0 — 2026-10-16 (protocol 1)
- First version (package `net.offkung.bhfx`, renamed to `net.deizyn.bhfx` before 0.3.0): `Fx` effects with depth-aware world-space shader passes and a composite pass, started from the server
  (`BHFx.start/stop`) or locally (`BHFxClient.play`), `Timeline` helpers, and the `/bhfx` client commands.
