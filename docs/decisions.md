# Design decisions

Format: **topic — decision — reason**, with the date. If a decision changes later, add a "Replaced by …" line instead of deleting the old one.
Decisions made during the original planning are in [`API_PLAN.md`](API_PLAN.md) section 10 and in "Where the code differs from the plan" in [`../README.md`](../README.md).

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
