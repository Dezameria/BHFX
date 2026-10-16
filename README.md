# bhfx (BeeHouse FX)

Library mod สำหรับ Forge 1.20.1: เอฟเฟค post-processing และ shader ในโลกที่รู้ depth ให้ BHSpells และ mod อื่นเรียกใช้ได้
เป็น **ชั้นเสริม** — mod ที่ใช้ยังคงหน้าตาหลักจาก Effekseer/GeckoLib และทำงานได้ถ้าไม่มี bhfx

- แผน API ฉบับเต็ม: [`docs/API_PLAN.md`](docs/API_PLAN.md)
- เวอร์ชัน: **0.1.0** (API ยังเปลี่ยนได้)

## Build / ใช้งาน

```bash
./gradlew.bat build                 # jar อยู่ที่ build/libs/bhfx-0.1.0.jar
./gradlew.bat publishToMavenLocal   # ให้ BHSpells อ่านได้จาก mavenLocal()
./gradlew.bat runClient             # ทดสอบ
```

ฝั่ง BHSpells (soft dependency, ดู `docs/API_PLAN.md` หัวข้อ 6.0):
```gradle
repositories { mavenLocal() }
dependencies {
    compileOnly fg.deobf("net.offkung:bhfx:0.1.0")
    runtimeOnly fg.deobf("net.offkung:bhfx:0.1.0")   // เฉพาะตอน dev ถ้าอยากรันพร้อมกัน
}
```

## ทดสอบในเกม

| คำสั่ง | ฝั่ง | ทำอะไร |
| :--- | :--- | :--- |
| `/bhfx_test [radius]` | server (op 2) | เล่นเอฟเฟคทดสอบตรงที่มอง: วงเตือนบนพื้น → ลำแสงที่ภูเขาบังถูกต้อง + แสงวาบ + สั่นจอ + พื้นร้อนแล้วเย็นลง |
| `/bhfx status` | client | สถานะ: เปิด/ปิด, จำนวน fx/pass, ความละเอียด FX, VRAM โดยประมาณ |
| `/bhfx off` / `/bhfx on` | client | ปิด/เปิดชั่วคราวเพื่อเทียบ FPS (ปิด = ไม่สร้าง fx และไม่แตะ GPU) |
| `/bhfx reload` | client | คอมไพล์ shader ใหม่ และล้างสถานะ "ปิดเพราะ GL error" (F3+T ก็ได้) |

## Config

- `config/bhfx-client.toml`: `quality` (OFF/LOW/MEDIUM/HIGH), `maxFxMegapixels`, `maxActiveFx`, `maxPassesPerFrame`, `cullDistance`, `flashScale`, `shakeScale`
- `config/bhfx-server.toml`: `enabled` (false = ไม่ส่งเอฟเฟค, mod ที่ใช้กลับไปใช้ทางสำรอง), `maxRange`

## ถอดออก

ลบ `bhfx-*.jar` ออกจาก **ทั้ง** server และ client (network channel เป็นแบบ strict ต้องมีหรือไม่มีเหมือนกันทั้งสองฝั่ง) bhfx ไม่เก็บอะไรลง save โลกจึงไม่เสีย

## โครงสร้าง

```
net.offkung.bhfx
 ├─ api/            BHFx, FxSpawn, FxParam(s), FxHandle, StopMode, Follow, Audience
 │   ├─ timeline/   Timeline, Ease
 │   └─ client/     Fx, FxInit, FxFactory, FxContext, FrameContext, PassList, PassOptions, Uniforms,
 │                  ScreenParams, BHFxClient, LocalFxHandle, RegisterFxEvent
 └─ impl/           (ภายใน เปลี่ยนได้ตลอด)
     ├─ net/        channel bhfx:main + S2CFxStart / S2CFxStop
     ├─ server/     ServerFx (id, อายุ), ServerFxEvents (/bhfx_test)
     ├─ client/     ClientFxManager, ClientEvents (/bhfx), test/TestFx
     └─ render/     PostRenderer, GlStateGuard, ShaderManager, UniformBinder
assets/bhfx/shaders/bhfx/
 ├─ include/        scene.glsl, shapes.glsl, light.glsl, noise.glsl
 ├─ composite.fsh
 └─ test.fsh
```

Shader ของ mod อื่นวางที่ `assets/<ns>/shaders/bhfx/<name>.fsh` แล้วเรียกด้วย `"<ns>:<name>"`;
include ด้วย `#include "bhfx:light.glsl"` (ไฟล์ include อยู่ที่ `assets/<ns>/shaders/bhfx/include/`)

## ส่วนที่ 0.1 ทำต่างจากแผน

| เรื่อง | ในแผน | ใน 0.1 | เหตุผล |
| :--- | :--- | :--- | :--- |
| Hook การเรนเดอร์ | Mixin `LevelRenderer` | **ไม่มี mixin เลย**: `RenderLevelStageEvent` (เก็บ matrix ที่ `AFTER_SKY`/`AFTER_SOLID_BLOCKS`, วาดที่ `AFTER_LEVEL`) + `ViewportEvent` สำหรับสั่นจอ | เข้ากับ mod อื่นง่ายกว่า ไม่ต้อง refmap |
| Depth | snapshot ทั้ง color และ depth | sample depth ของ Minecraft ตรงๆ, copy แค่ color | FBO ของ bhfx ไม่ attach depth จึงไม่มี feedback loop ประหยัด ~8 MB ที่ 1080p |
| Duration | override `duration()` | override **`defaultDuration()`**; `duration()` เป็น final และเคารพค่าใน `FxSpawn` | ให้ฝั่งที่สั่งกำหนดเวลาได้เสมอ |
| `ScreenParams` | ครบทุกค่า | เฉพาะ `shake`, `flash`, `vignette`, `desaturate`, `aberration` | ที่เหลือ (bloom, impact ฯลฯ) อยู่ใน 0.2 ตามแผน |
| `PassOptions` | `order` + `resolution` | เฉพาะ `order` | ทุก pass ใช้ความละเอียด FX เดียวกันใน 0.1 |
| คำสั่งทดสอบ | `/bhfx test` | `/bhfx_test` (server) แยกจาก `/bhfx ...` (client) | client กับ server command ใช้ชื่อ root เดียวกันแล้วชนกัน |
| Texture ใน pass | — | ครั้งแรกที่ขอจะโหลดหลังจบเฟรม texture จึงขึ้นตั้งแต่เฟรมถัดไป | ไม่ให้การโหลดของ Minecraft ไปเปลี่ยน GL state ระหว่างที่ bhfx ถืออยู่ |
| Packet batching | 0.1 | ยังไม่ทำ (ย้ายไป 0.2 พร้อม rate limit) | 0.1 ส่ง 1 packet ต่อการ start |

## สถานะการทดสอบ 0.1

- ✅ `gradlew build` ผ่าน
- ✅ `composite.fsh` และ `test.fsh` (รวม include แล้ว) คอมไพล์ผ่านใน WebGL2 / GLSL ES 3.00 (เข้มกว่า GLSL 150)
- ⏳ **ยังไม่ได้ทดสอบในเกมจริง**: ต้อง `runClient` แล้ว `/bhfx_test` เพื่อดูภาพ, เช็ค log ว่าไม่มี shader error, และทดสอบบน dedicated server (`runServer`)
