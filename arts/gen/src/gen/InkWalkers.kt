package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.pathOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.Point

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * InkWalkers — Random walkers, Monochrome/ink, Minimal/sparse.
 *
 * Style x Technique defaults (Random walkers x Minimal/sparse):
 *   3–5 walkers; thick strokes; long runs (>=5000 steps).
 * Theme (Monochrome/ink): ivory background, black ink strokes.
 * Style (Minimal/sparse): background dominates, few walkers, generous empty space,
 *   thin clean strokes (but thick per the technique default), no jitter.
 *
 * Walkers use heading + small angular drift for smooth organic paths.
 * Each walker's path is collected as a List<Point> and drawn as a polyline.
 */
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("InkWalkers", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // Ivory background — Monochrome/ink theme
    c.clear(CssColors.ivory)

    // Style x Technique: 3–5 walkers, thick strokes, >=5000 steps each
    val walkerCount = rng.rndi(3, 6) // 3 to 5 inclusive
    val steps = 6000
    val stepSize = d.wf * 0.003f   // ~3px per step at 1024
    val maxAngleDrift = 0.08f       // radians per step — gentle momentum drift
    val strokeWidth = d.wf * 0.004f // thick ink stroke (~4px at 1024)

    // Ink color — near-black for ink feel
    val inkColor = (0xFF shl 24) or (0x1A shl 16) or (0x1A shl 8) or 0x1A

    // Build and draw each walker
    repeat(walkerCount) {
        // Start at a random interior point (keep away from edges)
        val margin = d.wf * 0.15f
        var x = rng.rndf(margin, d.wf - margin)
        var y = rng.rndf(margin, d.hf - margin)
        var heading = rng.rndf(0f, (2f * Math.PI).toFloat())

        val points = mutableListOf<Point>()
        points.add(Point(x, y))

        repeat(steps) {
            // Drift heading by a small random angle (momentum-based wandering)
            heading += rng.rndf(-maxAngleDrift, maxAngleDrift)

            x += cos(heading) * stepSize
            y += sin(heading) * stepSize

            // Soft-wrap: reflect off canvas boundary so walker stays visible
            if (x < 0f) { x = -x; heading = Math.PI.toFloat() - heading }
            if (x > d.wf) { x = 2f * d.wf - x; heading = Math.PI.toFloat() - heading }
            if (y < 0f) { y = -y; heading = -heading }
            if (y > d.hf) { y = 2f * d.hf - y; heading = -heading }

            points.add(Point(x, y))
        }

        val path = pathOf(points)
        val paint = strokeOf(inkColor, strokeWidth)
        paint.isAntiAlias = true
        c.drawPath(path, paint)
    }

    gart.saveImage(g)
}
