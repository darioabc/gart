package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.drawCircle
import dev.oblac.gart.gfx.fillOfRed
import dev.oblac.gart.math.*       // brings all Random extension fns (rndi, rndf, rndb, rndsgn, etc.) into scope
import kotlin.random.Random

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts the env var to -DGART_SIZE=512 JVM property.
// Read here as a JVM property — do NOT use System.getenv("GART_SIZE").
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
// Random.nextLong() calls the companion object (Random.Default.nextLong()) — valid.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * Headless PNG template for the `generate-art` skill.
 *
 * Scaffolding rules (mandatory for all generated pieces):
 *  - SIZE: controlled by GART_SIZE shell env var (render.sh converts to JVM -D prop).
 *    Default 1024. Set GART_SIZE=512 for a fast draft, then run without it for full-res.
 *  - SEED: controlled by GART_SEED shell env var. Printed to stdout so you can reproduce
 *    any result by passing the same value next time.
 *  - Never call gart.window() / showImage() / movie() — headless throws.
 *  - All randomness via `rng` (a seeded kotlin.random.Random). Never call the global
 *    free functions rndi() / rndf() / rndb() etc. — those delegate to Random.Default
 *    and bypass the seed. Call them as methods on rng: rng.rndi(max), rng.rndf(min, max).
 *  - Always use SIZE constant for canvas dimensions, not a hardcoded literal.
 *
 * Render with:
 *   GART_SIZE=512 .claude/skills/generate-art/render.sh arts:gen gen.TemplateKt out/  # draft
 *   .claude/skills/generate-art/render.sh arts:gen gen.TemplateKt out/                # full-res
 *   GART_SEED=<long> .claude/skills/generate-art/render.sh arts:gen gen.TemplateKt out/  # reproduce
 */
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("template", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    c.clear(CssColors.navy)
    c.drawCircle(d.center, rng.rndf(100f, 300f), fillOfRed())

    gart.saveImage(g) // -> template.png in the current directory (out/ when using render.sh)
}
