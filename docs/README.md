# bhfx — development knowledge base

All bhfx development knowledge lives here as markdown, split into categories, so that people and Claude can pick up where the work left off.
> `docs/` and `README.md` are in git (Dezameria/BHFX). `.claude/` (the `bhfx-notes` skill) and `CLAUDE.md` are gitignored and kept locally only.

**Before starting bhfx work, read this file first, then open only the categories you need.** **When the work is done, record the results in the right category.** The steps are in the `bhfx-notes` skill at `.claude/skills/bhfx-notes/SKILL.md`.

## Categories

| File | Holds | Update when |
| :--- | :--- | :--- |
| [`worklog.md`](worklog.md) | Daily work log, newest first: what was done, which files, build/test results | At the end of every task |
| [`roadmap.md`](roadmap.md) | Open tasks, the 1.0 plan, backlog, decisions waiting on the user | A task is added or finished, or the plan changes |
| [`testing.md`](testing.md) | Test status (✅ passed / ⏳ not tested) and in-game checklists | Something gets tested, or new work needs testing |
| [`bugs.md`](bugs.md) | Known open bugs and fixed bugs (symptom → cause → fix) | A bug is found or fixed |
| [`decisions.md`](decisions.md) | Design decisions with their reasons | A decision with long-term effect is made |
| [`shaders.md`](shaders.md) | Shader checking (`checkShaders` + glslang), shader conventions, pitfalls | The check changes, or a new pitfall turns up |
| [`release.md`](release.md) | Release builds, server/client install, mods BHSpells requires, pre-release checklist | Dependencies or the release process change |
| [`integrations/bhspells.md`](integrations/bhspells.md) | How BHSpells uses bhfx: the bridge, which spells, lessons from real use | A spell starts using bhfx, or something new comes up on the BHSpells side |
| [`API_PLAN.md`](API_PLAN.md) | Full public API spec and design principles | The API is added to or changed |

Docs that live elsewhere:
- [`../README.md`](../README.md): overview for users (build, commands, config, lag protection).
- `../BHSpells/docs/bhfx/`: how each spell's effect was designed (for example `heavenly_combo.md`), shader techniques, and tools for previewing shaders in a browser.
- `../BHSpells/docs/spells/<school>/<spell_id>.md`: each spell's spec, including its bhfx part. BHSpells rules require updating it.

## Recording rules

1. **Keep one fact in one place.** Link to it from elsewhere instead of copying it.
2. **Always write full dates** (`2026-10-17`). Never write "yesterday" or "latest".
3. **Language**: write the notes in English, with class, method, file and command names exactly as in the code, in `backticks`.
4. **Fix or strike text that is no longer true.** Never leave two lines that contradict each other (for example, change ⏳ to ✅ instead of adding a new line).
5. **State how sure you are**: keep "build passed", "tested in game" and "not tested" clearly separate.
