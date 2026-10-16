# bhfx (BeeHouse FX)

Library mod สำหรับ Forge 1.20.1: เอฟเฟค post-processing และ shader ในโลกที่รู้ depth ให้ BHSpells และ mod อื่นเรียกใช้ได้
เป็น **ชั้นเสริม** — mod ที่ใช้ยังคงหน้าตาหลักจาก Effekseer/GeckoLib และทำงานได้ถ้าไม่มี bhfx

- แผน API ฉบับเต็ม: [`docs/API_PLAN.md`](docs/API_PLAN.md)
- เวอร์ชัน: **0.2.0** (API ยังเปลี่ยนได้)

## Build / ใช้งาน

```bash
./gradlew.bat build                 # jar อยู่ที่ build/libs/bhfx-0.2.0.jar
./gradlew.bat publishToMavenLocal   # ให้ BHSpells อ่านได้จาก mavenLocal()
./gradlew.bat runClient             # ทดสอบ
```

ฝั่ง BHSpells (soft dependency, ดู `docs/API_PLAN.md` หัวข้อ 6.0):
```gradle
repositories { mavenLocal() }
dependencies {
    compileOnly fg.deobf("net.deizyn:bhfx:0.2.0")
    runtimeOnly fg.deobf("net.deizyn:bhfx:0.2.0")   // เฉพาะตอน dev ถ้าอยากรันพร้อมกัน
}
```

## ทดสอบในเกม

| คำสั่ง | ฝั่ง | ทำอะไร |
| :--- | :--- | :--- |
| `/bhfx_test [radius]` | server (op 2) | เล่นเอฟเฟคทดสอบตรงที่มอง: วงเตือนบนพื้น → ลำแสงที่ภูเขาบังถูกต้อง + bloom + impact frame + คลื่นภาพ (ripple) + zoom blur + แสงวาบ + สั่นจอ + พื้นร้อนแล้วเย็นลง |
| `/bhfx status` | client | สถานะ: เปิด/ปิด, quality ที่ใช้จริง, จำนวน fx/pass, ความละเอียด FX, % งบ, เวลา GPU, VRAM โดยประมาณ |
| F3 | client | บรรทัด `[bhfx] ...` ด้านซ้ายแสดงข้อมูลเดียวกันแบบสด |
| `/bhfx off` / `/bhfx on` | client | ปิด/เปิดชั่วคราวเพื่อเทียบ FPS (ปิด = ไม่สร้าง fx และไม่แตะ GPU) |
| `/bhfx reload` | client | คอมไพล์ shader ใหม่, ล้างสถานะ "ปิดเพราะ GL error" และยกเลิกการลดคุณภาพอัตโนมัติ (F3+T ก็คอมไพล์ใหม่) |

## Config

- `config/bhfx-client.toml`
  - quality: `quality` (OFF/LOW/MEDIUM/HIGH — LOW ไม่มี bloom), `maxFxMegapixels`, `gpuBudgetMs` (ค่าเริ่มต้น 6), `autoDowngrade`, `bloom`
  - limits: `maxActiveFx`, `maxPassesPerFrame`, `cullDistance`
  - comfort: `flashScale`, `shakeScale`, `impactFrames`
- `config/bhfx-server.toml`: `enabled` (false = ไม่ส่งเอฟเฟค, mod ที่ใช้กลับไปใช้ทางสำรอง), `maxRange`, `maxStartsPerTick` (64), `maxActivePerCaster` (16)

## ระบบป้องกัน lag ที่ทำงานอยู่ (0.2)

- **ไม่มีเอฟเฟค = ไม่แตะ GPU**; ถ้ามีแค่การสั่นจอ ก็ไม่รัน post-processing
- **วัดเวลา GPU จริง** ด้วย `GL_TIME_ELAPSED` query (อ่านผลย้อนหลัง ไม่ทำให้ CPU รอ GPU) แล้วลดพิกเซลของ pass ลงได้ถึง 25% เพื่อให้อยู่ใต้ `gpuBudgetMs` (ลดทันที เพิ่มกลับช้าๆ ปรับทีละ 1/8 จึงไม่ต้องจอง texture ใหม่ทุกเฟรม)
- **ลดคุณภาพอัตโนมัติ**: ถ้ายังเกินงบ 25% ต่อเนื่อง 5 วินาทีทั้งที่ลดความละเอียดสุดแล้ว จะลด quality ลง 1 ระดับเฉพาะ session นี้ (HIGH → MEDIUM → LOW) แจ้งในแชทและ log `/bhfx reload` เพื่อคืนค่า
- Bloom, freeze และ stepped-frame จอง texture **เฉพาะตอนที่มีเอฟเฟคใช้** และทั้งหมดคืนให้ GPU หลังไม่มีเอฟเฟค 30 วินาที
- เซิร์ฟเวอร์จำกัดจำนวน start ต่อ tick และจำนวนเอฟเฟคต่อผู้ร่าย

## ถอดออก

ลบ `bhfx-*.jar` ออกจาก **ทั้ง** server และ client (network channel เป็นแบบ strict ต้องมีหรือไม่มีเหมือนกันทั้งสองฝั่ง) bhfx ไม่เก็บอะไรลง save โลกจึงไม่เสีย

## โครงสร้าง

```
net.deizyn.bhfx
 ├─ api/            BHFx, FxSpawn, FxParam(s), FxHandle, FxKey, StopMode, Follow, Audience
 │   ├─ timeline/   Timeline, Ease
 │   └─ client/     Fx, FxInit, FxFactory, FxContext, FrameContext, PassList, PassOptions, Uniforms,
 │                  ScreenParams, ImpactStyle, ImpactSequence, FxSettings, BHFxClient, LocalFxHandle, RegisterFxEvent
 └─ impl/           (ภายใน เปลี่ยนได้ตลอด)
     ├─ net/        channel bhfx:main + S2CFxStart / S2CFxStop / S2CFxUpdate
     ├─ server/     ServerFx (id, key, อายุ, rate limit, tracker), ServerFxEvents (/bhfx_test)
     ├─ client/     ClientFxManager (ลดคุณภาพอัตโนมัติ), ClientEvents (/bhfx, F3), test/TestFx
     └─ render/     PostRenderer, GpuTimer, ResolutionController, GlStateGuard, ShaderManager, UniformBinder
assets/bhfx/shaders/bhfx/
 ├─ include/        scene.glsl, shapes.glsl, light.glsl, noise.glsl
 ├─ bloom_down.fsh, bloom_up.fsh
 ├─ composite.fsh   (ripple, aberration, zoom blur, bloom, grade, tone-map, impact, speed lines, afterimage, flash, grain)
 └─ test.fsh
```

Shader ของ mod อื่นวางที่ `assets/<ns>/shaders/bhfx/<name>.fsh` แล้วเรียกด้วย `"<ns>:<name>"`;
include ด้วย `#include "bhfx:light.glsl"` (ไฟล์ include อยู่ที่ `assets/<ns>/shaders/bhfx/include/`)

## ส่วนที่ทำต่างจากแผน

| เรื่อง | ในแผน | ที่ทำจริง | เหตุผล |
| :--- | :--- | :--- | :--- |
| Hook การเรนเดอร์ | Mixin `LevelRenderer` | **ไม่มี mixin เลย**: `RenderLevelStageEvent` (เก็บ matrix ที่ `AFTER_SKY`/`AFTER_SOLID_BLOCKS`, วาดที่ `AFTER_LEVEL`) + `ViewportEvent` สำหรับสั่นจอ | เข้ากับ mod อื่นง่ายกว่า ไม่ต้อง refmap |
| Depth | snapshot ทั้ง color และ depth | sample depth ของ Minecraft ตรงๆ, copy แค่ color | FBO ของ bhfx ไม่ attach depth จึงไม่มี feedback loop ประหยัด ~8 MB ที่ 1080p |
| Duration | override `duration()` | override **`defaultDuration()`**; `duration()` เป็น final และเคารพค่าใน `FxSpawn` | ให้ฝั่งที่สั่งกำหนดเวลาได้เสมอ |
| `PassOptions` | `order` + `resolution` | เฉพาะ `order` | ทุก pass ใช้ความละเอียด FX เดียวกัน |
| คำสั่งทดสอบ | `/bhfx test` | `/bhfx_test` (server) แยกจาก `/bhfx ...` (client) | client กับ server command ใช้ชื่อ root เดียวกันแล้วชนกัน |
| Texture ใน pass | — | ครั้งแรกที่ขอจะโหลดหลังจบเฟรม texture จึงขึ้นตั้งแต่เฟรมถัดไป | ไม่ให้การโหลดของ Minecraft ไปเปลี่ยน GL state ระหว่างที่ bhfx ถืออยู่ |
| Bloom | 13-tap / 9-tap | dual filter 5-tap ลง / 8-tap ขึ้น (บวกกับชั้นเดียวกัน) | ถูกกว่า และไม่เหมือน implementation ของต้นฉบับ |
| Tracker กับ entity ที่ follow | `PlayerEvent.StartTracking` | ตรวจระยะจากตำแหน่งปัจจุบันของ entity ทุก 10 tick | ใช้โค้ดชุดเดียวกับเอฟเฟคที่ไม่ follow |
| การส่ง update | ส่งเฉพาะคนที่ได้รับเอฟเฟค | ส่งทั้ง dimension (client ที่ไม่มีเอฟเฟคนั้นจะทิ้ง) | packet เล็ก และครอบคลุมเอฟเฟคที่ไม่ persistent |

## ยังไม่ได้ทำ (ย้ายไป 0.3)

- `ShaderWarmup` (คอมไพล์ shader ล่วงหน้าตอนโหลดโลก) — ตอนนี้คอมไพล์ครั้งแรกที่ใช้ อาจกระตุกเล็กน้อยตอนร่ายครั้งแรก
- `/bhfx stats` แบบย้อนหลัง 10 วินาที และ `/bhfx bench`
- Include `ground.glsl` / `sdf2d.glsl`
- รวม packet หลาย start ใน tick เดียวเป็นก้อนเดียว

## สถานะการทดสอบ

- ✅ 0.1: ทดสอบในเกมแล้ว (โลก flat) เอฟเฟคแสดงผล ไม่มี shader/GL error ใน log
- ✅ 0.2: `gradlew build` ผ่าน; `composite`, `test`, `bloom_down`, `bloom_up` (รวม include แล้ว) คอมไพล์ผ่านใน WebGL2 / GLSL ES 3.00
- ⏳ 0.2 **ยังไม่ได้ทดสอบในเกม**: bloom, impact frame, ripple, การลดความละเอียดตามเวลา GPU, การลดคุณภาพอัตโนมัติ, tracker (เดินเข้าระยะทีหลัง / login ระหว่างเอฟเฟค / ตายแล้วเกิดใหม่), dedicated server
