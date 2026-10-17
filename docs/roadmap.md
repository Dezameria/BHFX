# Roadmap

Open work and plans. When something is done, tick it as `[x]` with the date, and record the details in [`worklog.md`](worklog.md).

## Now (0.3.x)

- [ ] Test Annihilation Arrow in multiplayer with a friend (checklist in [`testing.md`](testing.md)).
- [ ] Test Heavenly Combo after the bridge switched to `BHFxClient.fx(...)` (2026-10-17).
- [x] 2026-10-17 Permanent glslang at `ironspell_more/tools/glslang`; the build finds it on its own (see [`shaders.md`](shaders.md)).
- [x] 2026-10-17 Commit pending work (the user committed it).
- [x] 2026-10-17 Declare the mods BHSpells requires in `mods.toml` (see [`release.md`](release.md)).
- [ ] Confirm a dedicated server can open a world with the real mod set (2026-10-17: mods loaded, but the world was locked by another server).
- [x] 2026-10-17 Fix the `followSeen` leak in `ServerFx`.
- [x] 2026-10-17 Lock the bhfx version range in BHSpells `mods.toml` to `[0.3,0.4)`.
- [x] 2026-10-17 `/bhfx_test` in dev only.
- [x] 2026-10-17 API `BHFxClient.fx/update/isAlive` for local handles.
- [x] 2026-10-17 `checkShaders` with glslang.
- [x] 2026-10-17 Docs translated to English.

## 1.0 plan

| Step | Work | Done when |
| :--- | :--- | :--- |
| 1. Lock the API | Go through every class in `api.*` and mark it stable, `@ApiStatus.Experimental`, or removed. Decide on `stopEverywhere(server, key)`, `ctx.effek(...)` and Timeline loaded from JSON. Complete the javadoc. | There is a list of APIs promised not to change throughout 1.x |
| 2. Automated tests (JUnit) | `FxParams` (encode/decode, merge, size), `Timeline`/`Ease`, `ScreenPulse`. Pull the `ServerFx` tracker logic away from `ServerLevel` and test an entity vanishing in the first 10 ticks. Pull out the merge rules of `ScreenParamsImpl`. Optional: Forge GameTest. | `gradlew test` passes, and past bugs are covered by tests |
| 3. Real shader checks | Permanent glslang, and the build uses `-PrequireGlslCheck` | Every shader in both mods passes |
| 4. Test with other mods and several players | Embeddium, Oculus (pack on/off), Epic Fight in 1st/3rd person, a dedicated server with 2 players, `CameraParams` FULL | No ⏳ left in [`testing.md`](testing.md) |
| 5. Measure performance | With no Fx, under 0.1 ms difference from not having bhfx; bhfx GPU time ≤ 6 ms; first cast hitches < 50 ms; VRAM ≤ 80 MB and freed after 30 seconds (`/bhfx bench`, `/bhfx stats`) | Every criterion passes on a mid-range machine at 1080p |
| 6. Release 1.0 | Version 1.0.0, changelog, BHSpells accepts `[1.0,2.0)` | — |

Suggested order: 1 → 2 → 3 can be done locally right away. 4–5 need time in game and a friend; do them together with the multiplayer test.

## Waiting on the user

- Where to distribute bhfx: only your own modpack, or Modrinth/CurseForge.
- License: keep All Rights Reserved if other mods are going to import it?

## Backlog (not scheduled)

- Shader pack mode: while a pack is on, keep screen-only effects (flash, vignette, ripple, impact, bloom, pulses) and skip only depth-based world passes, instead of the all-or-nothing `offWithShaderpack`. Only needed if world-space parts look wrong with a pack (2026-10-17: the user reports effects visible with Complementary forced on).

- `AaaParticlesBridge` (`ctx.effek(...)`): when there is a real use case.
- BHSpells spells that would suit bhfx next: Indigo Lightning, Fiery Dance, Venomous Blossomfall (LONG/CONTINUOUS with all the hooks), and long-lasting zone spells (to test the tracker with long effects).
