package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.Palette
import dev.oblac.gart.gfx.drawArc
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Point
import org.jetbrains.skia.Rect

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM · Recursive quadtree collage / quilt ────────────────────────────────
// The frame is recursively quartered — harder where a simplex "busyness" field is
// active — down to a min cell. Every LEAF gets one of a dozen distinct print-shop
// micro-treatments (solid, stipple, hatch, rings, Truchet arc, halftone gradient,
// checker, flow scribble, nested squares, dot-grid, stripes, cross-hatch). Colours
// are pulled from "Patriot Gold" keyed by position so regions harmonise into one
// composed mixed-media quilt. Grain finish, like a risograph proof.
private lateinit var rng: Random
private lateinit var noise: OpenSimplexNoise
private lateinit var ramp: Palette
private lateinit var ground: Palette

private class Cell(val x: Float, val y: Float, val s: Float)

// palette lookup keyed by position so neighbouring cells share a regional hue
private fun col(x: Float, y: Float, jitter: Float = 0f): Int {
    val nv = (noise.random2D(x * 0.0021f, y * 0.0021f).toFloat() * 0.5f + 0.5f + jitter)
        .coerceIn(0f, 1f)
    return ramp.bound(nv * (ramp.size - 1))
}

private fun groundCol(x: Float, y: Float): Int {
    val nv = (noise.random2D(x * 0.0014f + 80f, y * 0.0014f).toFloat() * 0.5f + 0.5f).coerceIn(0f, 1f)
    return ground.bound(nv * (ground.size - 1))
}

private fun fill(c: Canvas, cell: Cell, color: Int) {
    c.drawRect(Rect.makeLTRB(cell.x, cell.y, cell.x + cell.s, cell.y + cell.s), fillOf(color))
}

// ── micro-treatments ───────────────────────────────────────────────────────────

private fun tSolid(c: Canvas, cell: Cell) {
    fill(c, cell, col(cell.x, cell.y))
}

private fun tStipple(c: Canvas, cell: Cell) {
    fill(c, cell, groundCol(cell.x, cell.y))
    val ink = col(cell.x, cell.y, 0.25f)
    val n = (cell.s * cell.s / 26f).toInt().coerceIn(20, 1400)
    val rMax = (cell.s * 0.028f).coerceAtLeast(1.1f)
    repeat(n) {
        val px = cell.x + rng.nextFloat() * cell.s
        val py = cell.y + rng.nextFloat() * cell.s
        c.drawCircle(px, py, rng.rndf(0.6f, rMax), fillOf(ink))
    }
}

private fun tHatch(c: Canvas, cell: Cell) {
    fill(c, cell, groundCol(cell.x, cell.y))
    val ink = col(cell.x, cell.y, 0.2f)
    val ang = rng.rndf(0f, TAUf)
    val dx = cos(ang); val dy = sin(ang)
    val gap = (cell.s * rng.rndf(0.05f, 0.12f)).coerceAtLeast(2.2f)
    val w = (cell.s * 0.02f).coerceAtLeast(1.0f)
    val cxC = cell.x + cell.s / 2f; val cyC = cell.y + cell.s / 2f
    val reach = cell.s * 0.75f
    var off = -reach
    val paint = strokeOf(ink, w).apply { strokeCap = PaintStrokeCap.ROUND; isAntiAlias = true }
    c.save(); c.clipRect(Rect.makeLTRB(cell.x, cell.y, cell.x + cell.s, cell.y + cell.s))
    while (off <= reach) {
        // line through (cxC + off*-dy, cyC + off*dx) along (dx,dy)
        val ox = cxC + (-dy) * off; val oy = cyC + dx * off
        c.drawLine(Point(ox - dx * reach, oy - dy * reach), Point(ox + dx * reach, oy + dy * reach), paint)
        off += gap
    }
    c.restore()
}

private fun tCrossHatch(c: Canvas, cell: Cell) {
    tHatch(c, cell)
    val ink = col(cell.x, cell.y, -0.2f)
    val gap = (cell.s * rng.rndf(0.06f, 0.14f)).coerceAtLeast(2.4f)
    val w = (cell.s * 0.018f).coerceAtLeast(1.0f)
    val paint = strokeOf(ink, w).apply { strokeCap = PaintStrokeCap.ROUND; isAntiAlias = true }
    c.save(); c.clipRect(Rect.makeLTRB(cell.x, cell.y, cell.x + cell.s, cell.y + cell.s))
    var yy = cell.y
    while (yy <= cell.y + cell.s) {
        c.drawLine(Point(cell.x, yy), Point(cell.x + cell.s, yy), paint)
        yy += gap
    }
    c.restore()
}

private fun tRings(c: Canvas, cell: Cell) {
    fill(c, cell, groundCol(cell.x, cell.y))
    val ink = col(cell.x, cell.y, 0.18f)
    val cxC = cell.x + cell.s / 2f; val cyC = cell.y + cell.s / 2f
    val step = (cell.s * rng.rndf(0.06f, 0.11f)).coerceAtLeast(2.5f)
    val w = (cell.s * 0.02f).coerceAtLeast(1.1f)
    val paint = strokeOf(ink, w).apply { mode = PaintMode.STROKE; isAntiAlias = true }
    var r = step
    while (r < cell.s * 0.72f) {
        c.drawCircle(cxC, cyC, r, paint)
        r += step
    }
    c.drawCircle(cxC, cyC, step * 0.5f, fillOf(ink))
}

private fun tArc(c: Canvas, cell: Cell) {
    fill(c, cell, col(cell.x, cell.y, -0.18f))
    val ink = col(cell.x, cell.y, 0.28f)
    val r = cell.s / 2f
    val w = (cell.s * 0.16f).coerceAtLeast(1.4f)
    val paint = strokeOf(ink, w).apply { mode = PaintMode.STROKE; strokeCap = PaintStrokeCap.ROUND; isAntiAlias = true }
    val x = cell.x; val y = cell.y; val s = cell.s
    if (rng.nextBoolean()) {
        c.drawArc(Rect.makeLTRB(x - r, y - r, x + r, y + r), 0f, 90f, false, paint)
        c.drawArc(Rect.makeLTRB(x + s - r, y + s - r, x + s + r, y + s + r), 180f, 90f, false, paint)
    } else {
        c.drawArc(Rect.makeLTRB(x + s - r, y - r, x + s + r, y + r), 90f, 90f, false, paint)
        c.drawArc(Rect.makeLTRB(x - r, y + s - r, x + r, y + s + r), 270f, 90f, false, paint)
    }
}

private fun tHalftone(c: Canvas, cell: Cell) {
    fill(c, cell, groundCol(cell.x, cell.y))
    val ink = col(cell.x, cell.y, 0.22f)
    val vertical = rng.nextBoolean()
    val cells = (cell.s / (cell.s * rng.rndf(0.10f, 0.16f))).toInt().coerceIn(4, 16)
    val cs = cell.s / cells
    for (gy in 0 until cells) for (gx in 0 until cells) {
        val px = cell.x + (gx + 0.5f) * cs
        val py = cell.y + (gy + 0.5f) * cs
        // dot radius ramps along an axis → tonal gradient like a halftone screen
        val t = if (vertical) gy.toFloat() / (cells - 1) else gx.toFloat() / (cells - 1)
        val rr = (cs * 0.52f) * t
        if (rr > 0.5f) c.drawCircle(px, py, rr, fillOf(ink))
    }
}

private fun tChecker(c: Canvas, cell: Cell) {
    val a = col(cell.x, cell.y)
    val b = col(cell.x, cell.y, 0.4f)
    val n = rng.nextInt(2, 6)
    val cs = cell.s / n
    for (gy in 0 until n) for (gx in 0 until n) {
        val color = if ((gx + gy) % 2 == 0) a else b
        c.drawRect(Rect.makeLTRB(cell.x + gx * cs, cell.y + gy * cs, cell.x + (gx + 1) * cs, cell.y + (gy + 1) * cs), fillOf(color))
    }
}

private fun tScribble(c: Canvas, cell: Cell) {
    fill(c, cell, groundCol(cell.x, cell.y))
    val ink = col(cell.x, cell.y, 0.3f)
    val w = (cell.s * 0.014f).coerceAtLeast(0.9f)
    val paint = strokeOf(ink, w).apply { strokeCap = PaintStrokeCap.ROUND; isAntiAlias = true }
    val streamers = rng.nextInt(6, 14)
    c.save(); c.clipRect(Rect.makeLTRB(cell.x, cell.y, cell.x + cell.s, cell.y + cell.s))
    repeat(streamers) {
        var px = cell.x + rng.nextFloat() * cell.s
        var py = cell.y + rng.nextFloat() * cell.s
        val steps = 24
        val stepLen = cell.s * 0.06f
        for (i in 0 until steps) {
            val ang = noise.random2D(px * 0.02f, py * 0.02f).toFloat() * TAUf
            val nx = px + cos(ang) * stepLen
            val ny = py + sin(ang) * stepLen
            c.drawLine(Point(px, py), Point(nx, ny), paint)
            px = nx; py = ny
        }
    }
    c.restore()
}

private fun tNested(c: Canvas, cell: Cell) {
    val layers = rng.nextInt(4, 8)
    for (i in 0 until layers) {
        val t = i.toFloat() / layers
        val inset = (cell.s * 0.5f) * t
        val color = col(cell.x, cell.y, (t - 0.4f) * 0.8f)
        c.drawRect(Rect.makeLTRB(cell.x + inset, cell.y + inset, cell.x + cell.s - inset, cell.y + cell.s - inset), fillOf(color))
    }
}

private fun tDotGrid(c: Canvas, cell: Cell) {
    fill(c, cell, col(cell.x, cell.y, -0.2f))
    val ink = col(cell.x, cell.y, 0.32f)
    val n = rng.nextInt(3, 8)
    val cs = cell.s / n
    val rr = cs * rng.rndf(0.18f, 0.34f)
    for (gy in 0 until n) for (gx in 0 until n) {
        c.drawCircle(cell.x + (gx + 0.5f) * cs, cell.y + (gy + 0.5f) * cs, rr, fillOf(ink))
    }
}

private fun tStripes(c: Canvas, cell: Cell) {
    val a = col(cell.x, cell.y)
    val b = col(cell.x, cell.y, 0.35f)
    fill(c, cell, a)
    val vertical = rng.nextBoolean()
    val n = rng.nextInt(3, 9)
    val cs = cell.s / n
    for (i in 0 until n) {
        if (i % 2 == 0) continue
        if (vertical)
            c.drawRect(Rect.makeLTRB(cell.x + i * cs, cell.y, cell.x + (i + 1) * cs, cell.y + cell.s), fillOf(b))
        else
            c.drawRect(Rect.makeLTRB(cell.x, cell.y + i * cs, cell.x + cell.s, cell.y + (i + 1) * cs), fillOf(b))
    }
}

private fun tBullseye(c: Canvas, cell: Cell) {
    val cxC = cell.x + cell.s / 2f; val cyC = cell.y + cell.s / 2f
    fill(c, cell, col(cell.x, cell.y, -0.25f))
    val bands = rng.nextInt(3, 7)
    val step = (cell.s * 0.72f) / bands
    for (i in bands downTo 1) {
        val color = col(cell.x, cell.y, (i.toFloat() / bands - 0.4f))
        c.drawCircle(cxC, cyC, step * i, fillOf(color))
    }
}

private val treatments: List<(Canvas, Cell) -> Unit> = listOf(
    ::tSolid, ::tStipple, ::tHatch, ::tCrossHatch, ::tRings, ::tArc,
    ::tHalftone, ::tChecker, ::tScribble, ::tNested, ::tDotGrid, ::tStripes, ::tBullseye
)

private fun renderLeaf(c: Canvas, cell: Cell) {
    // bias slightly toward solid/halftone for visual rest among busy cells
    val t = treatments[rng.nextInt(treatments.size)]
    t(c, cell)
    // thin keyline so the quilt seams read as composition
    val seam = strokeOf(0xFF1A1410.toInt(), (cell.s * 0.012f).coerceIn(0.6f, 2.4f)).apply {
        mode = PaintMode.STROKE; isAntiAlias = true
    }
    c.drawRect(Rect.makeLTRB(cell.x, cell.y, cell.x + cell.s, cell.y + cell.s), seam)
}

private fun subdivide(c: Canvas, x: Float, y: Float, s: Float, minS: Float, depth: Int) {
    val busy = noise.random2D(x * 0.0035f + 30f, y * 0.0035f).toFloat() * 0.5f + 0.5f
    val canSplit = s / 2f >= minS && depth < 6
    val pSplit = 0.28f + 0.5f * busy
    if (canSplit && rng.nextFloat() < pSplit) {
        val h = s / 2f
        subdivide(c, x, y, h, minS, depth + 1)
        subdivide(c, x + h, y, h, minS, depth + 1)
        subdivide(c, x, y + h, h, minS, depth + 1)
        subdivide(c, x + h, y + h, h, minS, depth + 1)
    } else {
        renderLeaf(c, Cell(x, y, s))
    }
}

fun main() {
    println("seed=$SEED")
    rng = Random(SEED)

    val gart = Gart.of("CollageQuad", SIZE, SIZE)
    val d = gart.d
    noise = OpenSimplexNoise(SEED)
    ramp = Coolors.patriotGold.expand(256)
    // a calmer ground sub-palette derived from the same family for negative space
    ground = Palette(0xFFEAE2B7, 0xFFFCBF49, 0xFF003049, 0xFFF77F00).expand(256)

    val buf = gart.gartvas()
    val c = buf.canvas
    c.clear(0xFFEAE2B7.toInt())

    val base = SIZE / 4f
    val minS = SIZE / 48f
    val cols = (d.wf / base).toInt() + 1
    val rows = (d.hf / base).toInt() + 1
    for (gy in 0 until rows) for (gx in 0 until cols) {
        subdivide(c, gx * base, gy * base, base, minS, 0)
    }

    // one bold framing border to seat the quilt as a composed object
    val border = strokeOf(0xFF003049.toInt(), SIZE * 0.012f).apply {
        mode = PaintMode.STROKE; isAntiAlias = true
    }
    val inset = SIZE * 0.006f
    c.drawRect(Rect.makeLTRB(inset, inset, SIZE - inset, SIZE - inset), border)

    val finalv = grainOnly(gart, buf.snapshot(), grain = 0.06f)
    gart.saveImage(finalv)
    println("  collage done (${cols}x$rows base grid, ${treatments.size} treatments)")
}
