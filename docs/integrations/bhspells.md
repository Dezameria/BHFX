# BHSpells ใช้ bhfx ยังไง

BHSpells (`../BHSpells`) ใช้ bhfx แบบ **soft dependency**: ไม่มี bhfx ก็เล่นได้ปกติ (หน้าตาหลักยังเป็น Effekseer/GeckoLib/particle) ตาม `API_PLAN.md` หัวข้อ 6.0 และ 11.1

## โครงสร้าง bridge (`net.offkung.bhspells.compat.bhfx`)

| คลาส | ฝั่ง | หน้าที่ |
| :--- | :--- | :--- |
| `BhfxCompat` | common | จุดที่โค้ด spell เรียก; **ห้าม import bhfx**; เช็ค `CompatMods.isBhfxLoaded()` และจับ `LinkageError` |
| `BhfxLoadedBridge` | common | คลาสเดียวฝั่งเซิร์ฟที่เรียก `BHFx.*` |
| `BhfxIds` | common | id ของเอฟเฟค, slot ของ `FxKey`, `FxParam` ที่ใช้ร่วมกันระหว่าง server กับ client (import bhfx ได้ เพราะโหลดผ่าน bridge เท่านั้น) |
| `BhfxPulse` | common | คำอธิบาย ScreenPulse ที่ไม่อ้าง class ของ bhfx |
| `client/BhfxClientCompat` | client | เหมือน `BhfxCompat` ฝั่ง client (เรียกผ่าน `DistExecutor` CLIENT ใน `CompatBootstrap`) |
| `client/BhfxClientBridge` | client | ลงทะเบียน Fx (`RegisterFxEvent`) และเล่น local Fx |
| `client/*Fx` | client | คลาส Fx ของแต่ละเวท |

Shader อยู่ที่ `BHSpells/src/main/resources/assets/bhspells/shaders/bhfx/<name>.fsh`
`mods.toml`: `modId="bhfx" mandatory=false versionRange="[0.3,0.4)"`
`build.gradle`: `compileOnly` + `runtimeOnly fg.deobf("net.deizyn:bhfx:0.3.0")` จาก `mavenLocal()` — **แก้ bhfx แล้วต้อง `publishToMavenLocal` ก่อน BHSpells จะเห็น**

## เวทที่ใช้ bhfx

| เวท | ใช้อะไร | ไฟล์ |
| :--- | :--- | :--- |
| Annihilation Arrow | **ตัวแรกที่ใช้ `BHFx.start` + `FxKey` + `update` + `stop`** จากเซิร์ฟ: เริ่มใน `onServerPreCast`, `update(READY)` ตอนชาร์จครบ 200 tick, `stop` ใน `onServerCastComplete`; follow ผู้ร่าย, `UNTIL_STOPPED`, ระยะ 64, persistent (คนมาทีหลังเห็นต่อ) | `client/AnnihilationChargeFx`, `annihilation_charge.fsh`, spec: `docs/spells/fire/annihilation_arrow.md` |
| Heavenly Combo | `BHFxClient.play` เล่น local Fx จาก cue ที่มีอยู่แล้ว (ไม่มี packet เพิ่ม), `BHFxClient.fx(...)` ดึงตัว Fx มาสั่ง blow/slam | `client/HeavenlyComboFx`, `heavenly_combo.fsh`, การออกแบบ: `docs/bhfx/heavenly_combo.md` |
| Shocking, Lightning Strike | `BHFx.pulse` (ScreenPulse) | ใน spell class ผ่าน `BhfxCompat.pulse` |

## ข้อควรระวังจากการใช้จริง

1. **Iron's ยกเลิก cast หลังเปลี่ยนมิติจาก level ใหม่** แต่ `FxKey` แยกตามมิติ → stop ต้องวนทุก level (`level.getServer().getAllLevels()`) ดู [`../bugs.md`](../bugs.md)
2. **mob ที่ถูกขัด Iron's เรียก `onServerCastComplete(..., cancelled=false)`** (`AbstractSpellCastingMob.cancelCast` → `castComplete`) → ถือว่าถูกขัดเมื่อไม่ใช่ `Player` และ `getCastDurationRemaining() > 0`
3. **คนที่เข้ามาทีหลังอาจได้ start ก่อน client จะ track ผู้ร่าย** (bhfx ส่งตามระยะฝั่งเซิร์ฟ 64 บล็อก แต่ vanilla track ตาม view distance) → Fx ฝั่ง client ห้ามจบตัวเองเพราะ "ไม่เจอผู้ร่าย" จนกว่าจะเคยเห็นแล้ว (`casterSeen`)
4. **ค่าที่ส่งด้วย `update` ถูก merge เข้า params ของคนที่มาทีหลังแล้ว** → constructor ของ Fx ต้องเช็ค `init.params()` ด้วย (เช่น `READY` อยู่แล้ว = แสดง halo โดยไม่วาบซ้ำ)
5. **Iron's ไม่เรียก `onServerPreCast`/`onServerCastComplete` ให้ทุกเส้นทางเสมอ** — Annihilation มี `releaseCharge` ของตัวเองที่เรียก complete เอง; stop ซ้ำไม่เป็นไร (key หายแล้ว = ไม่ทำอะไร)
6. ทุก method ใน `BhfxCompat` ต้องเช็ค `isLoaded()` และจับ `LinkageError` แบบเดียวกัน; โค้ด spell ห้าม import อะไรจาก `net.deizyn.bhfx`
