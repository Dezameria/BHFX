# How BHSpells uses bhfx

BHSpells (`../BHSpells`) uses bhfx as a **soft dependency**: it plays normally without bhfx, and its main visuals still come from Effekseer, GeckoLib and particles (see `API_PLAN.md` sections 6.0 and 11.1).

## Bridge layout (`net.offkung.bhspells.compat.bhfx`)

| Class | Side | Role |
| :--- | :--- | :--- |
| `BhfxCompat` | common | What spell code calls. **Must not import bhfx.** Checks `CompatMods.isBhfxLoaded()` and catches `LinkageError`. |
| `BhfxLoadedBridge` | common | The only server-side class that calls `BHFx.*`. |
| `BhfxIds` | common | Effect ids, `FxKey` slots and `FxParam`s shared by server and client. It may import bhfx because it is only loaded through the bridge. |
| `BhfxPulse` | common | Describes a ScreenPulse without referencing bhfx classes. |
| `client/BhfxClientCompat` | client | Client-side counterpart of `BhfxCompat` (called through `DistExecutor` CLIENT in `CompatBootstrap`). |
| `client/BhfxClientBridge` | client | Registers the Fx classes (`RegisterFxEvent`) and plays local Fx. |
| `client/*Fx` | client | One Fx class per spell. |

Shaders live in `BHSpells/src/main/resources/assets/bhspells/shaders/bhfx/<name>.fsh`.

`mods.toml`: `modId="bhfx" mandatory=false versionRange="[0.3.10,0.4)"` (0.3.2 for server-wide keys, 0.3.6 for `drawingMirrorReflection`, 0.3.7 for `PassOptions.within`, 0.3.8 for the fog uniforms `solar_eclipse` uses, 0.3.9 for particles / textures / emitters / ribbons in `conqueror_haki` and `dawn_rend`, 0.3.10 for `ribbonNormal` / `along` / `normal` in `plasma_clash`, `anime_punch` and `anime_shockwave`; raised 2026-11-10). Re-added 2026-11-02 on `feature/deizy-integrate-1.8.0`, which had lost it.

`build.gradle`: `compileOnly` + `runtimeOnly fg.deobf("net.deizyn:bhfx:0.3.2")`, resolved from **`BHSpells/libs/bhfx-0.3.2.jar`** (`flatDir`; for the same version a ~/.m2 copy wins, see below). The jar is committed in BHSpells, so a friend who clones it can build without the bhfx repo.
- **After changing bhfx, run `./gradlew.bat build` in bhfx.** The `copyToBHSpells` task (which finalizes `build`) copies the reobfuscated jar over the one in `../BHSpells/libs` and deletes older `bhfx-*.jar` files. If you forget, BHSpells keeps using the old jar in `libs/`, even if mavenLocal is newer.
- **mavenLocal beats `libs/` for the same version** (Gradle prefers a repository with a pom over flatDir), so `copyToBHSpells` also runs `publishToMavenLocal`. It then deletes ForgeGradle's deobf copy (`~/.gradle/caches/forge_gradle/deobf_dependencies/net/deizyn/bhfx/<version>_mapped_*`), which only works while BHSpells' client is closed; if the build warns "could not clear", close the client and delete the folder. See [`bugs.md`](../bugs.md) 2026-11-04.
- Whenever the jar in `libs/` changes, commit it in BHSpells too. If the bhfx version changes, update the version in BHSpells' `build.gradle` to match the file name.

## Spells that use bhfx

| Spell | What it uses | Files |
| :--- | :--- | :--- |
| Annihilation Arrow | **The first user of `BHFx.start` + `FxKey` + `update` + `stop`** from the server: starts in `onServerPreCast`, sends `update(READY)` when the charge reaches 200 ticks, stops in `onServerCastComplete`. Follows the caster, `UNTIL_STOPPED`, range 64, persistent (late joiners see it continue). | `client/AnnihilationChargeFx`, `annihilation_charge.fsh`, spec: `docs/spells/fire/annihilation_arrow.md` |
| Heavenly Combo | `BHFxClient.play` plays a local Fx from the existing VFX cues (no extra packets). `BHFxClient.fx(...)` gets the Fx instance to trigger blow/slam. | `client/HeavenlyComboFx`, `heavenly_combo.fsh`, design notes: `docs/bhfx/heavenly_combo.md` |
| Shocking, Lightning Strike | `BHFx.pulse` (ScreenPulse) | In the spell class, through `BhfxCompat.pulse` |

## GPU particles and textures (bhfx 0.3.9, 2026-11-04)
- Available to BHSpells effects through the same `Fx` subclasses: `createParticles(material, capacity)` in the constructor, spawn in `tick`, `out.particles(batch, PassOptions.PARTICLES, u -> u.texture(...))` in `passes`. Materials go in `assets/bhspells/shaders/bhfx/particle/<name>.fsh`; textures anywhere under `assets/bhspells/textures/`. Spec: [`../API_PLAN.md`](../API_PLAN.md) 5.4. Reference effect: bhfx `ParticlesTestFx`.
- Keep vanilla/Iron's particles as the fallback when `ctx.postAvailable()` is false (bhfx particles are off with post).
- First user: the standalone `bhspells:conqueror_haki` (2026-11-04, not a spell). No spell uses them yet; BHSpells builds against 0.3.9 (`build.gradle`, `libs/bhfx-0.3.9.jar`).
- 2026-11-04 `bhspells:dawn_rend` (standalone, `DawnRendFx`): the first BHSpells user of emitters, streams, orbit / pull, turbulence, over-life curves, ribbons, `onGround` and the built-in materials, plus a reused pass (`flame_field.fsh` from another Fx) and 12 particle batches in one effect. Design notes: `BHSpells/docs/bhfx/dawn_rend.md`. Not seen in game yet.
- Lesson: a sprite with a black core and a glowing edge needs one particle that both covers and adds light — use `#define BHFX_PREMULTIPLIED` on an ALPHA batch rather than two batches.

## Lessons from real use

- 2026-11-02 (`bhspells:solar_eclipse`, not seen in game yet):
  - A screen-space lens should sample `DiffuseSampler` in a pass with a higher order. That way it bends what the earlier passes drew; `SceneSampler` is the world before any pass.
  - Use effect-wide randomness that every client must agree on, such as strike points and times, from a hash of `init().seed()` and an index, not from `random()` in sequence. This also keeps late joiners in step.
  - Sky-wide darkening needs a full-screen pass only while the camera is in the area. From outside, bound it to the footprint.
- 2026-11-03 (`solar_eclipse` round 2, not seen in game yet):
  - Replacing only depth-1 pixels leaves a band of day fog on far terrain at the horizon, stepped at chunk edges. The user saw it in round 1. Swap that fog with `fog.glsl` `fogAmount` + `refog` (bhfx 0.3.8).
  - Treat the cloud layer as sky by its height; pass `level.effects().getCloudHeight()` minus the camera Y.
  - A sky object placed a few dozen blocks up parallaxes as you walk and reads as "floating". Put it at a fixed direction far away (camera + dir × 600), like the real sun.
  - Lightning as a polyline of a few straight segments reads stiff (round 6). A channel whose sideways offset is smooth multi-layer noise along its length looks natural; march the view ray through its column, or for 2D measure the offset from the line. Expose the summed light with `1 - exp(-x)` (round 7, after grandsigil's `fx_bolt_column`).
  - Marching a thin core in steps turns it into dots, because steps skip over it (round 7 screenshot). Draw the core analytically as a polyline through the noise path, and march only the soft sheath (round 8).
  - Many small per-strike screen flashes at several strikes a second tint the whole view. Keep each flash small.
  - Don't lay a world object out in screen pixels round its projected point. It turns with the camera and stretches near the screen edges. Use each pixel's view ray against a world-fixed plane or shape instead (round 3, 2026-11-03).

- 2026-11-02: pass screen bounds. `client/FxBounds.cylinder(base, horizontal, below, above)` turns an upright cylinder into `PassOptions.within` (sphere ×1.15). Used by the 10 radial Fx (flame_field, shadow_domain, lunebris_aura, ground_beat, ground_pulse, impact_ring, scorched_ground, golden_aura, purple_flame, smoke_aura); the per-effect numbers, read from each shader, are in `BHSpells/docs/bhfx/reusable_effects.md`. Not used where the shape is not one cylinder (annihilation_charge, golden_clash, heavenly_combo, ningyao_mid). Rule: when a shader starts drawing farther out, widen its bounds too, or it is cut along a rectangle.
- 2026-10-24: Mirror Veil (`bhspells:mirror_veil`) hides a player everywhere except bhfx mirrors. The mirror draws players through the normal `EntityRenderDispatcher.render`, so render hooks can't tell a reflection from the world by themselves; `BHFxClient.drawingMirrorReflection()` gives them that (via `BhfxClientCompat`, false without bhfx).

1. **Iron's cancels a cast after a dimension change from the new level**, but `FxKey` is per dimension, so the stop has to loop over every level (`level.getServer().getAllLevels()`). See [`../bugs.md`](../bugs.md). Since bhfx 0.3.2 keys are server-wide, so `BhfxLoadedBridge.annihilationChargeEnd` now makes a single `BHFx.stop` (2026-10-17), and `annihilationChargeReady` also finds the charge after a dimension change.
2. **For an interrupted mob, Iron's calls `onServerCastComplete(..., cancelled=false)`** (`AbstractSpellCastingMob.cancelCast` → `castComplete`). Treat it as interrupted when the caster isn't a `Player` and `getCastDurationRemaining() > 0`.
3. **A late joiner can get the start before its client tracks the caster.** bhfx sends by server-side range (64 blocks), but vanilla tracks entities by view distance. So a client Fx must not end itself for "caster not found" until it has seen the caster at least once (`casterSeen`).
4. **Values sent with `update` are already merged into the params a late joiner receives.** So the Fx constructor must check `init.params()` too (for example, if `READY` is already set, show the halo without replaying its flash).
5. **Iron's doesn't always go through `onServerPreCast`/`onServerCastComplete`.** Annihilation has its own `releaseCharge`, which calls complete itself. A second stop is harmless: once the key is gone it does nothing.
6. Every method in `BhfxCompat` must check `isLoaded()` and catch `LinkageError` the same way. Spell code must never import anything from `net.deizyn.bhfx`.
7. Particle API misreadings found in `anime_shockwave` (2026-11-10): `ParticleSpawn.size(a, b)` is the half-size at birth and at death, not width × height; `Facing.FLAT` always lies in the world XZ plane (`spawn.normal` only affects RIBBON); `pull` moves a particle towards the batch orbit axis and never grows it, so an expanding ring needs `sizeOverLife` or `size(start, end)`. A texture sheet (strips, 4x4 cells) must be sliced in the material by `p.seed`, or every particle draws the whole sheet.
8. A looping effect that should explode only at the end: stream in `passes()` while running, set a flag in `onStop(FINISH)` and spawn the burst from `passes()`, and give `endTicks` enough time for the longest particle life (batches are freed when the ending runs out).
9. A RIBBON in a plane widens along `cross(tangent, point normal)`. For arcs drawn the positive way round an axis with a wall-style normal (radial, tilted), that side points down the wall, so a material wanting "up" uses `-p.corner.y` (`power_wave.fsh`, 2026-11-10).
10. Never key a RIBBON material on `p.seed` / `p.seconds`: they are flat per segment, so the strip breaks into slices (`anime_shockwave` round 4). Lay one texture by `p.along` and put per-ribbon choices in `p.data` (2026-11-10, see `../bugs.md`).
