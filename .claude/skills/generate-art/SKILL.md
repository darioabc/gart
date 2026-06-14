---
name: generate-art
description: Generate a PNG image using the gȧrt generative-art framework. Use when the user wants to create, generate, render, or produce an image/PNG/artwork (e.g. "generate a png of concentric circles", "make some generative art", "render an image"). With a description it builds directly; invoked bare it runs a guided wizard (technique → theme → style). Scaffolds a Kotlin gart piece, then renders it headlessly to a PNG.
---

# Generate Art (PNG)

Scaffold a new gȧrt piece and render it to a PNG — end to end, headless.

**Two entry modes:**

- **Description given** (e.g. `/generate-art swirling neon flow field`, or any brief
  in ARGUMENTS): treat it as the brief, infer technique/theme/style from it, and
  **skip the wizard and the confirm step** — go straight to *Build*.
- **Invoked bare** (no meaningful ARGUMENTS): run the **Wizard** below to collect the
  four choices, then show the **Confirm** step, then *Build*.

## Wizard (only when no description was given)

Drive it with the `AskUserQuestion` tool, in two stages. `AskUserQuestion` always
appends an "Other" free-text choice on top of the named options; the named-option
limit is 4, which is why technique is split family → specific.

### Stage 1 — technique family

One `AskUserQuestion`, single question, header **"Technique"**, these 4 options:

| family | what it produces |
|---|---|
| **Fields & formulas** | per-pixel math/noise → colour (smooth gradients, plasma) |
| **Flow & particles** | agents moving through a vector field, leaving trails |
| **Simulation** | step a physical/biological system, render its state |
| **Constructive geometry** | shapes placed by rule, recursion, or curve math |

### Stage 2 — technique + theme + style + seed

One `AskUserQuestion` with **four** questions. (Four is the hard limit; do not add
further questions to this call.) Populate the **Technique** question's 4 options
from the family chosen in Stage 1:

**Fields & formulas**
| option | engine pkg(s) | gallery example | imitation note |
|---|---|---|---|
| Complex/math field | `math` | `arts/z/src/z/Z1.kt` ⚠️ | Per-pixel: map pixel→Complex, apply fn, color by result. **Imitate pixel-loop and coloring only — do NOT copy `gart.window()` or `w.showImage()`; use `gart.saveImage(g)` instead.** |
| Noise field | `noise` | `arts/pixelmania/src/pixelmania/cosmic/CosmicTopo.kt` ⚠️ | `SimplexNoise` per-pixel → palette lookup; JFA for contour lines. **Do NOT copy `gart.window()`.** |
| Plasma | `math` (pure trig) | `arts/plasma/src/plasma/Plasma.kt` ⚠️ | Precomputed sin/sqrt lookup tables; phase offsets. **Do NOT copy `gart.window()`.** |
| Reaction-diffusion | `reactiondiffusion` | `arts/orbitr/src/orbitr/twelve_monkeys.kt` ⚠️⚠️ | `FitzHughNagumo` RD stepped per frame; `ColorRamp` coloring. **CRITICAL: `saveImage` in this file is inside the `w.show` callback — restructure: run RD iterations in a plain loop, call `saveImage` after the loop, omit `gart.window()` entirely.** |

**Flow & particles**
| option | engine pkg(s) | gallery example | imitation note |
|---|---|---|---|
| Flow field | `flow`, `noise` | `arts/flowforce/src/flowforce/worms/Worms.kt` ⚠️ | Poisson-disk seeds traced through `FlowField`; `StreamlineTracer` paths. **Do NOT copy `gart.window()`.** |
| Particle system | `flow` | `arts/flowforce/src/flowforce/Orb1.kt` ⚠️ | `FlowField` of sinusoidal angles; `PointX` pool flows through it. **Do NOT copy `gart.window()`.** |
| Random walkers | `walker` | engine only: `gart/src/main/kotlin/dev/oblac/gart/walker/walker.kt` | No gallery piece confirmed. Use `walkRandom(pos, step)` / `walkMomentum(m)`. |
| Physarum | `physarum` | `arts/cell/src/cell2/Cell2.kt` ⚠️⚠️ | `dev.oblac.gart.physarum.Physarum` slime-mold; `Gartmap` buffer; blur decay. **CRITICAL: `saveImage` is inside `win.show` callback — restructure the same way as Reaction-diffusion above.** |

**Simulation**
| option | engine pkg(s) | gallery example | imitation note |
|---|---|---|---|
| Fluid/Navier-Stokes | `fluid` | `arts/fluid/src/wind/FluidWind.kt` ⚠️ | `FluidSolver` + `FluidParticles` + Perlin force; `FluidRenderer` draws each frame. **Do NOT copy `gart.window()`.** |
| N-body/orbits | `nbody` | `arts/orbitr/src/orbitr/Orbirt.kt` ⚠️ | `BarnesHutSimulation` run 200 times; concentric orbit traces. `saveImage(g)` precedes `w.show` here — keep `saveImage`, drop window call. |
| Cellular automata | `cellular` | `arts/cell/src/cell/Cell1.kt` ⚠️ | `CellularAutomata` with `newBelousovZhabotinskyReaction2`; cells as circles. **Do NOT copy `gart.window()`.** |
| Differential growth | `grow` | `arts/lines/src/lines/growth/Grow.kt` ⚠️ | `Growth` + `seedCircle()`; stroke each frame's edge list at low alpha. **Do NOT copy `gart.window()`.** |

**Constructive geometry**
| option | engine pkg(s) | gallery example | imitation note |
|---|---|---|---|
| Triangulation/low-poly | `triangulation`, `tri3d` | `arts/triangular/src/triangular/v1/Triage.kt` ⚠️ | Poisson-disk → `Delaunator.triangles()` → fill from palette. **`saveImage` is commented out in Triage.kt — use `gart.saveImage(g)`, do NOT copy `window().showImage`.** |
| Spiro/harmonograph | `spirograph`, `harmongraph` | `arts/spirograph/src/spirograph/Sg1.kt` (spirograph) · `arts/harmongraph/src/harmongraph/formulas/hA.kt` (harmonograph) ⚠️ | Sg1: `createSpirograph()`, colored segments. hA: `harmongraph2()`, `zipWithNext()` lines. **Do NOT copy `gart.window()`.** |
| Recursive rects | `gfx` | `arts/rects/src/rects/mondrian/MondrianRects.kt` ⚠️ | `divideRects()` called 3× recursively; leaf rects filled with Mondrian colors; thick black stroke borders. **Do NOT copy `gart.window()`.** |
| Shape packing | `pack` | `arts/bubbles/src/bubbles/bubbub/BubBub.kt` ⚠️ | `simpleCirclePacker` fills region; sub-circles packed inside; fill + `saveImage`. **Do NOT copy `gart.window()`.** |

The other three questions are the same for every family:

- **Theme** (header "Theme") — palette + mood. Set each option's `preview` field to
  the SVG string after "preview:" — do NOT include the SVG in the option label itself:
  - `Cream & blues` — preview: `<svg xmlns="http://www.w3.org/2000/svg" width="60" height="30"><rect width="60" height="30" fill="#fffdd0"/><circle cx="12" cy="15" r="6" fill="#b0d4e8"/><circle cx="26" cy="15" r="6" fill="#6fa8c8"/><circle cx="40" cy="15" r="6" fill="#2e6da4"/><circle cx="54" cy="15" r="6" fill="#0d2b6b"/></svg>`
  - `Neon on black` — preview: `<svg xmlns="http://www.w3.org/2000/svg" width="60" height="30"><rect width="60" height="30" fill="#000000"/><circle cx="12" cy="15" r="6" fill="#ff00ff"/><circle cx="26" cy="15" r="6" fill="#00ffff"/><circle cx="40" cy="15" r="6" fill="#ff4500"/><circle cx="54" cy="15" r="6" fill="#ccff00"/></svg>`
  - `Earthy / muted` — preview: `<svg xmlns="http://www.w3.org/2000/svg" width="60" height="30"><rect width="60" height="30" fill="#faebd7"/><circle cx="12" cy="15" r="6" fill="#a0522d"/><circle cx="26" cy="15" r="6" fill="#cd853f"/><circle cx="40" cy="15" r="6" fill="#bdb76b"/><circle cx="54" cy="15" r="6" fill="#6b8e23"/></svg>`
  - `Monochrome / ink` — preview: `<svg xmlns="http://www.w3.org/2000/svg" width="60" height="30"><rect width="60" height="30" fill="#fffff0"/><circle cx="12" cy="15" r="6" fill="#1a1a1a"/><circle cx="26" cy="15" r="6" fill="#555555"/><circle cx="40" cy="15" r="6" fill="#999999"/><circle cx="54" cy="15" r="6" fill="#cccccc"/></svg>`

- **Style** (header "Style") — composition:
  `Minimal / sparse` · `Dense / maximal` · `Hard-edge geometric` · `Organic / soft`

- **Seed** (header "Seed") — for reproducibility:
  - `Surprise me` — a random seed is picked inside the piece and printed to stdout
    after render; no `-DGART_SEED` flag is passed to render.sh
  - `Enter a seed` — free-text; user types any integer; pass as `GART_SEED=<integer>`
    in front of the render.sh call
  - `Reuse last` — user pastes the `seed=<N>` line from a prior render; **strip the
    `seed=` prefix and extract only the integer**; pass as `GART_SEED=<integer>`.
    Example: user pastes `seed=8374629103` → set `GART_SEED=8374629103`.

## Map the selections → code

- **Technique** → open the listed gallery example and imitate its structure; the
  algorithm lives in `gart/src/main/kotlin/dev/oblac/gart/<package>/`. Where the
  example is engine-only (Random walkers), read the engine package directly.

  **⚠️ Gallery examples and window calls:** virtually every gallery piece calls
  `gart.window()` or `w.showImage()`. **Never copy those calls.** The rule is:
  - If `saveImage(g)` appears *before* `gart.window()` → keep `saveImage`, drop
    everything from `gart.window()` onwards.
  - If `saveImage(g)` appears *inside* a `w.show { ... }` or `win.show { ... }`
    callback (Reaction-diffusion, Physarum) → restructure: run the iteration loop
    without the callback, then call `gart.saveImage(g)` after the loop.
  - If `saveImage` is absent or commented out (Triage.kt) → add `gart.saveImage(g)`
    after the drawing code and omit `window()` entirely.

- **Theme** → background + palette:

  | theme | background | palette |
  |---|---|---|
  | Cream & blues | `CssColors.cornsilk` | blue ramp `powderBlue→navy`, or a cool `Palettes` map |
  | Neon on black | `CssColors.black` | saturated hues; **black must dominate — scale brightness by field intensity so low/background values fall to black and only peaks glow** |
  | Earthy / muted | `CssColors.antiqueWhite` | `sienna`,`peru`,`darkKhaki`,`oliveDrab`,`tan` |
  | Monochrome / ink | `CssColors.ivory` | one hue or black; or a grayscale ramp |

  Use `Palettes.colormapNNN.expand(256)` for smooth ramps. Confirm colour names exist
  by grepping `color/CssColors.kt`.

- **Style** → params:

  | style | knobs |
  |---|---|
  | Minimal / sparse | background must dominate — fill only a minority of elements; thin clean strokes, generous empty space, no jitter |
  | Dense / maximal | high element count, overlap & layering, fill the canvas |
  | Hard-edge geometric | flat fills, crisp aligned shapes, no transparency |
  | Organic / soft | curves, low alpha, positional jitter/noise, round caps |

## Style × Technique defaults

Before writing the Kotlin piece body, look up the row where **Technique = chosen
technique** and the column where **Style = chosen style**. Implement the cell's
directives as concrete initial parameter values in the scaffolded code.

| Technique | Minimal / sparse | Dense / maximal | Hard-edge geometric | Organic / soft |
|---|---|---|---|---|
| Complex/math field | Palette of 3 colors max; wide banded regions; background covers ≥50% | Full palette; tight iteration contours; every pixel colored | Quantize field to 6 flat bands, crisp edges, no blur | Smooth gradient ramp; light Gaussian blur post-render |
| Noise field | 2–3 contour lines only; low-contrast palette; ≥60% background | Dense isocontour mesh; saturated palette; every pixel mapped | Step-quantize to 8 flat levels; sharp color boundaries | Wide smooth gradient bands; low-frequency noise (zoom ≥4×) |
| Plasma | Single sine wave per axis; 2-color lerp; ≥50% midtone | High-frequency composite of 4+ sine waves; full hue rotation | Phase-quantize to 8 hard steps; no lerp between steps | Low-frequency waves; 3-stop palette lerp; soft-clamp extremes |
| Reaction-diffusion | ≤300 iterations; render only U channel; high threshold | ≥800 iterations; render U and V; low threshold | Threshold U to binary; solid fill; sharp color boundary | Anti-alias threshold with 2-px smoothstep; apply slight blur |
| Flow field | ≤50 streamlines; long sparse strokes; ≥70% background | ≥500 streamlines; short dense strokes; full coverage | Rectilinear angles only (0°, 90°, 45°); hard stroke ends | Curved Bezier strokes; alpha ≤80; variable width with taper |
| Particle system | ≤200 particles; large radius; alpha ≤60 | ≥2000 particles; small radius; accumulate many frames | Particles snap to grid; axis-aligned trails only | Gaussian-blurred trail; velocity-based color; soft fade-out |
| Random walkers | 3–5 walkers; thick strokes; long runs (≥5000 steps) | 50+ walkers; thin overlapping strokes; full canvas fill | Grid-snapped steps (horizontal/vertical); hard stroke caps | Any direction; alpha ≤50; round caps; slow speed |
| Physarum | ≤1000 agents; wide sensor angle; high decay (background clears fast) | ≥10 000 agents; narrow sensor angle; low decay (trails persist) | Quantize trail map to 4 levels; flat filled regions | Wide diffusion kernel; smooth trail map; soft color gradient LUT |
| Fluid/Navier-Stokes | Low viscosity; single force injection; ≤500 particles | High viscosity; 8+ injection points; dense particle field | Velocity vectors as axis-aligned arrows on a grid | Smooth color field (speed→hue); particle trails with fade |
| N-body/orbits | 3–5 bodies; long trails; high contrast background | 50+ bodies; short dense trails; full frame fill | Circular orbits only; render ellipses as crisp outlines | Elliptical orbits; low-alpha trail accumulation; smooth color ramp |
| Cellular automata | Cell size ≥12 px; few generations; high-contrast 2-color palette | Cell size ≤4 px; many generations; full palette | Hard 2-color palette; no anti-alias on cell edges | Blend states with alpha; round corners; smooth transitions |
| Differential growth | ≤200 nodes; thick strokes; wide spacing constraints | ≥1000 nodes; hairline strokes; tight packing | Straight-segment render; no smoothing; hard joints | Catmull-Rom smooth curve; low alpha; tapered ends |
| Triangulation/low-poly | ≤80 triangles; large faces; 3-color palette | ≥500 triangles; small faces; full palette | Flat fill; black stroke on every edge; no gradient | No stroke; face color lerped from gradient by centroid |
| Spiro/harmonograph | Single curve; thick stroke; ≤3 rotations | Dense multi-curve fill; thin strokes; many rotations | Integer ratio frequencies; closed polygon outline | Non-integer ratios; low alpha; long integration for petal density |
| Recursive rects | ≤25% canvas filled; no cell >12% of canvas; background dominates | Fill ≥80% of cells; tight subdivision; small residual rects | Black borders ≥3 px; flat color fill; no texture | Rounded corners; semi-transparent fills; thin or no border |
| Shape packing | ≤30 circles; large radii; generous whitespace | ≥300 circles; small minimum radius; full coverage | Circles on grid with fixed size increments; crisp outlines | Jittered centers; Gaussian radius distribution; soft fill with alpha |

## Finishing pass (gallery-grade) — apply to most pieces

Stock "draw shapes on a flat background" output reads as a clean-but-dull vector.
What makes a piece look like the gȧrt gallery is the **finishing pass**. Apply it to
line / particle / flow / curve / simulation pieces **unless** the brief explicitly
wants flat, minimal, or hard-edge geometric. Four levers, in order of impact:

1. **Dark ground + luminous strokes** ("light drawn in the dark"). Default to a dark
   ground with bright, low-alpha strokes that *accumulate* — overlaps build to bright
   cores, sparse areas stay dim. This one choice beats dark-on-light for most
   line/flow work. (Even a "monochrome / ink" brief reads far richer as white-on-dark
   than grey-on-cream.) Reserve flat-on-light for hard-edge geometric (Mondrian,
   low-poly).

2. **Bloom via SCREEN-blend + blur.** Render strokes to a *transparent* buffer, then
   composite over the ground through 1–2 Gaussian-blur passes so dense areas glow:

   ```kotlin
   import org.jetbrains.skia.BlendMode
   import org.jetbrains.skia.FilterTileMode
   import org.jetbrains.skia.ImageFilter
   import org.jetbrains.skia.Paint
   import dev.oblac.gart.shader.createNoiseGrainFilter

   val buf = gart.gartvas()            // transparent — do NOT clear()
   // ... draw bright, low-alpha strokes into buf.canvas (default SRC_OVER) ...
   val sharp = buf.snapshot()

   val out = gart.gartvas()
   out.canvas.clear(GROUND)            // dark ground (an Int color)
   val s = SIZE / 170f                 // blur sigma scales with canvas size
   listOf(s, s / 2.5f).forEach { sigma ->
       out.canvas.drawImage(sharp, 0f, 0f, Paint().apply {
           imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL)
           blendMode = BlendMode.SCREEN
       })
   }
   out.canvas.drawImage(sharp, 0f, 0f, Paint().apply { blendMode = BlendMode.SCREEN }) // crisp top

   // 3) grain texture pass
   val finalv = gart.gartvas()
   finalv.canvas.drawImage(out.snapshot(), 0f, 0f, Paint().apply {
       imageFilter = createNoiseGrainFilter(0.10f, gart.d)
   })
   gart.saveImage(finalv)             // save the FINAL gartvas, not the raw buffer
   ```

3. **Texture pass** — the final `createNoiseGrainFilter(0.05f–0.15f, d)` overlay above
   gives a printed, non-flat feel. Alternatives in `dev.oblac.gart.shader`:
   `createNoiseGrain2Filter`, `createSketchingPaperFilter` (light grounds),
   `createMarbledFilter`. **Avoid `createRisographFilter`** unless verified — it sets an
   `intensity` uniform its SKSL doesn't declare. `applyGaussianBlur(gartmap)` is a cheap
   3×3 alternative if you don't want the buffer-composite.

4. **One accent + restrained palette, and fill the frame.** Keep the palette
   restrained (e.g. white ink on dark) and give ONE saturated accent to a *subset* of
   elements — the accent glows hardest through the bloom. For colour pieces use the
   rich ramps: `Palettes.colormapNNN.expand(256)`. **Auto-fit** the figure so it spans
   the canvas (measure max excursion, scale to ~0.46·SIZE) instead of floating small in
   empty space.

Confirmed helpers (grep `dev/oblac/gart/shader/` + `pixels/` before reaching for
others): `createNoiseGrainFilter`, `createNoiseGrain2Filter`, `createSketchingPaperFilter`,
`createMarbledFilter`, `applyGaussianBlur`; skia `ImageFilter.makeBlur`,
`ImageFilter.makeDropShadow`, `BlendMode.SCREEN`. Proven in gallery:
`arts/cotton/src/cotton/circles2/CottonCircles2.kt`,
`arts/palecircles/src/palecircles/around/Around.kt`. Full worked example (multi-harmonic
curves + bloom + grain + accent): `arts/gen/src/gen/HarmonInk.kt`.

## Confirm before building

> **Wizard mode only.** If invoked with a description (complement mode), skip this
> section and proceed directly to Build.

After Stage 2 picks are captured, display this summary and wait for explicit
confirmation before writing any file or running any render:

```
**Ready to build:**
- Technique: <chosen technique> (`<engine packages>`)
- Theme: <chosen theme>
- Style: <chosen style>
- Seed: <the integer if user entered one, or "random (seed printed to stdout after render)">
- Output name: `<Name>.kt`

Proceed, or go back to adjust?
```

Options: `Build it` · `Change technique` · `Change theme/style` · `Change seed`

- **`Build it`** → proceed to Build.
- **`Change technique`** → re-run only the Technique sub-question from Stage 2, then
  redisplay this summary.
- **`Change theme/style`** → re-run only the Theme and Style sub-questions from Stage 2
  (a single two-question `AskUserQuestion`), then redisplay this summary.
- **`Change seed`** → re-run only the Seed sub-question from Stage 2 (a single
  one-question `AskUserQuestion`), then redisplay this summary. Do not reset
  technique, theme, or style.

## Build (after confirmation)

1. **Name** — short lowerCamelCase from the brief (e.g. `neonFlowField`). File
   `arts/gen/src/gen/<Name>.kt` (capitalized); main class `gen.<Name>Kt`.

2. **Write** the Kotlin file, starting from `arts/gen/src/gen/Template.kt`.

   ### Scaffolding rules (mandatory)

   Every generated piece MUST open with this exact header after the package line:

   ```kotlin
   package gen

   import dev.oblac.gart.Gart
   import dev.oblac.gart.math.*       // brings all Random extension fns into scope
   import kotlin.random.Random

   // Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
   // render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
   private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

   // Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
   // Printed to stdout so the value is always recoverable.
   private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
       ?: Random.nextLong()
   ```

   The `fun main()` must always begin with:

   ```kotlin
   fun main() {
       println("seed=$SEED")
       val rng = Random(SEED)

       val gart = Gart.of("<Name>", SIZE, SIZE)  // use SIZE, not a hardcoded literal
       val g = gart.gartvas()
       val c = g.canvas
       val d = gart.d
       // ...
       gart.saveImage(g)
   }
   ```

   Hard rules:
   - **Never** call `gart.window()`, `showImage()`, or `movie()` — throws in headless.
   - **Never** call the global free functions `rndi()`, `rndf()`, `rndb()`, etc. — they
     delegate to `Random.Default` and bypass the seed. Always call on `rng`:
     `rng.rndi(max)`, `rng.rndf(min, max)`, `rng.rndb()`, `rng.rndsgn()`, etc.
   - **Always** use `SIZE` constant for canvas dimensions, not a hardcoded literal.
   - Import: `import dev.oblac.gart.math.*` (star import) to bring all extensions into
     scope. Each extension is defined as a function on the `Random` receiver in
     `dev.oblac.gart.math.random`.
   - Before writing the piece body, look up the Style × Technique defaults table for
     the (chosen technique × chosen style) cell and implement those directives as
     concrete initial parameter values.

3. **Render — draft first, full-res on approval:**

   ```bash
   # Draft (512 px — fast). Both draft and full-res write the same filename;
   # the full-res render will overwrite the draft, so show the draft immediately.
   GART_SIZE=512 .claude/skills/generate-art/render.sh arts:gen gen.<Name>Kt out/

   # Full-res (default size — slow on first compile):
   .claude/skills/generate-art/render.sh arts:gen gen.<Name>Kt out/
   ```

   If a specific seed was chosen, prefix **both** commands with `GART_SEED=<integer>`
   so draft and full-res use the same seed:

   ```bash
   GART_SEED=<integer> GART_SIZE=512 .claude/skills/generate-art/render.sh arts:gen gen.<Name>Kt out/
   GART_SEED=<integer> .claude/skills/generate-art/render.sh arts:gen gen.<Name>Kt out/
   ```

   After the draft renders, call `SendUserFile` immediately to show it. Then ask:
   "Happy with this? I'll run the full-res render." Only run the full-res render after
   the user confirms.

4. **Show the result** with `SendUserFile`, pointing at `out/<name>.png`. Note the
   technique / theme / style / seed that produced it.

## The gart drawing API (essentials)

Imports live under `dev.oblac.gart.*`. Common building blocks:

- **Colors**: `dev.oblac.gart.color.CssColors` (e.g. `CssColors.black`, `.navy`),
  and `dev.oblac.gart.color.Palettes` (e.g. `Palettes.colormap092.expand(256)`
  returns an indexable list of colors).
- **Fills/strokes**: `dev.oblac.gart.gfx.fillOf(color)`, `fillOfRed()`,
  `strokeOf(color, width)`; or extension fns `Int.toFillPaint()`,
  `Int.toStrokePaint(width)` in `dev.oblac.gart.color`.
- **Shapes** (extension fns on `Canvas`): `c.clear(color)`,
  `c.drawCircle(center, radius, paint)`, `c.drawPoint(x, y, paint)`,
  plus lines, rects, paths, polygons under `dev.oblac.gart.gfx`.
- **Geometry**: `d.center`, `d.w`, `d.h` from the `Dimension`.
- **Math/noise**: `dev.oblac.gart.math` — `map(v, inLo, inHi, outLo, outHi)`,
  noise functions, vectors, complex numbers, etc.
- **Post-processing / FX** (see *Finishing pass* above): shader filters in
  `dev.oblac.gart.shader` (`createNoiseGrainFilter`, `createSketchingPaperFilter`,
  `createMarbledFilter`), `applyGaussianBlur` in `dev.oblac.gart.pixels`, and skia
  `ImageFilter.makeBlur` / `makeDropShadow` + `BlendMode.SCREEN` applied via a `Paint`
  on `canvas.drawImage(image, 0f, 0f, paint)`. `gartvas.snapshot()` returns the `Image`
  to composite.

When unsure which helper exists, grep `gart/src/main/kotlin/dev/oblac/gart/gfx/`,
`.../color/`, and `.../math/` rather than guessing names.

## Reference examples

- `arts/gen/src/gen/Template.kt` — minimal skeleton with SIZE and SEED blocks.
- `arts/gen/src/gen/HarmonInk.kt` — the *Finishing pass* applied end-to-end: bespoke
  multi-harmonic curves, auto-fit, transparent buffer → SCREEN-blend bloom → grain, on
  a dark ground with one accent. Copy this structure for any glowing line/curve piece.
- Per technique: the gallery example named in the Stage-2 table above. See the ⚠️
  annotations for which call patterns to imitate and which to restructure or drop.

## Notes

- First render compiles the project and may download dependencies (Skiko, etc.),
  so it can be slow; subsequent renders are fast.
- The `arts:gen` module is a reusable sandbox already registered in
  `settings.gradle.kts`. Add as many pieces to `arts/gen/src/gen/` as you like;
  each top-level `fun main()` is independently renderable by its main class.

## Skill edits and reloading

SKILL.md (and render.sh) are read at Claude Code startup and cached for the session.
If you edit either file, **restart Claude Code** before testing the change via the
`/generate-art` launcher — the cached version will run otherwise, not your edit.
