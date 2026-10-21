# Design decisions

Format: **topic — decision — reason**, with the date. If a decision changes later, add a "Replaced by …" line instead of deleting the old one.
Decisions made during the original planning are in [`API_PLAN.md`](API_PLAN.md) section 10 and in "Where the code differs from the plan" in [`../README.md`](../README.md).

## 2026-10-22 — Own settings screen, presets
- **Decision**: bhfx ships its own small config screen instead of depending on Cloth Config / Configured. Presets: Low = LOW, 2 ms, 1.0 MP; Medium = MEDIUM, 4 ms, 1.6 MP; High = HIGH, 6 ms, 2.3 MP (the defaults); Max = HIGH, 10 ms, 8.3 MP. Any change clears the session auto-downgrade.
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
