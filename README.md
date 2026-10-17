# bhfx (BeeHouse FX)

A library mod for Forge 1.20.1 that adds post-processing effects and depth-aware world-space shaders for BHSpells and other mods to use.
It is an **extra layer**: mods that use it keep their main visuals from Effekseer/GeckoLib and still work without bhfx.

- Full API plan: [`docs/API_PLAN.md`](docs/API_PLAN.md)
- Version: **0.3.0** (in progress; the API can still change). The network protocol is `2`, which is not compatible with 0.2.

## Build / use

```bash
./gradlew.bat build                 # jar at build/libs/bhfx-0.3.0.jar, also copied to ../BHSpells/libs
./gradlew.bat publishToMavenLocal   # makes it available from mavenLocal()
./gradlew.bat runClient             # testing (/bhfx_test exists only in runs like this)
./gradlew.bat checkShaders          # check shaders with glslang (also part of build), see docs/shaders.md
```

On the BHSpells side (soft dependency, see `docs/API_PLAN.md` section 6.0, and [`docs/integrations/bhspells.md`](docs/integrations/bhspells.md) for how the jar is resolved from `libs/`):
```gradle
dependencies {
    compileOnly fg.deobf("net.deizyn:bhfx:0.3.0")
    runtimeOnly fg.deobf("net.deizyn:bhfx:0.3.0")   // dev only, to run them together
}
```

## Testing in game

| Command | Side | What it does |
| :--- | :--- | :--- |
| `/bhfx_test [radius]` | server (op 2, dev only) | Plays the test effect where you look: a warning ring on the ground → a light beam correctly hidden by mountains, plus bloom, impact frame, ripple, zoom blur, flash, screen shake, and ground that heats up and cools down |
| `/bhfx_test pulse` | server (op 2, dev only) | A ScreenPulse on yourself: shake, flash, red screen edges and colour fringing (players within 32 blocks see it too, weaker with distance) |
| `/bhfx_test tracker [range]` | server (op 2, dev only) | A ground ring that lasts 60 seconds where you look, sent only to players within range (default 16). The top-left corner shows the effect's age and your distance. Stand outside the range when you run it, then walk in: the age must keep counting (not restart from 0). |
| `/bhfx stats` | client | Cost over the last 10 seconds: CPU time while drawing and ticking, GPU time (average/max), max fx/pass count, VRAM held (counting only frames with effects) |
| `/bhfx bench [fx] [seconds]` | client | Plays the fx repeatedly where you look (on your machine only), toggling bhfx drawing off and on every 1 second. Reports average frame time, the worst 1% and the difference (default `bhfx:test`, 10 seconds) |
| `/bhfx status` | client | Status: on/off, the quality actually in use, fx/pass count, FX resolution, % of budget, GPU time, estimated VRAM |
| F3 | client | A live `[bhfx] ...` line on the left with the same information |
| `/bhfx off` / `/bhfx on` | client | Turn off/on temporarily to compare FPS (off = no fx created and the GPU is not touched) |
| `/bhfx reload` | client | Recompile shaders, clear the "disabled after a GL error" state and undo the automatic quality downgrade (F3+T also recompiles) |

## Config

- `config/bhfx-client.toml`
  - quality: `quality` (OFF/LOW/MEDIUM/HIGH; LOW has no bloom), `maxFxMegapixels`, `gpuBudgetMs` (default 6), `autoDowngrade`, `bloom`, `warmup`
  - limits: `maxActiveFx`, `maxPassesPerFrame`, `cullDistance`
  - comfort: `flashScale`, `shakeScale`, `impactFrames`
  - camera: `control` (NONE / **SUBTLE** / FULL: how much your own spells may move your camera), `fromOthers` (**false**: other players' spells never touch your camera), `fovScale`
  - compat: `offWithShaderpack` (default true: turn post-processing off while an Iris/Oculus shader pack is on)
- `config/bhfx-server.toml`: `enabled` (false = send no effects; mods that use bhfx fall back to their own visuals), `maxRange`, `maxStartsPerTick` (64), `maxActivePerCaster` (16)

## Lag protection in place

- **No effects = the GPU is not touched.** With only screen shake, post-processing doesn't run either.
- **Real GPU time is measured** with `GL_TIME_ELAPSED` queries (read back later, so the CPU never waits for the GPU). Pass resolution can drop to 25% of the pixels to stay under `gpuBudgetMs`. It drops immediately and recovers slowly, in steps of 1/8, so textures aren't reallocated every frame.
- **Automatic quality downgrade**: if the budget is still exceeded by 25% for 5 seconds straight at the lowest resolution, quality drops one level for this session (HIGH → MEDIUM → LOW), with a chat message and a log line. `/bhfx reload` restores it.
- Bloom, freeze and stepped-frame textures are allocated **only while an effect uses them**, and all of them are released to the GPU after 30 seconds without effects.
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
 │                  ScreenParams, ImpactStyle, ImpactSequence, CameraParams, CameraPose, FxSettings, BHFxClient,
 │                  LocalFxHandle, RegisterFxEvent
 └─ impl/           (internal, can change at any time)
     ├─ net/        channel bhfx:main (protocol 2) + S2CFxStart / S2CFxStartBatch / S2CFxStop / S2CFxUpdate / S2CScreenPulse
     ├─ server/     ServerFx (ids, keys, age, rate limits, tracker), ServerFxEvents (/bhfx_test)
     ├─ client/     ClientFxManager (automatic downgrade), ClientEvents (/bhfx, F3), ShaderWarmup, ShaderpackDetector,
     │              FrameStats (/bhfx stats), FxBench (/bhfx bench), CameraParamsImpl, CameraMover, test/TestFx
     └─ render/     PostRenderer, GpuTimer, ResolutionController, GlStateGuard, ShaderManager, UniformBinder
assets/bhfx/shaders/bhfx/
 ├─ include/        scene.glsl, shapes.glsl, light.glsl, noise.glsl, ground.glsl, sdf2d.glsl
 ├─ bloom_down.fsh, bloom_up.fsh
 ├─ composite.fsh   (ripple, aberration, zoom blur, bloom, grade, tone-map, impact, speed lines, afterimage, flash, grain)
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
| Test command | `/bhfx test` | `/bhfx_test` (server), separate from `/bhfx ...` (client) | Client and server commands with the same root name clash |
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
