# Testing

✅ = passed (say how), ⏳ = not tested. Once something is tested, change its ⏳ to ✅ with the date. Don't add new lines that contradict old ones.

Where to test (since 2026-11-02): the BHSpells dev client (`BHSpells` `gradlew runClient`, a second client with `run-client2`). The Mingyue Eclipse modpack is no longer used; older entries below that name it stay as history.

## bhfx

### Scoped stop (2026-11-10, build + JUnit only)
- ✅ 2026-11-10 `gradlew build`: `FxCommandsStopTest` 6 and `FxCommandArgsTest` 7 pass. Not run in game.
- ⏳ In the BHSpells dev client:
  1. `/bhfx test bhfx:test {hold:true}` twice at different spots, plus `/bhfx test bhfx:test_flow {hold:true}`: `/bhfx stop fx bhfx:test` (tab suggests the playing ids) fades only the two `bhfx:test`, chat says "stopped 2 test effect(s) of bhfx:test".
  2. Two effects 30 blocks apart: stand by one, `/bhfx stop near 10` stops only that one.
  3. `/bhfx test bhfx:test attach @s {hold:true}`, walk 30 blocks, `/bhfx stop near 5` still stops it (measured from where you are now, not where it started).
  4. `/bhfx test bhfx:test {hold:true,name:a}` and `/bhfx test bhfx:test_flow {hold:true,name:a}` and one unnamed: `/bhfx stop name a` (suggests `a`) stops the two named ones; `{name:"two words"}` is refused.
  5. `... finish` on each scope plays the full ending; `/bhfx stop` alone still stops everything.
  6. `/bhfx_play stop near 10` / `fx` / `name` do the same for `/bhfx_play fx`.

### Scale plan phase 1: known vs built effects, even thinning (2026-11-07, build + JUnit only)
- ✅ 2026-11-07 `gradlew build`: 98 JUnit tests pass (9 new in `FxSelectionTest`; `ClientConfigMigrationTest` checks the new defaults), glslang OK. Not run in game.
- Before testing: the stale 0.3.9 deobf cache was deleted 2026-11-07 after the game closed; the phase 1 jar (sha1 `29b3625f…`) is in `BHSpells/libs/` and `~/.m2`. Just run BHSpells `runClient`. The log shows `maxActiveFx` 32 moved to 96 once (config migration 4) unless it was changed by hand.
- ⏳ In the BHSpells dev client (single player, MEDIUM):
  1. `/bhfx stress 70 32 10 bhfx:test_flow bhfx:test_vortex bhfx:test_particles` (the run that flickered 2026-11-07): no flicker while standing still or turning the camera. `/bhfx status` during it shows "particles thinned to 1 in N" (N well above 1: 70 of these want roughly 300,000 slots against 16,384); effects look thinner, evenly, and N does not jump back and forth.
  2. `/bhfx stats` during it: "70 fx known, 70 built ..., 0 hidden"; the busiest tick's constructor ms.
  3. `/bhfx stress 120 100 0 bhfx:test_flow bhfx:test_vortex bhfx:test_particles`, stand at the centre: 120 known, 96 built, ~24 hidden (the farthest). Walk 50 blocks to one side: effects ahead fade in (0.5 s), effects behind fade out; nothing near you blinks. Turn the camera around: nothing changes.
  4. `/bhfx stress 70` (all registered, one tick): busiest tick constructor ms compared with 7.55 ms before (it should be lower: only close ones and 2 ms of others in the first tick, the rest over the next ticks).
  5. Spells still work: Heavenly Combo, Annihilation Arrow charge, Conqueror Haki, Dawn Rend (local effects stay built, cues still arrive).
  6. Two clients (`run-client2`, LAN): `/bhfx_play stress 70 32 5 bhfx:test_flow bhfx:test_vortex`; both see the same effects; walking away and back brings them back at their current age.

### Scale plan phase 0: stress and counters (2026-11-07, build + JUnit only)
- ✅ 2026-11-07 `gradlew build`: 89 JUnit tests pass, 13 new (`FxStressTest` 9, `NetMeterTest` 4); glslang OK. Not run in game.
- 2026-11-07 first in-game round (user, BHSpells `runClient` 04:18–04:23, MEDIUM, single player; log `BHSpells/run/logs/latest.log`). No bhfx exception, no crash; warmup "43 shader(s) in 176 ms". Before numbers:
  - ✅ 1. `/bhfx stress 70` (25 kinds, one tick): "started 70 of 70, 32 fx running, 38 evicted, 0 failed". Busiest tick 70 starts, **7.55 ms** constructing in total; slowest constructor 5.32 ms (`bhfx:test_flow`, the first one built).
  - ✅ 2. `/bhfx stats` 12 s later: up to 26 fx and 9 passes drawn; render CPU avg 0.56 / max 5.97 ms; **GPU avg 1.75 / max 7.05 ms**; tick CPU avg 0.16 / max 3.58 ms; ~46 MB GPU memory; effect resolution 100% (63% 10 s later). "Particles: no batches" by then (only 4 long effects without particles were left).
  - ❌ 3. `/bhfx stress 70 32 10 bhfx:test_flow bhfx:test_vortex bhfx:test_particles` (run twice): 70 of 70 started over 10 s, 38 evicted; **the user saw some effects flicker**. See [`bugs.md`](bugs.md) Open 2026-11-07. No `/bhfx stats` or `/bhfx status` was taken during this run.
  - ✅ 4. `/bhfx_play stress 70`: "started 64 of 70 (6 refused by the rate limits)"; the net report: **0.55 KB/s, 0.4 packets/s, 6.4 starts/s** for 1 player, "6 by maxStartsPerTick", 64 running. That is ~86 B per start in 4 batch packets (the 2026-11-06 estimate was 150–250 B).
  - ⏳ 4b. The spread server run (`/bhfx_play stress 70 32 5 ...` with `/bhfx_play net 10`) was not run.
  - ⏳ 6. F3 frame time and heap, `/bhfx status` during the run, and "post idle" 30 s after: not in the log (`/bhfx status` was never run).
- ⏳ Still to do in the BHSpells dev client (single player):
  1. `/bhfx stress 70` (all registered effects, 32 blocks, one tick): the chat reports "started N of 70" and a counter line; with `maxActiveFx` 32, "38 evicted" is expected (oldest first, today's rule). No crash, no bhfx error in the log.
  2. `/bhfx stats` right after: the Effects, Starts and Particles lines appear; the busiest tick shows ~70 starts and its constructor ms; the biggest batches show peak / capacity.
  3. `/bhfx stress 70 32 10 bhfx:test_flow bhfx:test_vortex bhfx:test_particles`: starts spread over 10 s, about 7 per second; `/bhfx stop` ends them.
  4. `/bhfx_play net 10`, then `/bhfx_play stress 70`: the stress line reports refusals by the rate limit (64 starts per dimension per tick, so ~6 refused); the net report after 10 s shows KB/s, starts/s and "6 by maxStartsPerTick".
  5. `/bhfx_play stress 70 32 5 bhfx:test_flow bhfx:test_vortex` with `/bhfx_play net 10`: no refusals; record KB/s and starts/s here.
  6. Record the before numbers for the scale plan ([`roadmap.md`](roadmap.md)): frame time (F3 or `/bhfx stats`), GPU ms, heap from F3 before / during / 30 s after, particle slots, busiest tick constructor ms.

### 0.3.9 textures and GPU particles (2026-11-04, build + JUnit + glslang only)
- ✅ 2026-11-04 loads and runs: BHSpells `runClient` (started by Claude after the user reported "runClient fails"; not reproduced, no error text given) reached the menu with bhfx 0.3.9; warmup "28 shader(s) in 647 ms", none broken (the 6 particle materials included); `bhfx:test_particles` played 7 times; no bhfx error, GL error from bhfx or crash in `latest.log`; normal shutdown. 2026-11-04 the user reports it works, no per-item details; the items below stay open until checked one by one.
- ⏳ How it looks (items below). In the BHSpells dev client (it now runs bhfx 0.3.9 from `libs/`):
  1. `/bhfx test bhfx:test_particles at ^ ^ ^6 {duration:30}` on flat ground. Expect: rising embers (orange → dark red as they cool), dark smoke lit orange from below that spins and dissolves, wavy air above the fire (heat haze), and every 2.5 s a burst of sparks that arc down, a flipbook puff and a ring spreading on the ground. Bright parts bloom.
  2. Walk behind a wall or a block: particles behind it are hidden, and fade softly where they touch the ground (no hard cut line).
  3. Turn away: `/bhfx status` shows skipped batches; turn back, they are there at once.
  4. `/bhfx status` while it plays: "N particle slot(s)", "4 texture(s)", memory goes up by a few MB only.
  5. Textures load late at worst by a frame or two (first run after start): parts may pop in, never a stall.
  6. F3+T while it plays: nothing breaks, textures and materials reload.
  7. Log: no `bhfx particle material ... broken`, no GL error; warmup line counts the 6 materials.
  8. With a shader pack (post forced on): same look; world occlusion lines up.
  9. `/bhfx bench bhfx:test_particles 20`: record the GPU ms here.
  10. Resolution steps: with a heavy effect plus this one, F3 shows the effect size dropping at once and rising one step every ~2 s.
- ⏳ 2026-11-10 `bhspells:ink_spread` (build + glslang only): `/bhfx test bhspells:ink_spread at ~ ~ ~ {radius:48}` spreads from that spot (not following you), holds, washes away and ends; from outside only the pool is ink, walking in turns the whole view to ink, walking out eases back to colour; `{hold:true}` stays until `/bhfx stop`, then washes away; sounds at burst / front near you / passing over you / washing away; `bhspells:formation_array` ink still behaves as before.
- ⏳ 2026-11-10 `bhspells:formation_array` (build + glslang + browser preview only). `/bhfx test bhspells:formation_array attach @s`, first and third person, then from 20+ blocks: five layers open in turn and counter-rotate, words readable (light half / ink half), no seam line where the rings wrap, no shimmer far away, the rim curtain does not wash the screen white from inside. Round 9: no ink with the array any more; rings emerge slowly one by one (1.2 s apart, 1.8 s each), `{ring_delay:2, ring_fade:3}` and `{ring_delay:0}` change it. Round 7: `{radius:120}` from the middle and from outside — every sound audible at the right beat (bells per ring, ink front creeping up and washing over you), layers stay flat, motes visible round you; seam line gone (round 6). Round 4 (round 3 seen in game): rings appear whole one by one every 0.9 s rim → yin-yang with a flash, cells blink slowly, light fades over the array, not too bright; ink dyes outward from 4.6 s over 14 s with no patches, then washes away by ~29 s while the array stays, whole view ink and paper at the end, not too busy on real block textures, not black at night. Checklist: `BHSpells/docs/bhfx/formation_array.md`.
- ⏳ 2026-11-04 `bhspells:dawn_rend` (BHSpells showcase, every technique at once; build + glslang only). `/bhfx test bhspells:dawn_rend at ^ ^ ^6`, then `attach @s`: fire spreads over the ground, ribbon flames and motes into the hand, at 1.6 s five claw strokes drawn up into the sky with slash impact frames, the tears open onto a violet starry sky with burning lips and bent edges, cracks, furrows on the ground, embers falling and light shafts, then the tears seal (4.5–6 s) and the fire dies (7.5 s). Log: warmup counts the 3 new shaders, no broken material. `/bhfx bench bhspells:dawn_rend 20` GPU ms. Details: `BHSpells/docs/bhfx/dawn_rend.md`.
- 2026-11-04 first in-game round (user, BHSpells `runClient` 21:33–22:05, MEDIUM, flat world + a dirt pillar with water; log `BHSpells/run/logs/latest.log`, 3 screenshots in chat):
  - ✅ warmup "37 shader(s) in 1255 ms", none broken, no bhfx exception or bhfx GL error (the many "GL ERROR 65539: Invalid key 0" lines come with chat commands: GLFW key input, not bhfx). The grandsigil "not drawn a frame in 6–20 s" dumps mostly show the frame limiter / event wait (window in the background), not bhfx.
  - `test_flow`: ✅ 1 smoke, ✅ 3 helices (user), ✅ 5 dust and scorch on the terrain, flipbook plays. ❌ 2 embers white (user couldn't tell them apart), ❌ 4 comet tail white not red, zigzag; also the helices zigzag in the screenshots → fixed same day (`bugs.md`). ? 5 sparks colour/fall not judged. ? 6 over water the points "float" (by design they drop to the floor within 4 blocks or stay; to clarify with the user). ❌ 7 attached: moves oddly when walking → orbit fix (`bugs.md`).
  - `test_vortex`: ✅ 1, 3, 4, 5, 7; ✅ 2 (the ring is tilted, so it looks oval: expected). ❌ 6 attached: follows but swings → same orbit fix.
  - `test_particles`: ✅ no hard cut line, ✅ F3+T reload; ? off-screen skip ("1 off-screen pass(es) skipped" seen in `/bhfx status`); ⏳ shader pack.
  - ❌ all three at once: some flicker, "as if they fight to spawn". Not reproduced; 3 instances need 13,184 slots, under the MEDIUM budget 16,384, so the budget only explains it with extra instances running. Next: `/bhfx status` while it flickers.
  - Bench (camera still, 20 s, 119 fps cap so frame cost ~0): bhfx GPU `test_flow` 0.90 ms, `test_vortex` 1.01 ms, `test_particles` 0.57 ms (+0.46 ms/frame, 97 fps scene), `bhspells:conqueror_haki` 0.76 ms. `/bhfx status`: 2,936–3,920 particle slots, ~45 MB.
- 2026-11-04 second round (user, after the ramp / seed / orbit-frame fixes): ✅ ramp colours (comet tail, embers, puff); ✅ comet and helices smooth (comet head a bit square; helix start shaky); ✅ `test_flow` attached embers trail; ✅ `test_vortex` attached whirlwind follows; ✅ `test_flow` sparks; ✅ off-screen skip. ❌ `test_flow` attached helices swing and split into sheets when walking; ❌ vortex hole ring stays where it was spawned; ❌ water: points land under water and can't be seen. Fixed same day (`bugs.md`), not re-tested.
- 2026-11-04 third round (user): ✅ attached `test_flow` helices smooth, no sheets, steady start; ✅ `test_vortex` hole ring follows; ✅ dust and scorch on the water surface; ✅ comet head soft. ❌ several effects at once: some parts still vanish → pass-cap fix (`bugs.md`).
- ⏳ 2026-11-07 ribbons along curves (build + 17 `ParticleBatchTest` cases + glslang only). Clear the deobf cache first (same 0.3.9 version). `/bhfx test bhfx:test_swirl at ^ ^ ^5 {duration:30}` on flat ground; then `attach @s` and walk:
  1. Every 0.9 s a gust: three white wind arcs flat on the ground spin and spread out; seen from low down they thin to lines (not cards turned to you); from above they are full bands.
  2. Each arc's streaks run along the curve (texture stretched over the arc, not repeated per segment, no steps at joints); the arc sweeps in from its thin tail to its full head and leaves.
  3. Three tilted rings round the body at different heights and tilts, spinning; their tilt turns as they spin (precession), no flipping or cracks.
  4. Two curls above, rising and tightening, always turned to you.
  5. Attached and walking: everything stays round you; no white sheets, no NaN flashes.
  6. Other ribbons unchanged: `bhfx:test_flow` helices and comet look as before.
- ⏳ 2026-11-04 fourth round: `test_flow`, `test_vortex` and `test_particles` together, walk round them and move closer / farther: every part of all three stays, no flicker; `/bhfx status` still shows the slot count.
- ✅ 2026-11-04 (third round) attached `test_flow` + walk/sprint: helices smooth round you, no sheets, steady at their start; `test_vortex` attached: the hole ring above follows; `test_flow` by a pool: dust and scorch lie on the water surface; comet head soft.
- ✅ 2026-11-04 (second round) re-test after the ramp / ribbon seed / orbit-frame fixes: comet tail white → orange → red, embers white-yellow → red, puff orange; comet and helices smooth curves (no kinks); `attach @s` + walk: helices and whirlwind stay round you without swinging; embers and smoke trail behind.
- ⏳ 2026-11-04 turbulence, curves, ribbons, ground snapping, built-in materials (build + 13 `ParticleBatchTest` cases + glslang only). `/bhfx test bhfx:test_flow at ^ ^ ^8 {duration:30}` on hilly ground, then `attach @s`:
  1. Smoke: soft grey puffs that grow, darken and fade, drifting and curling (not straight up), neighbours moving together.
  2. Embers: start white-yellow, cool to red along the ramp, wobble, shrink and fade out.
  3. Three cyan → blue → purple ribbons winding up round the centre as helices, smooth curves with no gaps, cracks or flicker at the joints; they thin and fade at the top. From the side and from above.
  4. A comet whose tail is one continuous ribbon, yellow-white at the head to red at the tail, wobbling; smooth at 30 FPS too.
  5. Every 2.5 s on uneven ground: the dust ring and the ring of scorch marks sit on the terrain at each point's own height (on slopes, steps and slabs; none floating or buried); a flipbook puff in the centre; sparks white → orange → red.
  6. While attached and walking: the helices stay round you.
  7. Log: warmup counts the 4 built-in materials; no broken material or GL error. (2026-11-04: the 15:50 and 19:45 BHSpells `runClient` checks loaded the stale 14:18 jar from ~/.m2, not this one; see `bugs.md`. The 19:56 `runClient` loaded this jar and reached the title screen with no bhfx error; no world joined, so warmup and the driver's compile/link of the rewritten vertex shader are still unchecked.)
- ⏳ 2026-11-04 emitters, streams and orbits (build + 8 `ParticleBatchTest` cases + glslang only). `/bhfx test bhfx:test_vortex at ^ ^ ^7 {duration:30}`, then `/bhfx test bhfx:test_vortex attach @s` and walk:
  1. A whirlwind: brown dust and orange embers rise from the ground, turning anticlockwise seen from above, the funnel widening as they rise. Smooth flow, no puffs every tick.
  2. Attached: the whirlwind stays centred on you while you walk (the axis follows each frame); particles already in the air move with it.
  3. Above: every 2.5 s a tilted purple ring of streaks spirals into one point, spinning faster as it shrinks; the streaks point along their motion (not radially).
  4. Same pulse: an even ring of embers flies outwards at the feet, and sparks fountain up from 2.5 blocks and fall.
  5. A blue comet circles the whirlwind; its trail is an even line with no gaps or clumps, at high and low FPS (try a capped 30 FPS).
  6. Particles don't vanish at the screen edge when looking slightly away (cull sphere includes the turn).
  7. `/bhfx stop` mid-effect: what is in the air finishes, nothing new spawns.

- ⏳ 2026-11-02 pass bounds (`PassOptions.within`, build + `ScreenRectTest` only): give a test Fx a bounded pass (e.g. `bhfx:test` with `.within(position, radius)`). Check: (1) the effect looks the same as without bounds when the radius covers it, with no seam at the rect edge, also at reduced FX resolution; (2) a too-small radius cuts it off along a rectangle (expected); (3) turning away makes `/bhfx status` show "off-screen pass(es) skipped"; (4) standing inside the sphere still draws it; (5) two bounded passes plus one unbounded chain correctly; (6) GPU ms in `/bhfx stats` drops for a small distant effect compared with no bounds.
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
- ✅ 2026-11-02 0.3: tracker when walking into range later (`/bhfx_play fx bhfx:test_tracker {range:16}`), 2 players, shader pack detection: the user reports tested (no log or details recorded).
- ✅ 2026-11-02 2026-10-20 window-resize fix (drag-resize, maximize/restore, minimize/restore, F11): the user reports tested (no details recorded).
- ✅ 2026-11-02 0.2 in game: bloom, impact frames, ripple, resolution scaling by GPU time, automatic quality downgrade, tracker (walking into range later / logging in mid-effect / dying and respawning): the user reports tested (no details recorded).
- ⏳ 2026-10-17: `followSeen` fix (covered by JUnit `entityGoneBeforeFirstPassEndsTheEffect`; in game still open). What it checks: an effect that follows an entity must end on the server when that entity is gone within the first 10 ticks (0.5 s), instead of staying forever, being re-sent to players who walk past, and using up `maxActivePerCaster`. How: three chain command blocks fired by one button, so all run in the same tick: `summon zombie ~ ~2 ~ {Tags:["fs"]}` → `bhfx_play fx bhfx:test attach @e[tag=fs,limit=1] {hold:true}` → `kill @e[tag=fs]`. Pass: the effect disappears at once; walking 100+ blocks away and back does not bring it back; a second player who joins afterwards does not see it.
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
- ✅ 2026-10-17 effect commands, 0.3.1, single player (Mingyue Eclipse): the user reports they work; the log shows `/bhfx test`, `/bhfx_play fx`, `/bhfx_play pulse` and `/bhfx status` running. Items below that need a second player (`seen by a second player`, `to @s` seen only by you): the user reports tested 2026-11-02. Checklist:
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
- ✅ 2026-11-02 the screen fits at GUI scale 4 on 1080p: the user reports tested.
- ⏳ Still to check: a dedicated server still starts with this jar (the screen class is client-only).

### Config migration (2026-10-22)
- ✅ 2026-10-22 bhfx `runClient`: an old-default `bhfx-client.toml` moved to Medium once (`settingsVersion = 1`, log line printed).
- ⏳ Second launch keeps the values (no second migration); a file with hand-made values (e.g. HIGH + 8 ms) is left alone in game; `BHSpells/run` and `run-client2` move on their next launch.

- ✅ 2026-11-02 Mirror block: the user reports the checklist below tested (no details recorded per item):
  - [ ] All six faces reflect (walls on both sides, floor, ceiling); faces between two mirrors don't; your reflection is the right way round and moves with you in 1st and 3rd person.
  - [ ] Maze of ~20,000 mirrors: frame time with and without players in view; chunk loading has no long hitch; `maxPlanes` / `maxReflections` / `distance` change the cost.
  - [ ] Only players show (no mobs, items, spell cloud entities); spectators don't show.
  - [ ] Wall check: another player behind a wall behind you is not reflected; behind glass they are; walking out from behind the wall makes them appear within a tick.
  - [ ] A big mirror wall (e.g. 20x10) shows one continuous image; F3 frame time stays flat with no player in front; `maxPlanes`/`distance` changes apply.
  - [ ] Lighting/culling of reflected models looks right (no inside-out faces); other players' name tags (expected: mirrored text).
  - [ ] Figura avatar shows in the mirror (incl. custom parts and emissives), also for a player behind the camera; hair/physics parts sit right and don't jitter; the log says "Figura found; mirror overrides view culling, reuses render scripts" with the BeeHouse build. Epic Fight player model.
  - [ ] F5 (both views): mirror in front always shows you; floor mirror under your feet shows you; a small mirror shows a player whose arm only just reaches it.
  - [ ] Translucent things in front (water, stained glass, particles) still sort correctly.
  - [ ] Closed mirror room (floor, ceiling, walls): every surface in view reflects with the default `maxPlanes` 6; Config > Mirror... sliders change distance/surfaces/players live and are saved; an old file with 32 / 4 moves to 16 / 6 once (log has no error).
  - [ ] Mirror floor + mirror wall on it, and walls behind walls: the front reflection is whole, no cut along the floor's horizon, also when stepping back; with and without a shader pack (2026-10-24 fix).
  - [ ] Clipping: the reflection never shows outside the mirror face or through blocks/entities in front of it (the 2026-10-23 leak, see `bugs.md`); window resize keeps it lined up.
  - [ ] Fabulous graphics: a held translucent block item (stained glass) in the reflection.
  - [ ] Embeddium; Oculus without a pack; Oculus with packs (Complementary, Photon, BSL): reflection lines up with the mirror while turning and walking, TAA packs don't smear it, no flicker, block texture shows where nobody is reflected; toggling the pack mid-game.

- ✅ 2026-11-02 `bhspells:purple_flame`, the user reports tested: `/bhfx test bhspells:purple_flame` — one flame, cyan tips / violet body / pink root, colours blend without bands; floor pool only under it; embers; dies down on `/bhfx stop`; `{radius:..,height:..,color:..}` change it; `{density:0}` no embers, `{density:1}` 64; looks right from above and up close.

## BHSpells spells that use bhfx

### ⏳ `bhspells:anime_shockwave` round 4 (2026-11-10, `compileJava` + glslcheck only; replaces the round 3 list)
- [ ] `{hold:true}`: walls are smooth crescents of wind with no seams or slices, also while they fray away.
- [ ] Arcs differ: short/long, spiralling, arched, leaning differently; at several heights; steady, no pulses.
- [ ] Bright line sits towards the wall's upper edge (if low, flip `v` in `power_wave.fsh`).
- [ ] `/bhfx stop finish`: rings show the streaky band all round (no gap), expand; nothing cut off.
- [ ] `/bhfx stop`: dies down, no explosion. Look from the side, above and inside.

### bhfx 0.3.8 fog uniforms (2026-11-03, build + glslang only)
- ⏳ `FogColor` / `FogRange` hold the terrain fog. Checked through `solar_eclipse`: with the sky replaced, far terrain fades into the new sky with no pale band. Try render distance 8 and 24, rain, underwater (water fog) and lava, and Embeddium on/off.

### `bhspells:conqueror_haki` (2026-11-04, build + glslang only)
- ✅ 2026-11-04 round 1 seen in game by the user: works, but the sprites were stills, too small, and read as "electric current" (fixed in round 2).
- ✅ 2026-11-04 round 2 seen in game by the user (feedback → round 3).
- ✅ 2026-11-04 round 3 seen in game by the user (feedback → round 4).
- ✅ 2026-11-04 round 4 seen in game by the user (tone too red → round 5).
- ⏳ Round 5: the screen tone is faint grey-dark with only a trace of red; the world's own colours stay readable.
- ⏳ Round 4 items still to look at: the screen bends (ripple, zoom, distortion shell, FOV) only for a moment at the first release, then only shakes; with `{hold:true}` later bursts shake without bending; the whole screen looks muted, slightly dark red, with deeper blacks, and settles but stays tinted while the effect lasts.
- ⏳ Round 3: no dark patch on the ground; three red rings run out over the ground after the burst; bolts deep red (not pink or white); only one red impact flash, and with `{hold:true}` the later bursts have no flash or ripple.
- ⏳ Round 2 items still to look at: big bolts tear out of the body in every direction (some into the ground), each bent 2–3 times, tilted so their angle shows in depth when walking round them, each playing the 14 frames as an animation. Then the items below.
- ⏳ `/bhfx test bhspells:conqueror_haki attach @s` in third person and first person. Expect: bolts snapping on the body for 0.6 s, then a boom with impact frames, a bending shell racing out to 14 blocks, bolts popping where the front passes, the ground behind the front drained towards red-black with a red line at the front. Bolt cores must look black (cover the world), not glowing; glow red around them. Check `{hold:true}` (repeat every 3 s, `/bhfx stop` fades out) and `{radius:24, color:"#8A2BFF"}`.

### `bhspells:solar_eclipse` (2026-11-02 round 1 seen in game by the user; round 2 2026-11-03, build + glslang only)
- 2026-11-02 round 1 (user screenshot): the space sky shows, but there was a pale band along the horizon (day fog on far terrain), and the eclipse looked like it was floating. Both are addressed in round 2, which is not seen yet.
- ⏳ In BHSpells `runClient`, `/bhfx test bhspells:solar_eclipse` then `/bhfx_play fx bhspells:solar_eclipse {hold:true}`:
  - (1) Phases in order: partial → diamond ring → totality → second diamond ring → collapse with boom and impact frames → black star with red lightning.
  - (2) Totality:
    - the sky turns from twilight into open space (red/gold nebula, stars) inside the umbra, with no seam where it meets terrain or the horizon rim
    - the nebula swirls round the black star later
    - corona with no seam, red prominences
    - check the GPU ms of the sky pass while looking up
  - (3) Shadow bands show on floors only, before and after totality.
  - (4) The lens bends the scene round the black star with no edge at the bounds; nothing smears at the screen edge; it still works looking straight up.
  - (4b) Round 2:
    - The eclipse stays put in the sky while you walk.
    - The black star shows a tilted fire disk whose near half crosses the hole, and an arch over and under the hole.
    - ✅ 2026-11-03 Round 9 look, checked in game by the user in BHSpells `runClient`: OK apart from the molten drip, which is now removed. No `/bhfx stats` numbers recorded.
    - Round 9:
      - after the collapse the black sun shows a charred disc with faint lava veins, an uneven ring of fire, slow flame tongues, a red hair-line corona with long streamers (a few curved), embers, and a molten drip running down the sky
      - no shimmer in the corona's hair-lines from far away
      - at the finish the fire flares and blows out
    - Round 8:
      - The black disc has marbled liquid (crimson, red, pale gold) wound round it in a slow spiral, thick on one side, with gold filaments and star glints; no ring, no rays.
      - The finish shows no lightning: the swirl unwinds.
      - Ground bolts have a crisp core with no dots, plus a soft sheath.
    - Round 7:
      - The black disc reads clearly, not blown out, with a red corona drifting slowly.
      - Nothing pulses fast.
      - The sky is near black.
      - Ground strikes look like crawling channels, not straight zigzags.
      - At the finish the tears are smooth.
      - `/bhfx stats` with ~4 bolts marched at once.
    - Round 6:
      - after the collapse: a black disc with a white rim and fine rays, inside a red disc of light with a sharp sparkling edge
      - red cracks growing outward; red rings pulsing out
      - the sky is mostly black
      - at `/bhfx stop` (finish) or the end of the duration: lightning tears rip across the sky with a shock ring, then fade
      - no black-hole disk or lens any more
    - Round 5: no bolts round the black star. Many red bolts (about 7.5 a second) strike down out of the sky across the area. The eclipse hangs higher (60°). From totality on the sky stays red and black and flashes with the strikes. Check the sound isn't too busy and `/bhfx stats` with 32 strike slots.
    - Round 4: the whole view turns blood red under the black star.
    - Bolts come out of the star's direction.
    - Clouds are replaced by the new sky.
  - (5) A roof or hill in front hides the star and corona.
  - (6) Bolts end at the terrain; scorch shows on floors and fades in about 4 s; thunder, shake and a red flash play on each strike.
  - (7) `{duration:5}` plays every phase, compressed.
  - (8) `/bhfx stop` (FINISH) and IMMEDIATE work both during the intro and during the black star.
  - (9) Night, Nether, End.
  - (10) From radius+80 away you see only the dark footprint, with no sky darkening.
  - (11) `/bhfx stats` with `hold:true` at radius 32 and 120.
- ⏳ Multiplayer: two clients see the same strike points and times; a client that joins mid-effect is in the right phase.

### Lunebris surge (`bhspells:lunebris_surge`, 2026-11-10)
- ✅ 2026-11-10 round 1 seen in game by the user: fine apart from the body flames, the upward lashes and the doubled ground ink (fixed in round 2).
- ✅ 2026-11-10 round 2 seen in game by the user (round 3 feedback: two bursts of the same kind, flame size, smoke fade, size param).
- ✅ 2026-11-10 round 3 seen in game by the user (round 4 feedback: smoke flame, all-round ink, long spires, roar).
- ✅ 2026-11-10 round 4 seen in game by the user (round 5 feedback: lashes round the body, not the pillar; only one smoke on the body).
- ✅ 2026-11-10 round 5 seen in game by the user (round 6 feedback: the lines still odd → rays of dark light).
- ⏳ Round 8: the ink spreads round the body (no tall column), lingers and fades out; no hard edge at its bounds; readable view when the caster is inside it; GPU cost OK up close.
- ⏳ Round 7: the big rays (12 long, 1.6 wide) do not black out the view near the caster.
- ⏳ Round 6 (`compileJava` + glslcheck only): the rays shine out every way from the chest, all at once at the release, then flicker; no pop when a ray changes angle; not cut by their bounds; hidden in first person; GPU cost OK with the camera close.
- ⏳ Round 5 checks still open: the pillar is back; no smoke puffs on the body, the smoke flame reads clearly and appears with the core flame; the ground smoke ring is still there.
- ⏳ Round 4 checks still open: the smoke flame stays on the body behind the core flame without clipping; the roar waves bend the air from the mouth (facing the look), sounds play, the rumble is strong but not sickening.
- ⏳ Round 3 checks still open: exactly one burst at the release (no second lash/smoke burst, one air ring); later flares are fire only; smoke fades out instead of breaking into holes; `{scale:0.5}` and `{scale:2}` resize the flame, pillar, lashes and smoke without clipping.
- ⏳ Round 2 checks still open: one body-sized flame (no gaps or clipping on its sides when it flares, invisible in first person, follows the caster smoothly through dash/updraft); big upward lashes; yin ripples over the ground arrive with the aura's floor as one wave, bend the ground, no ink smear.
- ⏳ In BHSpells `runClient`:
  1. `/bhfx test bhspells:lunebris_surge attach @s`: lashes fly in and the chest knot grows for ~1.6 s, then the release (pillar, dome rolling to 20 blocks, lashes and smoke ring outward), then black flames and spurts on the body for 8 s. No grey sheets / white screen (NaN), no rectangle cut on the dome or pillar.
  2. Cast Lunebris: the release lands on the banner plant together with `lunebris_aura`'s ink frames; screen not too dark with both on.
  3. Cancel the windup: the gathered darkness fades without bursting.
  4. Dash / updraft: the flames trail behind the caster without breaking into sheets.
  5. Viewed from a second player and from ~40 blocks; `/bhfx stats` particle counts and GPU ms.

### Pass screen bounds in BHSpells (2026-11-02, `compileJava` only)
- ✅ 2026-11-02 the horizontal-line bug (see `bugs.md`) is gone on `lunebris_aura` after the `sceneAt` fix (user, BHSpells `runClient`, MEDIUM quality). The rest of the checklist below is still open.
- ⏳ In BHSpells `runClient`, for each of the 10 bounded effects (`/bhfx test bhspells:<id>` with defaults, then with a big `radius`/`height`): (1) nothing is cut along a rectangle: walk round it, look from above and from far away, check glow tails, embers/ash at the top and floor light at the edge; (2) turning away shows "off-screen pass(es) skipped" in `/bhfx status`; (3) `/bhfx stats` GPU ms for `flame_field` / `shadow_domain` seen from ~60 blocks is lower than before (2.3–4.8 ms at full view in the 2026-10-22 run). If one is cut off, widen its numbers in its `*Fx.passes`.

### Mirror Veil effect (`bhspells:mirror_veil`, uses `BHFxClient.drawingMirrorReflection()`)
- ✅ 2026-11-02, the user reports tested: `/effect give @s bhspells:mirror_veil 120 0 true`, stand in front of a `bhfx:mirror`. In F5 and from a second player: no body, armour, held item, name tag or shadow in the world; the mirror shows the full player. With Epic Fight on: same, and no weapon trails. With Figura: avatar shows in the mirror only. With an Oculus pack: still hidden in the world, shown in the mirror. Effect ends → visible again. `extra_invisibility` unchanged (hidden in mirrors too, shadow still shown).

### Annihilation Arrow (`bhspells:annihilation_charge`)
- ✅ 2026-10-17, tested in game by the user: the shader compiles and renders; charge to full (halo) → fire (streak); cancel or die mid-charge; game without bhfx.
- ✅ 2026-10-17 glslang passed.
- ✅ 2026-11-02 Multiplayer checklist, the user reports tested:
  1. A friend watches from about 20 blocks while you charge. Do they see the light and fire lines?
  2. A friend walks in from beyond 64 blocks during the charge, before and after it is full. They should see it continue from the current point, and the halo flash should not replay.
  3. On a server with low view distance (≤ 4), a friend starts about 50 blocks away and walks in. The effect must not collapse on its own.
  4. Fire, cancel (switch items), die, log out and change dimension mid-charge. The friend should see the right ending (fire = a streak of light; everything else = collapse).
  5. If a mob can cast this spell, interrupting it must collapse the effect, not show the release streak.

### Heavenly Combo (`bhspells:heavenly_combo`)
- ✅ 2026-10-17 glslang passed.
- ✅ 2026-11-02 (user reports), after the bridge switched to `BHFxClient.fx(...)` on 2026-10-17: punches still show the effect as before (aura, blows, slam).
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
