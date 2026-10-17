# bhfx — API plan (draft v0.1)

A library mod for Forge 1.20.1 that provides post-processing effects and depth-aware world-space shaders, for BHSpells (and deizyn's other mods) to import.

> Status: **0.1, 0.2 and 0.3 are implemented** (see "Where the code differs from the plan" in `README.md`, and [`roadmap.md`](roadmap.md) for what is left). All class and method names can change until 1.0.
> The ideas came from analysing the mod The Shooting Star (documents `01`–`07` in the docs folder of the unpacked jar), but **all code and shaders were written from scratch** (the original is All Rights Reserved).

---

## 0. Basic requirements

| Topic | Value |
| :--- | :--- |
| mod id / package | `bhfx` / `net.deizyn.bhfx` |
| Minecraft / Forge | 1.20.1 / 47.4.0 (same as BHSpells) |
| Mappings | Parchment `2023.09.03-1.20.1` (same as BHSpells) |
| OpenGL | 3.3 core (GLSL `#version 150`) |
| Distribution | maven local during development (`net.deizyn:bhfx:0.3.1`), **shipped as a separate jar** (not `jarJar`ed into BHSpells, because it must stay removable) |
| Relationship to BHSpells | **Soft dependency**: BHSpells works with Effekseer alone when bhfx is absent (section 6.0) |
| Client/Server | **bhfx must be on both sides or on neither** (strict network channel, section 3) |
| World data | **Nothing is saved, and no blocks/items/entities are registered**, so it can be removed at any time without harming worlds |
| Versioning | `0.x` = the API can still change, `1.0` = the API is stable (SemVer) |

### Library rules
1. **Other mods may import only `net.deizyn.bhfx.api.*`.** `impl.*` can change at any time.
2. **The common-side API must not reference client classes** (`Minecraft`, `RenderSystem`, etc.), so a dedicated server never crashes.
3. **bhfx owns a single network channel.** Mods that use it don't register their own VFX packets (replacing `HeavenlyComboVfxPacket` and future skill-specific VFX packets).
4. **No effects = no cost**: when no Fx is running, OpenGL must not be touched at all.
5. **Everything is registered by `ResourceLocation`.** No `if (skill == ...)` in the library.

---

## 1. Package layout

```
net.deizyn.bhfx
 ├─ BHFxMod                      @Mod entry
 ├─ api/                         ← public
 │   ├─ BHFx                     server-side facade (start/stop/update/screen pulse)
 │   ├─ FxSpawn                  builder for start data
 │   ├─ FxParams, FxParam<T>     typed parameter values
 │   ├─ FxHandle, FxKey, StopMode
 │   ├─ Audience                 who should see it
 │   ├─ timeline/  Timeline, Ease
 │   └─ client/                  ← client side only
 │       ├─ BHFxClient           client-side facade (local play, settings)
 │       ├─ RegisterFxEvent      event for registering Fx
 │       ├─ Fx, FxFactory, FxInit
 │       ├─ FxContext, FrameContext
 │       ├─ PassList, PassOptions, Uniforms
 │       ├─ ScreenParams, ImpactStyle
 │       ├─ CameraParams         (v0.3)
 │       └─ FxSettings           (read-only)
 └─ impl/                        ← internal
     ├─ net/      BHFxNetwork, S2CFxStart, S2CFxUpdate, S2CFxStop, S2CScreenPulse
     ├─ server/   ServerFxTracker
     ├─ client/   ClientFxManager, ScreenParamsImpl, CameraController
     ├─ render/   PostRenderer, GlStateGuard, TargetPool, GpuTimer, ResolutionController
     ├─ shader/   ShaderManager, ShaderSource (include resolver), ShaderWarmup
     ├─ compat/   ShaderpackDetector, AaaParticlesBridge (soft)
     └─ mixin/    LevelRendererMixin, GameRendererMixin, CameraMixin
```

---

## 2. Server-side API (common)

### 2.1 `BHFx` — the main entry point

Designed to be used like `AAALevel.addParticle(level, distance, info)`, which BHSpells already uses: call it from server code, and the library sends the packets.

```java
public final class BHFx {
    /** Start an effect and send it to players in range. Returns a handle for stopping/updating it. */
    public static FxHandle start(ServerLevel level, ResourceLocation fxId, FxSpawn spawn);

    /** Like start, but bound to a key: an effect already playing under the same key is stopped and replaced. */
    public static FxHandle start(ServerLevel level, FxKey key, ResourceLocation fxId, FxSpawn spawn);

    /** Stop by handle / key (plays the ending for the StopMode). */
    public static void stop(FxHandle handle, StopMode mode);
    public static void stop(ServerLevel level, FxKey key, StopMode mode);

    /** Change parameters while it plays (for example the charge level). */
    public static void update(FxHandle handle, FxParams changed);

    /** A short screen effect with no Fx class (shake, flash, vignette, aberration, zoomBlur, desaturate), weaker with distance. */
    public static void pulse(ServerLevel level, Vec3 at, double range, ScreenPulse pulse);
    /** For one player at full strength (for example the one who got hit). */
    public static void pulse(ServerPlayer player, ScreenPulse pulse);
    // ScreenPulse.of().shake(0.6f).flash(rgb, strength, ticks).vignette(rgb, strength, ticks) ...

    public static boolean isAlive(FxHandle handle);

    /** The handle currently playing under this key, if any. */
    public static Optional<FxHandle> handle(ServerLevel level, FxKey key);

    /** false when the server disables bhfx in its config; calling mods should use their fallback (for example Effekseer only). */
    public static boolean available(ServerLevel level);
}
```

### 2.2 `FxSpawn` — start data (builder)

```java
FxSpawn.at(Vec3 origin)                      // start point (required)
    .target(Vec3 target)                     // target point (default = origin)
    .caster(Entity caster)                   // caster → the client uses it for position/pose
    .follow(Entity entity, Follow mode)      // POSITION | POSITION_AND_LOOK  (for auras/charges)
    .seed(long seed)                         // default = random from the level
    .param(FxParam<T> key, T value)
    .params(FxParams params)
    .spellLevel(int level)                   // = .param(FxParam.SPELL_LEVEL, level)
    .duration(int ticks)                     // overrides the Fx duration (-1 = unlimited, until stopped)
    .range(double blocks)                    // packet range, default 128
    .audience(Audience audience)             // default = Audience.tracking()
    .persistent(boolean)                     // (0.2) also send to late joiners (default: true when duration > 100 or -1), see section 12
```

`Audience`: `tracking()` (everyone in range), `only(ServerPlayer)`, `players(Collection<ServerPlayer>)`, `exceptCaster()` (when the caster's client already played it).

### 2.3 `FxParams` / `FxParam<T>` — typed parameters

Typed keys instead of plain strings, so the compiler catches type mistakes:

```java
public final class FxParam<T> {
    public static FxParam<Float>   f(String name);
    public static FxParam<Integer> i(String name);
    public static FxParam<Boolean> b(String name);
    public static FxParam<Vec3>    vec3(String name);
    public static FxParam<Integer> color(String name);          // 0xRRGGBB
    public static FxParam<float[]> floats(String name);         // ≤ 64 values
    public static FxParam<List<Vec3>> points(String name);      // ≤ 32 points (chain lightning, constellations)
    public static FxParam<Integer> entity(String name);         // entity id

    // standard keys
    public static final FxParam<Integer> SPELL_LEVEL = i("spell_level");
    public static final FxParam<Float>   RADIUS      = f("radius");
    public static final FxParam<Integer> COLOR       = color("color");
    public static final FxParam<Boolean> EMPOWERED   = b("empowered");
}

FxParams p = FxParams.builder().put(FxParam.RADIUS, 12f).put(FxParam.COLOR, 0xFF7A20).build();
FxParams one = FxParams.of(FxParam.RADIUS, 12f);   // a single value
float r = p.get(FxParam.RADIUS, 8f);   // default when missing
```

All of it serializes to a `FriendlyByteBuf`, with a size check of at most 32 KB per packet.

### 2.4 `FxHandle`, `FxKey`, `StopMode`

```java
public record FxHandle(ResourceKey<Level> dimension, int id) {}

public record FxKey(UUID owner, String slot) {
    public static FxKey of(Entity owner, String slot);   // e.g. FxKey.of(caster, "bhspells:solar_charge")
}

public enum StopMode {
    FINISH,     // play the full ending (e.g. the spell was released)
    CANCEL,     // a short / collapsing ending (interrupted)
    IMMEDIATE   // vanish at once (dimension change, death)
}
```

**Why `FxKey`**: Iron's LONG/CONTINUOUS spells start in `onServerPreCast` but end in `onServerCastComplete`, a different method, and keeping a handle in `MagicData` is awkward. A key built from the caster plus a name gives the same value every time without storing any state.

### 2.5 `ServerFxTracker` (impl, but its behavior is specified)
- Keeps `persistent` Fx: id, spawn, start time, latest params.
- Players who come into range or log in later get an `S2CFxStart` with `startAge` = the current age, so the client continues correctly from the middle of the timeline (the original had no such system).
- Removed when the duration ends, when stopped, or when the followed entity disappears.
- Everything is cleared on `LevelEvent.Unload`.

---

## 3. Network (impl)

| Packet | Direction | Data |
| :--- | :--- | :--- |
| `S2CFxStart` | S → C | handle id, fxId, origin, target, casterId, followId+mode, seed, duration, startAge, params |
| `S2CFxUpdate` | S → C | handle id, changed params |
| `S2CFxStop` | S → C | handle id, StopMode |
| `S2CScreenPulse` | S → C | position, range, kind (shake/flash/vignette), value, color, duration |

Channel `bhfx:main`, protocol `"2"` (since 0.3; 0.1–0.2 used `"1"`), **strict** (`"2"::equals` on both sides): client and server must have bhfx with the same protocol version. If either side lacks it, Forge reports "mismatched mod channel" when connecting, which is better than joining and seeing effects only partly.

**Packet batching**: starts/stops for the same player in the same tick are combined into one packet (`S2CFxBatch`), with a per-tick limit (section 11.3).

---

## 4. Client-side API

### 4.1 Registration

```java
// In BHSpells (mod bus, client only)
@SubscribeEvent
static void onRegisterFx(RegisterFxEvent event) {
    event.register(BHSpells.id("meteor"), MeteorFx::new);
    event.register(BHSpells.id("solar_charge"), SolarChargeFx::new);
}
```

```java
@FunctionalInterface
public interface FxFactory { Fx create(FxInit init); }

public record FxInit(ResourceLocation id, int handleId, Vec3 origin, Vec3 target, int casterId,
                     int followId, Follow follow, long seed, int duration, int startAge, FxParams params) {}
```

An unregistered id logs one warning and is skipped (no crash).

### 4.2 `Fx` — base class of every effect

```java
public abstract class Fx {
    protected Fx(FxInit init);

    // ---------- data (final) ----------
    protected final FxInit init();
    protected final RandomSource random();         // seeded by the server → same result on every machine
    protected final float seedF();                 // the seed as 0..1, for shaders
    protected final FxParams params();             // latest values (updated by S2CFxUpdate)

    // ---------- time ----------
    public final int age();                        // ticks since start (including startAge)
    protected final float ticks(FrameContext f);   // age + partialTick
    protected final float seconds(FrameContext f);
    protected final boolean stopping();            // in the ending after a stop
    protected final float stopProgress(FrameContext f);   // 0..1 through the ending
    protected final StopMode stopMode();

    // ---------- lifecycle (overridable) ----------
    protected int duration();                      // default = init.duration(), -1 = unlimited
    protected int endTicks(StopMode mode);         // ending length, default FINISH 10 / CANCEL 5 / IMMEDIATE 0
    protected void onStart(FxContext ctx) {}
    protected void tick(FxContext ctx, int age) {}           // 20 times/second: particles, sounds, effekseer
    protected void onStop(FxContext ctx, StopMode mode) {}
    protected void onParamsChanged(FxParams changed) {}
    protected void onRemoved() {}                             // release resources, stop looping sounds

    // ---------- per frame (overridable) ----------
    protected void passes(FrameContext f, PassList out) {}    // shader passes
    protected void screen(FrameContext f, ScreenParams s) {}  // screen effects
    protected void camera(FrameContext f, CameraParams c) {}  // (v0.3)
    protected void hud(GuiGraphics g, FrameContext f) {}

    // ---------- culling ----------
    protected Vec3 focus(FrameContext f);           // default = target, or the followed position
    protected double cullDistance();                // default 256; beyond this passes/screen aren't called
}
```

**End rule**: `finished = (duration >= 0 && age > duration) || (stopping && past endTicks)`. When the followed entity disappears, it counts as `CANCEL`.

### 4.3 `FxContext` (while ticking) and `FrameContext` (while drawing)

```java
public interface FxContext {
    ClientLevel level();
    Optional<Entity> caster();
    Vec3 position();                                   // current point (follows, if set)
    Vec3 cameraPos();
    void sound(SoundEvent s, Vec3 at, float volume, float pitch);
    SoundHandle loopSound(SoundEvent s, Supplier<Vec3> at, float volume);   // stops automatically in onRemoved
    void particle(ParticleOptions p, Vec3 at, Vec3 velocity);              // respects the player's particle setting
    void effek(ResourceLocation effect, Vec3 at, float scale);             // forwarded to AAA Particles if present (soft)
    void shake(Vec3 source, float amount, double range);                   // trauma that fades with distance
    boolean postAvailable();                                               // false if post is off or a shader pack is on
}

public interface FrameContext {
    float partialTick();
    Vec3 cameraPos();
    Vec3 rel(Vec3 world);                    // world - camera (what shaders use)
    Vec3 position();                         // interpolated follow position
    Optional<Vector2f> screenUv(Vec3 world); // empty when behind the camera
    int width();  int height();
    FxSettings settings();
}
```

### 4.4 `PassList`, `PassOptions`, `Uniforms`

```java
public interface PassList {
    void add(ResourceLocation shader, Consumer<Uniforms> setup);
    void add(ResourceLocation shader, PassOptions options, Consumer<Uniforms> setup);
}

public record PassOptions(int order, Resolution resolution) {
    public enum Resolution { FX_SCALED, FULL }   // FX_SCALED = follows the GPU budget (default)
    public static final PassOptions DEFAULT = new PassOptions(0, Resolution.FX_SCALED);
    // order: lower draws first, sorted across all Fx. Suggested: world-space 0, overlays/synthetic scenes 100
}

public interface Uniforms {
    Uniforms f1(String name, float x);
    Uniforms f2(String name, float x, float y);
    Uniforms f3(String name, float x, float y, float z);
    Uniforms f4(String name, float x, float y, float z, float w);
    Uniforms vec3(String name, Vec3 v);                 // converts double → float
    Uniforms color(String name, int rgb, float alpha);  // 0xRRGGBB → linear vec4
    Uniforms floats(String name, float[] values);       // uniform array (vec4[N])
    Uniforms points(String name, List<Vec3> worldPoints, FrameContext f);   // converted to camera-relative coordinates
    Uniforms texture(String sampler, ResourceLocation texture);              // loads async; black until loaded
}
```

Uniforms the shader doesn't declare are skipped silently (locations are cached). Texture units 6–15 are available for `texture()` (10 slots).

### 4.5 `ScreenParams` — screen effects (merged by strongest value)

Every method merges values from several Fx using the rule shown, so stacking skills can't white out the screen.

```java
public interface ScreenParams {
    ScreenParams shake(float amount);                         // max
    ScreenParams aberration(float amount);                    // max
    ScreenParams desaturate(float amount);                    // max
    ScreenParams zoomBlur(float amount);                      // max
    ScreenParams flash(int rgb, float strength);              // strongest wins (color and value)
    ScreenParams vignette(int rgb, float strength);           // strongest wins
    ScreenParams bloom(float strength, float radius);         // max of both values
    ScreenParams impact(ImpactStyle style, float strength, Vec3 focus);   // strongest wins
    ScreenParams speedLines(float amount);                    // max
    ScreenParams contrast(float amount);                      // max
    ScreenParams grain(float amount);                         // max
    ScreenParams freeze();                                    // OR
    ScreenParams afterimage(float amount);                    // max
    ScreenParams stepFps(float fps);                          // min (the choppiest wins)
    ScreenParams synthetic(float coverage);                   // max: a pass drawing over the whole screen, no detail transfer
    ScreenParams ripple(Vec3 center, float radius, float width, float strength);   // (v0.2) the image bends in rings, up to 8
}
```

The player's config values are always multiplied in before reaching the shader (for example `flashScale`, `shakeScale`), so Fx never have to check them.

### 4.6 `ImpactStyle` — configurable impact frames

Instead of hardcoding dozens of numbered modes, styles are defined by parameters. That is more flexible and doesn't copy the original's set of modes:

```java
public record ImpactStyle(Kind kind, int darkRgb, int lightRgb, float lineDensity, float dotSize) {
    public enum Kind { INK, INK_INVERTED, NEGATIVE, HALFTONE, GLITCH, SLASH }

    public static ImpactStyle ink(int dark, int light);
    public static ImpactStyle inverted(int dark, int light);
    public static ImpactStyle negative();
    public static ImpactStyle halftone(int dark, int light, float dotSize);
    public static ImpactStyle glitch(int tint);
    public static ImpactStyle slash(int dark, int light, float angleDeg);
    public ImpactStyle withFocusLines(float density);
}

// helper for anime-style frame switching
ImpactSequence.of(0.05f, ImpactStyle.ink(0x000000, 0xFFFFFF), ImpactStyle.inverted(0x000000, 0xFFFFFF), null, ...)
              .apply(screen, secondsSinceHit, strength, focus);   // null = skip a frame
```

### 4.7 `CameraParams` (v0.3)

An Fx overrides `camera(FrameContext, CameraParams)`. It is called every frame after `screen`, and takes effect from the next frame.

```java
public interface CameraParams {
    enum Level { NONE, SUBTLE, FULL }
    Level allowed();                                        // what this Fx may change on this machine right now
    CameraParams fov(float multiplier);                     // SUBTLE, clamped 0.7..1.3, multiplied by camera.fovScale
    CameraParams hideHand();                                // SUBTLE
    CameraParams roll(float degrees);                       // FULL, summed then clamped to ±30
    CameraParams hideHud();                                 // FULL (chat still shows)
    CameraParams override(CameraPose pose, float weight);   // FULL, highest weight wins, for short cutscenes
}
record CameraPose(Vec3 position, float yaw, float pitch, float roll) { of(...), lookAt(from, at), withRoll(...) }
```

**Server safety rules** (everything is implemented, but locked by player-side config):
- `camera.control` = NONE / **SUBTLE** (default) / FULL: the player chooses how much **their own** spells may move their camera.
- `camera.fromOthers` = **false**: spells from other players and mobs never touch your camera (prevents PvP control fights), unless the player turns it on.
- Requests above the allowed level are ignored silently. An Fx must look fine even with no camera effect (shake, flash and impact still work for everyone, per their comfort settings).
- `Fx.isLocalCaster()` says whether this machine belongs to the caster, for example to use a different flash strength.
- Moving the camera position uses reflection (`Camera#setPosition`, SRG `m_90584_`), with no mixin or AT. If it can't be found, only rotation is applied.

### 4.8 `BHFxClient` — client-side facade

```java
public final class BHFxClient {
    /** Play an Fx on this machine only (no packet), e.g. onClientPreCast or a CLIENT-side Epic Fight event. */
    public static LocalFxHandle play(ResourceLocation fxId, FxSpawn spawn);
    public static void stop(LocalFxHandle handle, StopMode mode);
    public static void update(LocalFxHandle handle, FxParams changed);              // (0.3.x) runs onParamsChanged
    public static <T extends Fx> Optional<T> fx(LocalFxHandle handle, Class<T> type); // (0.3.x) the running Fx, for client-side cues
    public static boolean isAlive(LocalFxHandle handle);                            // (0.3.x)

    public static void shake(Vec3 source, float amount, double range);
    public static FxSettings settings();
    public static boolean postAvailable();
    public static void reloadShaders();            // also called by /bhfx reload
}
```

### 4.9 `FxSettings` (client config, read-only)

| Value | Default | Meaning |
| :--- | :--- | :--- |
| `quality` | `HIGH` | `OFF` / `LOW` / `MEDIUM` / `HIGH` → controls the resolution budget and bloom. `OFF` = no Fx created and GL never touched (cost ≈ 0, you can still join servers) |
| `autoDowngrade` | `true` | Lower quality automatically when the GPU budget is exceeded for a while (section 11.3) |
| `maxActiveFx` / `maxPassesPerFrame` | `32` / `8` | Client-side limits |
| `gpuBudgetMs` | `6.0` | Target GPU time for FX per frame (the original used 16, which is too high) |
| `maxFxMegapixels` | `2.3` | Pass resolution cap |
| `bloom` | `true` | |
| `flashScale` | `1.0` | 0 = no flashes (for players sensitive to flashing light) |
| `impactFrames` | `true` | |
| `shakeScale` | `1.0` | |
| `cameraEffects` | `true` | Cutscenes/FOV/roll |
| `shaderpackMode` | `AUTO` | `AUTO` = turn post off when Iris/Oculus is detected, `FORCE` = always on |

---

## 5. Shader conventions

### 5.1 File locations
```
assets/<namespace>/shaders/bhfx/<name>.fsh          ← pass (called by ResourceLocation "<ns>:<name>")
assets/<namespace>/shaders/bhfx/include/<name>.glsl ← include file
```
The `bhfx/` folder avoids clashing with vanilla's `shaders/core` and `shaders/post`. Files are loaded through the ResourceManager, so resource packs can override them, and they reload with F3+T or `/bhfx reload`.

### 5.2 Header the library adds automatically
```glsl
#version 150
uniform sampler2D DiffuseSampler;   // the previous pass's result (or the world image for the first pass)
uniform sampler2D SceneSampler;     // the original world image before any effects
uniform sampler2D DepthSampler;     // world depth
uniform mat4 InvProj, InvView, ViewProj;
uniform vec3 Eye;                   // ray origin (camera-relative), not always 0, because view bobbing moves the eye while walking
uniform vec2 OutSize;               // this pass's target size
uniform float GameTime;             // seconds (wraps daily)
in vec2 texCoord;
out vec4 fragColor;                 // a = 1 light, a = 0 opaque drawn object (used as the bloom mask)
```
Analytic shapes (rays against cylinders, spheres, etc.) must use `Eye` (or `Scene.origin`) as the ray origin, never `vec3(0.0)`. Otherwise the effect wobbles relative to the ground while walking.

`#include "bhfx:light.glsl"` is replaced at load time, with `#line` directives so errors point at the right line of the real file.

### 5.3 Include library bhfx provides (all written from scratch)
| File | Functions |
| :--- | :--- |
| `scene.glsl` | `sceneDepth`, `depthIsSky`, `viewDir`, `unprojectAt`, `sceneAt` (struct `Scene`), `projectUv`, `pixelAngle` |
| `shapes.glsl` | `rayLine`, `raySphere`, `rayCylinderY`, `rayPlane`, `rayAxisY` |
| `light.glsl` | `glowGauss`, `glowExp`, `glowExp2` (two-layer haze), `lineLight` (1/d), `blackbody`, `tonemapSoft` |
| `ground.glsl` (0.3, include `scene.glsl` first) | `groundAA`, `groundBand`, `groundDistance`, `groundRing`, `groundDisc`, `groundDecal`, `shockRing` |
| `noise.glsl` | `hash11/12/13/22`, `vnoise2/3`, `fbm2/3` |
| `sdf2d.glsl` (0.3) | `sdCircle`, `sdBox`, `sdSegment`, `sdPolygon` (≤ 8 corners), `sdStar`, `aaFill`, `aaLine`, `rotate2` |

---

## 6. Examples with BHSpells

### 6.0 Calling through a bridge (because bhfx is optional)
BHSpells must not reference bhfx classes directly in code that is always loaded, or it gets `NoClassDefFoundError` when bhfx is absent. Use the same pattern as the existing `compat/aaaparticles/AaaParticlesCompat` + `AaaParticlesLoadedBridge`:

```
BHSpells
 ├─ build.gradle         compileOnly fg.deobf("net.deizyn:bhfx:0.3.1")   + runtimeOnly during dev
 │                       (not implementation, and no jarJar)
 ├─ mods.toml            [[dependencies.bhspells]] modId="bhfx" mandatory=false versionRange="[0.3,0.4)"
 └─ compat/bhfx/
     ├─ BhfxCompat.java          ← always loadable: checks ModList.isLoaded("bhfx") and forwards; must not import bhfx
     ├─ BhfxLoadedBridge.java    ← may import bhfx; only loaded when bhfx is present
     └─ fx/                      ← all client-side Fx classes (MeteorFx, etc.), loaded only through the bridge
```

```java
// compat/bhfx/BhfxCompat.java  (must not import net.deizyn.bhfx.*)
public final class BhfxCompat {
    private static final boolean LOADED = ModList.get().isLoaded("bhfx");

    /** true when bhfx is installed and the server has it enabled */
    public static boolean available(ServerLevel level) { return LOADED && BhfxLoadedBridge.available(level); }

    public static void meteor(ServerLevel level, LivingEntity caster, Vec3 target, int spellLevel) {
        if (LOADED) BhfxLoadedBridge.meteor(level, caster, target, spellLevel);
    }
    // ... one method per effect, so spell code never needs to know FxSpawn/FxParam
}
```

Spell code calls it like this (note that **Effekseer always plays** and bhfx is an extra layer, per section 11.1):
```java
AAALevel.addParticle(server, 96, METEOR_EFFEK.clone().position(target));   // main visuals: always played
BhfxCompat.meteor(server, entity, target, spellLevel);                    // post-FX: when bhfx is present
```
Examples 6.1–6.5 below are code **inside `BhfxLoadedBridge` / `compat/bhfx/fx/`**, not in the spell itself.
`RegisterFxEvent` registration must happen in a class that is only loaded when bhfx is present (subscribe through the bridge during client setup).

### 6.1 INSTANT spell
```java
@Override
public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource src, MagicData data) {
    Vec3 target = /* the skill's raycast */;
    if (level instanceof ServerLevel server) {
        BHFx.start(server, BHSpells.id("meteor"),
                FxSpawn.at(entity.position()).target(target).caster(entity)
                       .spellLevel(spellLevel).param(FxParam.RADIUS, 6f + spellLevel));
    }
    super.onCast(level, spellLevel, entity, src, data);
}
```

### 6.2 LONG spell (charge, then release)
```java
static final String CHARGE = "bhspells:solar_charge";

@Override
public void onServerPreCast(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData data) {
    if (level instanceof ServerLevel server)
        BHFx.start(server, FxKey.of(entity, CHARGE), BHSpells.id("solar_charge"),
                FxSpawn.at(entity.position()).caster(entity).follow(entity, Follow.POSITION).duration(-1));
    super.onServerPreCast(level, spellLevel, entity, data);
}

@Override
public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData data, boolean cancelled) {
    if (level instanceof ServerLevel server)
        BHFx.stop(server, FxKey.of(entity, CHARGE), cancelled ? StopMode.CANCEL : StopMode.FINISH);
    super.onServerCastComplete(level, spellLevel, entity, data, cancelled);
}
```

### 6.3 CONTINUOUS spell (held cast)
Same as 6.2, but values can be updated during the cast:
```java
@Override
public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData data) {
    // every 5 ticks is enough, not every tick
    if (entity.tickCount % 5 == 0 && level instanceof ServerLevel server)
        BHFx.handle(server, FxKey.of(entity, CHANNEL))
            .ifPresent(h -> BHFx.update(h, FxParams.of(POWER, currentPower)));
}
```

### 6.4 Epic Fight animation event
```java
AnimationEvent.InTimeEvent.create(0.85F, (patch, anim, params) -> {
    LivingEntity e = patch.getOriginal();
    BHFxClient.play(BHSpells.id("heavenly_slash"), FxSpawn.at(e.position()).caster(e));   // no packet needed
}, AnimationEvent.Side.CLIENT)
```
Epic Fight animations already play on every client that can see the entity, so `Side.CLIENT` + `BHFxClient.play` works without a packet (replacing `HeavenlyComboVfxPacket`). If the effect must match exactly on every machine, use `Side.SERVER` + `BHFx.start` instead.

### 6.5 Client side of the meteor
```java
public final class MeteorFx extends Fx {
    static final Timeline T = Timeline.ticks()
            .phase("warn", 0, 30).phase("fall", 20, 30).event("impact", 30).phase("burn", 30, 260)
            .build();

    MeteorFx(FxInit init) { super(init); }

    @Override protected int duration() { return 260; }

    @Override protected void tick(FxContext ctx, int t) {
        if (T.at("impact", t)) {
            ctx.sound(BHSounds.METEOR_IMPACT.get(), init().target(), 4f, 1f);
            ctx.shake(init().target(), 1f, 80);
            ctx.effek(BHSpells.id("meteor_burst"), init().target(), 1.5f);
        }
    }

    @Override protected void passes(FrameContext f, PassList out) {
        float t = ticks(f), r = params().get(FxParam.RADIUS, 6f);
        out.add(BHSpells.id("meteor"), u -> u
                .vec3("Target", f.rel(init().target()))
                .f4("P0", r, T.env("warn", t, 4, 6), T.progress("fall", t), T.since("impact", t))
                .f2("P1", T.decay("burn", t, 80), seedF()));
    }

    @Override protected void screen(FrameContext f, ScreenParams s) {
        float t = ticks(f);
        s.flash(0xFFD08A, T.pulse("impact", t, 0, 6) * 0.9f)
         .bloom(1.2f * T.env("burn", t, 0, 120), 1.6f)
         .impact(ImpactStyle.ink(0x140804, 0xFFF2E0), T.pulse("impact", t, 0, 3), init().target());
    }
}
```

---

## 7. `Timeline`

```java
public final class Timeline {
    public static Builder ticks();
    public interface Builder {
        Builder phase(String name, int start, int end);   // range [start, end)
        Builder event(String name, int tick);             // a single point
        Timeline build();
    }

    // for tick() (int)
    public boolean at(String event, int tick);            // exactly the event's tick / the start of a phase
    public boolean in(String phase, int tick);

    // for passes()/screen() (float = ticks + partial)
    public float progress(String phase, float t);         // 0..1 within the phase
    public float since(String eventOrPhase, float t);     // seconds since the start, < 0 if not reached yet
    public float env(String phase, float t, float fadeInTicks, float fadeOutTicks);   // smooth 0→1→0
    public float pulse(String event, float t, float riseTicks, float fallTicks);      // a short flash
    public float decay(String eventOrPhase, float t, float halfLifeTicks);            // exponential decay
    public int start(String name);  public int end(String name);
}

public final class Ease {
    public static float smooth(float x);          // smoothstep 0..1
    public static float smoother(float x);
    public static float window(float t, float in0, float in1, float out0, float out1);
    public static float expOut(float x, float k);
}
```

A Timeline stores its data as an immutable record, so it can be declared `static final`. Later it can be loaded from JSON (`assets/<ns>/bhfx/timelines/<name>.json`) without changing the API.

---

## 8. Required impl parts (not API, but they need planning)

| Component | Role | Improvement over the original |
| :--- | :--- | :--- |
| `LevelRendererMixin` | HEAD captures matrices, RETURN calls render | — |
| `PostRenderer` | snapshot → passes → bloom → composite | Passes sorted by `order` across all Fx |
| `GlStateGuard` | capture/restore | Restores only the texture units actually used |
| `TargetPool` | allocates FBOs/textures | Screen-size and FX-size groups kept separate; never reallocates the whole set |
| `GpuTimer` | measures GPU time | Ring of 3 `GL_TIME_ELAPSED` queries, no `glFinish` |
| `ResolutionController` | adjusts FX resolution | Targets `gpuBudgetMs` from the config |
| `ShaderManager` | load/compile/include/reload | Reads every namespace, reloads with resource reloads |
| `ShaderWarmup` | compiles ahead of time | Real 1×1 draw for every format, spread over time while the world loads |
| `ClientFxManager` | holds Fx, ticks, culls, limit 32 | Entity follow, endings, startAge |
| `ScreenParamsImpl` | merges values + applies config | — |
| `ShaderpackDetector` | detects Iris/Oculus | Turns post off automatically |
| `AaaParticlesBridge` (postponed) | `ctx.effek(...)` | Soft dependency like BHSpells' `AaaParticlesLoadedBridge`. Do it when extra Effekseer pieces need to sync with an Fx Timeline |

Passes used inside the library: `bhfx:bloom_down`, `bhfx:bloom_up`, `bhfx:composite` (all written from scratch).

---

## 9. Scope per version

| Version | Scope | Tested with |
| :--- | :--- | :--- |
| **0.1** | `BHFx.start/stop/available` (handle), `FxSpawn`, `FxParams`, `RegisterFxEvent`, `Fx` (lifecycle + passes), `PassList`, `Uniforms`, `Timeline`, `BHFxClient.play/stop`, includes `scene/shapes/light/noise`, minimal composite, `quality`/`enabled` config on both sides, Fx/pass limits, self-disable on GL errors, `/bhfx reload/on/off` | A ground warning ring + a light beam correctly hidden by mountains + the BHSpells bridge working both with and without bhfx |
| **0.2** | bloom, full `ScreenParams`, `ImpactStyle`, `FxKey`, `update`, `ServerFxTracker` (section 12), full `FxSettings`, `GpuTimer` + `ResolutionController` + automatic downgrade, F3, server-side rate limits (`ShaderWarmup`, `ground/sdf2d`, `/bhfx stats/bench` postponed to 0.3) | Post-FX added to 1 existing BHSpells spell + passing the 11.4 criteria |
| **0.3** | What was postponed from 0.2 + batched starts (protocol 2) ✅, `ShaderpackDetector` ✅, `ripple` ✅ (done in 0.2), `CameraParams` ✅ (with permission levels), `pulse` ✅. `AaaParticlesBridge` postponed until there is a real use case (BHSpells handles Effekseer itself) | 1 LONG/CONTINUOUS spell + 1 Epic Fight event |
| **1.0** | API lock, documentation, examples in a test mod | Everything above on a dedicated server |

---

## 10. Decision log

| # | Topic | Decision | Effect on the design |
| :--- | :--- | :--- | :--- |
| 1 | Name | `bhfx` / `net.deizyn.bhfx` | — |
| 2 | Dependency in BHSpells | **Optional**; Effekseer alone works without it | BHSpells must call through a bridge (6.0), and bhfx is designed as an "extra layer", not the main visuals |
| 3 | Client/server mismatch | **Must match on both sides**, with confidence in performance and the ability to remove it | Strict channel (3), off switches on client and server + lag protection (11) |
| 4 | Sending to late joiners | **Done in 0.2** | 0.1 used a temporary workaround through entities (12.3), so `BHFxClient.play` moved into 0.1 |
| 5 | Repo location | `D:\Minecraft\Dev\ironspell_more\bhfx` | Next to `BHSpells`; `publishToMavenLocal`, and BHSpells reads from `mavenLocal()` (since 2026-10-17 BHSpells resolves it from `libs/` instead, see [`decisions.md`](decisions.md)) |

---

## 11. Performance and removal

Goal: **players with bhfx installed must not lag**, and if it ever turns out to use too much, **it can be turned off or removed at once with nothing breaking**.

### 11.1 The "extra layer" principle (most important)
Every BHSpells spell **keeps its main visuals from Effekseer/GeckoLib as before**, and bhfx only **adds** what Effekseer can't do (bloom, flash, impact frames, ground warning rings, correctly occluded light beams).

```
No bhfx / bhfx off   →  spells play with Effekseer as usual (looks like BHSpells today)
With bhfx            →  the same Effekseer + post-FX on top
```

Benefit: **no need to write every effect twice**. Removing bhfx only loses the added parts; spells still play and their timing is still readable.

### 11.2 Three levels of off switch

| Level | How | Result |
| :--- | :--- | :--- |
| **Player (client)** | `bhfx-client.toml` → `quality = "OFF"`, or `/bhfx off` while playing | No Fx created, GL untouched, packets received and dropped. **Cost ≈ 0**, and you can still join servers |
| **Server** | `bhfx-server.toml` → `enabled = false` | No packets sent at all; `BHFx.available(level)` returns `false` → BHSpells falls back to Effekseer only |
| **Removal** | Delete `bhfx.jar` from **both** client and server | BHSpells doesn't detect the mod → Effekseer only. Worlds are unharmed because bhfx stores nothing in saves |

### 11.3 Lag protection built in from the start

**Client side (GPU/CPU)**
| Mechanism | Default | Version |
| :--- | :--- | :--- |
| No Fx = OpenGL not touched at all | — | 0.1 |
| Limit on concurrent Fx (the oldest is dropped beyond it) | 32 | 0.1 |
| Limit on passes per frame (beyond it, the highest-`order` / farthest passes are dropped) | 8 | 0.1 |
| Cull by camera distance | 256 blocks | 0.1 |
| FX resolution budget (pixel cap) | 2.3 MP | 0.1 |
| Resolution scaled by real GPU time (timer queries, no `glFinish`) | target 6 ms | 0.2 |
| **Automatic downgrade**: over budget for 5 seconds straight even at the lowest resolution → `quality` drops one level + one chat message | — | 0.2 |
| **Automatic self-disable**: a shader fails to compile / an incomplete framebuffer / repeated GL errors → post off for the session + log | — | 0.1 |
| Shaders compiled ahead of time while the world loads (no stutter on the first cast) | — | 0.2 |
| No new objects every frame (reused lists/matrices) | — | 0.1 |
| Particles through `ctx.particle` respect the player's particle setting | — | 0.1 |

**Server / network side**
| Mechanism | Default |
| :--- | :--- |
| Limit on `start`s per level per tick (beyond it = dropped + a log warning) | 64 |
| Limit on active Fx per caster | 16 |
| Packets from the same tick combined into one | — |
| `FxParams` size limit | 32 KB |
| `ServerFxTracker` (0.2) checks range every 10 ticks, not every tick | — |

### 11.4 Measuring tools (to answer "how much does it cost" with real numbers)
| Command / display | What it shows | Version |
| :--- | :--- | :--- |
| A line in F3 | `bhfx: 3 fx, 4 pass, 2.1 ms GPU, 78% res, 72 MB` | 0.2 |
| `/bhfx stats` | Average/max GPU and CPU time per frame, VRAM, Fx/pass counts over the last 10 seconds | 0.3 |
| `/bhfx bench [fx] [seconds]` | Plays the chosen Fx repeatedly while toggling bhfx drawing, then reports average frame time, the worst 1% and the difference | 0.3 |
| `/bhfx off` / `/bhfx on` | Toggle while playing to compare by eye | 0.1 |
| `/bhfx reload` | Reload shaders | 0.1 |

**Pass criteria before each release** (on a mid-range machine, 1080p, casting the heaviest spell once):
- No Fx: frame time differs from not having bhfx by **< 0.1 ms**
- With Fx: bhfx GPU time **≤ `gpuBudgetMs`** (6 ms) after the system adapts
- No hitch over 50 ms on the first cast (after warmup)
- Allocated VRAM **≤ 80 MB** at 1080p, released 30 seconds after the last Fx

### 11.5 Removal steps (for a modpack README)
1. Delete `bhfx-x.y.z.jar` from the `mods` folder on **the server and every client**.
2. (Optional) Delete `config/bhfx-client.toml` and `config/bhfx-server.toml` (leaving them does nothing).
3. Nothing to do for worlds. BHSpells falls back to Effekseer only automatically.

---

## 12. Sending effects to late joiners (`ServerFxTracker`, v0.2)

### 12.1 The problem it solves
Without a tracker, the server sends `S2CFxStart` **once at the start**, only to players in range at that moment. Anyone who arrives later sees nothing:

| Situation | No tracker (0.1) | With tracker (0.2) |
| :--- | :--- | :--- |
| A casts a 60-second domain, and B walks in at second 20 | B **doesn't see the domain** but still takes damage/debuffs from the server | B sees the domain as it is at second 20 and watches it to the end |
| A fire pit burns for 30 seconds, and someone logs in | The new player sees normal ground | They see the fire pit cooling down, in sync |
| B teleports / dies / respawns / changes dimension and comes back | The effect disappears from B's screen | B gets it again on coming back into range |
| A short effect (a 2-second explosion) | Barely different | No difference (nothing to remember) |

### 12.2 How it works (0.2)
1. On `BHFx.start(...)`, if `persistent` (default: true when duration > 100 ticks or = -1), the server keeps: id, spawn, start tick, latest params, and the set of players it was sent to.
2. **Every 10 ticks** it checks the players in that level. Anyone in range who hasn't received it gets `S2CFxStart` with `startAge = current age`. Anyone who leaves the range is removed from the set (and gets it again on returning).
3. Fx that `follow` an entity use that entity's `PlayerEvent.StartTracking` instead of a range check (matching vanilla's tracking).
4. A client that receives `startAge > 0` skips `onStart` and the `tick()` events already passed, but `passes()`/`screen()` compute from `age`, so they show the correct state for that moment immediately (for example a wall already half cooled).
5. Removed when the duration ends, when stopped, when the followed entity disappears, or on level unload.

**Cost**: a few hundred bytes of memory per Fx. The check is players × persistent Fx every 10 ticks (for example 20 players × 10 Fx = 200 distance checks per half second, which is very little).

### 12.3 While still on 0.1: a temporary workaround
Most long BHSpells spells already have an **entity** (AoE, zone, domain), and vanilla already sends entities to late joiners. So **let the entity's client start the Fx itself**:

```java
// in the entity's client tick / renderer (through the bridge)
if (!fxStarted) { fxHandle = BHFxClient.play(BHSpells.id("domain"), FxSpawn.at(position()).follow(this, Follow.POSITION).duration(-1)); fxStarted = true; }
// when the entity is removed on the client → BHFxClient.stop(fxHandle, StopMode.FINISH)
```

That's why `BHFxClient.play/stop` moved into **0.1**. Spells longer than 5 seconds with no entity have to wait for 0.2.
