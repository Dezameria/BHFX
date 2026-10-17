# Design decisions

Format: **topic — decision — reason**, with the date. If a decision changes later, add a "Replaced by …" line instead of deleting the old one.
Decisions made during the original planning are in [`API_PLAN.md`](API_PLAN.md) section 10 and in "Where the code differs from the plan" in [`../README.md`](../README.md).

## 2026-10-17 — Notes are written in English
- **Decision**: `README.md` and everything in `docs/` are in English. Code names stay as they are, in backticks.
- **Reason**: the docs are in git (Dezameria/BHFX) and are read by more people than the original author.

## 2026-10-17 — BHSpells uses bhfx from `libs/` (jar committed)
- **Decision**: commit `bhfx-<ver>.jar` in `BHSpells/libs` (like `bhweapons`), and have bhfx copy it there after each build. No jarJar for now.
- **Reason**: friends who clone BHSpells don't have the bhfx repo or mavenLocal. No jarJar because bhfx must stay removable (API_PLAN section 0).

## 2026-10-17 — BHSpells declares the mods it uses directly as mandatory
- **Decision**: mods imported outside `compat/` (15 of them) are declared `mandatory=true`, with the lower bound set to the version BHSpells is built against. `bhfx` and `epic_fight_avalon` stay optional.
- **Reason**: before, only Forge and Minecraft were declared, so a missing mod crashed mid-game with a hard-to-read error. Declaring them makes Forge say what is missing at the loading screen. Behavior is unchanged when all mods are present.

## 2026-10-17 — Access to the Fx behind a local handle
- **Decision**: add `BHFxClient.fx(handle, Class)`, `BHFxClient.update(handle, params)` and `BHFxClient.isAlive(handle)` instead of changing `LocalFxHandle` (it stays a record holding only an id).
- **Reason**: effects driven by client-side cues (for example "a blow landed here") are awkward to feed through params. Heavenly Combo used to grab the instance from its factory into a static field, which was fragile. Adding methods doesn't break existing code, so it can go into 0.3.x.

## 2026-10-17 — `/bhfx_test` in dev only
- **Decision**: register it only when `!FMLEnvironment.production` (not as a config option).
- **Reason**: it's a developer test tool with no use on a real server, and it doesn't need another config value.

## 2026-10-17 — BHSpells accepts bhfx `[0.3,0.4)` only
- **Decision**: lock the version range to a single minor version until 1.0 (then change it to `[1.0,2.0)`).
- **Reason**: the API can still change during 0.x. With an open range, an incompatible bhfx would load and then break mid-game. With a locked range, Forge reports it at the loading screen.

## 2026-10-17 — Check shaders at build time with glslang
- **Decision**: the `checkShaders` task assembles each shader the same way `ShaderManager` does and runs glslang on it. If glslang isn't found it warns and skips; `-PrequireGlslCheck` makes it fail instead.
- **Reason**: before, a broken shader only showed up in game. glslang isn't required, so people without it can still build. Both repos use the same script file, so shaders are assembled identically.
