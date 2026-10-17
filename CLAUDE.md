# bhfx

Forge 1.20.1 post-processing / depth-aware shader library (`net.deizyn.bhfx`), used by BHSpells (`../BHSpells`) as a soft dependency.

- Development knowledge is stored as markdown in `docs/`. Start with `docs/README.md`.
- Use the `bhfx-notes` skill (`.claude/skills/bhfx-notes/SKILL.md`): read the relevant notes before bhfx work, and record the results back into `docs/` when you finish.
- Only `net.deizyn.bhfx.api.*` is public API. `impl.*` can change at any time.
- After changing bhfx, run `./gradlew.bat build publishToMavenLocal` so BHSpells picks it up. `build` also runs `checkShaders` (see `docs/shaders.md`).
