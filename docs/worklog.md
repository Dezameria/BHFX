# Worklog

บันทึกงานรายวัน ใหม่สุดอยู่บนสุด รูปแบบ:

```
## YYYY-MM-DD — หัวข้อสั้นๆ
- repo: ไฟล์ที่แตะ — เปลี่ยนอะไร
- ผล: build / ทดสอบในเกม / ยังไม่ได้ทดสอบ
- ต่อ: งานที่เหลือ (ย้ายไป roadmap.md ถ้ายาว)
```

---

## 2026-10-17 — gitignore ไฟล์ Claude, เก็บ docs ใน git
- bhfx `.gitignore`: `.claude/`, `CLAUDE.md`, `.mcp.json`, `.agents/`, `AGENTS.md` — **`docs/` และ `README.md` ยังอยู่ใน git** (ผู้ใช้ขอให้เก็บไว้ใน Dezameria/BHFX)
- BHSpells `.gitignore`: เพิ่ม `*.md` และ `run-data` (BHSpells ไม่เก็บ markdown ใน git เลย)
- ลบบรรทัด `Co-Authored-By: Claude` ออกจากทุก commit: bhfx `main` (force push ไป Dezameria/BHFX), BHSpells รวมเป็น commit เดียว `b18767c` ที่ Yumeko-1234 `feature/deizy-integrate-bhfx`; `feature/deizy-integrate-1.6.2` ไม่ได้แตะ
- branch สำรองในเครื่อง: bhfx `backup/main-before-strip`, BHSpells `backup/feature-fx-before-squash`, `backup/feature-fx-before-resquash`

## 2026-10-17 — bhfx jar ใน `BHSpells/libs`
- bhfx `build.gradle`: task `copyToBHSpells` (หลัง `reobfJar`, ผูกท้าย `build`) copy jar ไป `../BHSpells/libs` แทนตัวเก่า
- BHSpells: commit `libs/bhfx-0.3.0.jar` เพื่อให้เพื่อน clone แล้ว build ได้ (ยังไม่ push)
- ผล: build bhfx แล้ว jar ไปอยู่ใน `libs/` ถูกต้อง; ย้าย bhfx ออกจาก mavenLocal ชั่วคราวแล้วสั่ง BHSpells `compileJava --refresh-dependencies` ผ่าน (exit 0) = เพื่อนที่ไม่มี mavenLocal build ได้จาก `libs/`; ย้าย mavenLocal กลับแล้ว

## 2026-10-17 — glslang ถาวร
- `D:\Minecraft\Dev\ironspell_more\tools\glslang\bin\glslang.exe` (16.6.0, 4.9 MB) + `VERSION.txt`
- bhfx + BHSpells `gradle/glsl-check.gradle`: หา `<repo>/../tools/glslang/bin` อัตโนมัติ (ไฟล์ยังเหมือนกันทั้งสอง repo)
- ผล: `checkShaders -PrequireGlslCheck` โดยไม่ระบุ path ผ่านทั้งสอง repo (bhfx 4, BHSpells 2)

## 2026-10-17 — เตรียมใช้งานจริง
- BHSpells `mods.toml`: ประกาศ mod ที่ BHSpells เรียกตรงๆ 15 ตัวเป็น `mandatory=true` (รายการใน [`release.md`](release.md))
- bhfx: [`release.md`](release.md) (build, ติดตั้ง, checklist)
- ผล: build ทั้งสอง repo ผ่านพร้อม `-PrequireGlslCheck`; ตรวจ jar แล้ว (bhfx มี shader 10 ไฟล์, BHSpells มี shader 2 ตัว + dependency 17 รายการ); `runServer` ของ BHSpells โหลด mod ทั้งหมดผ่าน (dependency check ผ่าน, เจอ bhfx, registry freeze) แต่เปิดโลกไม่ได้เพราะมี dev server อีกตัวรันอยู่ใน `run/` และล็อกโลกไว้ — เข้าโลกบน dedicated server ยังไม่ได้ยืนยันรอบนี้
- ต่อ: เปิด dedicated server อีกครั้งตอนไม่มีตัวอื่นรัน; multiplayer กับเพื่อน

## 2026-10-17 — ระบบบันทึก markdown + skill `bhfx-notes`
- bhfx: สร้าง `docs/README.md` (สารบัญ), `worklog.md`, `roadmap.md`, `testing.md`, `bugs.md`, `decisions.md`, `shaders.md`, `integrations/bhspells.md`; ย้าย "สถานะการทดสอบ" และ "ใช้งานจริงใน BHSpells" ออกจาก `README.md` มาไว้ในหมวด
- bhfx: `.claude/skills/bhfx-notes/SKILL.md` + `CLAUDE.md` ชี้ไปที่ `docs/README.md`

## 2026-10-17 — ตรวจ shader ด้วย glslang
- bhfx + BHSpells: `gradle/glsl-check.gradle` (ไฟล์เดียวกัน) + task `checkShaders` ผูกกับ `check`/`build` — รายละเอียดใน [`shaders.md`](shaders.md)
- ผล: glslang 16.6.0 ตรวจผ่านทั้ง 6 ตัว (bhfx 4: `test`, `composite`, `bloom_down`, `bloom_up`; BHSpells 2: `heavenly_combo`, `annihilation_charge`); ลองทำให้ผิดโดยตั้งใจแล้ว glslang จับได้ บอกบรรทัดถูก
- ต่อ: ~~ติดตั้ง glslang ถาวร~~ ทำแล้ว (ดูรายการ "glslang ถาวร")

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
