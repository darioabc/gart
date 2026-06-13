package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.color.lerpColor
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.math.*       // brings all Random extension fns into scope
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.random.Random

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * NoiseField — Earthy / muted noise field, Organic / soft style.
 *
 * Style × Technique cell: Noise field × Organic / soft:
 *   "Wide smooth gradient bands; low-frequency noise (zoom ≥4×)"
 *
 * Per-pixel OpenSimplex at low frequency (scale 0.0015 → ~1.5 cycles across 1024px,
 * zoom factor ~667x relative to 1-unit-per-pixel baseline). Each pixel's noise value
 * in [-1,1] is mapped through a 4-stop earthy ramp:
 *   antiqueWhite → tan → peru → oliveDrab → sienna
 * producing wide, smooth, organic bands with gentle color transitions.
 */
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("NoiseField", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // Background: antiqueWhite (earthy theme base)
    c.clear(CssColors.antiqueWhite)

    // Low-frequency noise for wide organic bands (zoom ≥4× — scale ~0.0015 gives very
    // low frequency, well under 1 cycle per 4 pixels, producing smooth broad gradients).
    // A small random offset perturbs the field so each seed looks distinct.
    val noise = OpenSimplexNoise(SEED)
    val scale = 0.0015          // controls band width — smaller = wider bands
    val offsetX = rng.rndf(-1000f, 1000f).toDouble()
    val offsetY = rng.rndf(-1000f, 1000f).toDouble()

    // Earthy 4-stop palette: antiqueWhite → tan → peru → oliveDrab → sienna
    // t in [0,1] maps across the ramp via 4 lerp segments.
    val stop0 = CssColors.antiqueWhite
    val stop1 = CssColors.tan
    val stop2 = CssColors.peru
    val stop3 = CssColors.oliveDrab
    val stop4 = CssColors.sienna

    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val nx = px * scale + offsetX
            val ny = py * scale + offsetY
            val n = noise.random2D(nx, ny)   // range ~[-1, 1]

            // Map n from [-1,1] to [0,1]
            val t = (n + 1.0) * 0.5          // Double in [0,1]

            // 4-segment ramp across 5 stops
            val color: Int = when {
                t < 0.25 -> lerpColor(stop0, stop1, (t / 0.25).toFloat())
                t < 0.50 -> lerpColor(stop1, stop2, ((t - 0.25) / 0.25).toFloat())
                t < 0.75 -> lerpColor(stop2, stop3, ((t - 0.50) / 0.25).toFloat())
                else     -> lerpColor(stop3, stop4, ((t - 0.75) / 0.25).toFloat())
            }

            c.drawPoint(px.toFloat(), py.toFloat(), fillOf(color))
        }
    }

    gart.saveImage(g)
}
