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
- ⏳ 2026-10-20 window-resize fix: with an effect running, drag-resize, maximize/restore, minimize/restore and toggle fullscreen (F11). Effects must keep showing, at the right size, with no "post effects turned off" message.
- ⏳ 0.2 in game: bloom, impact frames, ripple, resolution scaling by GPU time, automatic quality downgrade, tracker (walking into range later / logging in mid-effect / dying and respawning).
- ⏳ 2026-10-17: `followSeen` fix. Change dimension within 0.5 seconds of the start, then check the effect doesn't stay stuck on the server.
- ✅ 2026-10-17 0.3.1 with other shader packs (Mingyue Eclipse, Oculus 1.8.0): after a restart the user saw effects with Photon v1.3b and Complementary Unbound r5.9 + Euphoria Patches. `/bhfx status` with Photon: post forced on, 1-2 fx drawn, 0.08-0.15 ms GPU. In the session before the restart nothing showed with Photon or Noble v1.9.6 (Oculus failed to parse part of Noble: `RENDER_SCALE`); no bhfx error was logged and the cause is unknown. If it happens again, run `/bhfx status` while the effect plays.
- ✅ 2026-10-17 0.3.2 (API lock), Mingyue Eclipse with a shader pack: the user reports everything works as before; the log shows no bhfx error, `/bhfx status` normal (0.30 ms GPU). Forge rewrote `bhfx-client.toml` once on load ("is not correct. Correcting") because the allowed values of `camera.control` changed; the user's values (`control = SUBTLE`, `offWithShaderpack = false`) were kept. What was checked: `bhfx:test` (bloom, impact frame, ripple, FOV punch) and Heavenly Combo's slam (zoom blur, desaturate, vignette, bloom) look the same as before; Annihilation Arrow cancel after a dimension change still stops the charge (keys are now server-wide).
- ✅ 2026-10-21 1.0 step 4, Embeddium + Oculus: the user reports they checked both and bhfx works. No log or version numbers recorded for this check; the shader-pack world-space alignment item above and "shader pack detection" in the 0.3 line stay ⏳ until checked on their own.
- 2026-10-17 1.0 step 5, performance (BHSpells dev client `runClient`, bhfx 0.3.2, 854x480 window, no shader pack; log `BHSpells/run/logs/latest.log` 16:45-16:49):
  - ✅ No effects = no cost: `/bhfx stats` "no frames with effects (bhfx did no work)", `/bhfx status` "post idle (no GPU memory held)" with bhfx on.
  - ✅ Cost of `bhfx:test`: two benches +0.03 and -0.13 ms per frame (noise), bhfx GPU 0.33-0.37 ms (budget 6 ms). Earlier modpack run at 2560x1369: 0.3-0.7 ms.
  - ✅ VRAM: ~11 MB while `bhfx:test` played; "no GPU memory held" about 30 s after the bench ended (16:48:55 → 16:49:26).
  - ⚠ The `bhspells:heavenly_combo` bench (-0.05 ms, no GPU figure) measured nothing: started by a command it gets no blow cues and draws nothing.
  - ✅ First cast: the user saw no stutter on the first cast after joining (by eye, no frame-time number). Shader warmup had compiled all 6 shaders while loading (256 ms, slowest 127 ms), so the cast compiled nothing.
  - ⏳ The criteria ask for a mid-range machine at 1080p; this run was 854x480 on the user's machine.
- 2026-10-22 1.0 step 5, second run (Mingyue Eclipse Production Client 0.1.3, bhfx 0.3.2, BHSpells 1.7.1, NVIDIA, **2560x1369, not 1080p**, shader pack on with post forced on, camera config left at `control = SUBTLE`, `shakeScale = 1.0`; logs `2026-10-21-4.log.gz` and `latest.log` of 2026-10-22):
  - ✅ `bhfx:test` 20 s bench: +0.07 ms per frame (8.53 → 8.61 ms, ~117 fps), bhfx GPU 0.29 ms.
  - ✅ VRAM ~68 MB (post 2073x1109) ≤ 80 MB; "post idle (no GPU memory held)" 23–40 s after effects stopped (00:02:46 stop → 00:03:26 idle; 00:00:02 → 00:00:55 after the bench).
  - ✅ GPU per effect, `/bhfx status` while playing via `/bhfx test`: `flame_field` 2.27–2.41 ms, `shadow_domain` 3.45–4.82 ms (budget 6 ms; `shadow_domain` is the closest to it).
  - ⚠ Warmup on the cold start compiled 14 shaders in 1154 ms (slowest 632 ms) while joining the world (23:58:31), not at the first cast. No first-cast frame time was measured.
  - ⚠ `bhspells:heavenly_combo` bench again measured nothing (+0.03 ms, no GPU figure): from a command it gets no cues. Still needs `/bhfx stats` right after a real cast.
  - ✅ (barely) `shadow_domain` + `flame_field` at the same time (00:08:49–00:09:10, 2 fx, 2 passes): **5.32–5.93 ms** GPU, just under the 6 ms budget. Two of these together on a mid-range GPU at 1080p may go over; no auto-downgrade fired here.
  - ⏳ Still open for step 5: a 1080p run on a mid-range machine; Heavenly Combo while really casting (no `/bhfx stats` line in the log up to 00:09:10).
- ✅ 2026-10-17 JUnit (`gradlew test`, part of `build`), 41 tests pass (`ScreenParamsImplTest` 5: max/strongest merges, bloom radius floor, ripple cap and behind-camera; `SelectorSpecTest` 5: kinds, options, distance forms, rejected options), plus: `FxCommandArgsTest` (6), `FxParamsTest` (4), `WireTest` (7: every param type and `ScreenPulse` round-trip the wire, hostile input is rejected), `TimelineTest` (6: `Timeline`, `Ease`, `FxSpawn.isPersistent`), `TrackingTest` (8: expiry, the `followSeen` bug, enter/leave hysteresis, `forget`). The `followSeen` fix is now covered by `entityGoneBeforeFirstPassEndsTheEffect`; the in-game check below is still open.
- ✅ 2026-10-17 effect commands, 0.3.1, single player (Mingyue Eclipse): the user reports they work; the log shows `/bhfx test`, `/bhfx_play fx`, `/bhfx_play pulse` and `/bhfx status` running. Items below that need a second player (`seen by a second player`, `to @s` seen only by you) stay ⏳ until the multiplayer test. Checklist:
  - `/bhfx test bhfx:test` (look target), `... at ~ ~ ~`, `... {radius:12}` (int into a float param), `... {seconds:3}` (now `{duration:3}`, renamed 2026-10-20), `... {hold:true}` then `/bhfx stop` (fades) and `/bhfx stop finish`.
  - `/bhfx test bhfx:test attach @s` follows you; `attach @e[type=zombie,limit=3]`; running it twice on the same entity replaces instead of stacking; `/bhfx stop @s` stops only yours.
  - Errors: unknown id → "not a registered effect"; `{tint:"red"}`, `{hold:true, seconds:3}`, `attach @e[tag=x]` (client) → readable messages.
  - `/bhfx_play fx` tab-suggests ids in single player; `/bhfx_play fx bhfx:test` seen by a second player; `... to @s` seen only by you; `attach @e[tag=...]`; `/bhfx_play stop`; `/bhfx_play pulse`.
  - Works in the release jar (modpack), `/bhfx_play` op only.
- ✅ 2026-10-17 BHSpells `runServer` (dedicated, dev): all mods load, including the newly declared dependencies. No client-class crash, and bhfx is detected. ✅ The world opens: a dev server started at 12:45 logged `Preparing level "world"` and `Done (7.142s)` (bhfx 0.3.0; `run/logs/2026-10-17-1.log.gz`). It was still running at 16:34 (PID 19392), which is why a second `runServer` failed on the world lock. Not re-run with 0.3.2.

### Settings screen (2026-10-22)
- ✅ 2026-10-22 Mingyue Eclipse (jar copied 00:23, log `latest.log` 00:25–00:33): the user opened Mods → bhfx → Config and changed settings; `bhfx-client.toml` afterwards holds the Medium preset (`MEDIUM`, 4.0 ms, 1.6 MP) and `flashScale = 0.5`, so changes save. Quality changes applied live (`/bhfx stats` shows LOW, then HIGH, in the same session). No bhfx error in the log.
- ✅ Same run, `shadow_domain` + `flame_field` together (`/bhfx stats`, 2 fx, 2 passes):
  - LOW: GPU avg 0.82–1.10 ms, max 1.35–2.49 ms, ~20 MB, ~117 fps (1168–1181 frames / 10 s).
  - HIGH: GPU avg 5.63 ms, max 6.94 ms, ~61–68 MB, ~106 fps (1062 frames / 10 s). Over the 6 ms budget, so the resolution controller dropped to 88%: first in-game evidence that resolution scaling by GPU time works.
  - ⚠ One render-CPU spike of 18.95 ms right after switching to HIGH (likely targets and bloom being rebuilt); not seen again.
- ⏳ Still to check: the screen fits at GUI scale 4 on 1080p; a dedicated server still starts with this jar (the screen class is client-only).

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

### Reusable `bhspells:impact` / `impact_ring` / `impact_ripple` / `scorched_ground`
- ✅ 2026-10-20 BHSpells `compileJava`; glslang 16.6.0 by hand (the `feature/integrate-deizy` branch has no `checkShaders`).
- ⏳ In game:
  1. `/bhfx test bhspells:impact`: sound, shake, no particles, flash, 4 impact frames, aberration, zoom blur, FOV punch; no ground ring, no ripple.
  2. `/bhfx test bhspells:impact_ring {color:"#40C0FF", radius:8}`: blue ring on the ground only, stops at about 8 blocks; nothing on the screen.
  3. `/bhfx test bhspells:impact_ripple {radius:8}`: screen ripple only, spreading to about 8 blocks.
  4. All three at the same spot together look like the impact of `bhfx:test`, ring and ripple moving together.
  5. Watched from ~60 blocks away: no flash, impact frame or ripple; the ground ring still visible.
  6. `/bhfx test bhspells:scorched_ground {color:"#7A3CFF", radius:5}`: purple glow about 5 blocks across, cools over 6 s, scorch fades at the end. `{hold:true}` stays hot; `/bhfx stop` cools it over 1.5 s.

### `bhspells:flame_field`
- ✅ 2026-10-20 BHSpells `compileJava`; glslang 16.6.0 by hand; WebGL preview (`BHSpells/docs/bhfx/tools/preview_flame_field.html`) looked right from the edge, far, above and inside after tuning.
- ⏳ In game:
  1. `/bhfx test bhspells:flame_field`: the area spreads out in about a second; golden fires big and small spring up across ~24 blocks with plain ground between them, nothing painted on the ground, no circle, ragged edge; burns 10 s, dies down over 1.5 s.
  2. Fires sit on the ground on uneven terrain (they are drawn from the effect's y; hills inside the domain may cover or float them).
  3. Terrain in front hides fires and embers. Each fire has a pink pool of light on the floor round its root (pale gold at the heart), none over the gaps and none on block sides.
  4. From high above: no field of flat paper triangles.
  5. `/bhfx stats` standing inside and from ~40 blocks. Budget 6 ms; if heavy, lower the small layer's cell count (44) or `EMBERS` (64) first.
  6. Mostly Lightning Strike pink (`#FF66CC`, deeper `#E61A99` at the tips) melting smoothly (no bands) into a pale, almost white gold in the lower body and core of each fire, still visible (mix varies per fire); `{color2:"#B040FF"}` changes the pink. `{radius:40, height:8}` (fires reach the far side) and `{color:"#40C0FF"}` change size and colour as expected; `{height:3}` makes fires narrower too, not just shorter; `{density:0.2}` / `{density:1}` fewer / more fires.
  7. With a shader pack on.

### 2026-10-20 first in-game run of `flame_field` + `shadow_domain` (BHSpells dev client, log `BHSpells/run/logs/latest.log` 15:36–15:38)
- The user felt heavy GPU load. Both ran together via `/bhfx_play` for about a minute (flame_field from 15:36:26, shadow_domain from 15:37:26, both stopped 15:38:25).
- No per-frame numbers: `/bhfx stats` was run only after they stopped ("no frames with effects"). It did show `effect resolution 75% of budget`: the resolution controller had cut the effect pixel share to 0.75, so bhfx GPU time was roughly 6–8 ms per frame at full resolution (budget 6 ms), versus 0.3–0.7 ms for `bhfx:test`. Not at the 25% floor, so no auto-downgrade.
- ✅ 2026-10-20 15:41 `/bhfx bench` (radius 24, before LOD): flame_field 2.80 ms GPU (frame +0.03 ms at a 120 fps cap), shadow_domain 4.69 ms GPU (+0.17 ms); `/bhfx stats` during shadow_domain: avg 4.62, max 5.09 ms.
- 2026-10-20 15:49–15:50, the user's real commands (`height:20, density:0.3, radius:120`), `/bhfx stats` 10 s windows: shadow_domain alone (about 5 s of it in the window) avg 2.81 ms, max 5.37 ms; then flame_field added (about 3 s of both) avg 3.52 ms, **max 12.21 ms**. ⚠ These ran the **old shaders** (before the LOD and height scaling): the dev client was started 15:35 and `build/resources` dates from 15:35:30, while the shader edits were made after; runClient only copies resources at launch.
- ✅ 2026-10-20 15:54–15:56, after the distance LOD and the height-scaled aurora (client restarted 15:51, `build/resources` 15:51:24 has the new shaders), the user's real commands (`height:20, density:0.3, radius:120`), full 10 s `/bhfx stats` windows, effect resolution stayed 100%:
  - shadow_domain alone: avg 3.46 ms, max 8.96 ms (one spike; avg is under budget)
  - both together: avg 4.68 ms, **max 5.97 ms** (was max 12.21 ms with the old shaders)
  - flame_field alone: avg 2.57 ms, max 4.93 ms
- 2026-10-20 15:57–15:59 stress: the user stacked 8 `shadow_domain` and 8 `flame_field` (real params) round the same area (16 effects, 2 more refused by the server's per-caster limit of 16). GPU avg 7.75 ms, max 16.09 ms; after 5 s over budget at the lowest resolution bhfx auto-downgraded to MEDIUM ("effects used 9.5 ms ... quality lowered to MEDIUM"); effect resolution then 25–38%, GPU avg ~5.9 ms, max ~8 ms; one render-CPU spike of 34 ms. Expected: every domain is its own full-screen pass, so cost adds up per copy. Idea (not done): draw only the nearest 1–2 of each kind, since overlapping copies look the same.
  - ⏳ still to check by eye: aurora curtains at height 20 look smooth, not flickering dots; where the 8.96 ms shadow_domain spike comes from (likely looking up into the aurora or the opening flash).
- The `GL ERROR 65539: Invalid key 0` lines in that log come from GLFW (a key with keycode 0 pressed); not bhfx.

### `bhspells:heavenly_combo` from a command (2026-10-21)
- ✅ 2026-10-21 BHSpells `compileJava`.
- ⏳ In game: `/bhfx_play fx bhspells:heavenly_combo attach @s {hold:true}` keeps the aura until stopped; `{duration:10}` ends after 10 s; casting Heavenly Combo still ends its effect ~2.5 s after the last hit.

### `bhspells:shadow_domain`
- ✅ 2026-10-20 BHSpells `compileJava`; glslang 16.6.0 by hand; WebGL preview (`BHSpells/docs/bhfx/tools/preview_domains.html`), alone and with `flame_field`.
- ⏳ In game:
  1. `/bhfx test bhspells:shadow_domain`: dark flash + ink frame + squeezed FOV on open; green aurora curtains (bright lower edge ~6 blocks up, vertical rays, fading upward) wind across ~24 blocks over a dark green mist; no dome, no ring on the ground, no white blow-out when looking along a curtain.
  2. Every 1.4 s a pressure wave: curtains flare as it passes, a denser band moves out through the mist, no screen ripple, a shove when it passes you, low wing-beat sound.
  3. Inside: the scene greys and darkens (desaturate + green vignette); outside it does not.
  4. Both at once (`/bhfx test bhspells:flame_field` and `/bhfx test bhspells:shadow_domain at ~30 ~ ~`): gold below vs dark green above read as two opposing domains; neither washes the other out.
  5. `/bhfx stats` inside each and inside both. Budget 6 ms; if heavy, lower `STEPS` (28) first.
  6. `{height:12}`, `{density:0.8}`, `{color:"#B040FF"}`.
  7. (2026-10-21, `VEIL`) at `{density:1}` the aurora curtains stay clearly visible through the mist while the scene behind is still dimmed. After the same-day retune: many long, unbroken curtains dominate over a thin mist; no flickering/aliasing of the closer curtains at `height:20, radius:120`.

### `bhspells:ground_beat`
- ✅ 2026-10-20 BHSpells `compileJava`; glslang 16.6.0 by hand; WebGL preview (`BHSpells/docs/bhfx/tools/preview_beat_aura.html`).
- ⏳ In game:
  1. `/bhfx test bhspells:ground_beat at ~ ~ ~`: a crest rolls out every 0.75 s; the ground looks like it rises and falls; soft pink light on crests, a pink glow at the centre on each beat.
  2. Block sides, walls, trees and mobs are not bent or tinted, only floors near the effect's height.
  3. `{beat:0.4}`, `{amplitude:0.8}`, `{radius:20}`, `{color:"#80C0FF"}`.
  4. With a shader pack on (it relies on the depth buffer for the floor normal).

### `bhspells:ground_pulse`
- ✅ 2026-10-20 BHSpells `compileJava`; glslang 16.6.0 by hand; WebGL preview (`BHSpells/docs/bhfx/tools/preview_ground_pulse.html`).
- ⏳ In game:
  1. `/bhfx test bhspells:ground_pulse at ~ ~ ~`: the floor over ~12 blocks turns pink (texture still visible) with brighter patches drifting, no white lines; the whole area gently brightens every 0.75 s and never fades out between beats. No streaks, nothing bent, no circle at the edge.
  2. It stays on the floor on slopes and steps (within ±6 blocks of the effect's height); block sides, walls, mobs are not coloured.
  3. Works on different ground (grass, sand, stone): stays pink, not brown.
  4. `{beat:0.4}`, `{density:0.9}`, `{color:"#66CCFF"}`, `{radius:24}`.

### `bhspells:smoke_aura`
- ✅ 2026-10-20 BHSpells `compileJava`; glslang 16.6.0 by hand; WebGL preview.
- ⏳ In game:
  1. `/bhfx test bhspells:smoke_aura attach @s {hold:true}`: in third person, green smoke swirls up round you; in first person it stays at the edges of the view, never a green wall; `/bhfx stop` fades it.
  2. Attached to a mob that moves: the smoke follows.
  3. `{height:3.5, radius:1.4}`, `{density:0.9}`, `{color:"#B040FF"}`.
