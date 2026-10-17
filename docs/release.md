# Releasing (build, install, check)

## Building release jars

Order matters: build bhfx first. The bhfx `build` copies its jar into `BHSpells/libs/` (task `copyToBHSpells`), and BHSpells compiles against that copy.

```bash
cd bhfx
./gradlew.bat build publishToMavenLocal   # bhfx always requires the shader check (gradle.properties)
cd ../BHSpells
./gradlew.bat build -PrequireGlslCheck
```

glslang is found automatically in `ironspell_more/tools/glslang` (see [`shaders.md`](shaders.md)).

| Jar | File |
| :--- | :--- |
| bhfx | `bhfx/build/libs/bhfx-<version>.jar` (not the `-sources.jar`) |
| BHSpells | `BHSpells/build/libs/bhspells-1.20.1-<version>-forge.jar` (not the `-sources.jar`) |

Release jars are production builds. They include the player commands `/bhfx on|off|stats|status|bench|reload|test|stop` and the op-2 server command `/bhfx_play`.

When the bhfx version changes, update the two `net.deizyn:bhfx:<version>` lines in `BHSpells/build.gradle` (the `copyToBHSpells` task already swaps the jar in `BHSpells/libs`), and replace the old jar in any modpack's `mods/` instead of adding a second one.

## Installing

| Where | Needs |
| :--- | :--- |
| Server | BHSpells + the mods BHSpells requires (table below) + bhfx (if used) |
| Every client | The same set as the server |
| Single player | Everything on one machine |

- **bhfx is optional**, but if you use it, it must be on **the server and every client** (the network channel is strict). The version must be in the range BHSpells accepts (currently `[0.3,0.4)`).
- bhfx can be turned off or removed at any time (see "Removing bhfx" in `../README.md`). Worlds are not affected.

### Mods BHSpells requires (`BHSpells/src/main/resources/META-INF/mods.toml`)

Declared `mandatory=true` on 2026-10-17, because BHSpells code (spells, entities, effects, mixins) calls these mods directly rather than through compat. If one is missing, Forge reports it at the loading screen instead of crashing.
The minimum versions are the versions BHSpells is built and tested against.

| modId | Minimum | Used for |
| :--- | :--- | :--- |
| `irons_spellbooks` | 1.20.1-3.16.1 | The spell system (pulls in `irons_lib`, `playeranimator`, `curios`) |
| `geckolib` | 4.8.3 | Spell entities and models |
| `epicfight` | 20.14.17 | Skills, effects, mixins |
| `traveloptics` | 6.3.0-1.20.1 | Configs, items and effects the spells use (needs `cataclysm`, `alexscaves`, `attributeslib`) |
| `aaa_particles` | 2.2.3 | Effekseer |
| `alexscaves` | 2.0.2 | Spell visuals and entities |
| `citadel` | 2.6.3 | Particles |
| `bhweapons` | 0.3.9 | BeeHouse weapons used by effects and renderers |
| `dungeons_and_combat` | 1.2.2 | Gold-school spells |
| `iss_magicfromtheeast` | any | Ice and jade spell entities |
| `wom` | 2.0.162 | Gale Drive, Venomous Blossomfall |
| `invincible` | 20.14.8.2 | Jing Guang Pan combos |
| `efn` | 3.3.4 | Amethyst Decree, Wheel of Karma events |
| `wukong` | 1.0.0 | Wheel of Karma |
| `epicfightx` | 1.2 | Client-side animation setup |

Optional: `bhfx`, `epic_fight_avalon` (used only in `compat/`).
When code outside `compat/` starts importing a new mod, add it to this table and to `mods.toml`. To find them, grep `^import` by package and check which ones appear outside `compat/`.

## Pre-release checklist

- [ ] Both repos build with `-PrequireGlslCheck`.
- [ ] The bhfx jar has all of `assets/bhfx/shaders/bhfx/` (4 passes + 6 includes).
- [ ] The BHSpells jar has `assets/bhspells/shaders/bhfx/*.fsh` and an expanded `mods.toml` (all dependencies present).
- [ ] A dedicated server starts with the real mod set (no client-class crash).
- [ ] The game is playable without bhfx.
- [ ] Multiplayer passes the checklist in [`testing.md`](testing.md).
