# บั๊ก

รูปแบบ: **อาการ → สาเหตุ → วิธีแก้ (หรือทางเลี่ยง)** พร้อมวันที่และไฟล์ ถ้ายังไม่แก้ให้อยู่ในหัวข้อ "ยังไม่แก้"

## ยังไม่แก้ / ข้อจำกัดที่รู้

- **`FxKey` แยกตามมิติ** (`ServerFx` ใช้ `KeyInLevel(dimension, key)`): ถ้าโค้ดที่เรียก stop รู้แค่ level ปัจจุบันของผู้ร่าย (เช่น Iron's ยกเลิก cast หลังเปลี่ยนมิติแล้ว) จะหา key ไม่เจอ
  - ทางเลี่ยงตอนนี้: BHSpells วนเรียก `BHFx.stop` ทุก level (`BhfxLoadedBridge.annihilationChargeEnd`)
  - ทางแก้ที่คิดไว้: เพิ่ม API `stopEverywhere(server, key)` (ตัดสินใจในขั้นล็อก API ของ [`roadmap.md`](roadmap.md))

## แก้แล้ว

### 2026-10-17 — Fx ที่ follow entity ค้างบนเซิร์ฟถาวร
- **อาการ**: entity ที่ follow หาย/ตาย/เปลี่ยนมิติภายใน 10 tick แรก แล้วเอฟเฟค `UNTIL_STOPPED` ไม่ถูกลบ ถูกส่งซ้ำให้คนที่เดินผ่านจุดเดิม และกินโควตา `maxActivePerCaster`
- **สาเหตุ**: `followLost()` คืน true เฉพาะเมื่อ `followSeen` เป็น true แต่ flag นี้ตั้งเฉพาะใน tracker ที่วิ่งทุก 10 tick
- **แก้**: `ServerFx.start` ตั้ง `followSeen` ทันทีถ้า `level.getEntity(followId)` มีอยู่
- พบโดย: `bhspells-reviewer` ตอนรีวิว Annihilation Arrow

### 0.3 — ตัวจับเวลา GPU ชนกับ F3
- **อาการ**: `GL_INVALID_OPERATION` ทุกเฟรมตอนเปิด F3
- **สาเหตุ**: `GL_TIME_ELAPSED` query ซ้อนกับ query ของ F3 ไม่ได้
- **แก้**: `GpuTimer` เปลี่ยนเป็น `GL_TIMESTAMP`

### 0.3 — `/bhfx bench` ให้ค่าติดลบ
- **อาการ**: bench แบบครึ่งแรก/ครึ่งหลังได้ส่วนต่างติดลบ
- **สาเหตุ**: FPS แกว่งตามเวลาเอง
- **แก้**: สลับปิด/เปิดการวาดทุก 1 วินาที
