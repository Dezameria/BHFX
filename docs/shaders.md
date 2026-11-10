# Shaders: checking and conventions

The basic conventions (file locations, the header bhfx adds, the includes it provides) are in [`API_PLAN.md`](API_PLAN.md) section 5.
Effect design techniques and the in-browser preview tools are in `../BHSpells/docs/bhfx/` (`techniques.md`, `tools/`).

## `checkShaders` (checked at build time)

File: `gradle/glsl-check.gradle`. **It is identical in bhfx and BHSpells**, so a change in one must be copied to the other. (2026-10-24: BHSpells at `3770530` has neither the file nor the `checkShaders` task; until it is restored, check a BHSpells shader by copying it into bhfx's `shaders/bhfx/` and running bhfx's `checkShaders`, then removing the copy.)
It is wired into `check` → `build`, so it runs on every `gradlew build`. Run it alone with `gradlew checkShaders`.

Steps:
1. Find every pass file: `assets/<ns>/shaders/bhfx/*.fsh` in `src/main/resources` (not `include/`).
2. Assemble it the way `ShaderManager` does in game:
   - Prepend the header: `#version 150` plus `DiffuseSampler`, `SceneSampler`, `DepthSampler`, `InvProj`, `InvView`, `ViewProj`, `Eye`, `OutSize`, `ScreenSize`, `GameTime`, `FogColor`, `FogRange` (0.3.8), `texCoord`, `fragColor`.
   - Replace each `#include "ns:file.glsl"` with that file from `assets/<ns>/shaders/bhfx/include/`.
   - Add `#line` directives so error line numbers match the original files.
   - bhfx looks up includes in `src/main/resources`.
   - BHSpells looks them up in `src/main/resources` and in **the bhfx jar on the compileClasspath**, so it checks against the same bhfx version it compiles against.
3. Write the result to `build/glsl-check/<ns>_<name>.frag` and run glslang on each file. If any fails, the build fails with the errors.
4. (0.3.9) Particle materials `assets/<ns>/shaders/bhfx/particle/*.fsh`: bhfx's `particle/_header.glsl` + the material (includes inlined) + `particle/_footer.glsl`, written as `<ns>_particle_<name>.frag`. The header and footer are read from the include roots, so BHSpells would check against the bhfx jar it compiles with. `particle/*.vsh` files are checked as they are. In game `ShaderManager.getParticle` assembles them the same way, from the same files.

**If the header in `ShaderManager.HEADER` changes, update `glslHeader` in `glsl-check.gradle` in both repos too.**

### Where glslang is looked up (in order)
1. `-PglslangValidator=<path>`
2. env `GLSLANG_VALIDATOR`
3. **`D:\Minecraft\Dev\ironspell_more\tools\glslang\bin\glslang.exe`**: the shared copy next to bhfx and BHSpells (found as `<repo>/../tools/glslang/bin`). This is the one normally used; nothing to set up.
4. `%VULKAN_SDK%\Bin`
5. `PATH` (`glslangValidator.exe` or `glslang.exe`)

If none is found, it warns "shaders were NOT checked" and passes. Add `-PrequireGlslCheck` to make it fail instead.
- bhfx sets `requireGlslCheck=true` in `gradle.properties` (since 2026-10-17), so every bhfx build fails without glslang. `-PrequireGlslCheck=false` turns it off for one build.
- BHSpells keeps it optional, because friends who clone it may not have glslang. Use `-PrequireGlslCheck` there for releases.
Version in use: glslang 16.6.0 from the KhronosGroup/glslang GitHub release (`glslang-16.6.0-windows-x86_64-release.zip`). Only `bin/glslang.exe` and `VERSION.txt` are kept in `ironspell_more/tools/glslang`. `tools/` is not in any repo's git, so on a new machine copy it over or download it again.

### What it catches, and what it doesn't
- **Catches**: syntax errors, type mismatches, missing functions or variables, wrong parameters, names clashing with includes, missing include files, features GLSL 1.50 doesn't have.
- **Doesn't catch**: wrong-looking output, uniform names that don't match the Java side (the shader silently gets 0), driver-specific bugs, slowness (use `/bhfx stats`, `/bhfx bench`), undefined behavior such as `smoothstep` with reversed edges.

## Uniforms bhfx sets on its own (2026-11-07)
- Pass shaders: `uniform float Presence;` if the shader declares it, 0..1: the effect fading in (built late, far away) or out (hidden past `maxActiveFx`). Multiply the pass's contribution by it. Not in the prepended header (declare it yourself).
- Particle vertex shader: `PRing.z` = stride (instance i draws slot i × stride when the frame thins particles; ribbons always 1). Particle footer: `PMode.w` = presence, folded into `soft`, so every material fades with no change.

## Pitfalls found

- **`smoothstep(a, b, x)` with `a > b`**: GLSL leaves the result undefined. Most drivers compute it anyway, but it isn't guaranteed. Use `1.0 - smoothstep(b, a, x)` instead (found in `annihilation_charge.fsh`, 2026-10-17).
- **`texture()` inside a per-pixel branch**: implicit derivatives are undefined there. Use `textureLod(..., 0.0)` (2026-10-17).
- **`dFdx`/`dFdy` inside a branch**: the values are undefined. Compute them outside the `if` and use them inside, and flip the normal to face the camera (`dot(nrm, s.dir) > 0.0 ? -nrm : nrm`).
- **`pow(x, 2.0)` with a negative `x` is undefined** (GLSL spec): the driver may return NaN, which glslang does not catch. In BHSpells `dawn_rend.fsh` (2026-11-04) `exp(-pow((s - head) * k, 2.0))` behind the stroke's head turned the whole tear plane into a flat grey sheet that the bloom spread further. Square with `x * x`; `pow` only on a base known to be ≥ 0 (`max(…, 0.0)`). Also guard `normalize` of a possibly zero vector, and a pass may end with `if (any(isnan(col)) || any(isinf(col))) col = s.color;` as a safety net. BHSpells `golden_clash.fsh` still has the same pattern (lines 125, 315, 383), not changed.
- **`Uniforms.color` is linear, the frame is not**: `UniformBinder.color` applies `pow(c, 2.2)`, but passes add straight onto the display-encoded frame and nothing converts back. A tint used as an emitted colour comes out darker and more saturated than asked (`#FFB424` gold looks orange-red). Convert back with `pow(Tint.rgb, vec3(1.0 / 2.2))` (found in BHSpells `flame_field.fsh`, then `inferno.fsh`, 2026-10-20).
- **Rays must start at `Eye`, not `vec3(0.0)`**: view bobbing moves the eye (see `API_PLAN.md` 5.2).
- **Particle depth**: compare clip-space w with w, not with `-viewZ` from `InvProj`: Minecraft folds view bobbing into the projection, so the two spaces differ slightly. The scene's w is `1 / (InvProj * ndc).w` (`_footer.glsl`, 2026-11-04).
- **Two sampler types on one unit**: a `samplerBuffer` left on unit 0 next to the `sampler2D` there is an invalid operation at draw time. Always set `PData` (unit 5), also in warmup (2026-11-04).
- **Reserved words**: `half` (also `input`, `output`, `filter`, `sample`, ...) is reserved in GLSL 1.50 and fails to compile as a variable name (`particle/vertex.vsh`, 2026-11-04).
- **Particle materials and `discard`**: run `material()` before discarding, since its `texture()` calls need derivatives (same rule as the `texture()` in a branch item above).

## Noise round a circle (2026-11-10)
- Noise indexed by an angle from `atan` must repeat once per turn, or it shows a seam line where `atan` wraps (BHSpells `formation_array.fsh` curtain, seen in game). Use noise of `(cos a, sin a)` or a value noise whose x wraps every N cells (`loopNoise` in that shader).
- Texture gradients for polar coordinates: don't take `dFdx`/`dFdy` of an atan angle, not even the min-of-`u`/`fract(u + 0.5)` trick (fine in a browser, a dashed seam line on some in-game drivers). Work them out from the continuous coordinates: `d(angle in turns)/dq = (-q.y, q.x) / (|q|^2 * 2pi)`, then `dot(dFdx(q), that)` and `dot(dFdy(q), that)` for `textureGrad` (BHSpells `formation_array.fsh`, 2026-11-10).
