# การปล่อยใช้งานจริง (build, ติดตั้ง, ตรวจ)

## Build jar สำหรับใช้จริง

ลำดับสำคัญ: bhfx ก่อน — `build` ของ bhfx copy jar ไปที่ `BHSpells/libs/` ให้เอง (task `copyToBHSpells`) แล้ว BHSpells compile กับตัวนั้น

```bash
cd bhfx
./gradlew.bat build publishToMavenLocal -PrequireGlslCheck
cd ../BHSpells
./gradlew.bat build -PrequireGlslCheck
```

glslang หาเจอเองจาก `ironspell_more/tools/glslang` (ดู [`shaders.md`](shaders.md))

| jar ที่ได้ | ไฟล์ |
| :--- | :--- |
| bhfx | `bhfx/build/libs/bhfx-<version>.jar` (ไม่ใช่ `-sources.jar`) |
| BHSpells | `BHSpells/build/libs/bhspells-1.20.1-<version>-forge.jar` (ไม่ใช่ `-sources.jar`) |

jar ที่ build จริงเป็น production: ไม่มี `/bhfx_test` (คำสั่งฝั่งผู้เล่น `/bhfx on|off|stats|status|bench|reload` ยังมี)

## ติดตั้ง

| ที่ | ต้องมี |
| :--- | :--- |
| เซิร์ฟเวอร์ | BHSpells + mod ที่ BHSpells บังคับ (ตารางล่าง) + bhfx (ถ้าใช้) |
| client ทุกคน | ชุดเดียวกับเซิร์ฟเวอร์ทุกตัว |
| เล่นคนเดียว | ทุกอย่างในเครื่องเดียว |

- **bhfx ไม่บังคับ** แต่ถ้าลง ต้องลง**ทั้งเซิร์ฟและทุก client** (network channel แบบ strict) เวอร์ชันต้องอยู่ในช่วงที่ BHSpells รับ (ตอนนี้ `[0.3,0.4)`)
- ปิด/ถอด bhfx ได้ทุกเมื่อ (ดู `../README.md` หัวข้อ "ถอดออก") โลกไม่เสีย

### mod ที่ BHSpells บังคับ (`BHSpells/src/main/resources/META-INF/mods.toml`)

ประกาศเป็น `mandatory=true` เมื่อ 2026-10-17 เพราะโค้ดของ BHSpells (spell, entity, effect, mixin) เรียก mod เหล่านี้ตรงๆ ไม่ได้ผ่าน compat — ถ้าขาด Forge จะแจ้งตั้งแต่หน้าโหลดแทนการ crash
ขอบล่างของเวอร์ชัน = เวอร์ชันที่ใช้ build/ทดสอบ

| modId | ขั้นต่ำ | ใช้ทำอะไร |
| :--- | :--- | :--- |
| `irons_spellbooks` | 1.20.1-3.16.1 | ระบบเวท (ดึง `irons_lib`, `playeranimator`, `curios` มาเอง) |
| `geckolib` | 4.8.3 | entity/model ของเวท |
| `epicfight` | 20.14.17 | skill, effect, mixin |
| `traveloptics` | 6.3.0-1.20.1 | config/item/effect ที่เวทใช้ (ต้องมี `cataclysm`, `alexscaves`, `attributeslib` ตามมา) |
| `aaa_particles` | 2.2.3 | Effekseer |
| `alexscaves` | 2.0.2 | ภาพ/entity ของเวท |
| `citadel` | 2.6.3 | particle |
| `bhweapons` | 0.3.9 | อาวุธ BeeHouse ที่ effect/renderer ใช้ |
| `dungeons_and_combat` | 1.2.2 | เวทสายทอง |
| `iss_magicfromtheeast` | ทุกเวอร์ชัน | entity ของเวทน้ำแข็ง/หยก |
| `wom` | 2.0.162 | Gale Drive, Venomous Blossomfall |
| `invincible` | 20.14.8.2 | คอมโบ Jing Guang Pan |
| `efn` | 3.3.4 | Amethyst Decree, Wheel of Karma events |
| `wukong` | 1.0.0 | Wheel of Karma |
| `epicfightx` | 1.2 | animation ฝั่ง client |

ไม่บังคับ: `bhfx`, `epic_fight_avalon` (ใช้เฉพาะใน `compat/`)
เมื่อเพิ่ม import จาก mod ใหม่นอก `compat/` ต้องเพิ่มลงตารางนี้และ `mods.toml` ด้วย (วิธีหา: grep `^import` แยกตาม package แล้วดูว่าอยู่นอก `compat/` ไหม)

## ตรวจก่อนปล่อย

- [ ] build ทั้งสอง repo ผ่าน พร้อม `-PrequireGlslCheck`
- [ ] jar bhfx มี `assets/bhfx/shaders/bhfx/` ครบ (pass 4 + include 6)
- [ ] jar BHSpells มี `assets/bhspells/shaders/bhfx/*.fsh` และ `mods.toml` ที่ expand แล้ว (dependency ครบ)
- [ ] dedicated server เปิดได้กับชุด mod จริง (ไม่มี client class crash)
- [ ] เกมที่ไม่มี bhfx เล่นได้
- [ ] multiplayer ตาม checklist ใน [`testing.md`](testing.md)
