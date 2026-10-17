# Testing

✅ = passed (say how), ⏳ = not tested. Once something is tested, change its ⏳ to ✅ with the date. Don't add new lines that contradict old ones.

## bhfx

- ✅ 0.1: tested in game (flat world). Effects render, no shader/GL errors in the log.
- ✅ 0.2: `gradlew build` passed. `composite`, `test`, `bloom_down` and `bloom_up` (includes inlined) compile in WebGL2 / GLSL ES 3.00.
- ✅ 0.3 (finished parts): `gradlew build` passed. All shaders, including `ground.glsl` and `sdf2d.glsl`, compile in WebGL2.
- ✅ 0.3 first in-game round (dev client, flat world + mountains):
  - Warmup compiled 4 shaders (278 ms while loading the world).
  - `/bhfx_test`, `/bhfx_test pulse` and `/bhfx bench` work.
  - bhfx GPU time was 0.3–0.5 ms per frame, with no exceptions.
  - 30-second bench: +2.0 ms with screen shake / FOV punch, but **+0.09 ms** with them off (`shakeScale=0`, `control=NONE`). bhfx's own drawing is very cheap. Most of the cost is Minecraft recomputing visible chunks every frame while the camera moves.
- ✅ Dedicated server (dev): the server starts, a client connects, and the tracker re-sends effects after logging in again and after dying and respawning.
- ✅ 2026-10-17 glslang 16.6.0: all 4 bhfx shaders pass (`checkShaders`).
- ⏳ 0.3: tracker when walking into range later (`/bhfx_test tracker`), 2 players, `CameraParams` FULL, shader pack detection (needs Oculus).
- ⏳ 0.2 in game: bloom, impact frames, ripple, resolution scaling by GPU time, automatic quality downgrade, tracker (walking into range later / logging in mid-effect / dying and respawning).
- ⏳ 2026-10-17: `followSeen` fix. Change dimension within 0.5 seconds of the start, then check the effect doesn't stay stuck on the server.
- ⏳ 2026-10-17: `/bhfx_test` is absent from the release (production) jar.
- ✅ 2026-10-17 BHSpells `runServer` (dedicated, dev): all mods load, including the newly declared dependencies. No client-class crash, and bhfx is detected. ⏳ The world was not opened (it was locked by another dev server).

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
