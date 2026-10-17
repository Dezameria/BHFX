# Shader: การตรวจและข้อตกลง

ข้อตกลงพื้นฐาน (ตำแหน่งไฟล์, header ที่ bhfx เติมให้, include ที่มีให้) อยู่ใน [`API_PLAN.md`](API_PLAN.md) หัวข้อ 5
เทคนิคการออกแบบเอฟเฟคและเครื่องมือดูในเบราว์เซอร์อยู่ที่ `../BHSpells/docs/bhfx/` (`techniques.md`, `tools/`)

## `checkShaders` (ตรวจตอน build)

ไฟล์: `gradle/glsl-check.gradle` — **เหมือนกันทั้ง bhfx และ BHSpells** แก้ที่หนึ่งต้อง copy ไปอีกที่
ผูกกับ `check` → `build` จึงรันทุกครั้งที่ `gradlew build`; สั่งเดี่ยวได้ด้วย `gradlew checkShaders`

ขั้นตอน:
1. หาไฟล์ pass ทุกตัว: `assets/<ns>/shaders/bhfx/*.fsh` ใน `src/main/resources` (ไม่รวม `include/`)
2. ประกอบเหมือน `ShaderManager` ในเกม: เติม header (`#version 150` + `DiffuseSampler`, `SceneSampler`, `DepthSampler`, `InvProj`, `InvView`, `ViewProj`, `Eye`, `OutSize`, `ScreenSize`, `GameTime`, `texCoord`, `fragColor`) แล้วแทน `#include "ns:file.glsl"` ด้วยเนื้อไฟล์ใน `assets/<ns>/shaders/bhfx/include/` ใส่ `#line` ให้เลขบรรทัดของ error ตรงกับไฟล์ต้นฉบับ
   - bhfx หา include จาก `src/main/resources`
   - BHSpells หา include จาก `src/main/resources` และ **jar ของ bhfx บน compileClasspath** (ตรวจกับ bhfx เวอร์ชันเดียวกับที่ compile)
3. เขียนผลลง `build/glsl-check/<ns>_<name>.frag` แล้วรัน glslang ทีละไฟล์; มีตัวไหนไม่ผ่าน build fail พร้อม error

**ถ้า header ใน `ShaderManager.HEADER` เปลี่ยน ต้องแก้ `glslHeader` ใน `glsl-check.gradle` ทั้งสอง repo ด้วย**

### หา glslang จากไหน (ตามลำดับ)
1. `-PglslangValidator=<path>`
2. env `GLSLANG_VALIDATOR`
3. `%VULKAN_SDK%\Bin`
4. `PATH` (`glslangValidator.exe` หรือ `glslang.exe`)

หาไม่เจอ: เตือน "shaders were NOT checked" แล้วผ่าน; ใส่ `-PrequireGlslCheck` ให้ fail แทน
ตัวที่ใช้ทดสอบ: glslang 16.6.0 (`glslang-16.6.0-windows-x86_64-release.zip` จาก GitHub release ของ KhronosGroup/glslang) — ไฟล์ข้างในชื่อ `bin/glslang.exe`

### จับอะไรได้ / ไม่ได้
- **ได้**: syntax, type ไม่ตรง, ฟังก์ชัน/ตัวแปรไม่มีหรือพารามิเตอร์ผิด, ชื่อซ้ำกับ include, include หาไม่เจอ, ฟีเจอร์ที่ GLSL 1.50 ไม่มี
- **ไม่ได้**: หน้าตาผิด, ชื่อ uniform ไม่ตรงกับฝั่ง Java (shader จะได้ 0 เงียบๆ), บั๊กเฉพาะ driver, ความช้า (ใช้ `/bhfx stats`, `/bhfx bench`), undefined behavior อย่าง `smoothstep` ที่ขอบกลับด้าน

## กับดักที่เจอ

- **`smoothstep(a, b, x)` ที่ `a > b`**: GLSL ไม่กำหนดผล driver ส่วนใหญ่คำนวณได้แต่ไม่รับประกัน → ใช้ `1.0 - smoothstep(b, a, x)` (เจอใน `annihilation_charge.fsh`, 2026-10-17)
- **`texture()` ใน branch ที่ขึ้นกับพิกเซล**: implicit derivative ไม่กำหนดค่า → ใช้ `textureLod(..., 0.0)` (2026-10-17)
- **`dFdx`/`dFdy` ใน branch**: ค่าไม่กำหนด → คำนวณนอก `if` แล้วค่อยใช้ข้างใน และกลับทิศ normal ให้หันเข้ากล้อง (`dot(nrm, s.dir) > 0.0 ? -nrm : nrm`)
- **ray ต้องเริ่มที่ `Eye` ไม่ใช่ `vec3(0.0)`**: view bobbing ขยับตา (ดู `API_PLAN.md` 5.2)
