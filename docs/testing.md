# Testing

✅ = passed (say how), ⏳ = not tested. Once something is tested, change its ⏳ to ✅ with the date. Don't add new lines that contradict old ones.

## bhfx

- ✅ 0.1: tested in game (flat world). Effects render, no shader/GL errors in the log.
- ✅ 0.2: `gradlew build` passed. `composite`, `test`, `bloom_down` and `bloom_up` (includes inlined) compile in WebGL2 / GLSL ES 3.00.
- ✅ 0.3 (finished parts): `gradlew build` passed. All shaders, including `ground.glsl` and `sdf2d.glsl`, compile in WebGL2.
- ✅ 0.3 first in-game round (dev client, flat world + mountains):
  - Warmup compiled 4 shaders (278 ms while loading the world).
  - `/bhfx_test`, `/bhfx_test pulse` and `/bhfx bench` work (the old commands, replaced by `/bhfx_play` on 2026-10-17).
  - bhfx GPU time was 0.3–0.5 ms per frame, with no exceptions.
  - 30-second bench: +2.0 ms with screen shake / FOV punch, but **+0.09 ms** with them off (`shakeScale=0`, `control=NONE`). bhfx's own drawing is very cheap. Most of the cost is Minecraft recomputing visible chunks every frame while the camera moves.
- ✅ Dedicated server (dev): the server starts, a client connects, and the tracker re-sends effects after logging in again and after dying and respawning.
- ✅ 2026-10-17 glslang 16.6.0: all 4 bhfx shaders pass (`checkShaders`).
- ✅ 2026-10-17 Mingyue Eclipse Production Client 0.1.3 (real modpack: Oculus 1.8.0, Embeddium, Distant Horizons 3.2, ImmediatelyFast, Create, ~156 mods, 2560x1369):
  - The user saw effects (Annihilation Arrow, Heavenly Combo, `bhfx:test`) with the shader pack off, and with Complementary on plus `offWithShaderpack = false` ("post forced on").
  - `/bhfx status`: quality HIGH, post 2073x1109 (2.3 MP cap), 0.3–0.7 ms GPU, ~68 MB, 6 shaders, 4 Fx registered. Warmup 51 ms (923 ms on the cold first start).
  - Clean `/bhfx bench` runs of `bhspells:heavenly_combo`: −0.33 / −0.08 / +0.53 ms per frame (noise level), bhfx GPU ~0.57 ms. Two `bhfx:test` benches were spoiled because the shader pack was toggled mid-bench (the +12.82 ms is the pack reloading).
  - ⏳ Not yet checked: whether world-space parts (light on blocks, terrain occlusion) line up with a shader pack on, and a `/bhfx status` taken *during* a charge (all status lines were after the release, so they show 0 fx).
- ⏳ 0.3: tracker when walking into range later (`/bhfx_play fx bhfx:test_tracker {range:16}`), 2 players, shader pack detection (needs Oculus).
- ⏳ 0.2 in game: bloom, impact frames, ripple, resolution scaling by GPU time, automatic quality downgrade, tracker (walking into range later / logging in mid-effect / dying and respawning).
- ⏳ 2026-10-17: `followSeen` fix. Change dimension within 0.5 seconds of the start, then check the effect doesn't stay stuck on the server.
- ✅ 2026-10-17 0.3.1 with other shader packs (Mingyue Eclipse, Oculus 1.8.0): after a restart the user saw effects with Photon v1.3b and Complementary Unbound r5.9 + Euphoria Patches. `/bhfx status` with Photon: post forced on, 1-2 fx drawn, 0.08-0.15 ms GPU. In the session before the restart nothing showed with Photon or Noble v1.9.6 (Oculus failed to parse part of Noble: `RENDER_SCALE`); no bhfx error was logged and the cause is unknown. If it happens again, run `/bhfx status` while the effect plays.
- ✅ 2026-10-17 0.3.2 (API lock), Mingyue Eclipse with a shader pack: the user reports everything works as before; the log shows no bhfx error, `/bhfx status` normal (0.30 ms GPU). Forge rewrote `bhfx-client.toml` once on load ("is not correct. Correcting") because the allowed values of `camera.control` changed; the user's values (`control = SUBTLE`, `offWithShaderpack = false`) were kept. What was checked: `bhfx:test` (bloom, impact frame, ripple, FOV punch) and Heavenly Combo's slam (zoom blur, desaturate, vignette, bloom) look the same as before; Annihilation Arrow cancel after a dimension change still stops the charge (keys are now server-wide).
- ✅ 2026-10-17 JUnit (`gradlew test`, part of `build`), 41 tests pass (`ScreenParamsImplTest` 5: max/strongest merges, bloom radius floor, ripple cap and behind-camera; `SelectorSpecTest` 5: kinds, options, distance forms, rejected options), plus: `FxCommandArgsTest` (6), `FxParamsTest` (4), `WireTest` (7: every param type and `ScreenPulse` round-trip the wire, hostile input is rejected), `TimelineTest` (6: `Timeline`, `Ease`, `FxSpawn.isPersistent`), `TrackingTest` (8: expiry, the `followSeen` bug, enter/leave hysteresis, `forget`). The `followSeen` fix is now covered by `entityGoneBeforeFirstPassEndsTheEffect`; the in-game check below is still open.
- ✅ 2026-10-17 effect commands, 0.3.1, single player (Mingyue Eclipse): the user reports they work; the log shows `/bhfx test`, `/bhfx_play fx`, `/bhfx_play pulse` and `/bhfx status` running. Items below that need a second player (`seen by a second player`, `to @s` seen only by you) stay ⏳ until the multiplayer test. Checklist:
  - `/bhfx test bhfx:test` (look target), `... at ~ ~ ~`, `... {radius:12}` (int into a float param), `... {seconds:3}`, `... {hold:true}` then `/bhfx stop` (fades) and `/bhfx stop finish`.
  - `/bhfx test bhfx:test attach @s` follows you; `attach @e[type=zombie,limit=3]`; running it twice on the same entity replaces instead of stacking; `/bhfx stop @s` stops only yours.
  - Errors: unknown id → "not a registered effect"; `{tint:"red"}`, `{hold:true, seconds:3}`, `attach @e[tag=x]` (client) → readable messages.
  - `/bhfx_play fx` tab-suggests ids in single player; `/bhfx_play fx bhfx:test` seen by a second player; `... to @s` seen only by you; `attach @e[tag=...]`; `/bhfx_play stop`; `/bhfx_play pulse`.
  - Works in the release jar (modpack), `/bhfx_play` op only.
- ✅ 2026-10-17 BHSpells `runServer` (dedicated, dev): all mods load, including the newly declared dependencies. No client-class crash, and bhfx is detected. ✅ The world opens: a dev server started at 12:45 logged `Preparing level "world"` and `Done (7.142s)` (bhfx 0.3.0; `run/logs/2026-10-17-1.log.gz`). It was still running at 16:34 (PID 19392), which is why a second `runServer` failed on the world lock. Not re-run with 0.3.2.

## BHSpells spells that use bhfx

### Annihilation Arrow (`bhspells:annihilation_charge`)
- ✅ 2026-10-17, tested in game by the user: the shader compiles and renders; charge to full (halo) → fire (streak); cancel or die mid-charge; game without bhfx.
- ✅ 2026-10-17 glslang passed.
- ⏳ Multiplayer checklist:
  1. A friend watches from about 20 blocks while you charge. Do they see the light and fire lines?
  2. A friend walks in from beyond 64 blocks during the charge, before and after it is full. They should see it continue from the current point, and the halo flash should not replay.
  3. On a server with low view distance (≤ 4), a friend starts about 50 blocks away and walks in. The effect must not collapse on its own.
  4. Fire, cancel (switch items), die, log out and change dimension mid-charge. The friend should see the right ending (fire = a streak of light; everything else = collapse).
  5. If a mob can cast this spell, interrupting it must collapse the effect, not show the release streak.

### Heavenly Combo (`bhspells:heavenly_combo`)
- ✅ 2026-10-17 glslang passed.
- ⏳ 2026-10-17, after the bridge switched to `BHFxClient.fx(...)`: punches still show the effect as before (aura, blows, slam).
- More to check: see `../BHSpells/docs/bhfx/heavenly_combo.md`.
