# bhfx (BeeHouse FX)

A library mod for Forge 1.20.1 that adds post-processing effects and depth-aware world-space shaders for BHSpells and other mods to use.
It is an **extra layer**: mods that use it keep their main visuals from Effekseer/GeckoLib and still work without bhfx.

- Full API plan: [`docs/API_PLAN.md`](docs/API_PLAN.md); changes per version: [`CHANGELOG.md`](CHANGELOG.md)
- Version: **0.3.5** (API reviewed for 1.0, see `docs/API_PLAN.md` section 0.1). The network protocol is `2`, which is not compatible with 0.2.

## Build / use

```bash
./gradlew.bat build                 # jar at build/libs/bhfx-0.3.5.jar, also copied to ../BHSpells/libs
./gradlew.bat publishToMavenLocal   # makes it available from mavenLocal()
./gradlew.bat runClient             # testing
./gradlew.bat checkShaders          # check shaders with glslang (also part of build), see docs/shaders.md
```

On the BHSpells side (soft dependency, see `docs/API_PLAN.md` section 6.0, and [`docs/integrations/bhspells.md`](docs/integrations/bhspells.md) for how the jar is resolved from `libs/`):
```gradle
dependencies {
    compileOnly fg.deobf("net.deizyn:bhfx:0.3.5")
    runtimeOnly fg.deobf("net.deizyn:bhfx:0.3.5")   // dev only, to run them together
}
```

## Testing in game

| Command | Side | What it does |
| :--- | :--- | :--- |
| `/bhfx test <fx> [at <pos> \| attach <who>] [{...}]` | client | Plays a registered effect. Only you see it. Tab completes the effect id |
| `/bhfx stop [<who>] [finish]` | client | Fades out what `/bhfx test` started (all, or only the effects attached to `who`). `finish` plays the full ending instead |
| `/bhfx_play fx <fx> [at <pos> \| attach <who>] [to <players>] [{...}]` | server (op 2) | Same, but every player in range sees it, or only `to` (through `BHFx.start`). Effect ids are suggested in single player and on a LAN host; a dedicated server cannot check or suggest them (only clients know which effects are registered), so try them with `/bhfx test` first |
| `/bhfx_play stop [<who>] [finish]` | server (op 2) | Like `/bhfx stop`, for what `/bhfx_play` started |
| `/bhfx_play pulse` | server (op 2) | A ScreenPulse on yourself: shake, flash, red screen edges and colour fringing (players within 32 blocks see it too, weaker with distance) |
| `/bhfx stats` | client | Cost over the last 10 seconds: CPU time while drawing and ticking, GPU time (average/max), max fx/pass count, VRAM held (counting only frames with effects) |
| `/bhfx bench [fx] [seconds]` | client | Plays the fx repeatedly where you look (on your machine only), toggling bhfx drawing off and on every 1 second. Reports average frame time, the worst 1% and the difference (default `bhfx:test`, 10 seconds) |
| `/bhfx status` | client | Status: on/off, the quality actually in use, fx/pass count, FX resolution, % of budget, GPU time, estimated VRAM |
| F3 | client | A live `[bhfx] ...` line on the left with the same information |
| `/bhfx off` / `/bhfx on` | client | Turn off/on temporarily to compare FPS (off = no fx created and the GPU is not touched) |
| `/bhfx reload` | client | Recompile shaders, clear the "disabled after a GL error" state and undo the automatic quality downgrade (F3+T also recompiles) |

### Effect commands

- **Where**: nothing = where you look; `at <pos>` = a fixed point (`~ ~ ~`, `^ ^ ^5`, `100 64 -20`); `attach <who>` = rides on each matched entity (follows its position and look; that entity is also the caster). At most 32 entities per command. Starting the same effect on the same entity again replaces it.
- **Who** (`attach`, `stop`, `to`): `/bhfx_play` takes every vanilla selector (`@e[tag=boss]`, `@a[team=red]`, ...). `/bhfx test` runs on the client, which cannot see tags, teams or scores, so it takes `@s @p @a @r @e`, player names and UUIDs, with `type= name= distance= limit= sort=`.
- **`{...}`**: SNBT, as in `/summon`. `hold:true` (play until `stop`), `duration:N` (seconds; called `seconds` before 2026-10-20, which is now refused with a hint) and `range:N` (broadcast radius, `/bhfx_play` only, default 128) are options of the command and never reach the effect. Every other entry is an effect param: `true`/`false` → bool, `12` → int (also fills a float param), `12.5` → float, `"#FF40C0"` → colour. Effects read params by name and fall back to their own default, so a misspelled name is silently ignored.
- **Stop**: fades out (`StopMode.CANCEL`); `finish` plays the effect's full ending (`StopMode.FINISH`, e.g. a release flash).

Command-friendly effects (they play on their own and use the common param names `radius`, `color`, ...):

| Effect | Params (default) | Example |
| :--- | :--- | :--- |
| `bhfx:test` | `radius` (8) | `/bhfx test bhfx:test {radius:12}`: warning ring → light beam hidden by terrain, bloom, impact frame, ripple, zoom blur, flash, shake, cooling ground |
| `bhfx:test_tracker` | — (60 s) | `/bhfx_play fx bhfx:test_tracker {range:16}`: stand outside the range, walk in; the HUD age must keep counting, not restart from 0 |

More examples:
```
/bhfx test bhfx:test at ^ ^ ^10 {radius:16, duration:3}
/bhfx test bhfx:test attach @e[type=zombie,limit=3,sort=nearest]
/bhfx_play fx bhfx:test attach @e[tag=boss] to @s {radius:6}
/bhfx_play stop @e[tag=boss] finish
```

Spell effects (`bhspells:annihilation_charge`, `bhspells:heavenly_combo`, ...) belong to their spells: the spell starts, updates, cues and stops them, and their params are whatever the spell needs. A command can start them, but they may not look complete (Heavenly Combo waits for blow cues; Annihilation's charge ends after the 10 s default unless `{hold:true}`). To preview a spell effect, add a separate preview effect in that mod instead of changing the spell's one (see [`docs/decisions.md`](docs/decisions.md)).

## Config

- `config/bhfx-client.toml`
  - quality: `quality` (OFF/LOW/MEDIUM/HIGH; LOW has no bloom; default MEDIUM), `maxFxMegapixels` (default 1.6), `gpuBudgetMs` (default 4), `autoDowngrade`, `bloom`, `warmup`
  - limits: `maxActiveFx`, `maxPassesPerFrame`, `cullDistance`
  - comfort: `flashScale`, `shakeScale`, `impactFrames`
  - camera: `control` (NONE / **SUBTLE**: whether your own spells may punch your FOV), `fromOthers` (**false**: other players' spells never touch your camera), `fovScale`
  - mirror: `distance` (16 blocks), `maxPlanes` (6 flat mirror surfaces per frame; 0 = no reflections), `playerRange` (16 blocks), `maxReflections` (6 player drawings per frame over all mirrors); also in Mods > bhfx > Config > Mirror...
  - compat: `offWithShaderpack` (default false since 0.3.5; true = turn post-processing off while an Iris/Oculus shader pack is on)
- `config/bhfx-server.toml`: `enabled` (false = send no effects; mods that use bhfx fall back to their own visuals), `maxRange`, `maxStartsPerTick` (64), `maxActivePerCaster` (16)

## Lag protection in place

- **No effects = the GPU is not touched.** With only screen shake, post-processing doesn't run either.
- **Real GPU time is measured** with `GL_TIME_ELAPSED` queries (read back later, so the CPU never waits for the GPU). Pass resolution can drop to 25% of the pixels to stay under `gpuBudgetMs`. It drops immediately and recovers slowly, in steps of 1/8, so textures aren't reallocated every frame.
- **Automatic quality downgrade**: if the budget is still exceeded by 25% for 5 seconds straight at the lowest resolution, quality drops one level for this session (HIGH → MEDIUM → LOW), with a chat message and a log line. `/bhfx reload` restores it.
- Bloom textures are allocated **only while an effect uses them**, and all of them are released to the GPU after 30 seconds without effects.
- The server limits starts per tick and effects per caster.
- **Shader warmup** (0.3): after joining a world, every shader from every mod (`assets/*/shaders/bhfx/**.fsh`) is compiled one per frame, with a real draw to a 1x1 target (some drivers only finish compiling on the first draw). The first cast therefore doesn't stutter. The log reports the total time and the slowest shader.
- **Shader packs** (0.3): when an Iris/Oculus shader pack is on, post-processing is turned off automatically (the depth doesn't match the pack's image). Screen shake still works, and `BHFxClient.postAvailable()` returns false so mods can pick a fallback.
- **Packet batching** (0.3): starts for the same player in the same tick are sent in one batch (up to 16 per batch). A stop or update first flushes pending starts, so the order is never swapped.

## Removing bhfx

Delete `bhfx-*.jar` from **both** the server and every client (the network channel is strict, so both sides must have it or both must not). bhfx stores nothing in saves, so worlds are not affected.

## Layout

```
net.deizyn.bhfx
 ├─ api/            BHFx, FxSpawn, FxParam(s), FxHandle, FxKey, StopMode, Follow, Audience, ScreenPulse
 │   ├─ timeline/   Timeline, Ease
 │   └─ client/     Fx, FxInit, FxFactory, FxContext, FrameContext, PassList, PassOptions, Uniforms,
 │                  ScreenParams, ImpactStyle, ImpactSequence, CameraParams, FxSettings, BHFxClient,
 │                  LocalFxHandle, RegisterFxEvent
 └─ impl/           (internal, can change at any time)
     ├─ net/        channel bhfx:main (protocol 2) + S2CFxStart / S2CFxStartBatch / S2CFxStop / S2CFxUpdate / S2CScreenPulse
     ├─ server/     ServerFx (ids, keys, age, rate limits, tracker), ServerFxEvents (/bhfx_play)
     ├─ FxCommands, FxCommandArgs   shared grammar of /bhfx test and /bhfx_play, {...} → params
     ├─ client/     ClientFxManager (automatic downgrade), ClientEvents (/bhfx, F3), ShaderWarmup, ShaderpackDetector,
     │              FrameStats (/bhfx stats), FxBench (/bhfx bench), CameraParamsImpl, CameraMover, test/TestFx
     └─ render/     PostRenderer, GpuTimer, ResolutionController, GlStateGuard, ShaderManager, UniformBinder
assets/bhfx/shaders/bhfx/
 ├─ include/        scene.glsl, shapes.glsl, light.glsl, noise.glsl, ground.glsl, sdf2d.glsl
 ├─ bloom_down.fsh, bloom_up.fsh
 ├─ composite.fsh   (ripple, aberration, zoom blur, bloom, desaturate, vignette, tone-map, impact, flash)
 └─ test.fsh
```

Other mods put their shaders at `assets/<ns>/shaders/bhfx/<name>.fsh` and call them as `"<ns>:<name>"`.
Include files with `#include "bhfx:light.glsl"` (include files live in `assets/<ns>/shaders/bhfx/include/`).

## Where the code differs from the plan

| Topic | Plan | Actual | Reason |
| :--- | :--- | :--- | :--- |
| Render hook | Mixin into `LevelRenderer` | **No mixins at all**: `RenderLevelStageEvent` (matrices captured at `AFTER_SKY`/`AFTER_SOLID_BLOCKS`, drawing at `AFTER_LEVEL`) + `ViewportEvent` for screen shake | Easier to get along with other mods, no refmap needed |
| Depth | Snapshot both color and depth | Sample Minecraft's depth directly, copy only color | bhfx's FBOs don't attach depth, so there's no feedback loop; saves ~8 MB at 1080p |
| Duration | Override `duration()` | Override **`defaultDuration()`**; `duration()` is final and honors the value in `FxSpawn` | The caller can always set the duration |
| `PassOptions` | `order` + `resolution` | `order` only | Every pass uses the same FX resolution |
| Test command | `/bhfx test` | `/bhfx test` (client, only you see it) and `/bhfx_play` (server, everyone sees it) | Client and server commands with the same root name clash, so the server one has its own root |
| Textures in a pass | — | On first request, the texture loads after the frame ends, so it shows from the next frame | Keeps Minecraft's loading from changing GL state while bhfx holds it |
| Bloom | 13-tap / 9-tap | Dual filter: 5-tap down / 8-tap up (added onto the same level) | Cheaper, and different from the original's implementation |
| Tracker for followed entities | `PlayerEvent.StartTracking` | Range check from the entity's current position every 10 ticks | Same code path as effects that don't follow |
| Sending updates | Only to players who have the effect | To the whole dimension (clients without that effect drop it) | Small packets, and it covers non-persistent effects |

## Screen pulse (0.3)

A short screen effect sent from the server, with no Fx class to write. Good for adding impact when a spell hits:
```java
BHFx.pulse(level, target, 24, ScreenPulse.of().shake(0.6f).flash(0xFFE0B0, 0.5f, 6));  // everyone in range, weaker with distance
BHFx.pulse(hitPlayer, ScreenPulse.of().vignette(0x600000, 0.5f, 20));                  // one player, full strength
```
It has `shake`, `flash`, `vignette`, `aberration`, `zoomBlur` and `desaturate`, each fading out over its own number of ticks. Pulses count toward `maxStartsPerTick` and respect the player's `flashScale` / `shakeScale`.

## Development docs

Development knowledge is kept as markdown, split by category, in [`docs/`](docs/README.md):
open work and the 1.0 plan ([`roadmap.md`](docs/roadmap.md)), test status ([`testing.md`](docs/testing.md)), bugs ([`bugs.md`](docs/bugs.md)), decisions ([`decisions.md`](docs/decisions.md)), shader checking ([`shaders.md`](docs/shaders.md)), releasing ([`release.md`](docs/release.md)), use in BHSpells ([`integrations/bhspells.md`](docs/integrations/bhspells.md)) and the daily work log ([`worklog.md`](docs/worklog.md)).
