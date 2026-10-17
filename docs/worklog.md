# Worklog

บันทึกงานรายวัน ใหม่สุดอยู่บนสุด รูปแบบ:

```
## YYYY-MM-DD — หัวข้อสั้นๆ
- repo: ไฟล์ที่แตะ — เปลี่ยนอะไร
- ผล: build / ทดสอบในเกม / ยังไม่ได้ทดสอบ
- ต่อ: งานที่เหลือ (ย้ายไป roadmap.md ถ้ายาว)
```

---

## 2026-10-17 — ระบบบันทึก markdown + skill `bhfx-notes`
- bhfx: สร้าง `docs/README.md` (สารบัญ), `worklog.md`, `roadmap.md`, `testing.md`, `bugs.md`, `decisions.md`, `shaders.md`, `integrations/bhspells.md`; ย้าย "สถานะการทดสอบ" และ "ใช้งานจริงใน BHSpells" ออกจาก `README.md` มาไว้ในหมวด
- bhfx: `.claude/skills/bhfx-notes/SKILL.md` + `CLAUDE.md` ชี้ไปที่ `docs/README.md`

## 2026-10-17 — ตรวจ shader ด้วย glslang
- bhfx + BHSpells: `gradle/glsl-check.gradle` (ไฟล์เดียวกัน) + task `checkShaders` ผูกกับ `check`/`build` — รายละเอียดใน [`shaders.md`](shaders.md)
- ผล: glslang 16.6.0 ตรวจผ่านทั้ง 6 ตัว (bhfx 4: `test`, `composite`, `bloom_down`, `bloom_up`; BHSpells 2: `heavenly_combo`, `annihilation_charge`); ลองทำให้ผิดโดยตั้งใจแล้ว glslang จับได้ บอกบรรทัดถูก
- ต่อ: ติดตั้ง glslang ถาวร (ตอนนี้อยู่ใน scratchpad ของ session ซึ่งเป็นที่ชั่วคราว)

## 2026-10-17 — housekeeping 1–3
- BHSpells `mods.toml`: เวอร์ชัน bhfx ที่รับ `[0.3,)` → `[0.3,0.4)`
- bhfx `ServerFxEvents`: `/bhfx_test` ลงทะเบียนเฉพาะ dev (`!FMLEnvironment.production`)
- bhfx `BHFxClient`: เพิ่ม `update(LocalFxHandle, FxParams)`, `fx(LocalFxHandle, Class)`, `isAlive(LocalFxHandle)`; BHSpells `BhfxClientBridge` เลิกใช้ static `created` แล้วใช้ `BHFxClient.fx(...)`
- ผล: build ผ่านทั้งสอง repo, `publishToMavenLocal` แล้ว; Heavenly Combo หลังเปลี่ยน bridge ยังไม่ได้ทดสอบในเกม

## 2026-10-17 — แก้ ServerFx `followSeen` leak + อัปเดตเอกสาร
- bhfx `ServerFx.start`: ตั้ง `followSeen` ทันทีถ้า entity ที่ follow มีอยู่ (ดู [`bugs.md`](bugs.md))
- bhfx `README.md`, `build.gradle`, `docs/API_PLAN.md`: แก้เวอร์ชัน/โปรโตคอลที่ค้าง
- ผล: build ผ่าน, `publishToMavenLocal` แล้ว

## 2026-10-17 — Annihilation Arrow: ผู้ใช้จริงคนแรกของ `BHFx.start/update/stop`
- BHSpells: `compat/bhfx/BhfxIds`, `BhfxCompat`/`BhfxLoadedBridge` (3 method), `client/AnnihilationChargeFx`, shader `annihilation_charge.fsh`, hook ใน `AnnihilationArrowSpell`, doc `docs/spells/fire/annihilation_arrow.md`
- รีวิวด้วย `bhspells-reviewer`: แก้ 3 IMPORTANT + 3 OPTIONAL (ดู [`integrations/bhspells.md`](integrations/bhspells.md))
- ผล: build ผ่าน, ผู้ใช้ทดสอบในเกมผ่าน ยกเว้น multiplayer
