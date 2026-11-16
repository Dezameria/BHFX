# Plan: port to Minecraft 1.21.1 on NeoForge (prepared 2026-11-16, not started)

Only a prepared plan. Nothing is ported yet and no start date is set. Status lives in [`roadmap.md`](roadmap.md).
Version facts below are from memory and were not checked against the NeoForge docs on 2026-11-16. Check them when
the port starts.

## Why 1.21.1

- It is the version modpacks settled on after 1.20.1. Iron's Spells, Epic Fight and GeckoLib all have NeoForge 1.21.1
  builds (check the exact versions BHSpells would need before starting).
- Rendering is still the classic blaze3d on GL 3.2 core. bhfx's renderer is raw GL (`PostRenderer`, `ParticleRenderer`,
  `FxTextures`, `GlStateGuard`) and has **no mixins**, so it carries over almost unchanged. 1.21.2 removes
  `ShaderInstance`, and 1.21.5 replaces the GL layer with `GpuDevice`, so any later version is a much bigger port.

## Where the code stands (counted 2026-11-16 on `feature/scale-phase0`)

- About 45 imports of `net.minecraftforge.*`, in roughly 20 files. Most of them are under `impl/` (bootstrap, events,
  network, config, commands, blocks); `api/` has `RegisterFxEvent` (Forge `Event` / `IModBusEvent`).
- `new ResourceLocation(...)`: 14 call sites.
- Reflection: `ShaderpackDetector` (Iris API) and `FiguraMirror` (Figura). Both are optional and fail softly.
- Dev-only dependency: Carpet (`build.gradle`). Vanilla has had `/tick` since 1.20.3, so it can probably be dropped.

## What changes

| Area | 1.20.1 Forge (now) | 1.21.1 NeoForge | Files |
| :--- | :--- | :--- | :--- |
| Build | ForgeGradle 6, Java 17, `reobfJar`, BHSpells uses `fg.deobf` | ModDevGradle (`net.neoforged.moddev`), Java 21, Parchment for 1.21.1. Mojang names at runtime, so no reobf, and BHSpells uses a plain `compileOnly` | `build.gradle`, `gradle.properties`, `settings.gradle` |
| Metadata | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml`, dependencies `type = "required"` | resources |
| Mod entry | `FMLJavaModLoadingContext`, `DistExecutor` | `@Mod` constructor `(IEventBus modBus, ModContainer container)`; `DistExecutor` is gone, so check `FMLEnvironment.dist` | `BHFxMod`, `ClientBootstrap` |
| Network | `SimpleChannel`, 5 messages, protocol `"3"` | `RegisterPayloadHandlersEvent` + `registrar("3")`; each message becomes a `CustomPacketPayload` record with a `StreamCodec` (the existing `encode`/`decode` can be wrapped); sending through `PacketDistributor.sendToPlayer` / `sendToPlayersInDimension`. Check which thread handlers run on (they must stay on the main thread) and that `NetMeter` still gets byte counts | `impl/net/*`, `NetMeter` |
| Ticks | `TickEvent.*` + `Phase` | `ClientTickEvent.Post`, `ServerTickEvent.Post`, `LevelTickEvent.Post` | `ClientEvents`, `ServerFxEvents` |
| Config | `ForgeConfigSpec`, `ConfigScreenHandler` | `ModConfigSpec`, `container.registerConfig`, `IConfigScreenFactory` | `BHFxConfig`, `ClientConfigMigration`, settings screen |
| Registries | `DeferredRegister` + `ForgeRegistries` | `DeferredRegister.Blocks/Items`, `BuiltInRegistries` | `impl/block/BHFxBlocks` |
| Blocks | no `use(...)` override today | if one is added before the port: `useWithoutItem` / `useItemOn`; block entity `loadAdditional(tag, provider)` (`MirrorBlockEntity` stores nothing, so trivial) | `impl/block/*` |
| Data | `data/bhfx/loot_tables/`, `recipes/`; recipe `result.item` | folders renamed to `loot_table/`, `recipe/`; `result` is `{ "id", "count" }` | resources |
| `ResourceLocation` | `new ResourceLocation(ns, path)` | `ResourceLocation.fromNamespaceAndPath` / `parse` | 14 sites |
| Render events | `RenderLevelStageEvent` stages, `RegisterShadersEvent`, `ViewportEvent`, `RenderGuiEvent`, `CustomizeGuiOverlayEvent` | the same events in NeoForge; partial tick may come from a `DeltaTracker`. Check every `Stage` constant `ClientEvents` uses still exists | `ClientEvents`, `ClientBootstrap` |
| Vertex building | `BufferBuilder` + `endVertex()` | 1.21 API: `Tesselator.begin(...)` returns the builder, `addVertex(...).setColor(...)`, `BufferUploader.drawWithShader(builder.buildOrThrow())` | `MirrorRenderer` only |
| Render layers | `EntityRenderersEvent.AddLayers.getSkins()` returns `String` | returns `PlayerSkin.Model` | `ClientBootstrap.addLayers` |
| Shader packs | Oculus or Iris | Iris itself on NeoForge (mod id `iris`); `ShaderpackDetector` already checks `iris` | test only |
| Sodium-type mods | Embeddium | Sodium 0.6 (NeoForge) or Embeddium 1.21.1 | test only |
| Public API | `RegisterFxEvent extends Event implements IModBusEvent` | NeoForge `Event` + `IModBusEvent`; same name, same methods | `api/client/RegisterFxEvent` |

Should not change: shaders (`#version 150`, same header), `checkShaders` and glslang, the JUnit tests (plain Java),
`FxParams` encoding, the command trees, and the `api.*` method signatures. BHSpells effect code should compile with no
changes except imports.

## How to keep two versions

- **One repo, a long-lived branch `mc/1.21.1-neoforge`** cut from `main` at a release tag. Features land on `main`
  (1.20.1) first and get cherry-picked. Keep the Forge-specific code in the files in the table above, so that most
  feature commits (render, shaders, particles, `api`) apply cleanly.
- Same `mod_version` on both. The jar is named `bhfx-neoforge-1.21.1-<ver>.jar` and the maven artifact
  `net.deizyn:bhfx-neoforge` (so mavenLocal can't mix them up, see [`bugs.md`](bugs.md) 2026-11-04 on stale jars).
- A multi-version tool (Stonecutter, Architectury) is not worth it for one loader and two versions.

## Cheap preparation that can be done on 1.20.1 now (optional, each its own small commit)

1. Route every `new ResourceLocation(...)` through `BHFxMod.id(...)` or one helper, so the port changes one place.
2. Keep the Forge event types out of the files that aren't in the table above (they already are, apart from
   `RegisterFxEvent`).
3. Write down which `RenderLevelStageEvent.Stage` each feature depends on and why (`ClientEvents`), so the port can
   check them one by one.

## Steps when the port starts

1. Finish and release on 1.20.1 first. Merge `feature/scale-phase0`, so the branch doesn't start in the middle of the
   scale plan.
2. Branch, new MDG build files, Java 21. Get `compileJava` passing area by area: build, mod entry, config, network,
   events, blocks, mirror renderer.
3. `build` + `checkShaders` + JUnit.
4. In game, run the existing checklists in [`testing.md`](testing.md) again: `/bhfx test` effects, particles,
   mirror and mirror panel, body parts, `/bhfx stress`, the settings screen, a dedicated server, Iris on and off.
5. BHSpells port (its own plan, not bhfx's): Iron's Spells, Epic Fight (`EpicFightBhfxBodyParts` uses its joint API),
   GeckoLib, AAA Particles if still used. Check that each has a 1.21.1 NeoForge build before starting.
6. Release `bhfx-neoforge` and update [`release.md`](release.md) with the install matrix for both versions.

## Open questions for the user

- Does BHSpells move to 1.21.1 too, or should bhfx 1.21.1 exist for other mods only?
- Keep 1.20.1 updated after the port, or only fix bugs there?
