# Changelog

All notable changes to bhfx. Versions follow the policy in [`docs/API_PLAN.md`](docs/API_PLAN.md) section 0.1
(0.x may still break the API; 1.x keeps the Stable API). "Protocol" is the network protocol version; client and
server must use the same one.

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
