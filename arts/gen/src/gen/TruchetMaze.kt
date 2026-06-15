package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.Palette
import dev.oblac.gart.gfx.drawArc
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.random.Random
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Rect

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM #3 · Recursive Truchet tiling ───────────────────────────────────────
// Smith / Truchet tiles — two quarter-circle arcs per cell — but the grid recursively
// subdivides where the dice say so, mixing big lazy curves with tight knots of detail.
// Because every tile connects edge-midpoints, the arcs join into one endless flowing
// circuit across scales. Neon arcs (coolors "Retro Pop") over near-black, bloomed.
private lateinit var rng: Random
private lateinit var noise: OpenSimplexNoise
private lateinit var ramp: Palette

private fun tile(c: Canvas, x: Float, y: Float, s: Float) {
    val r = s / 2f
    // colour from a smooth noise field over the tile centre → connected arcs share
    // a hue regionally, so the eye follows the flowing circuit instead of confetti
    val nv = (noise.random2D((x + r) * 0.0032f, (y + r) * 0.0032f).toFloat() * 0.5f + 0.5f).coerceIn(0f, 1f)
    val paint = strokeOf(ramp.bound(nv * (ramp.size - 1)), (s * 0.17f).coerceAtLeast(1.2f)).apply {
        mode = PaintMode.STROKE; strokeCap = PaintStrokeCap.ROUND; isAntiAlias = true
    }
    if (rng.nextBoolean()) {
        c.drawArc(Rect.makeLTRB(x - r, y - r, x + r, y + r), 0f, 90f, false, paint)
        c.drawArc(Rect.makeLTRB(x + s - r, y + s - r, x + s + r, y + s + r), 180f, 90f, false, paint)
    } else {
        c.drawArc(Rect.makeLTRB(x + s - r, y - r, x + s + r, y + r), 90f, 90f, false, paint)
        c.drawArc(Rect.makeLTRB(x - r, y + s - r, x + r, y + s + r), 270f, 90f, false, paint)
    }
}

private fun subdivide(c: Canvas, x: Float, y: Float, s: Float, minS: Float, depth: Int) {
    // subdivide harder where the noise field is busy → clear contrast of scales
    val busy = noise.random2D(x * 0.004f + 50f, y * 0.004f).toFloat() * 0.5f + 0.5f
    val canSplit = s / 2f >= minS && depth < 5
    if (canSplit && rng.nextFloat() < 0.30f + 0.45f * busy) {
        val h = s / 2f
        subdivide(c, x, y, h, minS, depth + 1)
        subdivide(c, x + h, y, h, minS, depth + 1)
        subdivide(c, x, y + h, h, minS, depth + 1)
        subdivide(c, x + h, y + h, h, minS, depth + 1)
    } else {
        tile(c, x, y, s)
    }
}

fun main() {
    println("seed=$SEED")
    rng = Random(SEED)

    val gart = Gart.of("TruchetMaze", SIZE, SIZE)
    val d = gart.d
    val ground = 0xFF0A0A12.toInt()
    noise = OpenSimplexNoise(SEED)
    ramp = Coolors.retroPop.expand(256)

    val buf = gart.gartvas()
    val c = buf.canvas

    val base = SIZE / 5f                 // top-level cell size
    val minS = SIZE / 40f
    val cols = (d.wf / base).toInt() + 1
    val rows = (d.hf / base).toInt() + 1
    for (gy in 0 until rows) for (gx in 0 until cols) {
        subdivide(c, gx * base, gy * base, base, minS, 0)
    }

    val finalv = bloom(gart, buf.snapshot(), ground, SIZE / 300f, grain = 0.05f)
    gart.saveImage(finalv)
    println("  done (${cols}x$rows base grid)")
}
