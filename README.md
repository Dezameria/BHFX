# bhfx (BeeHouse FX)

Library mod สำหรับ Forge 1.20.1: เอฟเฟค post-processing และ shader ในโลกที่รู้ depth ให้ BHSpells และ mod อื่นเรียกใช้ได้
เป็น **ชั้นเสริม** — mod ที่ใช้ยังคงหน้าตาหลักจาก Effekseer/GeckoLib และทำงานได้ถ้าไม่มี bhfx

- แผน API ฉบับเต็ม: [`docs/API_PLAN.md`](docs/API_PLAN.md)
- เวอร์ชัน: **0.3.0** (กำลังทำ, API ยังเปลี่ยนได้) — protocol ของ network เป็น `2` ใช้ร่วมกับ 0.2 ไม่ได้

## Build / ใช้งาน

```bash
./gradlew.bat build                 # jar อยู่ที่ build/libs/bhfx-0.3.0.jar
./gradlew.bat publishToMavenLocal   # ให้ BHSpells อ่านได้จาก mavenLocal()
./gradlew.bat runClient             # ทดสอบ
```

ฝั่ง BHSpells (soft dependency, ดู `docs/API_PLAN.md` หัวข้อ 6.0):
```gradle
repositories { mavenLocal() }
dependencies {
    compileOnly fg.deobf("net.deizyn:bhfx:0.3.0")
    runtimeOnly fg.deobf("net.deizyn:bhfx:0.3.0")   // เฉพาะตอน dev ถ้าอยากรันพร้อมกัน
}
```

## ทดสอบในเกม

| คำสั่ง | ฝั่ง | ทำอะไร |
| :--- | :--- | :--- |
| `/bhfx_test [radius]` | server (op 2) | เล่นเอฟเฟคทดสอบตรงที่มอง: วงเตือนบนพื้น → ลำแสงที่ภูเขาบังถูกต้อง + bloom + impact frame + คลื่นภาพ (ripple) + zoom blur + แสงวาบ + สั่นจอ + พื้นร้อนแล้วเย็นลง |
| `/bhfx_test pulse` | server (op 2) | ScreenPulse ที่ตัวเอง: สั่น + แสงวาบ + ขอบจอแดง + สีเหลื่อม (ผู้เล่นในระยะ 32 บล็อกเห็นด้วย เบาลงตามระยะ) |
| `/bhfx stats` | client | ต้นทุนย้อนหลัง 10 วินาที: เวลา CPU ตอนวาด/ตอน tick และเวลา GPU (เฉลี่ย/สูงสุด), จำนวน fx/pass สูงสุด, VRAM ที่ถืออยู่ (นับเฉพาะเฟรมที่มีเอฟเฟค) |
| `/bhfx bench [fx] [วินาที]` | client | เล่น fx ซ้ำตรงที่มอง (เฉพาะเครื่องตัวเอง) แล้วสลับปิด/เปิดการวาดของ bhfx ทุก 1 วินาที รายงาน frame time เฉลี่ย, 1% ที่แย่สุด และส่วนต่าง (ค่าเริ่มต้น `bhfx:test` 10 วินาที) |
| `/bhfx status` | client | สถานะ: เปิด/ปิด, quality ที่ใช้จริง, จำนวน fx/pass, ความละเอียด FX, % งบ, เวลา GPU, VRAM โดยประมาณ |
| F3 | client | บรรทัด `[bhfx] ...` ด้านซ้ายแสดงข้อมูลเดียวกันแบบสด |
| `/bhfx off` / `/bhfx on` | client | ปิด/เปิดชั่วคราวเพื่อเทียบ FPS (ปิด = ไม่สร้าง fx และไม่แตะ GPU) |
| `/bhfx reload` | client | คอมไพล์ shader ใหม่, ล้างสถานะ "ปิดเพราะ GL error" และยกเลิกการลดคุณภาพอัตโนมัติ (F3+T ก็คอมไพล์ใหม่) |

## Config

- `config/bhfx-client.toml`
  - quality: `quality` (OFF/LOW/MEDIUM/HIGH — LOW ไม่มี bloom), `maxFxMegapixels`, `gpuBudgetMs` (ค่าเริ่มต้น 6), `autoDowngrade`, `bloom`, `warmup`
  - limits: `maxActiveFx`, `maxPassesPerFrame`, `cullDistance`
  - comfort: `flashScale`, `shakeScale`, `impactFrames`
  - camera: `control` (NONE / **SUBTLE** / FULL — เวทของตัวเองขยับกล้องได้แค่ไหน), `fromOthers` (**false** — เวทคนอื่นไม่แตะกล้องเรา), `fovScale`
  - compat: `offWithShaderpack` (ค่าเริ่มต้น true — ปิด post ขณะเปิด shader pack ของ Iris/Oculus)
- `config/bhfx-server.toml`: `enabled` (false = ไม่ส่งเอฟเฟค, mod ที่ใช้กลับไปใช้ทางสำรอง), `maxRange`, `maxStartsPerTick` (64), `maxActivePerCaster` (16)

## ระบบป้องกัน lag ที่ทำงานอยู่

- **ไม่มีเอฟเฟค = ไม่แตะ GPU**; ถ้ามีแค่การสั่นจอ ก็ไม่รัน post-processing
- **วัดเวลา GPU จริง** ด้วย `GL_TIME_ELAPSED` query (อ่านผลย้อนหลัง ไม่ทำให้ CPU รอ GPU) แล้วลดพิกเซลของ pass ลงได้ถึง 25% เพื่อให้อยู่ใต้ `gpuBudgetMs` (ลดทันที เพิ่มกลับช้าๆ ปรับทีละ 1/8 จึงไม่ต้องจอง texture ใหม่ทุกเฟรม)
- **ลดคุณภาพอัตโนมัติ**: ถ้ายังเกินงบ 25% ต่อเนื่อง 5 วินาทีทั้งที่ลดความละเอียดสุดแล้ว จะลด quality ลง 1 ระดับเฉพาะ session นี้ (HIGH → MEDIUM → LOW) แจ้งในแชทและ log `/bhfx reload` เพื่อคืนค่า
- Bloom, freeze และ stepped-frame จอง texture **เฉพาะตอนที่มีเอฟเฟคใช้** และทั้งหมดคืนให้ GPU หลังไม่มีเอฟเฟค 30 วินาที
- เซิร์ฟเวอร์จำกัดจำนวน start ต่อ tick และจำนวนเอฟเฟคต่อผู้ร่าย
- **Shader warmup** (0.3): หลังเข้าโลก คอมไพล์ shader ทุกตัวของทุก mod (`assets/*/shaders/bhfx/**.fsh`) ทีละ 1 ตัวต่อเฟรม พร้อมวาดจริงลง target 1x1 (บาง driver คอมไพล์เสร็จตอนวาดครั้งแรก) การร่ายครั้งแรกจึงไม่กระตุก log บอกเวลารวมและตัวที่ช้าสุด
- **Shader pack** (0.3): ถ้า Iris/Oculus เปิด shader pack อยู่ จะปิด post ให้อัตโนมัติ (depth ไม่ตรงกับภาพของ pack) สั่นจอยังทำงาน และ `BHFxClient.postAvailable()` คืน false ให้ mod ที่ใช้เลือกทางสำรองได้
- **รวม packet** (0.3): start ที่เกิดใน tick เดียวกันของผู้เล่นคนเดียวจะส่งรวมเป็นก้อนเดียว (สูงสุด 16 ต่อก้อน) ส่วน stop/update จะส่ง start ที่ค้างอยู่ออกไปก่อน ลำดับจึงไม่สลับ

## ถอดออก

ลบ `bhfx-*.jar` ออกจาก **ทั้ง** server และ client (network channel เป็นแบบ strict ต้องมีหรือไม่มีเหมือนกันทั้งสองฝั่ง) bhfx ไม่เก็บอะไรลง save โลกจึงไม่เสีย

## โครงสร้าง

```
net.deizyn.bhfx
 ├─ api/            BHFx, FxSpawn, FxParam(s), FxHandle, FxKey, StopMode, Follow, Audience, ScreenPulse
 │   ├─ timeline/   Timeline, Ease
 │   └─ client/     Fx, FxInit, FxFactory, FxContext, FrameContext, PassList, PassOptions, Uniforms,
 │                  ScreenParams, ImpactStyle, ImpactSequence, CameraParams, CameraPose, FxSettings, BHFxClient,
 │                  LocalFxHandle, RegisterFxEvent
 └─ impl/           (ภายใน เปลี่ยนได้ตลอด)
     ├─ net/        channel bhfx:main (protocol 2) + S2CFxStart / S2CFxStartBatch / S2CFxStop / S2CFxUpdate / S2CScreenPulse
     ├─ server/     ServerFx (id, key, อายุ, rate limit, tracker), ServerFxEvents (/bhfx_test)
     ├─ client/     ClientFxManager (ลดคุณภาพอัตโนมัติ), ClientEvents (/bhfx, F3), ShaderWarmup, ShaderpackDetector,
     │              FrameStats (/bhfx stats), FxBench (/bhfx bench), CameraParamsImpl, CameraMover, test/TestFx
     └─ render/     PostRenderer, GpuTimer, ResolutionController, GlStateGuard, ShaderManager, UniformBinder
assets/bhfx/shaders/bhfx/
 ├─ include/        scene.glsl, shapes.glsl, light.glsl, noise.glsl, ground.glsl, sdf2d.glsl
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

## Screen pulse (0.3)

เอฟเฟคหน้าจอสั้นๆ จากฝั่งเซิร์ฟเวอร์ ไม่ต้องเขียนคลาส Fx — เหมาะกับเติมความรู้สึกตอนเวทโดน:
```java
BHFx.pulse(level, target, 24, ScreenPulse.of().shake(0.6f).flash(0xFFE0B0, 0.5f, 6));  // ทุกคนในระยะ เบาลงตามระยะ
BHFx.pulse(hitPlayer, ScreenPulse.of().vignette(0x600000, 0.5f, 20));                  // คนเดียว เต็มแรง
```
มี `shake`, `flash`, `vignette`, `aberration`, `zoomBlur`, `desaturate` แต่ละอย่างจางหายตามจำนวน tick ของมันเอง; นับรวมใน `maxStartsPerTick` และเคารพ `flashScale` / `shakeScale` ของผู้เล่น

## ยังไม่ได้ทำ

- `AaaParticlesBridge` (`ctx.effek(...)`) — **เลื่อนไปทำเมื่อมีเคสจริง** ตอนนี้ BHSpells จัดการ Effekseer เองอยู่แล้ว
- เชื่อมกับ BHSpells (bridge แบบ soft dependency ตามแผนหัวข้อ 6.0)

## สถานะการทดสอบ

- ✅ 0.1: ทดสอบในเกมแล้ว (โลก flat) เอฟเฟคแสดงผล ไม่มี shader/GL error ใน log
- ✅ 0.2: `gradlew build` ผ่าน; `composite`, `test`, `bloom_down`, `bloom_up` (รวม include แล้ว) คอมไพล์ผ่านใน WebGL2 / GLSL ES 3.00
- ✅ 0.3 (ส่วนที่ทำแล้ว): `gradlew build` ผ่าน; shader ทั้งหมดรวม `ground.glsl` / `sdf2d.glsl` คอมไพล์ผ่านใน WebGL2
- ✅ 0.3 ทดสอบในเกมรอบแรก (dev client, โลก flat + ภูเขา): warmup 4 shader (278 ms ตอนโหลดโลก), `/bhfx_test`, `/bhfx_test pulse`, `/bhfx bench` ทำงาน, GPU ของ bhfx 0.3–0.5 ms ต่อเฟรม, ไม่มี exception
  - 🐞 แก้แล้ว: ตัวจับเวลา GPU ชนกับ query ของ F3 (GL_INVALID_OPERATION ทุกเฟรมตอนเปิด F3) → เปลี่ยนเป็น GL_TIMESTAMP
  - 🐞 แก้แล้ว: bench แบบครึ่งแรก/ครึ่งหลังให้ค่าติดลบเพราะ FPS แกว่งตามเวลา → สลับปิด/เปิดทุก 1 วินาที
  - bench 30 วินาที: ส่วนต่าง +2.0 ms เมื่อมีสั่นจอ/FOV punch แต่ **+0.09 ms** เมื่อปิด (`shakeScale=0`, `control=NONE`) — การวาดของ bhfx เองถูกมาก ส่วนใหญ่เป็นค่าที่ Minecraft คำนวณ chunk ที่มองเห็นใหม่ทุกเฟรมเมื่อกล้องขยับ
- ⏳ 0.3 ยังไม่ได้ทดสอบ: CameraParams แบบ FULL, การรวม packet บน dedicated server, การตรวจ shader pack (ต้องมี Oculus), tracker
- ⏳ 0.2 **ยังไม่ได้ทดสอบในเกม**: bloom, impact frame, ripple, การลดความละเอียดตามเวลา GPU, การลดคุณภาพอัตโนมัติ, tracker (เดินเข้าระยะทีหลัง / login ระหว่างเอฟเฟค / ตายแล้วเกิดใหม่), dedicated server
