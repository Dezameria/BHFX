# bhfx — แผน API (ร่าง v0.1)

Library mod สำหรับเอฟเฟค post-processing และ world-space shader ที่รู้ depth สำหรับ Forge 1.20.1 ให้ BHSpells (และ mod อื่นของ deizyn) import ไปใช้

> สถานะ: **0.1 และ 0.2 เขียนโค้ดแล้ว** (ดู "ส่วนที่ทำต่างจากแผน" และ "ยังไม่ได้ทำ" ใน `README.md`) ชื่อคลาสและ method ทั้งหมดเปลี่ยนได้จนกว่าจะออก 1.0
> แนวคิดได้จากการวิเคราะห์ mod The Shooting Star (เอกสาร `01`–`07` ในโฟลเดอร์ docs ของ jar ที่แตกไฟล์ไว้) แต่ **โค้ดและ shader ทั้งหมดเขียนใหม่** (ต้นฉบับเป็น All Rights Reserved)

---

## 0. ข้อกำหนดพื้นฐาน

| หัวข้อ | ค่า |
| :--- | :--- |
| mod id / package | `bhfx` / `net.deizyn.bhfx` |
| Minecraft / Forge | 1.20.1 / 47.4.0 (ตรงกับ BHSpells) |
| Mappings | Parchment `2023.09.03-1.20.1` (ตรงกับ BHSpells) |
| OpenGL | 3.3 core (GLSL `#version 150`) |
| การแจก | maven local ตอนพัฒนา (`net.deizyn:bhfx:0.1.0`), **แจกเป็น jar แยก** (ไม่ `jarJar` ใน BHSpells เพราะต้องถอดออกได้) |
| ความสัมพันธ์กับ BHSpells | **soft dependency**: BHSpells ทำงานได้ด้วย Effekseer อย่างเดียวถ้าไม่มี bhfx (หัวข้อ 6.0) |
| Client/Server | **ต้องมี bhfx ทั้งสองฝั่งหรือไม่มีทั้งคู่** (network channel แบบ strict, หัวข้อ 3) |
| ข้อมูลในโลก | **ไม่เก็บอะไรลง save และไม่ลงทะเบียน block/item/entity** → ถอดออกได้ทุกเมื่อโดยโลกไม่เสีย |
| เวอร์ชัน | `0.x` = API ยังเปลี่ยนได้, `1.0` = API นิ่ง (SemVer) |

### กฎของ library
1. **`net.deizyn.bhfx.api.*` เท่านั้นที่ mod อื่น import ได้** ส่วน `impl.*` เปลี่ยนได้ตลอดเวลา
2. **API ฝั่ง common ห้ามอ้างถึง class ของ client** (`Minecraft`, `RenderSystem` ฯลฯ) เพื่อให้ dedicated server ไม่ crash
3. **bhfx เป็นเจ้าของ network channel เดียว** mod ที่ใช้ไม่ต้องลงทะเบียน packet เพื่อ VFX เอง (แทน `HeavenlyComboVfxPacket` และ packet VFX เฉพาะสกิลในอนาคต)
4. **ไม่มีเอฟเฟค = ไม่มีต้นทุน**: เมื่อไม่มี Fx ทำงาน ห้ามแตะ OpenGL เลย
5. **ทุกอย่างลงทะเบียนด้วย `ResourceLocation`** ไม่มี `if (skill == ...)` ใน library

---

## 1. โครงสร้าง package

```
net.deizyn.bhfx
 ├─ BHFxMod                      @Mod entry
 ├─ api/                         ← สาธารณะ
 │   ├─ BHFx                     facade ฝั่งเซิร์ฟเวอร์ (start/stop/update/screen pulse)
 │   ├─ FxSpawn                  builder ข้อมูลตอนสั่ง
 │   ├─ FxParams, FxParam<T>     ค่าพารามิเตอร์แบบ typed
 │   ├─ FxHandle, FxKey, StopMode
 │   ├─ Audience                 ใครควรเห็น
 │   ├─ timeline/  Timeline, Ease
 │   └─ client/                  ← ใช้ได้เฉพาะฝั่ง client
 │       ├─ BHFxClient           facade ฝั่ง client (play แบบ local, settings)
 │       ├─ RegisterFxEvent      event ลงทะเบียน Fx
 │       ├─ Fx, FxFactory, FxInit
 │       ├─ FxContext, FrameContext
 │       ├─ PassList, PassOptions, Uniforms
 │       ├─ ScreenParams, ImpactStyle
 │       ├─ CameraParams         (v0.3)
 │       └─ FxSettings           (อ่านอย่างเดียว)
 └─ impl/                        ← ภายใน
     ├─ net/      BHFxNetwork, S2CFxStart, S2CFxUpdate, S2CFxStop, S2CScreenPulse
     ├─ server/   ServerFxTracker
     ├─ client/   ClientFxManager, ScreenParamsImpl, CameraController
     ├─ render/   PostRenderer, GlStateGuard, TargetPool, GpuTimer, ResolutionController
     ├─ shader/   ShaderManager, ShaderSource (include resolver), ShaderWarmup
     ├─ compat/   ShaderpackDetector, AaaParticlesBridge (soft)
     └─ mixin/    LevelRendererMixin, GameRendererMixin, CameraMixin
```

---

## 2. API ฝั่งเซิร์ฟเวอร์ (common)

### 2.1 `BHFx` — จุดเรียกหลัก

ออกแบบให้ใช้คล้าย `AAALevel.addParticle(level, distance, info)` ที่ BHSpells ใช้อยู่แล้ว คือเรียกจากโค้ดเซิร์ฟเวอร์ แล้ว library จัดการส่ง packet ให้เอง

```java
public final class BHFx {
    /** เริ่มเอฟเฟค แล้วส่งให้ผู้เล่นที่อยู่ในระยะ คืน handle ไว้ใช้หยุด/อัปเดต */
    public static FxHandle start(ServerLevel level, ResourceLocation fxId, FxSpawn spawn);

    /** เหมือน start แต่ผูกกับ key: ถ้ามีเอฟเฟคของ key เดิมอยู่ จะถูกหยุดและแทนที่ */
    public static FxHandle start(ServerLevel level, FxKey key, ResourceLocation fxId, FxSpawn spawn);

    /** หยุดตาม handle / key (เล่นช่วงจบตาม StopMode) */
    public static void stop(FxHandle handle, StopMode mode);
    public static void stop(ServerLevel level, FxKey key, StopMode mode);

    /** เปลี่ยนพารามิเตอร์ระหว่างที่เล่นอยู่ (เช่น ระดับการชาร์จ) */
    public static void update(FxHandle handle, FxParams changed);

    /** เอฟเฟคหน้าจอสั้นๆ แบบไม่ต้องมีคลาส Fx (สั่น, flash) */
    public static void pulse(ServerLevel level, Vec3 at, double range, ScreenPulse pulse);

    public static boolean isAlive(FxHandle handle);

    /** หา handle ที่กำลังเล่นของ key นี้ (ถ้ามี) */
    public static Optional<FxHandle> handle(ServerLevel level, FxKey key);

    /** false เมื่อเซิร์ฟเวอร์ปิด bhfx ใน config → mod ที่เรียกใช้ควรใช้ทางสำรอง (เช่น Effekseer อย่างเดียว) */
    public static boolean available(ServerLevel level);
}
```

### 2.2 `FxSpawn` — ข้อมูลตอนสั่ง (builder)

```java
FxSpawn.at(Vec3 origin)                      // จุดเริ่ม (บังคับ)
    .target(Vec3 target)                     // จุดเป้า (default = origin)
    .caster(Entity caster)                   // ผู้ร่าย → client ใช้หาตำแหน่ง/ท่า
    .follow(Entity entity, Follow mode)      // POSITION | POSITION_AND_LOOK  (สำหรับออร่า/ชาร์จ)
    .seed(long seed)                         // default = สุ่มจาก level random
    .param(FxParam<T> key, T value)
    .params(FxParams params)
    .spellLevel(int level)                   // = .param(FxParam.SPELL_LEVEL, level)
    .duration(int ticks)                     // override duration ของ Fx (-1 = ไม่จำกัด จนกว่าจะ stop)
    .range(double blocks)                    // ระยะส่ง packet, default 128
    .audience(Audience audience)             // default = Audience.tracking()
    .persistent(boolean)                     // (0.2) ส่งให้คนที่เข้ามาทีหลังด้วย (default: true ถ้า duration > 100 หรือ -1) ดูหัวข้อ 12
```

`Audience`: `tracking()` (ทุกคนที่อยู่ในระยะ), `only(ServerPlayer)`, `players(Collection<ServerPlayer>)`, `exceptCaster()` (ใช้เมื่อ client ของคนร่ายเล่นไปก่อนแล้ว)

### 2.3 `FxParams` / `FxParam<T>` — พารามิเตอร์แบบมี type

ใช้ key แบบ typed แทน string ล้วน เพื่อให้ compiler จับ type ผิดได้:

```java
public final class FxParam<T> {
    public static FxParam<Float>   f(String name);
    public static FxParam<Integer> i(String name);
    public static FxParam<Boolean> b(String name);
    public static FxParam<Vec3>    vec3(String name);
    public static FxParam<Integer> color(String name);          // 0xRRGGBB
    public static FxParam<float[]> floats(String name);         // ≤ 64 ตัว
    public static FxParam<List<Vec3>> points(String name);      // ≤ 32 จุด (สายฟ้าโซ่, กลุ่มดาว)
    public static FxParam<Integer> entity(String name);         // entity id

    // key มาตรฐาน
    public static final FxParam<Integer> SPELL_LEVEL = i("spell_level");
    public static final FxParam<Float>   RADIUS      = f("radius");
    public static final FxParam<Integer> COLOR       = color("color");
    public static final FxParam<Boolean> EMPOWERED   = b("empowered");
}

FxParams p = FxParams.builder().put(FxParam.RADIUS, 12f).put(FxParam.COLOR, 0xFF7A20).build();
FxParams one = FxParams.of(FxParam.RADIUS, 12f);   // ตัวเดียว
float r = p.get(FxParam.RADIUS, 8f);   // ค่า default ถ้าไม่มี
```

ทั้งหมด serialize ลง `FriendlyByteBuf` ได้ ตรวจขนาดไม่ให้เกิน 32 KB ต่อ packet

### 2.4 `FxHandle`, `FxKey`, `StopMode`

```java
public record FxHandle(ResourceKey<Level> dimension, int id) {}

public record FxKey(UUID owner, String slot) {
    public static FxKey of(Entity owner, String slot);   // เช่น FxKey.of(caster, "bhspells:solar_charge")
}

public enum StopMode {
    FINISH,     // เล่นช่วงจบเต็ม (เช่น ปล่อยเวทสำเร็จ)
    CANCEL,     // ช่วงจบแบบสั้น/ยุบตัว (โดนขัดจังหวะ)
    IMMEDIATE   // หายทันที (เปลี่ยนมิติ, ตาย)
}
```

**ทำไมต้องมี `FxKey`**: เวท LONG/CONTINUOUS ของ Iron's เริ่มใน `onServerPreCast` แต่จบใน `onServerCastComplete` ซึ่งเป็นคนละ method การเก็บ handle ไว้ใน `MagicData` ทำได้ยาก ใช้ key ที่สร้างจาก caster + ชื่อแทน ได้ค่าเดิมทุกครั้งโดยไม่ต้องเก็บ state

### 2.5 `ServerFxTracker` (impl แต่กำหนดพฤติกรรมไว้)
- เก็บ Fx ที่ `persistent` ไว้: id, spawn, เวลาเริ่ม, params ล่าสุด
- ผู้เล่นที่เข้ามาในระยะหรือ login ทีหลัง จะได้ `S2CFxStart` พร้อม `startAge` = อายุปัจจุบัน → client เล่นต่อจากกลางไทม์ไลน์ได้ถูกต้อง (ต้นฉบับไม่มีระบบนี้)
- ลบเมื่อหมด duration, ถูก stop, หรือ entity ที่ follow หายไป
- ล้างทั้งหมดตอน `LevelEvent.Unload`

---

## 3. Network (impl)

| Packet | ทิศทาง | ข้อมูล |
| :--- | :--- | :--- |
| `S2CFxStart` | S → C | handle id, fxId, origin, target, casterId, followId+mode, seed, duration, startAge, params |
| `S2CFxUpdate` | S → C | handle id, params ที่เปลี่ยน |
| `S2CFxStop` | S → C | handle id, StopMode |
| `S2CScreenPulse` | S → C | ตำแหน่ง, ระยะ, ชนิด (shake/flash/vignette), ค่า, สี, ระยะเวลา |

Channel `bhfx:main`, protocol `"1"` แบบ **strict** (`"1"::equals` ทั้งสองฝั่ง): client กับ server ต้องมี bhfx เวอร์ชัน protocol เดียวกัน ถ้าฝั่งใดฝั่งหนึ่งไม่มี Forge จะแจ้ง "mismatched mod channel" ตั้งแต่ตอนเชื่อมต่อ ซึ่งดีกว่าให้เข้าไปแล้วเห็นเอฟเฟคไม่ครบ

**การรวม packet**: start/stop ที่เกิดใน tick เดียวกันถึงผู้เล่นคนเดียวกันจะถูกรวมเป็น packet เดียว (`S2CFxBatch`) และจำกัดจำนวนต่อ tick (หัวข้อ 11.3)

---

## 4. API ฝั่ง client

### 4.1 การลงทะเบียน

```java
// ใน BHSpells (mod bus, เฉพาะ client)
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

id ที่ไม่ได้ลงทะเบียน → log เตือนครั้งเดียวแล้วข้าม (ไม่ crash)

### 4.2 `Fx` — คลาสแม่ของทุกเอฟเฟค

```java
public abstract class Fx {
    protected Fx(FxInit init);

    // ---------- ข้อมูล (final) ----------
    protected final FxInit init();
    protected final RandomSource random();         // seed จากเซิร์ฟเวอร์ → ผลเหมือนกันทุกเครื่อง
    protected final float seedF();                 // seed เป็น 0..1 สำหรับส่งเข้า shader
    protected final FxParams params();             // ค่าล่าสุด (อัปเดตตาม S2CFxUpdate)

    // ---------- เวลา ----------
    public final int age();                        // tick ตั้งแต่เริ่ม (รวม startAge)
    protected final float ticks(FrameContext f);   // age + partialTick
    protected final float seconds(FrameContext f);
    protected final boolean stopping();            // อยู่ในช่วงจบหลังถูก stop
    protected final float stopProgress(FrameContext f);   // 0..1 ระหว่างช่วงจบ
    protected final StopMode stopMode();

    // ---------- lifecycle (override ได้) ----------
    protected int duration();                      // default = init.duration(), -1 = ไม่จำกัด
    protected int endTicks(StopMode mode);         // ความยาวช่วงจบ, default FINISH 10 / CANCEL 5 / IMMEDIATE 0
    protected void onStart(FxContext ctx) {}
    protected void tick(FxContext ctx, int age) {}           // 20 ครั้ง/วินาที: particle, เสียง, effekseer
    protected void onStop(FxContext ctx, StopMode mode) {}
    protected void onParamsChanged(FxParams changed) {}
    protected void onRemoved() {}                             // คืน resource, หยุดเสียงที่วนอยู่

    // ---------- ต่อเฟรม (override ได้) ----------
    protected void passes(FrameContext f, PassList out) {}    // shader pass
    protected void screen(FrameContext f, ScreenParams s) {}  // เอฟเฟคหน้าจอ
    protected void camera(FrameContext f, CameraParams c) {}  // (v0.3)
    protected void hud(GuiGraphics g, FrameContext f) {}

    // ---------- culling ----------
    protected Vec3 focus(FrameContext f);           // default = target หรือตำแหน่ง follow
    protected double cullDistance();                // default 256 ห่างกว่านี้ไม่เรียก passes/screen
}
```

**กฎการจบ**: `finished = (duration >= 0 && age > duration) || (stopping && เลยช่วง endTicks)` เมื่อ entity ที่ follow หายไป ถือว่าเป็น `CANCEL`

### 4.3 `FxContext` (ตอน tick) และ `FrameContext` (ตอนวาด)

```java
public interface FxContext {
    ClientLevel level();
    Optional<Entity> caster();
    Vec3 position();                                   // จุดปัจจุบัน (ตาม follow ถ้ามี)
    Vec3 cameraPos();
    void sound(SoundEvent s, Vec3 at, float volume, float pitch);
    SoundHandle loopSound(SoundEvent s, Supplier<Vec3> at, float volume);   // หยุดอัตโนมัติตอน onRemoved
    void particle(ParticleOptions p, Vec3 at, Vec3 velocity);              // เคารพ particle setting ของผู้เล่น
    void effek(ResourceLocation effect, Vec3 at, float scale);             // ส่งต่อ AAA Particles ถ้ามี (soft)
    void shake(Vec3 source, float amount, double range);                   // trauma ลดตามระยะ
    boolean postAvailable();                                               // false ถ้าปิด post หรือเปิด shaderpack
}

public interface FrameContext {
    float partialTick();
    Vec3 cameraPos();
    Vec3 rel(Vec3 world);                    // world - camera (ใช้ส่งเข้า shader)
    Vec3 position();                         // ตำแหน่ง follow แบบ interpolate
    Optional<Vector2f> screenUv(Vec3 world); // empty ถ้าอยู่หลังกล้อง
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
    public enum Resolution { FX_SCALED, FULL }   // FX_SCALED = ตามงบ GPU (default)
    public static final PassOptions DEFAULT = new PassOptions(0, Resolution.FX_SCALED);
    // order: น้อยวาดก่อน เรียงข้ามทุก Fx — แนะนำ: world-space 0, overlay/ฉากสังเคราะห์ 100
}

public interface Uniforms {
    Uniforms f1(String name, float x);
    Uniforms f2(String name, float x, float y);
    Uniforms f3(String name, float x, float y, float z);
    Uniforms f4(String name, float x, float y, float z, float w);
    Uniforms vec3(String name, Vec3 v);                 // แปลง double → float ให้
    Uniforms color(String name, int rgb, float alpha);  // 0xRRGGBB → linear vec4
    Uniforms floats(String name, float[] values);       // uniform array (vec4[N])
    Uniforms points(String name, List<Vec3> worldPoints, FrameContext f);   // แปลงเป็นพิกัดเทียบกล้องให้
    Uniforms texture(String sampler, ResourceLocation texture);              // โหลด async, ยังไม่เสร็จ = texture ดำ
}
```

uniform ที่ shader ไม่มีจะถูกข้ามเงียบๆ (cache location ไว้) ใช้ texture unit 6–15 ได้สำหรับ `texture()` (10 ช่อง)

### 4.5 `ScreenParams` — เอฟเฟคหน้าจอ (รวมแบบเอาค่าแรงสุด)

ทุก method รวมค่าจากหลาย Fx ด้วยกฎที่ระบุ ป้องกันสกิลซ้อนกันจนจอขาว

```java
public interface ScreenParams {
    ScreenParams shake(float amount);                         // max
    ScreenParams aberration(float amount);                    // max
    ScreenParams desaturate(float amount);                    // max
    ScreenParams zoomBlur(float amount);                      // max
    ScreenParams flash(int rgb, float strength);              // ตัวแรงสุดชนะ (ทั้งสีและค่า)
    ScreenParams vignette(int rgb, float strength);           // ตัวแรงสุดชนะ
    ScreenParams bloom(float strength, float radius);         // max ทั้งสองค่า
    ScreenParams impact(ImpactStyle style, float strength, Vec3 focus);   // ตัวแรงสุดชนะ
    ScreenParams speedLines(float amount);                    // max
    ScreenParams contrast(float amount);                      // max
    ScreenParams grain(float amount);                         // max
    ScreenParams freeze();                                    // OR
    ScreenParams afterimage(float amount);                    // max
    ScreenParams stepFps(float fps);                          // min (ตัวที่กระตุกที่สุดชนะ)
    ScreenParams synthetic(float coverage);                   // max: pass วาดทับทั้งจอ ไม่ต้อง detail transfer
    ScreenParams ripple(Vec3 center, float radius, float width, float strength);   // (v0.2) ภาพบิดเป็นวงคลื่น สูงสุด 8 วง
}
```

ค่าที่ผู้เล่นตั้งใน config จะคูณทับก่อนส่งเข้า shader เสมอ (เช่น `flashScale`, `shakeScale`) Fx จึงไม่ต้องเช็คเอง

### 4.6 `ImpactStyle` — impact frame แบบตั้งค่าได้

แทนการ hardcode โหมดเป็นตัวเลขหลายสิบแบบ ให้กำหนดด้วยพารามิเตอร์ ได้ความยืดหยุ่นกว่าและไม่ลอกชุดโหมดของต้นฉบับ:

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

// helper สำหรับสลับเฟรมแบบอนิเมะ
ImpactSequence.of(0.05f, ImpactStyle.ink(0x000000, 0xFFFFFF), ImpactStyle.inverted(0x000000, 0xFFFFFF), null, ...)
              .apply(screen, secondsSinceHit, strength, focus);   // null = เว้นเฟรม
```

### 4.7 `CameraParams` (v0.3)

```java
public interface CameraParams {
    CameraParams roll(float degrees);
    CameraParams fovMultiplier(float mul);         // min/max ตามทิศ
    CameraParams override(CameraPose pose, float weight);   // weight สูงสุดชนะ, ใช้ทำคัตซีน
    CameraParams hideHand();
    CameraParams hideHud();
}
```

### 4.8 `BHFxClient` — facade ฝั่ง client

```java
public final class BHFxClient {
    /** เล่น Fx เฉพาะเครื่องนี้ (ไม่มี packet) เช่น onClientPreCast หรือ Epic Fight event ฝั่ง CLIENT */
    public static LocalFxHandle play(ResourceLocation fxId, FxSpawn spawn);
    public static void stop(LocalFxHandle handle, StopMode mode);

    public static void shake(Vec3 source, float amount, double range);
    public static FxSettings settings();
    public static boolean postAvailable();
    public static void reloadShaders();            // เรียกจาก /bhfx reload ด้วย
}
```

### 4.9 `FxSettings` (config ฝั่ง client, อ่านอย่างเดียว)

| ค่า | default | ความหมาย |
| :--- | :--- | :--- |
| `quality` | `HIGH` | `OFF` / `LOW` / `MEDIUM` / `HIGH` → คุมงบความละเอียดและ bloom; `OFF` = ไม่สร้าง Fx และไม่แตะ GL เลย (ต้นทุน ≈ 0, ยังเข้าเซิร์ฟเวอร์ได้) |
| `autoDowngrade` | `true` | ลดคุณภาพอัตโนมัติเมื่อเกินงบ GPU ต่อเนื่อง (หัวข้อ 11.3) |
| `maxActiveFx` / `maxPassesPerFrame` | `32` / `8` | ขีดจำกัดฝั่ง client |
| `gpuBudgetMs` | `6.0` | เป้าเวลา GPU ของ FX ต่อเฟรม (ต้นฉบับใช้ 16 ซึ่งสูงเกินไป) |
| `maxFxMegapixels` | `2.3` | เพดานความละเอียดของ pass |
| `bloom` | `true` | |
| `flashScale` | `1.0` | 0 = ปิดแสงวาบ (สำหรับผู้ที่ไวต่อแสงกะพริบ) |
| `impactFrames` | `true` | |
| `shakeScale` | `1.0` | |
| `cameraEffects` | `true` | คัตซีน/FOV/roll |
| `shaderpackMode` | `AUTO` | `AUTO` = ปิด post เมื่อตรวจเจอ Iris/Oculus, `FORCE` = เปิดเสมอ |

---

## 5. ข้อตกลงของ Shader

### 5.1 ตำแหน่งไฟล์
```
assets/<namespace>/shaders/bhfx/<name>.fsh          ← pass (เรียกด้วย ResourceLocation "<ns>:<name>")
assets/<namespace>/shaders/bhfx/include/<name>.glsl ← ไฟล์ include
```
ใช้โฟลเดอร์ `bhfx/` เพื่อไม่ชนกับ `shaders/core` และ `shaders/post` ของ vanilla โหลดผ่าน ResourceManager ทำให้ resource pack ทับได้ และ reload ได้ด้วย F3+T หรือ `/bhfx reload`

### 5.2 ส่วนหัวที่ library เติมให้อัตโนมัติ
```glsl
#version 150
uniform sampler2D DiffuseSampler;   // ผลของ pass ก่อนหน้า (หรือภาพโลกถ้าเป็น pass แรก)
uniform sampler2D SceneSampler;     // ภาพโลกดั้งเดิมก่อนมีเอฟเฟค
uniform sampler2D DepthSampler;     // depth ของโลก
uniform mat4 InvProj, InvView, ViewProj;
uniform vec3 Eye;                   // จุดเริ่มของ ray (camera-relative) ไม่ใช่ 0 เสมอ เพราะ view bobbing ขยับตาตอนเดิน
uniform vec2 OutSize;               // ขนาด target ของ pass นี้
uniform float GameTime;             // วินาที (วนรอบวัน)
in vec2 texCoord;
out vec4 fragColor;                 // a = 1 แสง, a = 0 วัตถุทึบที่วาด (ใช้เป็น mask ของ bloom)
```
Shape แบบ analytic (ray กับทรงกระบอก ทรงกลม ฯลฯ) ต้องใช้ `Eye` (หรือ `Scene.origin`) เป็นจุดเริ่มของ ray ห้ามใช้ `vec3(0.0)` ไม่งั้นเอฟเฟคจะโยกเทียบกับพื้นตอนเดิน

คำสั่ง `#include "bhfx:light.glsl"` ถูกแทนที่ตอนโหลด พร้อมใส่ `#line` เพื่อให้ error ระบุบรรทัดตรงกับไฟล์จริง

### 5.3 ไลบรารี include ที่ bhfx มีให้ (เขียนใหม่ทั้งหมด)
| ไฟล์ | ฟังก์ชัน |
| :--- | :--- |
| `scene.glsl` | `sceneDepth`, `depthIsSky`, `viewDir`, `unprojectAt`, `sceneAt` (struct `Scene`), `projectUv`, `pixelAngle` |
| `shapes.glsl` | `rayLine`, `raySphere`, `rayCylinderY`, `rayPlane`, `rayAxisY` |
| `light.glsl` | `glowGauss`, `glowExp`, `glowExp2` (หมอก 2 ชั้น), `lineLight` (1/d), `blackbody`, `tonemapSoft` |
| `ground.glsl` (0.3, ต้อง include `scene.glsl` ก่อน) | `groundAA`, `groundBand`, `groundDistance`, `groundRing`, `groundDisc`, `groundDecal`, `shockRing` |
| `noise.glsl` | `hash11/12/13/22`, `vnoise2/3`, `fbm2/3` |
| `sdf2d.glsl` (0.3) | `sdCircle`, `sdBox`, `sdSegment`, `sdPolygon` (≤8 มุม), `sdStar`, `aaFill`, `aaLine`, `rotate2` |

---

## 6. ตัวอย่างการใช้กับ BHSpells

### 6.0 เรียกผ่าน bridge (เพราะ bhfx ไม่บังคับ)
BHSpells ห้ามอ้างถึง class ของ bhfx ตรงๆ ในโค้ดที่โหลดเสมอ ไม่อย่างนั้นจะ `NoClassDefFoundError` เมื่อไม่มี bhfx ให้ใช้รูปแบบเดียวกับ `compat/aaaparticles/AaaParticlesCompat` + `AaaParticlesLoadedBridge` ที่มีอยู่แล้ว:

```
BHSpells
 ├─ build.gradle         compileOnly fg.deobf("net.deizyn:bhfx:0.1.0")   + runtimeOnly ตอน dev
 │                       (ไม่ใช่ implementation และไม่ jarJar)
 ├─ mods.toml            [[dependencies.bhspells]] modId="bhfx" mandatory=false versionRange="[0.1,0.2)"
 └─ compat/bhfx/
     ├─ BhfxCompat.java          ← โหลดได้เสมอ: ตรวจ ModList.isLoaded("bhfx") แล้วส่งต่อ ห้าม import bhfx
     ├─ BhfxLoadedBridge.java    ← import bhfx ได้ ถูกโหลดเฉพาะเมื่อมี bhfx
     └─ fx/                      ← คลาส Fx ฝั่ง client ทั้งหมด (MeteorFx ฯลฯ) ถูกโหลดผ่าน bridge เท่านั้น
```

```java
// compat/bhfx/BhfxCompat.java  (ห้าม import net.deizyn.bhfx.*)
public final class BhfxCompat {
    private static final boolean LOADED = ModList.get().isLoaded("bhfx");

    /** true ถ้ามี bhfx และเซิร์ฟเวอร์เปิดใช้อยู่ */
    public static boolean available(ServerLevel level) { return LOADED && BhfxLoadedBridge.available(level); }

    public static void meteor(ServerLevel level, LivingEntity caster, Vec3 target, int spellLevel) {
        if (LOADED) BhfxLoadedBridge.meteor(level, caster, target, spellLevel);
    }
    // ... หนึ่ง method ต่อหนึ่งเอฟเฟค ทำให้โค้ด spell ไม่ต้องรู้จัก FxSpawn/FxParam
}
```

ในโค้ด spell เรียกแบบนี้ (สังเกตว่า **Effekseer เล่นเสมอ** ส่วน bhfx เป็นชั้นเสริม ตามหัวข้อ 11.1):
```java
AAALevel.addParticle(server, 96, METEOR_EFFEK.clone().position(target));   // หน้าตาหลัก: เล่นเสมอ
BhfxCompat.meteor(server, entity, target, spellLevel);                    // post-FX: ถ้ามี bhfx
```
ตัวอย่าง 6.1–6.5 ด้านล่างเป็นโค้ด **ภายใน `BhfxLoadedBridge` / `compat/bhfx/fx/`** ไม่ใช่ใน spell โดยตรง
การลงทะเบียน `RegisterFxEvent` ต้องทำใน class ที่ถูกโหลดเฉพาะเมื่อมี bhfx (subscribe ผ่าน bridge ตอน client setup)

### 6.1 เวท INSTANT
```java
@Override
public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource src, MagicData data) {
    Vec3 target = /* raycast ของสกิล */;
    if (level instanceof ServerLevel server) {
        BHFx.start(server, BHSpells.id("meteor"),
                FxSpawn.at(entity.position()).target(target).caster(entity)
                       .spellLevel(spellLevel).param(FxParam.RADIUS, 6f + spellLevel));
    }
    super.onCast(level, spellLevel, entity, src, data);
}
```

### 6.2 เวท LONG (ชาร์จก่อนปล่อย)
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

### 6.3 เวท CONTINUOUS (ร่ายค้าง)
เหมือน 6.2 แต่ระหว่างร่ายอัปเดตค่าได้:
```java
@Override
public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData data) {
    // ทุก 5 tick พอ ไม่ต้องทุก tick
    if (entity.tickCount % 5 == 0 && level instanceof ServerLevel server)
        BHFx.handle(server, FxKey.of(entity, CHANNEL))
            .ifPresent(h -> BHFx.update(h, FxParams.of(POWER, currentPower)));
}
```

### 6.4 Epic Fight animation event
```java
AnimationEvent.InTimeEvent.create(0.85F, (patch, anim, params) -> {
    LivingEntity e = patch.getOriginal();
    BHFxClient.play(BHSpells.id("heavenly_slash"), FxSpawn.at(e.position()).caster(e));   // ไม่ต้องส่ง packet
}, AnimationEvent.Side.CLIENT)
```
ท่าของ Epic Fight เล่นบนทุก client ที่เห็น entity อยู่แล้ว ใช้ `Side.CLIENT` + `BHFxClient.play` ได้โดยไม่ต้องมี packet (แทน `HeavenlyComboVfxPacket`) ถ้าเอฟเฟคต้องตรงกันทุกเครื่องเป๊ะ ให้ใช้ `Side.SERVER` + `BHFx.start` แทน

### 6.5 ฝั่ง client ของ meteor
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
        Builder phase(String name, int start, int end);   // ช่วง [start, end)
        Builder event(String name, int tick);             // จุดเดียว
        Timeline build();
    }

    // ใช้ใน tick() (int)
    public boolean at(String event, int tick);            // ตรง tick ของ event/ต้น phase พอดี
    public boolean in(String phase, int tick);

    // ใช้ใน passes()/screen() (float = ticks + partial)
    public float progress(String phase, float t);         // 0..1 ภายใน phase
    public float since(String eventOrPhase, float t);     // วินาทีนับจากจุดเริ่ม, < 0 ถ้ายังไม่ถึง
    public float env(String phase, float t, float fadeInTicks, float fadeOutTicks);   // 0→1→0 นุ่ม
    public float pulse(String event, float t, float riseTicks, float fallTicks);      // แสงวาบสั้น
    public float decay(String eventOrPhase, float t, float halfLifeTicks);            // ลดลงแบบ exponential
    public int start(String name);  public int end(String name);
}

public final class Ease {
    public static float smooth(float x);          // smoothstep 0..1
    public static float smoother(float x);
    public static float window(float t, float in0, float in1, float out0, float out1);
    public static float expOut(float x, float k);
}
```

Timeline เก็บข้อมูลเป็น record ที่ไม่เปลี่ยนค่า จึงประกาศเป็น `static final` ได้ ต่อไปจะโหลดจาก JSON (`assets/<ns>/bhfx/timelines/<name>.json`) ได้โดยไม่เปลี่ยน API

---

## 8. ส่วน impl ที่ต้องมี (ไม่ใช่ API แต่ต้องวางแผน)

| คอมโพเนนต์ | หน้าที่ | ปรับปรุงจากต้นฉบับ |
| :--- | :--- | :--- |
| `LevelRendererMixin` | HEAD เก็บ matrix, RETURN เรียก render | — |
| `PostRenderer` | snapshot → passes → bloom → composite | เรียง pass ตาม `order` ข้ามทุก Fx |
| `GlStateGuard` | capture/restore | คืนเฉพาะ texture unit ที่ใช้จริง |
| `TargetPool` | จอง FBO/texture | แยกกลุ่มขนาดจอกับขนาด FX, ไม่ realloc ทั้งชุด |
| `GpuTimer` | วัดเวลา GPU | `GL_TIME_ELAPSED` query แบบ ring 3 ตัว ไม่ใช้ `glFinish` |
| `ResolutionController` | ปรับความละเอียด FX | เป้าตาม `gpuBudgetMs` ใน config |
| `ShaderManager` | โหลด/คอมไพล์/include/reload | อ่านจากทุก namespace, reload ตาม resource reload |
| `ShaderWarmup` | คอมไพล์ล่วงหน้า | วาดจริง 1×1 ลงทุก format, ทำแบบแบ่งเวลาตอนโหลด world |
| `ClientFxManager` | เก็บ Fx, tick, cull, จำกัด 32 ตัว | follow entity, ช่วงจบ, startAge |
| `ScreenParamsImpl` | รวมค่า + คูณ config | — |
| `ShaderpackDetector` | ตรวจ Iris/Oculus | ปิด post อัตโนมัติ |
| `AaaParticlesBridge` | `ctx.effek(...)` | soft dependency แบบเดียวกับ `AaaParticlesLoadedBridge` ของ BHSpells |

Pass ที่ใช้ภายใน library: `bhfx:bloom_down`, `bhfx:bloom_up`, `bhfx:composite` (เขียนใหม่ทั้งหมด)

---

## 9. ขอบเขตแต่ละเวอร์ชัน

| เวอร์ชัน | ขอบเขต | ทดสอบด้วย |
| :--- | :--- | :--- |
| **0.1** | `BHFx.start/stop/available` (handle), `FxSpawn`, `FxParams`, `RegisterFxEvent`, `Fx` (lifecycle + passes), `PassList`, `Uniforms`, `Timeline`, `BHFxClient.play/stop`, include `scene/shapes/light/noise`, composite ขั้นต่ำ, config `quality`/`enabled` ทั้งสองฝั่ง, ขีดจำกัด Fx/pass, ปิดตัวเองเมื่อ GL error, `/bhfx reload/on/off` | วงเตือนบนพื้น + ลำแสงที่ภูเขาบังถูกต้อง + bridge ใน BHSpells ทำงานได้ทั้งตอนมีและไม่มี bhfx |
| **0.2** | bloom, `ScreenParams` ครบ, `ImpactStyle`, `FxKey`, `update`, `ServerFxTracker` (หัวข้อ 12), `FxSettings` ครบ, `GpuTimer` + `ResolutionController` + ลดคุณภาพอัตโนมัติ, F3, rate limit ฝั่งเซิร์ฟเวอร์ (`ShaderWarmup`, `ground/sdf2d`, `/bhfx stats/bench` เลื่อนไป 0.3) | เติม post-FX ให้เวทที่มีอยู่ 1 ตัวใน BHSpells + ผ่านเกณฑ์ 11.4 |
| **0.3** | ส่วนที่เลื่อนจาก 0.2 + รวม start เป็น batch (protocol 2) ✅, `ShaderpackDetector` ✅, `ripple` ✅ (ทำใน 0.2), `CameraParams`, `AaaParticlesBridge`, `pulse` | เวท LONG/CONTINUOUS 1 ตัว + Epic Fight event 1 ท่า |
| **1.0** | ล็อก API, เอกสาร, ตัวอย่างใน mod ทดสอบ | ทุกอย่างข้างบนบน dedicated server |

---

## 10. บันทึกการตัดสินใจ

| # | เรื่อง | ตัดสินใจ | ผลต่อการออกแบบ |
| :--- | :--- | :--- | :--- |
| 1 | ชื่อ | `bhfx` / `net.deizyn.bhfx` | — |
| 2 | Dependency ใน BHSpells | **ไม่บังคับ** ใช้แค่ Effekseer ได้ถ้าไม่มี | BHSpells ต้องเรียกผ่าน bridge (6.0) และออกแบบ bhfx เป็น "ชั้นเสริม" ไม่ใช่ตัวหลัก |
| 3 | Client/Server ต่างกัน | **ต้องมีเหมือนกันทั้งสองฝั่ง** + ต้องมั่นใจเรื่อง performance และถอดออกได้ | channel แบบ strict (3), มีสวิตช์ปิดทั้ง client/server + ระบบป้องกัน lag (11) |
| 4 | ส่งให้คนที่เข้ามาทีหลัง | **ทำใน 0.2** | 0.1 ใช้วิธีแก้ชั่วคราวผ่าน entity (12.3) จึงย้าย `BHFxClient.play` มาไว้ 0.1 |
| 5 | ตำแหน่ง repo | `D:\Minecraft\Dev\ironspell_more\bhfx` | อยู่ข้าง `BHSpells` ใช้ `publishToMavenLocal` แล้ว BHSpells อ่านจาก `mavenLocal()` |

---

## 11. Performance และการถอดออก

เป้าหมาย: **คนที่ลง bhfx ต้องไม่ lag** และถ้าวันหนึ่งพบว่ากินทรัพยากรเกิน **ต้องปิดหรือถอดออกได้ทันทีโดยไม่มีอะไรพัง**

### 11.1 หลักการ "ชั้นเสริม" (สำคัญที่สุด)
ให้แต่ละเวทใน BHSpells **ยังคงมีหน้าตาหลักจาก Effekseer/GeckoLib เหมือนเดิม** แล้ว bhfx แค่ **เติม** สิ่งที่ Effekseer ทำไม่ได้ (bloom, flash, impact frame, วงเตือนตามพื้น, ลำแสงที่ถูกบังถูกต้อง)

```
ไม่มี bhfx / ปิด bhfx   →  เวทเล่นด้วย Effekseer ปกติ (หน้าตาเหมือน BHSpells ตอนนี้)
มี bhfx                 →  Effekseer เหมือนเดิม + post-FX เพิ่มด้านบน
```

ข้อดี: **ไม่ต้องเขียนเอฟเฟคสองชุด** การถอด bhfx ออกจะเสียแค่ส่วนที่เติมเข้ามา เวทยังเล่นได้และอ่านจังหวะได้ครบ

### 11.2 สวิตช์ปิด 3 ระดับ

| ระดับ | วิธี | ผล |
| :--- | :--- | :--- |
| **ผู้เล่น (client)** | `bhfx-client.toml` → `quality = "OFF"` หรือ `/bhfx off` ระหว่างเล่น | ไม่สร้าง Fx, ไม่แตะ GL, รับ packet แล้วทิ้ง **ต้นทุน ≈ 0** แต่ยังเข้าเซิร์ฟเวอร์ได้ |
| **เซิร์ฟเวอร์** | `bhfx-server.toml` → `enabled = false` | ไม่ส่ง packet เลย, `BHFx.available(level)` คืน `false` → BHSpells กลับไปใช้ Effekseer อย่างเดียว |
| **ถอดออก** | ลบ `bhfx.jar` ออกจาก **ทั้ง** client และ server | BHSpells ตรวจไม่เจอ mod → ใช้ Effekseer อย่างเดียว โลกไม่เสียเพราะ bhfx ไม่เก็บข้อมูลลง save |

### 11.3 ระบบป้องกัน lag ที่ใส่ไว้ตั้งแต่แรก

**ฝั่ง client (GPU/CPU)**
| กลไก | ค่าเริ่มต้น | เวอร์ชัน |
| :--- | :--- | :--- |
| ไม่มี Fx = ไม่แตะ OpenGL เลย | — | 0.1 |
| จำกัดจำนวน Fx พร้อมกัน (เกินแล้วตัวเก่าสุดถูกตัด) | 32 | 0.1 |
| จำกัดจำนวน pass ต่อเฟรม (เกินแล้วตัด pass ที่ `order` สูง/ไกลกล้องที่สุด) | 8 | 0.1 |
| Cull ตามระยะกล้อง | 256 บล็อก | 0.1 |
| งบความละเอียด FX (pixel cap) | 2.3 MP | 0.1 |
| ปรับความละเอียดตามเวลา GPU จริง (timer query ไม่ใช้ `glFinish`) | เป้า 6 ms | 0.2 |
| **ลดคุณภาพอัตโนมัติ**: ถ้าเกินงบต่อเนื่อง 5 วินาทีแม้ลดความละเอียดสุดแล้ว → ลด `quality` ลง 1 ระดับ + แจ้งในแชทครั้งเดียว | — | 0.2 |
| **ปิดตัวเองอัตโนมัติ**: shader compile ไม่ผ่าน / framebuffer ไม่ครบ / GL error ซ้ำ → ปิด post ทั้ง session + log | — | 0.1 |
| คอมไพล์ shader ล่วงหน้าตอนโหลดโลก (ไม่กระตุกตอนร่ายครั้งแรก) | — | 0.2 |
| ไม่สร้าง object ใหม่ทุกเฟรม (reuse list/matrix) | — | 0.1 |
| particle ผ่าน `ctx.particle` เคารพ particle setting ของผู้เล่น | — | 0.1 |

**ฝั่ง server / network**
| กลไก | ค่าเริ่มต้น |
| :--- | :--- |
| จำกัดจำนวน `start` ต่อ level ต่อ tick (เกิน = ทิ้ง + log เตือน) | 64 |
| จำกัด Fx ที่ active ต่อผู้ร่าย 1 คน | 16 |
| รวม packet ที่เกิดใน tick เดียวกันเป็นก้อนเดียว | — |
| จำกัดขนาด `FxParams` | 32 KB |
| `ServerFxTracker` (0.2) ตรวจระยะทุก 10 tick ไม่ใช่ทุก tick | — |

### 11.4 เครื่องมือวัด (เพื่อตอบว่า "กินทรัพยากรขนาดไหน" ด้วยตัวเลขจริง)
| คำสั่ง / ที่แสดง | สิ่งที่บอก | เวอร์ชัน |
| :--- | :--- | :--- |
| บรรทัดใน F3 | `bhfx: 3 fx, 4 pass, 2.1 ms GPU, 78% res, 72 MB` | 0.2 |
| `/bhfx stats` | ค่าเฉลี่ย/สูงสุดของเวลา GPU และ CPU ต่อเฟรม, VRAM, จำนวน Fx/pass ย้อนหลัง 10 วินาที | 0.3 |
| `/bhfx bench [fx] [วินาที]` | ครึ่งแรกไม่มี Fx ครึ่งหลังเล่น Fx ที่เลือกซ้ำ แล้วรายงาน frame time เฉลี่ย, 1% ที่แย่สุด และส่วนต่าง | 0.3 |
| `/bhfx off` / `/bhfx on` | สลับระหว่างเล่นเพื่อเทียบด้วยตาเปล่า | 0.1 |
| `/bhfx reload` | โหลด shader ใหม่ | 0.1 |

**เกณฑ์ผ่านก่อนออกแต่ละเวอร์ชัน** (วัดบนเครื่องสเปกกลาง, 1080p, ร่ายเวทหนักสุด 1 ตัว):
- ไม่มี Fx: frame time ต่างจากตอนไม่ลง bhfx **< 0.1 ms**
- มี Fx: เวลา GPU ของ bhfx **≤ `gpuBudgetMs`** (6 ms) หลังระบบปรับตัวแล้ว
- ไม่มีการกระตุกเกิน 50 ms ตอนร่ายครั้งแรก (หลัง warmup)
- VRAM ที่จองไว้ **≤ 80 MB** ที่ 1080p และคืนหลังไม่มี Fx 30 วินาที

### 11.5 ขั้นตอนถอดออก (สำหรับใส่ใน README ของ modpack)
1. ลบ `bhfx-x.y.z.jar` ออกจากโฟลเดอร์ `mods` ของ **เซิร์ฟเวอร์และ client ทุกเครื่อง**
2. (ถ้ามี) ลบ `config/bhfx-client.toml` และ `config/bhfx-server.toml` (ไม่ลบก็ไม่มีผล)
3. ไม่ต้องทำอะไรกับโลก BHSpells จะกลับไปใช้ Effekseer อย่างเดียวโดยอัตโนมัติ

---

## 12. ระบบส่งเอฟเฟคให้คนที่เข้ามาทีหลัง (`ServerFxTracker`, v0.2)

### 12.1 ปัญหาที่ระบบนี้แก้
ถ้าไม่มี tracker เซิร์ฟเวอร์จะส่ง `S2CFxStart` **ครั้งเดียวตอนเริ่ม** ให้เฉพาะคนที่อยู่ในระยะตอนนั้น ใครที่มาทีหลังจะไม่เห็นอะไรเลย:

| สถานการณ์ | ไม่มี tracker (0.1) | มี tracker (0.2) |
| :--- | :--- | :--- |
| A ร่าย domain 60 วินาที แล้ว B เดินเข้ามาในวินาทีที่ 20 | B **ไม่เห็น domain** แต่ยังโดนดาเมจ/ดีบัฟจากเซิร์ฟเวอร์ | B เห็น domain ที่กำลังเล่นอยู่วินาทีที่ 20 ต่อจนจบ |
| หลุมไฟยังลุกอยู่ 30 วินาที แล้วมีคน login | คนที่ login เห็นพื้นปกติ | เห็นหลุมไฟที่กำลังเย็นลงตรงจังหวะ |
| B วาร์ป/ตาย/เกิดใหม่/เปลี่ยนมิติกลับมา | เอฟเฟคหายจากจอ B | ได้รับใหม่ตอนกลับเข้ามาในระยะ |
| เอฟเฟคสั้น (ระเบิด 2 วินาที) | ไม่ต่างกันมาก | ไม่ต่างกัน (ไม่ต้องจำ) |

### 12.2 วิธีทำงาน (0.2)
1. ตอน `BHFx.start(...)` ถ้า `persistent` (default: true เมื่อ duration > 100 tick หรือ = -1) เซิร์ฟเวอร์จะเก็บไว้: id, spawn, tick ที่เริ่ม, params ล่าสุด, ชุดผู้เล่นที่ส่งไปแล้ว
2. **ทุก 10 tick** ตรวจผู้เล่นใน level นั้น: ใครเข้ามาในระยะแต่ยังไม่ได้รับ → ส่ง `S2CFxStart` พร้อม `startAge = อายุปัจจุบัน`, ใครออกนอกระยะ → เอาออกจากชุด (ถ้ากลับเข้ามาจะได้รับใหม่)
3. Fx ที่ `follow` entity: ใช้ `PlayerEvent.StartTracking` ของ entity นั้นแทนการตรวจระยะ (ตรงกับระบบ tracking ของ vanilla)
4. Client ที่ได้ `startAge > 0` จะข้าม `onStart` และเหตุการณ์ใน `tick()` ที่ผ่านไปแล้ว แต่ `passes()`/`screen()` คำนวณจาก `age` จึงแสดงสถานะที่ถูกต้องของจังหวะนั้นทันที (เช่น ผนังที่เย็นลงไปแล้วครึ่งหนึ่ง)
5. ลบออกเมื่อหมด duration, ถูก stop, entity ที่ follow หายไป หรือ level unload

**ต้นทุน**: หน่วยความจำไม่กี่ร้อยไบต์ต่อ Fx, การตรวจ = ผู้เล่น × Fx ที่ persistent ทุก 10 tick (เช่น 20 คน × 10 Fx = 200 ครั้งคำนวณระยะ ต่อครึ่งวินาที ถือว่าน้อยมาก)

### 12.3 ระหว่างที่ยังเป็น 0.1: วิธีแก้ชั่วคราว
เวทที่ยาวใน BHSpells ส่วนใหญ่มี **entity** อยู่แล้ว (เช่น AoE, zone, domain) และ vanilla ส่ง entity ให้คนที่เข้ามาทีหลังอยู่แล้ว ดังนั้นให้ **client ของ entity เป็นคนเริ่ม Fx เอง**:

```java
// ใน client tick / renderer ของ entity (ผ่าน bridge)
if (!fxStarted) { fxHandle = BHFxClient.play(BHSpells.id("domain"), FxSpawn.at(position()).follow(this, Follow.POSITION).duration(-1)); fxStarted = true; }
// ตอน entity ถูกลบฝั่ง client → BHFxClient.stop(fxHandle, StopMode.FINISH)
```

ด้วยเหตุนี้ `BHFxClient.play/stop` จึงย้ายมาอยู่ใน **0.1** ส่วนเวทที่ไม่มี entity และยาวกว่า 5 วินาที ให้รอ 0.2
