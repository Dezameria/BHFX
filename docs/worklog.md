# Worklog

Daily work log, newest at the top. Format:

```
## YYYY-MM-DD — short title
- repo: files touched — what changed
- result: build / tested in game / not tested
- next: what remains (move it to roadmap.md if it is longer)
```

---

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
