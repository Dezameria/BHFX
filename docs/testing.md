# การทดสอบ

✅ = ผ่าน (บอกว่าผ่านแบบไหน), ⏳ = ยังไม่ได้ทดสอบ พอทดสอบแล้วให้ย้ายจาก ⏳ เป็น ✅ พร้อมวันที่ อย่าเพิ่มบรรทัดใหม่ที่ขัดกัน

## bhfx

- ✅ 0.1: ทดสอบในเกมแล้ว (โลก flat) เอฟเฟคแสดงผล ไม่มี shader/GL error ใน log
- ✅ 0.2: `gradlew build` ผ่าน; `composite`, `test`, `bloom_down`, `bloom_up` (รวม include แล้ว) คอมไพล์ผ่านใน WebGL2 / GLSL ES 3.00
- ✅ 0.3 (ส่วนที่ทำแล้ว): `gradlew build` ผ่าน; shader ทั้งหมดรวม `ground.glsl` / `sdf2d.glsl` คอมไพล์ผ่านใน WebGL2
- ✅ 0.3 ทดสอบในเกมรอบแรก (dev client, โลก flat + ภูเขา): warmup 4 shader (278 ms ตอนโหลดโลก), `/bhfx_test`, `/bhfx_test pulse`, `/bhfx bench` ทำงาน, GPU ของ bhfx 0.3–0.5 ms ต่อเฟรม, ไม่มี exception
  - bench 30 วินาที: ส่วนต่าง +2.0 ms เมื่อมีสั่นจอ/FOV punch แต่ **+0.09 ms** เมื่อปิด (`shakeScale=0`, `control=NONE`) — การวาดของ bhfx เองถูกมาก ส่วนใหญ่เป็นค่าที่ Minecraft คำนวณ chunk ที่มองเห็นใหม่ทุกเฟรมเมื่อกล้องขยับ
- ✅ dedicated server (dev): server เปิดได้, client ต่อได้, tracker ส่งเอฟเฟคต่อหลัง login ใหม่ และหลังตายแล้วเกิดใหม่
- ✅ 2026-10-17 glslang 16.6.0: shader ของ bhfx 4 ตัวผ่าน (`checkShaders`)
- ⏳ 0.3: tracker กรณีเดินเข้าระยะทีหลัง (`/bhfx_test tracker`), ผู้เล่น 2 คน, `CameraParams` แบบ FULL, การตรวจ shader pack (ต้องมี Oculus)
- ⏳ 0.2 ในเกม: bloom, impact frame, ripple, การลดความละเอียดตามเวลา GPU, การลดคุณภาพอัตโนมัติ, tracker (เดินเข้าระยะทีหลัง / login ระหว่างเอฟเฟค / ตายแล้วเกิดใหม่)
- ⏳ 2026-10-17: `followSeen` fix — เปลี่ยนมิติภายใน 0.5 วินาทีหลังเริ่ม แล้วดูว่าเอฟเฟคไม่ค้างบนเซิร์ฟ
- ⏳ 2026-10-17: `/bhfx_test` ไม่มีใน jar ที่ build จริง (production)

## BHSpells ที่ใช้ bhfx

### Annihilation Arrow (`bhspells:annihilation_charge`)
- ✅ 2026-10-17 ผู้ใช้ทดสอบในเกม: shader คอมไพล์และแสดงผล, ชาร์จจนเต็ม (halo) → ยิง (streak), ยกเลิก/ตายระหว่างชาร์จ, เกมที่ไม่มี bhfx
- ✅ 2026-10-17 glslang ผ่าน
- ⏳ multiplayer — checklist:
  1. เพื่อนยืนดูห่าง ~20 บล็อกตอนชาร์จ เห็นแสง/เส้นไฟไหม
  2. เพื่อนเดินเข้ามาจากนอก 64 บล็อกระหว่างชาร์จ (ก่อนและหลังชาร์จเต็ม) เห็นต่อจากจังหวะเดิม และ halo ไม่วาบซ้ำ
  3. เซิร์ฟ view-distance ต่ำ (≤ 4) เพื่อนอยู่ ~50 บล็อก แล้วเดินเข้ามา: เอฟเฟคต้องไม่ยุบหายเอง
  4. ยิง, ยกเลิก (สลับไอเทม), ตาย, logout, เปลี่ยนมิติระหว่างชาร์จ — เพื่อนเห็นจบถูกแบบ (ยิง = แสงพุ่ง, อื่นๆ = ยุบ)
  5. mob ที่ร่ายเวทนี้ (ถ้ามี) ถูกขัดแล้วต้องยุบ ไม่ใช่แสงพุ่ง

### Heavenly Combo (`bhspells:heavenly_combo`)
- ✅ 2026-10-17 glslang ผ่าน
- ⏳ 2026-10-17 หลังเปลี่ยน bridge มาใช้ `BHFxClient.fx(...)`: ต่อยแล้วเอฟเฟคขึ้นเหมือนเดิม (ออร่า, หมัด, slam)
- ข้อที่ต้องดูเพิ่ม: ดู `../BHSpells/docs/bhfx/heavenly_combo.md`
