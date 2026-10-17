# Shaders: checking and conventions

The basic conventions (file locations, the header bhfx adds, the includes it provides) are in [`API_PLAN.md`](API_PLAN.md) section 5.
Effect design techniques and the in-browser preview tools are in `../BHSpells/docs/bhfx/` (`techniques.md`, `tools/`).

## `checkShaders` (checked at build time)

File: `gradle/glsl-check.gradle`. **It is identical in bhfx and BHSpells**, so a change in one must be copied to the other.
It is wired into `check` → `build`, so it runs on every `gradlew build`. Run it alone with `gradlew checkShaders`.

Steps:
1. Find every pass file: `assets/<ns>/shaders/bhfx/*.fsh` in `src/main/resources` (not `include/`).
2. Assemble it the way `ShaderManager` does in game:
   - Prepend the header: `#version 150` plus `DiffuseSampler`, `SceneSampler`, `DepthSampler`, `InvProj`, `InvView`, `ViewProj`, `Eye`, `OutSize`, `ScreenSize`, `GameTime`, `texCoord`, `fragColor`.
   - Replace each `#include "ns:file.glsl"` with that file from `assets/<ns>/shaders/bhfx/include/`.
   - Add `#line` directives so error line numbers match the original files.
   - bhfx looks up includes in `src/main/resources`.
   - BHSpells looks them up in `src/main/resources` and in **the bhfx jar on the compileClasspath**, so it checks against the same bhfx version it compiles against.
3. Write the result to `build/glsl-check/<ns>_<name>.frag` and run glslang on each file. If any fails, the build fails with the errors.

**If the header in `ShaderManager.HEADER` changes, update `glslHeader` in `glsl-check.gradle` in both repos too.**

### Where glslang is looked up (in order)
1. `-PglslangValidator=<path>`
2. env `GLSLANG_VALIDATOR`
3. **`D:\Minecraft\Dev\ironspell_more\tools\glslang\bin\glslang.exe`**: the shared copy next to bhfx and BHSpells (found as `<repo>/../tools/glslang/bin`). This is the one normally used; nothing to set up.
4. `%VULKAN_SDK%\Bin`
5. `PATH` (`glslangValidator.exe` or `glslang.exe`)

If none is found, it warns "shaders were NOT checked" and passes. Add `-PrequireGlslCheck` to make it fail instead.
Version in use: glslang 16.6.0 from the KhronosGroup/glslang GitHub release (`glslang-16.6.0-windows-x86_64-release.zip`). Only `bin/glslang.exe` and `VERSION.txt` are kept in `ironspell_more/tools/glslang`. `tools/` is not in any repo's git, so on a new machine copy it over or download it again.

### What it catches, and what it doesn't
- **Catches**: syntax errors, type mismatches, missing functions or variables, wrong parameters, names clashing with includes, missing include files, features GLSL 1.50 doesn't have.
- **Doesn't catch**: wrong-looking output, uniform names that don't match the Java side (the shader silently gets 0), driver-specific bugs, slowness (use `/bhfx stats`, `/bhfx bench`), undefined behavior such as `smoothstep` with reversed edges.

## Pitfalls found

- **`smoothstep(a, b, x)` with `a > b`**: GLSL leaves the result undefined. Most drivers compute it anyway, but it isn't guaranteed. Use `1.0 - smoothstep(b, a, x)` instead (found in `annihilation_charge.fsh`, 2026-10-17).
- **`texture()` inside a per-pixel branch**: implicit derivatives are undefined there. Use `textureLod(..., 0.0)` (2026-10-17).
- **`dFdx`/`dFdy` inside a branch**: the values are undefined. Compute them outside the `if` and use them inside, and flip the normal to face the camera (`dot(nrm, s.dir) > 0.0 ? -nrm : nrm`).
- **Rays must start at `Eye`, not `vec3(0.0)`**: view bobbing moves the eye (see `API_PLAN.md` 5.2).
