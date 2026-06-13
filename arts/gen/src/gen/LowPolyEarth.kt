package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.color.lerpColor
import dev.oblac.gart.gfx.Triangle
import dev.oblac.gart.gfx.drawTriangle
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.random.Random
import org.jetbrains.skia.Point

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("LowPolyEarth", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // Style x Technique: Triangulation/low-poly x Hard-edge geometric
    // Directives: flat fill, black stroke on every edge, no gradient
    // Earthy palette: antiqueWhite bg, sienna/peru/darkKhaki/oliveDrab/tan

    // Grid parameters — enough triangles for good low-poly coverage
    // Hard-edge: ~16x16 grid → ~512 triangles (well above ≥500 dense target is optional,
    // we stay at 14x14 for visual breathing room; still far above "≤80 sparse")
    val cols = 14
    val rows = 14

    val cellW = d.wf / cols
    val cellH = d.hf / rows

    // Jitter amplitude: up to 35% of cell size for organic-but-geometric feel
    val jitterX = cellW * 0.35f
    val jitterY = cellH * 0.35f

    // Build grid of jittered points — (cols+1) x (rows+1) vertices
    val pts = Array(rows + 1) { row ->
        Array(cols + 1) { col ->
            val baseX = col * cellW
            val baseY = row * cellH
            // Interior points are jittered; border points stay on edge to avoid
            // triangles protruding outside the canvas.
            val jx = if (col in 1 until cols) rng.rndf(-jitterX, jitterX) else 0f
            val jy = if (row in 1 until rows) rng.rndf(-jitterY, jitterY) else 0f
            Point(baseX + jx, baseY + jy)
        }
    }

    // Noise instance for color variation by centroid position
    val noise = OpenSimplexNoise(SEED)
    val noiseScale = 2.5 / SIZE  // how fast colour changes across the canvas

    // Earthy palette stops in increasing warmth / darkness order
    // We map noise value (–1..1) to an index into this palette
    val palette = intArrayOf(
        CssColors.oliveDrab,     // muted green
        CssColors.darkKhaki,     // golden khaki
        CssColors.tan,           // warm sandy
        CssColors.peru,          // mid brown
        CssColors.sienna         // deep rust
    )

    // Flat fill paint (re-created per triangle with exact color)
    // Stroke: thin dark charcoal edge for hard-edge crispness
    val strokeColor = (0xFF shl 24) or (0x2B shl 16) or (0x1D shl 8) or 0x0F  // near-black warm
    val strokePaint = strokeOf(strokeColor, 1.0f)

    // Clear background to antiqueWhite
    c.clear(CssColors.antiqueWhite)

    // For each cell, split quad into 2 triangles (lower-left and upper-right)
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val tl = pts[row][col]
            val tr = pts[row][col + 1]
            val bl = pts[row + 1][col]
            val br = pts[row + 1][col + 1]

            // Triangle 1: top-left / top-right / bottom-left
            val t1 = Triangle(tl, tr, bl)
            val c1 = earthyColor(noise, noiseScale, t1.centroid, palette, rng)
            c.drawTriangle(t1, fillOf(c1))
            c.drawTriangle(t1, strokePaint)

            // Triangle 2: top-right / bottom-right / bottom-left
            val t2 = Triangle(tr, br, bl)
            val c2 = earthyColor(noise, noiseScale, t2.centroid, palette, rng)
            c.drawTriangle(t2, fillOf(c2))
            c.drawTriangle(t2, strokePaint)
        }
    }

    gart.saveImage(g)
}

/**
 * Pick an earthy colour for a triangle based on its centroid position via OpenSimplex noise.
 * A small per-triangle random nudge keeps adjacent cells from being identical.
 */
private fun earthyColor(
    noise: OpenSimplexNoise,
    scale: Double,
    centroid: Point,
    palette: IntArray,
    rng: Random
): Int {
    // Noise in [-1, 1]; shift to [0, 1]
    val n = (noise.random2D(centroid.x * scale, centroid.y * scale) + 1.0) / 2.0

    // Small per-triangle nudge (±8% of range) for variety
    val nudge = rng.rndf(-0.08f, 0.08f)
    val t = ((n + nudge).coerceIn(0.0, 1.0)).toFloat()

    // Map t into palette with lerp between adjacent stops
    val maxIdx = palette.size - 1
    val scaled = t * maxIdx
    val lo = scaled.toInt().coerceIn(0, maxIdx - 1)
    val hi = lo + 1
    val frac = scaled - lo

    return lerpColor(palette[lo], palette[hi], frac)
}
