# Worklog

Daily work log, newest at the top. Format:

```
## YYYY-MM-DD — short title
- repo: files touched — what changed
- result: build / tested in game / not tested
- next: what remains (move it to roadmap.md if it is longer)
```

---

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
