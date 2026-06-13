package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.color.lerpColor
import dev.oblac.gart.gfx.closedPathOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.math.*
import kotlin.math.*
import kotlin.random.Random
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.Point

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * Differential growth from first principles.
 *
 * Style x Technique: Differential growth x Organic / soft
 *   -> Catmull-Rom smooth curve; low alpha; tapered ends (implemented as multiple
 *      overlaid low-alpha strokes at varying widths for a soft tapering feel).
 *
 * Theme: Cream & blues
 *   -> bg cornsilk, stroke palette powderBlue->navy
 */
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("GrowthCurves", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // --- Background ---
    c.clear(CssColors.cornsilk)

    // --- Parameters (Organic / soft x Differential growth defaults) ---
    val cx = d.wf / 2f
    val cy = d.hf / 2f

    // Initial ring: ~40 nodes on a circle of radius ~12% of canvas
    val initialRadius = SIZE * 0.12f
    val initialNodeCount = 40
    val nodes: MutableList<Point> = MutableList(initialNodeCount) { i ->
        val angle = (2.0 * PI * i / initialNodeCount).toFloat()
        Point(cx + initialRadius * cos(angle), cy + initialRadius * sin(angle))
    }

    // Growth parameters
    val maxEdgeLength = SIZE * 0.018f    // insert midpoint if segment > this
    val repulsionRadius = SIZE * 0.022f  // nodes repel within this radius
    val repulsionStrength = 0.45f
    val attractionStrength = 0.18f       // spring pull toward path-neighbors
    val margin = SIZE * 0.04f            // keep nodes inside canvas with margin

    // Organic jitter (Brownian nudge per step)
    val brownianStrength = SIZE * 0.0008f

    val iterations = 220

    // Run simulation
    repeat(iterations) {
        val n = nodes.size
        val deltas = Array(n) { floatArrayOf(0f, 0f) }

        // 1. Spring attraction: pull each node toward its two path-neighbors
        for (i in 0 until n) {
            val prev = nodes[(i + n - 1) % n]
            val next = nodes[(i + 1) % n]
            val p = nodes[i]
            deltas[i][0] += (prev.x + next.x) / 2f - p.x
            deltas[i][1] += (prev.y + next.y) / 2f - p.y
        }

        // 2. Repulsion: push nodes apart if too close (O(n^2) but n stays manageable)
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == j) continue
                val dx = nodes[i].x - nodes[j].x
                val dy = nodes[i].y - nodes[j].y
                val dist2 = dx * dx + dy * dy
                if (dist2 < repulsionRadius * repulsionRadius && dist2 > 0.001f) {
                    val dist = sqrt(dist2)
                    val force = repulsionStrength * (repulsionRadius - dist) / repulsionRadius
                    deltas[i][0] += (dx / dist) * force
                    deltas[i][1] += (dy / dist) * force
                }
            }
        }

        // 3. Apply deltas + Brownian jitter, clamp to canvas
        for (i in 0 until n) {
            val nx = (nodes[i].x + deltas[i][0] * attractionStrength
                    + rng.rndf(-brownianStrength, brownianStrength))
                .coerceIn(margin, d.wf - margin)
            val ny = (nodes[i].y + deltas[i][1] * attractionStrength
                    + rng.rndf(-brownianStrength, brownianStrength))
                .coerceIn(margin, d.hf - margin)
            nodes[i] = Point(nx, ny)
        }

        // 4. Subdivision: insert midpoint for segments longer than maxEdgeLength
        val toInsert = mutableListOf<Int>()
        val currentN = nodes.size
        for (i in 0 until currentN) {
            val a = nodes[i]
            val b = nodes[(i + 1) % currentN]
            val dx = b.x - a.x
            val dy = b.y - a.y
            if (dx * dx + dy * dy > maxEdgeLength * maxEdgeLength) {
                toInsert.add(i)
            }
        }
        // Insert in reverse order so indices stay valid
        for (idx in toInsert.reversed()) {
            val a = nodes[idx]
            val b = nodes[(idx + 1) % nodes.size]
            val mid = Point((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            nodes.add(idx + 1, mid)
        }
    }

    // --- Catmull-Rom smooth path from the grown nodes ---
    // Build a smooth closed polyline by inserting Catmull-Rom interpolated points
    fun catmullRomClosed(pts: List<Point>, stepsPerSegment: Int): List<Point> {
        val result = mutableListOf<Point>()
        val n = pts.size
        for (i in 0 until n) {
            val p0 = pts[(i - 1 + n) % n]
            val p1 = pts[i]
            val p2 = pts[(i + 1) % n]
            val p3 = pts[(i + 2) % n]
            for (s in 0 until stepsPerSegment) {
                val t = s.toFloat() / stepsPerSegment
                val t2 = t * t
                val t3 = t2 * t
                val x = 0.5f * (
                    (2f * p1.x) +
                    (-p0.x + p2.x) * t +
                    (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                    (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3
                )
                val y = 0.5f * (
                    (2f * p1.y) +
                    (-p0.y + p2.y) * t +
                    (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                    (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
                )
                result.add(Point(x, y))
            }
        }
        return result
    }

    val smoothNodes = catmullRomClosed(nodes, stepsPerSegment = 4)
    val path = closedPathOf(smoothNodes)

    // --- Organic / soft: low alpha, round caps, layered strokes ---
    // Cream & blues palette: powderBlue -> steelBlue -> navy ramp
    val colorLight = CssColors.powderBlue   // 0xFFB0E0E6
    val colorMid   = CssColors.steelBlue    // 0xFF4682B4
    val colorDark  = CssColors.navy         // 0xFF000080

    // Draw several overlapping passes at different widths and alphas for a soft organic feel
    data class StrokePass(val width: Float, val alpha: Int, val t: Float)
    val passes = listOf(
        StrokePass(SIZE * 0.006f, 18, 0.15f),   // wide, very transparent, light blue
        StrokePass(SIZE * 0.004f, 28, 0.35f),   // mid
        StrokePass(SIZE * 0.0025f, 40, 0.55f),  // narrower, mid-dark
        StrokePass(SIZE * 0.0015f, 55, 0.80f),  // hairline, dark blue
        StrokePass(SIZE * 0.0008f, 70, 1.0f),   // finest, navy
    )

    for (pass in passes) {
        val baseColor = when {
            pass.t < 0.5f -> lerpColor(colorLight, colorMid, pass.t * 2f)
            else -> lerpColor(colorMid, colorDark, (pass.t - 0.5f) * 2f)
        }
        val paint = strokeOf(baseColor, pass.width).apply {
            this.alpha = pass.alpha
            this.strokeCap = PaintStrokeCap.ROUND
            this.strokeJoin = PaintStrokeJoin.ROUND
            this.isAntiAlias = true
        }
        c.drawPath(path, paint)
    }

    gart.saveImage(g)
}
