package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.math.*       // brings all Random extension fns into scope
import dev.oblac.gart.noise.SimplexNoise
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import java.io.File
import java.util.Locale
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PathEffect
import org.jetbrains.skia.Rect

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> to reproduce. Printed to stdout.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * Radial rays: a ring of straight rays around an accent disc, where each ray is drawn
 * as a hairline dotted line and turns into a solid stroke wherever a hidden shape field
 * is positive. The stroke steps up through three widths the deeper it sits inside the
 * shape, so the field reads as a silhouette made only of ray thickness.
 *
 * Technique: constructive geometry (hard-edge). Every preset shares the composition
 * (empty ring around the disc, rays between R_IN and R_OUT) and swaps the field:
 * eclipse crescent, petals, spiral arms, parallel tide bands, simplex islands, sonar
 * rings. Flat fills, no transparency; only a light grain pass for a printed feel.
 *
 * Each preset renders twice: once symmetrical (radialRaysNN), and once cut into bands
 * that slide back and forth to break the symmetry (radialRaysShiftNN). [RayShift] picks
 * horizontal bands sliding left/right, vertical bands sliding up/down, or both.
 *
 * Every image is written as a vector SVG and as a PNG preview. Both come from the same
 * geometry (ray segments, disc, band cuts); only the PNG gets the grain pass.
 *
 * All geometry is in units of SIZE, centred on the canvas: (u, v) in [-0.5, 0.5].
 */
private const val R_IN = 0.155f
private const val R_OUT = 0.42f
private const val STEP = 0.0015f          // radial sampling step along each ray

// Field value (same units) where a ray switches to thin, mid, and thick solid.
private const val T_MID = 0.010f
private const val T_THICK = 0.028f

// Stroke widths for dotted / thin / mid / thick, and the dot pattern.
private val WIDTHS = floatArrayOf(0.0011f, 0.0019f, 0.0029f, 0.0044f)
private const val DOT_ON = 0.0012f
private const val DOT_OFF = 0.0022f

private val TAU = (2 * PI).toFloat()

/** Signed "how deep inside the shape" at (u, v); r and a are the polar form. */
private typealias Field = (u: Float, v: Float, r: Float, a: Float) -> Float

private class RayInk(val ground: Int, val ink: Int, val accent: Int)

private class RayPreset(
    val tag: String,
    val ink: RayInk,
    val rays: Int,
    val discR: Float,
    val field: (Random) -> Field,
    val shift: RayShift,
)

private enum class RayShift { H, V, HV }

private fun ink(ground: Long, ink: Long, accent: Long) = RayInk(ground.toInt(), ink.toInt(), accent.toInt())

private val OLIVE = ink(0xFF2E2E27, 0xFFD9D0A7, 0xFFC94E22)       // the reference: olive ground, cream, vermilion
private val EMBER = ink(0xFF04151F, 0xFFEFD6AC, 0xFFC44900)       // Coolors.inkEmber
private val PATRIOT = ink(0xFF003049, 0xFFEAE2B7, 0xFFF77F00)     // Coolors.patriotGold
private val MOLTEN = ink(0xFFFDF0D5, 0xFF003049, 0xFFC1121F)      // Coolors.molten, light ground
private val SUNSET = ink(0xFF264653, 0xFFE9C46A, 0xFFE76F51)      // Coolors.sunset
private val PAPER = ink(0xFFECE5D2, 0xFF2B2B25, 0xFFC94E22)       // OLIVE inverted onto paper

// ---- fields ----

/** Crescent between an outer circle pushed toward [dir] and an inner one pushed away. */
private fun eclipse(rng: Random): Field {
    val dir = (1.25 * PI).toFloat() + rng.nextFloat() * 0.5f - 0.25f
    val ax = cos(dir)
    val ay = sin(dir)
    return { u, v, _, _ ->
        val outer = 0.365f - hypot(u - ax * 0.05f, v - ay * 0.05f)
        val inner = hypot(u + ax * 0.08f, v + ay * 0.08f) - 0.225f
        min(outer, inner)
    }
}

private fun petals(rng: Random): Field {
    val k = rng.nextInt(5, 9)
    val rot = rng.nextFloat() * TAU
    return { _, _, r, a ->
        val lobe = (0.5f + 0.5f * cos(k * (a - rot))).pow(1.6f)
        0.115f * lobe - abs(r - 0.285f)
    }
}

private fun spiral(rng: Random): Field {
    val arms = rng.nextInt(5, 9) * (if (rng.nextBoolean()) 1 else -1)
    val phase = rng.nextFloat() * TAU
    return { _, _, r, a ->
        0.04f * cos(TAU * r / 0.15f + arms * a + phase) - 0.008f - fadeOut(r)
    }
}

/** Parallel bands across the whole disc, like a banded planet. */
private fun tide(rng: Random): Field {
    val alpha = rng.nextFloat() * TAU
    val ca = cos(alpha)
    val sa = sin(alpha)
    val lambda = 0.24f + rng.nextFloat() * 0.06f
    val phase = rng.nextFloat() * TAU
    return { u, v, r, _ ->
        0.045f * cos(TAU * (u * ca + v * sa) / lambda + phase) - 0.01f - fadeOut(r)
    }
}

private fun islands(rng: Random): Field {
    val ox = rng.nextDouble(0.0, 100.0)
    val oy = rng.nextDouble(0.0, 100.0)
    return { u, v, r, _ ->
        0.055f * SimplexNoise.noise(u * 4.5 + ox, v * 4.5 + oy).toFloat() - 0.004f - fadeOut(r)
    }
}

/** Concentric rings that only fill in on one side, like a sonar sweep. */
private fun sonar(rng: Random): Field {
    val dir = rng.nextFloat() * TAU
    return { _, _, r, a ->
        0.03f * cos(TAU * r / 0.06f) + 0.035f * cos(a - dir) - 0.02f
    }
}

/** Pulls the field down near R_OUT so ray tips stay dotted. */
private fun fadeOut(r: Float) = 0.3f * max(0f, r - 0.35f)

private val PRESETS = listOf(
    RayPreset("eclipse", OLIVE, 96, 0.039f, ::eclipse, RayShift.H),
    RayPreset("petals", EMBER, 120, 0.046f, ::petals, RayShift.HV),
    RayPreset("spiral", PATRIOT, 144, 0.036f, ::spiral, RayShift.V),
    RayPreset("tide", MOLTEN, 96, 0.042f, ::tide, RayShift.V),
    RayPreset("islands", SUNSET, 120, 0.039f, ::islands, RayShift.H),
    RayPreset("sonar", PAPER, 108, 0.034f, ::sonar, RayShift.HV),
)

fun main() {
    println("seed=$SEED")
    val master = Random(SEED)

    val gart = Gart.of("radialRays", SIZE, SIZE)
    val s = SIZE.toFloat()
    val cx = gart.d.cx
    val cy = gart.d.cy

    PRESETS.forEachIndexed { i, p ->
        val rng = Random(master.nextLong())
        val field = p.field(rng)
        val art = RayArt(
            ink = p.ink,
            segs = (0 until p.rays).flatMap { k -> raySegments(cx, cy, s, k * TAU / p.rays, field) },
            cx = cx, cy = cy, discR = p.discR * s,
        )

        // Sliding both ways crosses the cuts into a grid, so use fewer, calmer bands.
        val chunk = if (p.shift == RayShift.HV) 1.8f else 1f
        val cuts = buildList {
            if (p.shift != RayShift.V) add(makeCut(horizontal = true, chunk, rng))
            if (p.shift != RayShift.H) add(makeCut(horizontal = false, chunk, rng))
        }

        listOf("radialRays" to emptyList(), "radialRaysShift" to cuts).forEach { (base, cs) ->
            val name = "%s%02d".format(base, i + 1)
            File("$name.svg").writeText(svg(art, cs))

            val g = gart.gartvas()
            g.canvas.clear(p.ink.ground)
            withCuts(g.canvas, cs) { drawArt(it, art) }
            gart.saveImage(grainOnly(gart, g.snapshot(), 0.05f), "$name.png")
        }
        println("  [${i + 1}/${PRESETS.size}] ${p.tag}: ${p.rays} rays, ${art.segs.size} strokes, shift ${p.shift}")
    }
    println("done: ${PRESETS.size * 2} svg + png pairs")
}

// ---- geometry ----

/** One stroke of a ray, in pixels: level 0 is the dotted underlay, 1..3 the solid widths. */
private class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val level: Int)

/** Everything one piece draws, before any cutting. */
private class RayArt(val ink: RayInk, val segs: List<Seg>, val cx: Float, val cy: Float, val discR: Float)

/**
 * One ray: the full length dotted underneath, then each run of samples that share a
 * solid level laid over it, so the dot pattern stays aligned from R_IN outwards.
 */
private fun raySegments(cx: Float, cy: Float, s: Float, theta: Float, field: Field): List<Seg> {
    val ca = cos(theta)
    val sa = sin(theta)
    fun seg(r0: Float, r1: Float, level: Int) =
        Seg(cx + ca * r0 * s, cy + sa * r0 * s, cx + ca * r1 * s, cy + sa * r1 * s, level)

    val out = mutableListOf(seg(R_IN, R_OUT, 0))
    val steps = ((R_OUT - R_IN) / STEP).toInt()
    val dr = (R_OUT - R_IN) / steps
    var runStart = R_IN
    var runLevel = 0
    for (i in 0..steps) {
        val r = R_IN + i * dr
        val level = if (i == steps) 0 else {
            val rm = r + dr / 2
            levelOf(field(ca * rm, sa * rm, rm, theta))
        }
        if (level != runLevel) {
            if (runLevel > 0) out += seg(runStart, r, runLevel)
            runStart = r
            runLevel = level
        }
    }
    return out
}

private fun levelOf(f: Float) = when {
    f <= 0f -> 0
    f < T_MID -> 1
    f < T_THICK -> 2
    else -> 3
}

// ---- cuts ----

/** One band of a cut, slid along its length by [off] pixels. */
private class Band(val start: Float, val size: Float, val off: Float)

/** One slicing pass across the whole canvas, in horizontal or vertical bands. */
private class Cut(val horizontal: Boolean, val bands: List<Band>) {
    fun rect(b: Band, s: Float): Rect =
        if (horizontal) Rect.makeXYWH(0f, b.start, s, b.size) else Rect.makeXYWH(b.start, 0f, b.size, s)

    fun dx(b: Band) = if (horizontal) b.off else 0f
    fun dy(b: Band) = if (horizontal) 0f else b.off
}

/**
 * Bands of random thickness that slide back and forth, alternating direction band to
 * band; some stay put. [chunk] > 1 makes bands thicker and offsets smaller. Band edges
 * and offsets are whole pixels so cuts stay crisp and SVG clips meet without seams.
 */
private fun makeCut(horizontal: Boolean, chunk: Float, rng: Random): Cut {
    val s = SIZE.toFloat()
    val bands = mutableListOf<Band>()
    var pos = 0f
    var dir = if (rng.nextBoolean()) 1 else -1
    while (pos < s) {
        val size = (s * (0.035f + rng.nextFloat() * 0.085f) * chunk).roundToInt().toFloat()
        val off = if (rng.nextFloat() < 0.2f) 0f
        else (dir * s * (0.012f + rng.nextFloat() * 0.03f) / sqrt(chunk)).roundToInt().toFloat()
        dir = -dir
        bands += Band(pos, size, off)
        pos += size
    }
    return Cut(horizontal, bands)
}

/**
 * Draw through the cuts, the last cut outermost: each band clips, then shifts, then
 * hands on to the cut before it. With H then V this matches slicing the image
 * horizontally first and slicing that result vertically.
 */
private fun withCuts(c: Canvas, cuts: List<Cut>, draw: (Canvas) -> Unit) {
    if (cuts.isEmpty()) return draw(c)
    val cut = cuts.last()
    for (b in cut.bands) {
        c.save()
        c.clipRect(cut.rect(b, SIZE.toFloat()), ClipMode.INTERSECT, false)
        c.translate(cut.dx(b), cut.dy(b))
        withCuts(c, cuts.dropLast(1), draw)
        c.restore()
    }
}

// ---- raster ----

private fun drawArt(c: Canvas, art: RayArt) {
    val s = SIZE.toFloat()
    val paints = Array(4) { level ->
        stroke(art.ink.ink, WIDTHS[level] * s).apply {
            if (level == 0) pathEffect = PathEffect.makeDash(floatArrayOf(DOT_ON * s, DOT_OFF * s), 0f)
        }
    }
    art.segs.forEach { c.drawLine(it.x0, it.y0, it.x1, it.y1, paints[it.level]) }
    c.drawCircle(art.cx, art.cy, art.discR, Paint().apply {
        color = art.ink.accent
        isAntiAlias = true
    })
}

private fun stroke(color: Int, width: Float) = Paint().apply {
    this.color = color
    mode = PaintMode.STROKE
    strokeWidth = width
    strokeCap = PaintStrokeCap.BUTT
    isAntiAlias = true
}

// ---- vector ----

/**
 * Hand-written SVG of the same geometry: dotted rays stay real dashed strokes, strokes
 * are grouped by width, and each cut band is a clipPath wrapping a shifted <use> of the
 * art, so the file stays small and every element is editable. No grain (vector is flat).
 */
private fun svg(art: RayArt, cuts: List<Cut>): String {
    val s = SIZE.toFloat()
    val sb = StringBuilder()
    sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="$SIZE" height="$SIZE" viewBox="0 0 $SIZE $SIZE">""")
    if (cuts.isNotEmpty()) {
        sb.appendLine("<defs>")
        cuts.forEachIndexed { ci, cut ->
            cut.bands.forEachIndexed { bi, b ->
                val r = cut.rect(b, s)
                sb.appendLine("""  <clipPath id="cut${ci}_$bi"><rect x="${n(r.left)}" y="${n(r.top)}" width="${n(r.width)}" height="${n(r.height)}"/></clipPath>""")
            }
        }
        sb.appendArt(art, s, id = "art")
        sb.appendLine("</defs>")
    }
    sb.appendLine("""<rect width="$SIZE" height="$SIZE" fill="${hex(art.ink.ground)}"/>""")
    if (cuts.isEmpty()) sb.appendArt(art, s, id = null) else sb.appendCuts(cuts, cuts.size - 1)
    sb.appendLine("</svg>")
    return sb.toString()
}

private fun StringBuilder.appendArt(art: RayArt, s: Float, id: String?) {
    appendLine("""<g${id?.let { " id=\"$it\"" } ?: ""} fill="none" stroke="${hex(art.ink.ink)}" stroke-linecap="butt">""")
    for (level in 0..3) {
        val dash = if (level == 0) """ stroke-dasharray="${n(DOT_ON * s)} ${n(DOT_OFF * s)}"""" else ""
        appendLine("""  <g stroke-width="${n(WIDTHS[level] * s)}"$dash>""")
        art.segs.filter { it.level == level }.forEach {
            appendLine("""    <line x1="${n(it.x0)}" y1="${n(it.y0)}" x2="${n(it.x1)}" y2="${n(it.y1)}"/>""")
        }
        appendLine("  </g>")
    }
    appendLine("""  <circle cx="${n(art.cx)}" cy="${n(art.cy)}" r="${n(art.discR)}" fill="${hex(art.ink.accent)}" stroke="none"/>""")
    appendLine("</g>")
}

/** Mirrors [withCuts]: clip on an outer group, shift on an inner one, recurse inward. */
private fun StringBuilder.appendCuts(cuts: List<Cut>, depth: Int) {
    if (depth < 0) {
        appendLine("""<use xlink:href="#art"/>""")
        return
    }
    val cut = cuts[depth]
    cut.bands.forEachIndexed { bi, b ->
        appendLine("""<g clip-path="url(#cut${depth}_$bi)"><g transform="translate(${n(cut.dx(b))} ${n(cut.dy(b))})">""")
        appendCuts(cuts, depth - 1)
        appendLine("</g></g>")
    }
}

/** Compact number for SVG attributes; Locale.ROOT so it never writes a decimal comma. */
private fun n(v: Float) = "%.2f".format(Locale.ROOT, v).trimEnd('0').trimEnd('.')

private fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)
