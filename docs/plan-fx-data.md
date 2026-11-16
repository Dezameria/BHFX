# Plan: `FxData`, runtime data tables for passes (proposed 2026-11-16; code done the same day, not tested in game)

A pass can read a large table of numbers that the `Fx` fills in Java at run time. Built on 2026-11-16 into the
unreleased 0.3.12 (together with body parts) instead of a separate 0.3.13. What was built is specified in
[`API_PLAN.md`](API_PLAN.md) section 5.5; the in-game checklist is in [`testing.md`](testing.md). Status lives in
[`roadmap.md`](roadmap.md); the reason for choosing this over the other options is in [`decisions.md`](decisions.md)
2026-11-16.

## Why

Found while reading `../orbital-laser` (a Horizon mod in Rust/WGSL) on 2026-11-16. Most of its looks already fit in a bhfx pass:
analytic beams and rings, SDF megastructures, noise volumes, black-hole lensing over `SceneSampler`, impact frames.
What it does that bhfx can't is feed a pass a table it computed at run time (`render.set-data`, a storage buffer of
hundreds of KB):
- Crystal Bloom: about 200 pieces, each with its own break, throw, spin and resting offset, computed once when the
  effect starts. The pass reads the table. It used to hash all of this per pixel, and that took about half its GPU time
  at the heaviest moments.
- Sky Breaks hand: an 18-bone skeleton plus baked per-bone distance grids (about 380 KB).
- Ground heights the mod looked up for each piece, so the pass rests them on the real ground.

bhfx today has `Uniforms.floats`, a uniform array. GL 3.2 only promises 1024 fragment uniform components (about 256
`vec4`), shared with everything else in the pass. `FxTexture` only loads image files. So only small or static data fits.
bhfx already uses a buffer texture for particles internally (`ParticleRenderer`, `GL_TEXTURE_BUFFER` +
`GL_RGBA32F`); this plan exposes the same mechanism to effects.

## API (all `@ApiStatus.Experimental`)

```java
// Fx
protected final FxData createData(int size);            // vec4 entries, 1..65536; freed when the Fx ends

// net.deizyn.bhfx.api.client.FxData
public interface FxData {
    int size();                                          // texels
    FxData set(int index, float x, float y, float z, float w);
    FxData set(int index, Vec3 v, float w);
    FxData put(int index, float[] values);               // values.length % 4 == 0, written from index on
    FxData fill(int from, int to, float x, float y, float z, float w);
    float get(int index, int component);                 // added while building
}

// Uniforms
Uniforms data(String name, FxData data);                 // binds `uniform samplerBuffer Name;`
```

Shader side, a new include `bhfx:data.glsl`:
```glsl
vec4 fxData(samplerBuffer s, int i);   // texelFetch with a bounds check: vec4(0) outside 0..textureSize(s)-1
int  fxDataSize(samplerBuffer s);
```
Particle materials can read it too, through the `setup` of `PassList.particles(batch, options, setup)`.

## Rules

- **Thread and time**: write from `tick` or `onStart` (or `passes`) on the client thread. Each frame, the first `data(...)` bind
  uploads only the dirty range (`glBufferSubData`), so writing every tick costs only what changed.
- **Same on every machine**: fill it from `random()`, `seedF()`, `params()` or world reads. Nothing is sent over the
  network, so the 32 KB params limit doesn't apply.
- **Floats only** (RGBA32F). GLSL 150 has no `floatBitsToUint`, so store integers as floats, which are exact up to 2^24.
  An integer variant (`RGBA32UI` + `usamplerBuffer`) only when an effect needs it.
- **Texture units**: a data bind takes one unit from the effect's 6..15, the same pool as `texture(...)`. Over the limit
  it is skipped and logged once, the same as textures.
- **Size cap**: 65,536 texels (1 MB), the `GL_MAX_TEXTURE_BUFFER_SIZE` minimum GL 3.1 promises. `createData` clamps to
  it and logs. All live data counts towards `/bhfx stats` VRAM and the idle free after 30 s.
- **GL state**: `GlStateGuard` must save and restore the `GL_TEXTURE_BUFFER` binding on every unit it touches, and the
  pass unbinds its buffer textures after drawing. See the note at `PostRenderer` line 567: a `samplerBuffer` left on a
  unit that a `sampler2D` later uses is an invalid operation.
- **Off paths**: with post off (quality OFF, shader pack, GL error), `createData` still returns a working object that only
  stores in Java and never touches GL, so effect code needs no checks.

## Work

Steps 1–3 and 6 done 2026-11-16 (build + JUnit + glslang); step 4 as a "data table(s)" count in `/bhfx status` (bytes
go into the held total, not a line of their own); step 5 simplified to 256 orbs looping over all of them, no tile grid;
step 7 done for `API_PLAN.md` and `testing.md`, the skill note waits for a real effect. As built, an unbound
`samplerBuffer` also has to be pointed off unit 0 (see `API_PLAN.md` 5.5); the plan had missed that.

1. `api/client/FxData.java` (interface); `impl/render/FxDataImpl` (a `float[]`, a dirty range, the GL buffer and texture,
   created lazily on the first bind); `Fx.createData`, freed in the same place as `createParticles` batches.
2. `UniformBinder.data(...)`: bind to `GL_TEXTURE_BUFFER` on the next unit; `GlStateGuard` support.
3. `assets/bhfx/shaders/bhfx/include/data.glsl`. `checkShaders` needs no change (an include). Make sure glslang accepts
   `samplerBuffer` under `#version 150`.
4. `/bhfx stats`: number of data tables and their bytes.
5. Test effect `bhfx:test_data`: 2,048 glowing orbs on paths read from a table filled in `onStart`, drawn by one pass
   that loops only over the orbs inside the pixel's tile (a coarse grid also stored in the table). Compare against the
   same effect hashing per pixel with `/bhfx bench`.
6. JUnit: dirty-range merging, clamping, `put` bounds.
7. Docs: `API_PLAN.md` sections 4.2, 4.4 and 5.3/5.4; a `testing.md` checklist (Intel/AMD/NVIDIA if possible, since
   buffer texture drivers vary); a skill note (`bhfx-effect-recipes`) once a real effect uses it.

## Done when

- `build` and `checkShaders` pass, and JUnit covers the dirty ranges.
- In game: `bhfx:test_data` draws correctly while writing every tick; the effect running together with particle effects
  doesn't break either; no GL errors in the log; VRAM goes back to idle 30 s after.
- One BHSpells effect uses it for real (candidate: pieces of `sky_shatter`, or a bone table for an SDF body).

## Not in this plan (options compared on 2026-11-16)

- **Block textures in a pass** (orbital-laser's `atlas`): next in line and small. Bind the block atlas
  (`TextureAtlas.LOCATION_BLOCKS`) and pass a sprite's UV rect (`Uniforms.sprite(name, ResourceLocation)` →
  `vec4 u0 v0 u1 v1`). Mip bleeding at sprite edges needs `textureGrad` or LOD 0. Before building it, check
  whether `FxTexture.of(new ResourceLocation("minecraft", "textures/block/stone.png"))` already works (not tested).
- **Half-resolution stage inside one pass** (`fs_half` / `fs_join`): a performance gain only. Every effect already
  runs at the dynamic FX resolution. Bigger work in `PostRenderer` and `ResolutionController`.
- **Bending the world itself** (orbital-laser's `set-warp`, The Fold): needs vertex hooks in the chunk renderer, which
  means mixins that would clash with Embeddium/Sodium and Oculus/Iris. Not planned.
