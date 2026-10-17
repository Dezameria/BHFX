---
name: bhfx-notes
description: Read and record development knowledge for the bhfx (BeeHouse FX) Forge 1.20.1 post-processing library, and for the bhfx side of BHSpells, in the markdown knowledge base at bhfx/docs/. Use it at the START of any bhfx task (to load the relevant notes, such as roadmap, test status, known bugs, decisions and shader rules) and at the END of every bhfx task that changed code, docs, tests, plans or decisions (to write the results back into the right category). Also use it whenever the user asks what's next, what's left, what was done, what's been tested, or asks to note, record or บันทึก something about bhfx.
---

# bhfx notes: the markdown knowledge base

All bhfx development knowledge lives as markdown in `docs/` of the bhfx repo (`D:\Minecraft\Dev\ironspell_more\bhfx\docs\`). Markdown is the primary store. Don't keep bhfx knowledge only in chat or in memory. The notes are written in Thai, and code identifiers stay as they are in the code, in backticks.

## Categories

| File | Holds | Read it when |
| :--- | :--- | :--- |
| `docs/README.md` | Index and recording rules | Always first |
| `docs/worklog.md` | Dated work log, newest on top | Asked "what was done" or resuming work |
| `docs/roadmap.md` | Open tasks, the 1.0 plan, backlog, decisions waiting on the user | Asked "what's next" or picking work |
| `docs/testing.md` | ✅/⏳ test status and in-game checklists | Before claiming something works, or planning tests |
| `docs/bugs.md` | Known limitations, plus fixed bugs (symptom → cause → fix) | Touching `ServerFx`, tracker, render, or seeing odd behaviour |
| `docs/decisions.md` | Design decisions with reasons | Before changing API or behaviour |
| `docs/shaders.md` | `checkShaders`/glslang pipeline, shader pitfalls | Writing or editing any `.fsh`/`.glsl` |
| `docs/integrations/bhspells.md` | How BHSpells uses bhfx, and lessons from real use | Any BHSpells ↔ bhfx work |
| `docs/API_PLAN.md` | Full API spec | Adding or changing public API |

Related docs that live elsewhere:
- `BHSpells/docs/bhfx/`: effect design notes per spell, plus shader techniques.
- `BHSpells/docs/spells/<school>/<id>.md`: the spell spec. BHSpells rules require updating it on every spell change.

## At the start of a task

1. Read `docs/README.md`.
2. Read only the categories the task needs (see the table above). For "what's next" questions, read `roadmap.md` and `testing.md`, and answer from them.
3. Treat what the notes say as true at the time it was written. Check that a file, method or flag still exists before relying on it.

## At the end of a task (record)

Update every category the work touched, in the same turn:

1. **`worklog.md`** (always): add a new section at the top.
   ```
   ## YYYY-MM-DD — short title
   - repo: files touched — what changed
   - result: build / tested in game / not tested
   - next: what remains (move it to roadmap.md if it is longer)
   ```
2. **`roadmap.md`**: tick finished items as `[x] YYYY-MM-DD ...` and add new follow-ups as `[ ]`.
3. **`testing.md`**: move items from ⏳ to ✅ with the date and how it was verified. New untested work goes in as ⏳, with a checklist when it needs in-game testing.
4. **`bugs.md`**: new bugs go under "ยังไม่แก้" (not fixed yet). Fixed bugs move to "แก้แล้ว" (fixed) with symptom → cause → fix.
5. **`decisions.md`**: add any choice with long-term effect, with its reason. Don't delete old decisions; add "แทนที่โดย …" ("replaced by …") instead.
6. **`shaders.md` / `integrations/bhspells.md` / `API_PLAN.md`**: update them when the pipeline, the integration lessons or the API change.

## Rules

- Keep one fact in one place, and link to it from elsewhere instead of copying.
- Write full dates (`2026-10-17`). Never write "today" or "latest".
- Fix or strike stale text so that no two lines contradict each other.
- Separate "build passed", "tested in game" and "not tested" every time. Never upgrade a status without evidence.
- Keep the notes short and factual. Don't paste code that is already in the repo; name the file and symbol instead.
- If a new kind of knowledge doesn't fit any category, add a new file to `docs/` and list it in `docs/README.md` and in the table above.
