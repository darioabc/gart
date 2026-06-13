package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.Circle
import dev.oblac.gart.gfx.drawCircle
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*       // brings all Random extension fns into scope
import kotlin.math.sqrt
import kotlin.random.Random

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * CreamPacking — Shape packing / Cream & blues / Hard-edge geometric
 *
 * Style×Technique cell: "Circles on grid with fixed size increments; crisp outlines"
 * - Flat fills from a powderBlue→navy ramp; crisp navy stroke outline on every circle.
 * - Naive seeded packer (simpleCirclePacker uses Random.Default internally, so we
 *   implement from scratch to honour SEED).
 * - Three passes: large circles first, then medium fill-in, then small gap-fill.
 */

// Blue ramp from light to dark (7 stops) — all confirmed in CssColors.kt
private val BLUE_RAMP = listOf(
    CssColors.powderBlue,
    CssColors.lightBlue,
    CssColors.skyBlue,
    CssColors.cornflowerBlue,
    CssColors.steelBlue,
    CssColors.royalBlue,
    CssColors.navy,
)

private val STROKE_PAINT = strokeOf(CssColors.navy, 2f).apply { isAntiAlias = false }

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("CreamPacking", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // Background: cream (cornsilk)
    c.clear(CssColors.cornsilk)

    // Margin so outlines at the edge don't get clipped
    val margin = (SIZE * 0.02f)
    val left = margin
    val top = margin
    val right = d.wf - margin
    val bottom = d.hf - margin

    // All placed circles — checked against when placing new ones
    val placed = mutableListOf<Circle>()

    // Minimum gap between circle edges (hard-edge: tight but not touching)
    val padding = (SIZE * 0.005f)

    fun Circle.isValid(): Boolean {
        if (x - radius < left || x + radius > right) return false
        if (y - radius < top  || y + radius > bottom) return false
        for (other in placed) {
            val dx = x - other.x
            val dy = y - other.y
            val minDist = radius + other.radius + padding
            if (dx * dx + dy * dy < minDist * minDist) return false
        }
        return true
    }

    // Size increments are fixed (hard-edge style directive: "fixed size increments")
    data class Pass(val attempts: Int, val minR: Float, val maxR: Float, val step: Float)

    val sizeF = SIZE.toFloat()
    val passes = listOf(
        Pass(attempts = 8_000,  minR = sizeF * 0.06f, maxR = sizeF * 0.14f, step = sizeF * 0.01f),
        Pass(attempts = 20_000, minR = sizeF * 0.025f, maxR = sizeF * 0.06f, step = sizeF * 0.005f),
        Pass(attempts = 60_000, minR = sizeF * 0.008f, maxR = sizeF * 0.025f, step = sizeF * 0.002f),
    )

    for (pass in passes) {
        repeat(pass.attempts) {
            val cx = rng.rndf(left, right)
            val cy = rng.rndf(top, bottom)

            // Start at minR; grow in fixed steps up to maxR
            var bestCircle: Circle? = null
            var r = pass.minR
            while (r <= pass.maxR + 0.001f) {
                val candidate = Circle(cx, cy, r)
                if (candidate.isValid()) {
                    bestCircle = candidate
                    r += pass.step
                } else {
                    break
                }
            }
            if (bestCircle != null) {
                placed.add(bestCircle)
            }
        }
    }

    // Draw in size order largest→smallest so large circles are painted first.
    // Small circles overlapping the stroke of large ones will still look crisp
    // because each gets its own fill + outline.
    placed.sortByDescending { it.radius }

    for (circle in placed) {
        // Pick a blue from the ramp: larger circles skew toward lighter, smaller toward darker
        val t = ((circle.radius - (sizeF * 0.008f)) / (sizeF * 0.14f)).coerceIn(0f, 1f)
        // Invert: biggest = lightest (powderBlue index 0), smallest = darkest (navy index 6)
        val idx = ((1f - t) * (BLUE_RAMP.size - 1)).toInt().coerceIn(0, BLUE_RAMP.size - 1)
        val color = BLUE_RAMP[idx]

        // Flat fill — hard-edge, no transparency
        c.drawCircle(circle, fillOf(color))
        // Crisp navy outline
        c.drawCircle(circle, STROKE_PAINT)
    }

    gart.saveImage(g)
}
