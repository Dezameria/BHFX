# Roadmap

งานที่เหลือและแผน เมื่อทำเสร็จให้ติ๊ก `[x]` พร้อมวันที่ แล้วบันทึกรายละเอียดใน [`worklog.md`](worklog.md)

## ตอนนี้ (0.3.x)

- [ ] ทดสอบ Annihilation Arrow แบบ multiplayer กับเพื่อน (checklist ใน [`testing.md`](testing.md))
- [ ] ทดสอบ Heavenly Combo หลังเปลี่ยน bridge มาใช้ `BHFxClient.fx(...)` (2026-10-17)
- [ ] ติดตั้ง glslang ถาวร: Vulkan SDK หรือตั้ง env `GLSLANG_VALIDATOR` (ดู [`shaders.md`](shaders.md))
- [ ] commit งานค้าง: bhfx 1 commit, BHSpells 2 commit (Heavenly Combo / Annihilation bhfx layer)
- [x] 2026-10-17 แก้ `followSeen` leak ใน `ServerFx`
- [x] 2026-10-17 ล็อกเวอร์ชันใน BHSpells `mods.toml` เป็น `[0.3,0.4)`
- [x] 2026-10-17 `/bhfx_test` เฉพาะ dev
- [x] 2026-10-17 API `BHFxClient.fx/update/isAlive` สำหรับ local handle
- [x] 2026-10-17 `checkShaders` ด้วย glslang

## แผน 1.0

| ขั้น | งาน | เสร็จเมื่อ |
| :--- | :--- | :--- |
| 1. ล็อก API | ไล่ทุกคลาสใน `api.*` แบ่งเป็น นิ่ง / `@ApiStatus.Experimental` / ตัดทิ้ง; ตัดสินใจเรื่อง `stopEverywhere(server, key)`, `ctx.effek(...)`, Timeline จาก JSON; javadoc ครบ | มีรายการ API ที่สัญญาไม่เปลี่ยนตลอด 1.x |
| 2. เทสอัตโนมัติ (JUnit) | `FxParams` (encode/decode, merge, ขนาด), `Timeline`/`Ease`, `ScreenPulse`; แยกตรรกะ tracker ของ `ServerFx` ออกจาก `ServerLevel` แล้วเทสกรณี entity หายใน 10 tick แรก; แยกกฎรวมค่าของ `ScreenParamsImpl`; (เสริม) Forge GameTest | `gradlew test` ผ่าน และบั๊กเก่ามีเทสคุม |
| 3. ตรวจ shader จริง | glslang ถาวร + build ใช้ `-PrequireGlslCheck` | shader ทุกตัวทั้งสอง mod ผ่าน |
| 4. ทดสอบกับ mod อื่น + หลายคน | Embeddium, Oculus (เปิด/ปิด pack), Epic Fight มุม 1/3, dedicated server 2 คน, `CameraParams` FULL | ไม่เหลือ ⏳ ใน [`testing.md`](testing.md) |
| 5. วัด performance | ไม่มี Fx ต่างจากไม่ลง bhfx < 0.1 ms; GPU ของ bhfx ≤ 6 ms; ร่ายครั้งแรกกระตุก < 50 ms; VRAM ≤ 80 MB และคืนหลัง 30 วินาที (`/bhfx bench`, `/bhfx stats`) | ผ่านทุกเกณฑ์บนเครื่องสเปกกลาง 1080p |
| 6. ออก 1.0 | เวอร์ชัน 1.0.0, changelog, BHSpells รับ `[1.0,2.0)` | — |

ลำดับที่แนะนำ: 1 → 2 → 3 ทำในเครื่องได้เลย; 4–5 ต้องเข้าเกม/มีเพื่อน ทำคู่กับเทส multiplayer

## รอผู้ใช้ตัดสินใจ

- แจก bhfx ที่ไหน: modpack ตัวเองอย่างเดียว หรือ Modrinth/CurseForge
- license: คง All Rights Reserved ไหม ถ้าให้ mod คนอื่น import

## Backlog (ยังไม่ได้วางเวลา)

- `AaaParticlesBridge` (`ctx.effek(...)`) — ทำเมื่อมีเคสจริง
- เวทใน BHSpells ที่เหมาะจะใช้ bhfx ต่อ: Indigo Lightning, Fiery Dance, Venomous Blossomfall (LONG/CONTINUOUS ที่มี hook ครบ) และเวทแบบโซนค้างนาน (ทดสอบ tracker กับเอฟเฟคยาว)
