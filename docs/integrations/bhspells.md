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

`mods.toml`: `modId="bhfx" mandatory=false versionRange="[0.3.8,0.4)"` (0.3.2 for server-wide keys, 0.3.6 for `drawingMirrorReflection`, 0.3.7 for `PassOptions.within`, 0.3.8 for the fog uniforms `solar_eclipse` uses). Re-added 2026-11-02 on `feature/deizy-integrate-1.8.0`, which had lost it.

`build.gradle`: `compileOnly` + `runtimeOnly fg.deobf("net.deizyn:bhfx:0.3.2")`, resolved from **`BHSpells/libs/bhfx-0.3.2.jar`** (`flatDir` comes before `mavenLocal`). The jar is committed in BHSpells, so a friend who clones it can build without the bhfx repo.
- **After changing bhfx, run `./gradlew.bat build` in bhfx.** The `copyToBHSpells` task (which finalizes `build`) copies the reobfuscated jar over the one in `../BHSpells/libs` and deletes older `bhfx-*.jar` files. If you forget, BHSpells keeps using the old jar in `libs/`, even if mavenLocal is newer.
- Whenever the jar in `libs/` changes, commit it in BHSpells too. If the bhfx version changes, update the version in BHSpells' `build.gradle` to match the file name.

## Spells that use bhfx

| Spell | What it uses | Files |
| :--- | :--- | :--- |
| Annihilation Arrow | **The first user of `BHFx.start` + `FxKey` + `update` + `stop`** from the server: starts in `onServerPreCast`, sends `update(READY)` when the charge reaches 200 ticks, stops in `onServerCastComplete`. Follows the caster, `UNTIL_STOPPED`, range 64, persistent (late joiners see it continue). | `client/AnnihilationChargeFx`, `annihilation_charge.fsh`, spec: `docs/spells/fire/annihilation_arrow.md` |
| Heavenly Combo | `BHFxClient.play` plays a local Fx from the existing VFX cues (no extra packets). `BHFxClient.fx(...)` gets the Fx instance to trigger blow/slam. | `client/HeavenlyComboFx`, `heavenly_combo.fsh`, design notes: `docs/bhfx/heavenly_combo.md` |
| Shocking, Lightning Strike | `BHFx.pulse` (ScreenPulse) | In the spell class, through `BhfxCompat.pulse` |

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
