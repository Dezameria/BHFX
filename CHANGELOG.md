# Changelog

All notable changes to bhfx. Versions follow the policy in [`docs/API_PLAN.md`](docs/API_PLAN.md) section 0.1
(0.x may still break the API; 1.x keeps the Stable API). "Protocol" is the network protocol version; client and
server must use the same one.

## Unreleased
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
