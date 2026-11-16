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

`mods.toml`: `modId="bhfx" mandatory=false versionRange="[0.3.11,0.4)"` (0.3.2 for server-wide keys, 0.3.6 for `drawingMirrorReflection`, 0.3.7 for `PassOptions.within`, 0.3.8 for the fog uniforms `solar_eclipse` uses, 0.3.9 for particles / textures / emitters / ribbons in `conqueror_haki` and `dawn_rend`, 0.3.10 for `ribbonNormal` / `along` / `normal` in `plasma_clash`, `anime_punch` and `anime_shockwave`; 0.3.11 for `TUMBLE` in `sky_shatter`; raised 2026-11-12). Re-added 2026-11-02 on `feature/deizy-integrate-1.8.0`, which had lost it.

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
- 2026-11-10 `bhspells:radiant_aura` (standalone, `RadiantAuraFx`): ribbons spiralling round a body (stream per band, head moved analytically each frame, trail points orbit at a fraction of the head speed so the tail lags into an arc, radial `normal`), and materials reused across effects (`power_line`, `power_haze`, `haki_bolt` / `haki_strike` recoloured gold). Not seen in game yet.
- 2026-11-14 `bhspells:confinement_song` (standalone, `ConfinementSongFx`): a solid drawn analytically in a pass (a slab test in the cube's own frame, axes sent as three `vec3`s, front and back faces shaded separately so the back edges show through), a ring buffer of timed pulses (`Pulse[8]` via `Uniforms.floats`) that the shader turns into out/echo fronts while Java plays the matching sounds at the same times, and TUMBLE shards cut into triangles by `p.data.x`. Not seen in game yet.
- 2026-11-14 `bhspells:yin_yang_burst` (standalone, `YinYangBurstFx`): a full-screen blackout / grey / colour-return sequence as an OVERLAY grade pass, gated per viewer by distance (only those the effect swallows), and a camera-inside-or-outside water sphere from one `raySphere`. Not seen in game yet.
- 2026-11-14 `bhspells:higanbana_domain` (standalone, `HiganbanaDomainFx`): radiant_aura's layered ribbon storm reused with a barbed claw material (`higan_claw`, round 3), a polar painterly floor smoke from blurred noise mips, AXIS thorns growing out of the ground, a procedural flower on `Facing.AXIS` + `pivot(1)` (stays upright, turns round the vertical to the camera) with `delay` = distance / wave speed so the field blooms behind a shock front, a batch-wide `Wither` uniform to age every live particle at once, and an absorbing raymarched shroud with a sky swap. Not seen in game yet.
- Lesson: a sprite with a black core and a glowing edge needs one particle that both covers and adds light — use `#define BHFX_PREMULTIPLIED` on an ALPHA batch rather than two batches.

## Body parts (bhfx 0.3.12, 2026-11-13)
- Any BHSpells Fx can ride on a body point: `FxSpawn.at(...).caster(e).follow(e, Follow.POSITION).part(BodyPart.RIGHT_HAND)`, or read several points per frame with `frame.part(BodyPart.LEFT_FOOT)` / `frame.part(entity, part)`. Spec: [`../API_PLAN.md`](../API_PLAN.md) 4.3.
- Epic Fight: `compat/epicfight/client/EpicFightBhfxBodyParts` reports each point as `stack * poseMatrices[joint] * local` from a `PatchedLayer` (registered by `EpicFightLoadedBridge` only when bhfx is loaded). `poseMatrices` are the posed joints in model space without the inverse bind (`Armature.setPose` passes `false`). `local` is precomputed from Epic Fight 20.14.17's `animmodels/entity/biped.json` bind pose, so the point and vanilla's model axes land exactly where the mesh has them: `Hand_R/L` is the forearm (pivot at the elbow, z 1.094; ARM and HAND ride on it), `Arm_R/L` the upper arm (SHOULDER rides on it, so the elbow bend shows), `Leg_R/L` the shin (pivot at the knee), the arm runs z 1.519 → 0.731. Epic Fight's Blender-to-Minecraft fix multiplies bind and mesh on the left alike, so it cancels. Derivation and script: `BHSpells/docs/bhfx/arm_shatter.md`.
  - 2026-11-14 fix: round 1 (2026-11-13) used `Tool_R/L` (the weapon grip, ~7 cm off) and `Elbow_R/L` with torso axes; the arm's `up` came out wrong, so cracks tilted off the arm (user report: "not very accurate"). Head and feet already matched (same matrices as `GildedHareEpicFightRenderCompat`).
- Switching Epic Fight battle ↔ vanilla mode needs nothing: each frame, whichever renderer draws the entity reports. Read from Epic Fight 20.14.17's bytecode (the version BHSpells builds against: `libs/EpicFight` in the ForgeGradle cache; the curse `epic-fight-mod-405076` jar there is an older 20.9.7): a player is drawn by Epic Fight when `isEpicFightMode() || !ClientConfig.enableOriginalModel` (`RenderEpicFightPlayerEvent` can override it); `LocalPlayerPatch` adds the first-person rule. Epic Fight's renderer calls only vanilla layers it has a patched version of (plus custom layers), so bhfx's `BodyPartLayer` never runs under Epic Fight's pose stack.
- No spell uses body parts yet. First user: the standalone `bhspells:arm_shatter` (2026-11-13), which re-emits AXIS crack pieces every frame in `passes()` so they stay on the swinging arm (bhfx particles stay where they are born; round 4 tried TUMBLE panes flush on each face with `softness(0)`, but anything above the skin reads as an outer layer; since round 5 the cracks are a depth-decal pass (`arm_shatter.fsh`): scene pixels inside the body-part boxes are the model's surface, so the cracks land on skin, sleeves and held items — the way to draw on a model), holds a `hold:true` effect cracked until a FINISH stop triggers the break (`onStop` + a state-dependent `endTicks`, a pattern for any "charge until released" effect), and since 2026-11-14 hides the arms for real through BHSpells `client/ShatteredArms` (vanilla model parts, armour and held-item mixins, Epic Fight `PrepareModelEvent` mesh parts and layer mixins, `RenderHandEvent`).
- Glow round a model's shape (`bhspells:meditation_aura`, 2026-11-14): rebuild the body as capsules from the body parts every frame (`MeditationAuraFx.skeleton`; hip = `LEG` + `up` × 0.375, body size = `HEAD`–`BODY` / 0.625), send them as a `vec4` array and raymarch their distance field in a pass. Anything that rises off the body must be gated off inside the sheath and dimmed on pixels that are the body itself, or it reads as white paint over the model.

## Models drawn by bhfx (bhfx 0.3.12, 2026-11-16)
- `bhspells:thousand_swords` (`compat/bhfx/client/ThousandSwordsFx`): the first BHSpells effect on `FxModel` /
  `PassList.models`: the user's Blockbench sword (`geo/geberia_feihua.geo.json`, `textures/fx/geberia_feihua.png`)
  drawn 1000 times in one instanced draw with the BHSpells material `model/flying_sword.fsh`. Notes in Thai:
  `BHSpells/docs/bhfx/thousand_swords.md`.
- Lesson (round 1 → 2, user saw swords planted under the floor): don't read server-side heightmaps on the client
  (`MOTION_BLOCKING_NO_LEAVES` and the other `LIVE_WORLD` ones; client chunks only carry `WORLD_SURFACE` and
  `MOTION_BLOCKING`). Scan the column's blocks instead (collision shape top, fluid surface), as bhfx's `LevelGround`
  does for `Shape.onGround`; and find the ground under an effect's target first, since `^ ^ ^N` targets tilt with the
  player's pitch.
- `.bbmodel` projects (Blockbench's own format) convert with `BHSpells/docs/bhfx/tools/bbmodel_to_geo.py` (cubes only;
  Blockbench's Bedrock export rules; `--pivot` picks the model's 0,0,0). BHSpells has no `checkShaders`: model
  materials were checked by assembling bhfx's `model/_header.glsl` + material + `_footer.glsl` and running glslang.

## Named points: GeckoLib bones (bhfx 0.3.12, 2026-11-16)
- `compat/bhfx/client/GeckoBhfxPoints` (registered from `client/fx/BHFxClientIntegration.register`, so only with bhfx
  loaded, client side) adds a draw-nothing layer to every GeckoLib entity renderer, BHSpells' and other mods', through
  GeckoLib's `CompileRenderLayers` event: no renderer file changed. It reports each bone's pivot by its `.geo.json` name
  while an effect asks about that entity (`BHFxClient.wantsPoints`); otherwise one check per bone. On any exception it
  turns itself off and logs once.
- Use in an Fx: follow the visual entity (`FxSpawn.follow(entity, ...)`), then `frame.point("bone_name")` in
  `passes()`; fall back to `frame.position()` while it is empty (the first frame or two, or when culled).
- Not covered: GeckoLib armour, items, blocks and replaced-entity renderers (only `GeoEntityRenderer`); bones of the
  player under Epic Fight (that is `EpicFightBhfxBodyParts`, body parts).

## Lessons from real use

- 2026-11-14 (`bhspells:aura_release`, user: "parts of it do not fade out when it goes"): an effect is dropped the tick `age > duration` (no ending phase on a natural end) or `stopAge >= endTicks`, and its particle batches go with it, so every particle still alive vanishes in one frame. Cap each particle's life at the time the effect has left, fade the emission rate and alpha along with the effect's own fade, and keep `endTicks` at least as long as the longest particle life. An overexposed shape (white core + bloom) needs a squared or eased fade, or it looks unchanged until it pops off.
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
12. A positive `ParticleSpawn.orbit` turns counter-clockwise seen from the orbit axis tip. To match something a pass draws turning counter-clockwise on a camera-facing plane, the axis must point towards the viewer, not along the look direction (`yin_yang_burst` round 5, 2026-11-14).
11. Starting another effect from inside an Fx hook must go through `Minecraft.getInstance().tell(...)`; a direct `BHFxClient.play` in `tick` crashed the client (2026-11-12, `QiBlastFx`, see [`../bugs.md`](../bugs.md)).
