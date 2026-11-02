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

`mods.toml`: `modId="bhfx" mandatory=false versionRange="[0.3.7,0.4)"` (0.3.2 for server-wide keys, 0.3.6 for `drawingMirrorReflection`, 0.3.7 for `PassOptions.within`). Re-added 2026-11-02 on `feature/deizy-integrate-1.8.0`, which had lost it.

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

- 2026-11-02: pass screen bounds. `client/FxBounds.cylinder(base, horizontal, below, above)` turns an upright cylinder into `PassOptions.within` (sphere ×1.15). Used by the 10 radial Fx (flame_field, shadow_domain, lunebris_aura, ground_beat, ground_pulse, impact_ring, scorched_ground, golden_aura, purple_flame, smoke_aura); the per-effect numbers, read from each shader, are in `BHSpells/docs/bhfx/reusable_effects.md`. Not used where the shape is not one cylinder (annihilation_charge, golden_clash, heavenly_combo, ningyao_mid). Rule: when a shader starts drawing farther out, widen its bounds too, or it is cut along a rectangle.
- 2026-10-24: Mirror Veil (`bhspells:mirror_veil`) hides a player everywhere except bhfx mirrors. The mirror draws players through the normal `EntityRenderDispatcher.render`, so render hooks can't tell a reflection from the world by themselves; `BHFxClient.drawingMirrorReflection()` gives them that (via `BhfxClientCompat`, false without bhfx).

1. **Iron's cancels a cast after a dimension change from the new level**, but `FxKey` is per dimension, so the stop has to loop over every level (`level.getServer().getAllLevels()`). See [`../bugs.md`](../bugs.md). Since bhfx 0.3.2 keys are server-wide, so `BhfxLoadedBridge.annihilationChargeEnd` now makes a single `BHFx.stop` (2026-10-17), and `annihilationChargeReady` also finds the charge after a dimension change.
2. **For an interrupted mob, Iron's calls `onServerCastComplete(..., cancelled=false)`** (`AbstractSpellCastingMob.cancelCast` → `castComplete`). Treat it as interrupted when the caster isn't a `Player` and `getCastDurationRemaining() > 0`.
3. **A late joiner can get the start before its client tracks the caster.** bhfx sends by server-side range (64 blocks), but vanilla tracks entities by view distance. So a client Fx must not end itself for "caster not found" until it has seen the caster at least once (`casterSeen`).
4. **Values sent with `update` are already merged into the params a late joiner receives.** So the Fx constructor must check `init.params()` too (for example, if `READY` is already set, show the halo without replaying its flash).
5. **Iron's doesn't always go through `onServerPreCast`/`onServerCastComplete`.** Annihilation has its own `releaseCharge`, which calls complete itself. A second stop is harmless: once the key is gone it does nothing.
6. Every method in `BhfxCompat` must check `isLoaded()` and catch `LinkageError` the same way. Spell code must never import anything from `net.deizyn.bhfx`.
