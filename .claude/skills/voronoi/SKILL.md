---
name: voronoi
description: Generate ONE weighted-Voronoi stipple artwork at high resolution (1920x1920) in the VoronoiOrb family — Lloyd-relaxed weighted-Voronoi pointillist stippling, tinted from the Coolors "Molten" palette over a papaya-whip ground. Use when the user asks for a voronoi / stipple / pointillist piece, or invokes /voronoi (optionally with a subject like "eclipse", "drapery", "fruit", "silhouette", "nested orbs", "cracked stone", or a custom subject).
---

# Voronoi Stipple (1920×1920)

Scaffold and render **exactly one** pointillist piece in the **VoronoiOrb family** and
nothing else. Every piece shares the same visual DNA so the whole set reads as cohesive:

- **Technique** — a grayscale luminance field (dark = denser dots) is fed to gȧrt's
  Lloyd-relaxed **weighted Voronoi stippler** (`stippleVoronoi`), which scatters
  thousands of dots whose density tracks the shading.
- **Palette** — dots tinted from **`Coolors.molten`**
  (`780000` dark-red · `C1121F` flag-red · `FDF0D5` papaya cream · `003049` navy ·
  `669BBC` steel-blue), expanded to a 256-stop ramp.
- **Ground** — the papaya-whip cream `0xFFFDF0D5`.
- **Finish** — `grainOnly(...)` grain pass, then `saveImage`.
- **Resolution** — always **1920×1920**, one image per invocation.

> This skill is self-contained — it does **not** use the `generate-art` wizard. Just
> pick a subject, write one `.kt` file from the template below, and render it with
> `render-voronoi.sh` (which bakes in the skiko render workaround).

## Entry modes

- **Subject given** (e.g. `/voronoi eclipse`, `/voronoi a cracked stone`, or anything in
  ARGUMENTS): treat it as the subject and go straight to *Build* — no questions.
- **Invoked bare** (no meaningful ARGUMENTS): ask the subject with **one**
  `AskUserQuestion` (header **"Subject"**), offering options drawn from the catalog
  below (the user can also type a custom subject via the auto-appended "Other").

## Subject catalog

Each subject already has a finished reference piece in `arts/gen/src/gen/`. For a listed
subject, **copy its reference file and adapt** (change the `Gart.of` name + tweak as the
user asks). For an unlisted/custom subject, **write a new piece from the template** and
implement the subject only in the luminance-field loop + tint expression.

| Subject (synonyms) | Reference piece | Shading idea |
|---|---|---|
| shaded sphere / orb / planet | `VoronoiOrb.kt` | Lambert-shaded sphere + simplex relief |
| eclipse / crescent / moon | `VoronoiEclipse.kt` | grazing light → thin lit crescent + faint corona |
| drapery / cloth / curtain / folds | `VoronoiDrapery.kt` | one-side-lit cylindrical vertical folds, sag + warp |
| fruit / still-life / cluster | `VoronoiFruit.kt` | overlapping shaded fruit spheres + contact shadow |
| portrait / silhouette / cameo / bust | `VoronoiSilhouette.kt` | profile boundary curve, lit facial contour |
| nested orbs / concentric / agate / rings | `VoronoiNested.kt` | banded `sin(rr·K·π)` shells, ramp cycles per ring |
| cracked stone / boulder / crackle | `VoronoiCracked.kt` | Lambert stone darkened along a Worley F2−F1 crack net |

For a **custom subject**, build its grayscale luminance field however the subject
demands (DARK where you want dense dots, near-white where you want bare cream ground),
then tint dots by local brightness or radius — keep everything else identical.

## The family template (write this, vary only the marked regions)

Create `arts/gen/src/gen/Voronoi<Subject>.kt` (PascalCase, e.g. `VoronoiEclipse.kt`):

```kotlin
package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import dev.oblac.gart.stipple.stippleVoronoi
import kotlin.math.*
import kotlin.random.Random

// Canvas size: render-voronoi.sh sets GART_SIZE=1920 (passed as a -D JVM prop).
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
// Seed: pass GART_SEED=<long> to reproduce; always printed to stdout.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val gart = Gart.of("Voronoi<Subject>", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val cx = d.cx; val cy = d.cy

    // ── (1) LUMINANCE FIELD — vary per subject. DARK = denser dots, white = bare ground.
    val src = Gartmap(gart.gartvas())
    val white = 0xFFFFFFFF.toInt()
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            // ... compute `lum` in 0f..1f for this subject; set `white` where empty ...
            val g = (lum * 255).toInt().coerceIn(0, 255)
            src[px, py] = argb(255, g, g, g)
        }
    }

    // ── (2) STIPPLE — keep these params (density/dot-size scale with SIZE).
    val dots = stippleVoronoi(
        src,
        pointCount = (SIZE * SIZE / 140),
        iterations = 18,
        gamma = 1.3f,
        minRadius = SIZE * 0.0009f,
        maxRadius = SIZE * 0.0045f,
        seed = SEED.toInt()
    )
    println("  ${dots.size} stipple dots")

    // ── (3) COMPOSITE — molten ramp on papaya-whip ground. Vary only the tint `t`.
    val ramp = Coolors.molten.expand(256)
    val out = gart.gartvas()
    val c = out.canvas
    c.clear(0xFFFDF0D5.toInt())   // papaya-whip ground
    for (dot in dots) {
        val t = /* 0f..1f tint expression — by local brightness or radius */
        val col = ramp.bound((t.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    // ── (4) FINISH
    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
```

### Hard rules (mandatory — keep the set cohesive)

- Keep the package `gen`, the `SIZE`/`SEED` blocks, the `println("seed=$SEED")`, the
  `stippleVoronoi(...)` params, the `c.clear(0xFFFDF0D5...)` ground, the `grainOnly`
  finish, and `saveImage` **exactly** as above. Vary **only** the luminance-field loop
  and the tint expression `t`.
- **Never** call `gart.window()`, `showImage()`, or `movie()` — throws in headless.
- **Never** call global `rndi()`/`rndf()` etc. — use the seeded `rng` (or simplex
  `noise`) so results are reproducible.
- `noise.random3D(xF, yF, zF)` takes **all-Float** args and returns a Double → call
  `.toFloat()`. Do **not** mix Float and Double args (won't compile). There is also an
  all-Double overload; pick one and be consistent.
- Do **not** use `TAU` (it's private in the package) — write `2f * PI.toFloat()`.
- `Coolors.molten.expand(256)` → index with `ramp.bound(index)`. In the ramp, low index
  ≈ dark-red, mid ≈ cream, high ≈ steel-blue. DARKER luminance ⇒ MORE dots.

## Build → render → show

1. **Write** `arts/gen/src/gen/Voronoi<Subject>.kt` (copy the matching reference for a
   listed subject; otherwise the template above). Main class is `gen.Voronoi<Subject>Kt`.

2. **Render one image at 1920×1920** with the self-contained wrapper (it temporarily
   pins skiko to a Maven-Central version, compiles, renders, and reverts the pin — you
   do **not** edit `gart/build.gradle.kts` yourself):

   ```bash
   .claude/skills/voronoi/render-voronoi.sh gen.Voronoi<Subject>Kt out/
   ```

   To reproduce a specific result, prefix a seed (the piece prints `seed=<N>`):

   ```bash
   GART_SEED=<integer> .claude/skills/voronoi/render-voronoi.sh gen.Voronoi<Subject>Kt out/
   ```

   This renders **only one** image. There is no draft pass — 1920×1920 is the output.
   The first render compiles the module and may be slow; later renders are fast.

3. **Show** the result with `SendUserFile`, pointing at `out/Voronoi<Subject>.png`.
   Note the subject and the printed `seed=<N>` so it can be reproduced.

## Notes

- After rendering, confirm `git diff gart/build.gradle.kts` is empty — the wrapper
  reverts the skiko pin automatically, so the swap must never appear in your changes.
- Reference pieces to read/copy live in `arts/gen/src/gen/Voronoi*.kt`; the palette is
  `arts/gen/src/gen/Coolors.kt` (`molten`); `grainOnly` is in
  `arts/gen/src/gen/WildFx.kt`.
- This skill (SKILL.md + render-voronoi.sh) is read at startup and cached; if you edit
  either file, restart Claude Code before testing `/voronoi`.
```
