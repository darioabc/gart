package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.math.*       // brings all Random extension fns into scope
import dev.oblac.gart.shader.createNoiseGrainFilter
import org.jetbrains.skia.Data
import org.jetbrains.skia.Paint
import org.jetbrains.skia.svg.SVGDOM
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Canvas size of the PNG previews: set GART_SIZE=512 for a fast draft render.
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> to reproduce. Printed to stdout.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * Rational: modernist decorations for a website, drawn from the Gaussian primes.
 *
 * Ten approaches to the same brief (squares, circles, lines, one warm accent), three
 * variants each:
 *  - modules: a grid of Bauhaus tiles. Each tile reads the four Gaussian integers at
 *    its corners, and the prime corners grow it: one prime is a quarter disc from that
 *    corner, two adjacent a half disc on that side, two opposite two small quarters,
 *    three a disc, four an accent disc. Tiles share corners, so shapes rhyme across them.
 *  - lattice: the Gaussian primes themselves, a square per prime in a window of the
 *    plane. Centred on 0 it has the primes' eightfold symmetry; moved off it, it does
 *    not.
 *  - overlay: a few overprinted rectangles, a bar and a disc on a 12-column grid, the
 *    Albers and Malevich register; the primes nearest a seeded point place and size
 *    them. Fills carry a printed grain.
 *  - score: ruled lines, with a thick mark wherever consecutive lattice points along a
 *    line are prime, the longest run in the accent. Reads like a score or a timeline.
 *  - nest: squares nested into one corner, each band stippled denser than the last,
 *    band widths from successive Gaussian prime norms.
 *  - arcs: Truchet tiles, two quarter-arc bands per tile joining its edge midpoints,
 *    turned by the primes at its corners, so the bands run on into long curves.
 *  - orbit: one ring per Gaussian prime norm, evenly spaced, with a mark at the true
 *    argument of every prime of that norm: eight to a ring, four on the axes.
 *  - division: De Stijl. The square cut again and again on a 12-unit grid, the
 *    direction, place and depth of every cut read from a stream of primes.
 *  - halftone: a dot screen over the odd sub-lattice, each dot sized by how prime its
 *    neighbourhood is, faded across the image so it can sit behind text.
 *  - stripes: Swiss-poster discs and fields filled with a barcode read off one row of
 *    the odd sub-lattice, a band for every prime and a hairline for every composite.
 *
 * Variance is the Gaussian primes rather than a random draw: a seed only picks where
 * in the plane to look, and everything placed follows from which integers there are
 * prime, their norms and their arguments. Primes off the axes have odd a + b, so the
 * modules and score read the odd sub-lattice, laid out as a square grid.
 *
 * Every image is written twice: a themable SVG for the web (no background; colours are
 * CSS custom properties with the paper palette as fallback, so a page sets them per
 * theme), and a PNG preview rendered from the same SVG with the light palette baked
 * in. Noise is an SVG filter on the shape's own colour, so it themes too.
 *
 * Geometry is in a 1000-unit square (the SVG viewBox); the PNG scales it to SIZE.
 */
private const val U = 1000f

// The folio paper palette, light (src/styles/tokens.css): ground, ink, rust accent. On
// the site the themable SVGs take --gen-ink and --gen-accent from the page instead; in
// dark mode those are cream #f2f0eb and rust #ec8f62 on #161718.
private class PaperInk(val ground: String, val ink: String, val accent: String)
private val LIGHT = PaperInk("#ece5d1", "#1d1f20", "#c84e22")

// ---- Gaussian primes ----

private fun isPrime(n: Long): Boolean {
    if (n < 2) return false
    if (n % 2 == 0L) return n == 2L
    if (n % 3 == 0L) return n == 3L
    var i = 5L
    while (i * i <= n) {
        if (n % i == 0L || n % (i + 2) == 0L) return false
        i += 6
    }
    return true
}

/** a + bi is a Gaussian prime: its norm is a rational prime, or it sits on an axis at
 *  a prime of the form 4k + 3. */
private fun gaussianPrime(a: Int, b: Int): Boolean {
    val x = abs(a.toLong())
    val y = abs(b.toLong())
    return when {
        x == 0L -> y % 4 == 3L && isPrime(y)
        y == 0L -> x % 4 == 3L && isPrime(x)
        else -> isPrime(x * x + y * y)
    }
}

private fun gnorm(a: Int, b: Int): Long = a.toLong() * a + b.toLong() * b

/** The odd sub-lattice from (a0, b0) as a square grid: cell (i, j) is
 *  (a0 + i + j) + (b0 + i - j)i, so every cell has odd a + b. */
private class OddGrid(val a0: Int, val b0: Int) {
    fun a(i: Int, j: Int) = a0 + i + j
    fun b(i: Int, j: Int) = b0 + i - j
    fun prime(i: Int, j: Int) = gaussianPrime(a(i, j), b(i, j))
    fun norm(i: Int, j: Int): Long = gnorm(a(i, j), b(i, j))
}

private fun oddGrid(rng: Random, reach: Int): OddGrid {
    val a = rng.nextInt(-reach, reach + 1)
    var b = rng.nextInt(-reach, reach + 1)
    if (Math.floorMod(a + b, 2) == 0) b += 1
    return OddGrid(a, b)
}

/** Gaussian primes in the first octant around (a0, b0), nearest first. */
private fun primesNear(a0: Int, b0: Int, count: Int): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    var r = 0
    while (out.size < count && r < 400) {
        val ring = mutableListOf<Pair<Int, Int>>()
        for (da in -r..r) for (db in -r..r) {
            if (max(abs(da), abs(db)) != r) continue
            if (gaussianPrime(a0 + da, b0 + db)) ring += (a0 + da) to (b0 + db)
        }
        ring.sortBy { (a, b) -> gnorm(a - a0, b - b0) }
        out += ring
        r++
    }
    return out.take(count)
}

// ---- shapes ----

private enum class Role { INK, ACCENT }

/** A texture is an SVG filter: noise from feTurbulence, thresholded into an alpha mask
 *  and kept only where the shape is. [keep] is the share of the shape left showing,
 *  [hard] how crisp the specks are. */
private data class Tex(val keep: Float, val hard: Float, val freq: Float, val seed: Int) {
    val id get() = "t${(keep * 100).toInt()}h${hard.toInt()}f${(freq * 100).toInt()}s$seed"
}

private fun stipple(keep: Float, seed: Int) = Tex(keep, 14f, 0.32f, seed)
private fun grain(seed: Int) = Tex(0.86f, 4f, 0.55f, seed)

private sealed interface Shape {
    val role: Role
    val alpha: Float
    val tex: Tex?
}

private data class Box(
    val x: Float, val y: Float, val w: Float, val h: Float,
    override val role: Role = Role.INK, override val alpha: Float = 1f, override val tex: Tex? = null,
) : Shape

private data class Disc(
    val cx: Float, val cy: Float, val r: Float,
    override val role: Role = Role.INK, override val alpha: Float = 1f, override val tex: Tex? = null,
) : Shape

/** A filled sector of a disc: [start] and [sweep] in degrees, clockwise from +x. */
private data class Sector(
    val cx: Float, val cy: Float, val r: Float, val start: Float, val sweep: Float,
    override val role: Role = Role.INK, override val alpha: Float = 1f, override val tex: Tex? = null,
) : Shape

/** Any filled SVG path: arc bands, rings, ticks. [evenOdd] cuts holes where subpaths
 *  overlap. */
private data class Outline(
    val d: String, val evenOdd: Boolean = false,
    override val role: Role = Role.INK, override val alpha: Float = 1f, override val tex: Tex? = null,
) : Shape

/** Shapes drawn only inside the path [clip]. A texture goes on the group as a whole:
 *  Skia's SVG renderer clips per-shape filters wrongly, browsers do not. */
private data class ClipGroup(
    val clip: String, val evenOdd: Boolean, val shapes: List<Shape>, override val tex: Tex? = null,
) : Shape {
    override val role: Role = Role.INK
    override val alpha: Float = 1f
}

private class Art(val name: String, val shapes: List<Shape>)

private fun pt(cx: Float, cy: Float, r: Float, deg: Float): String {
    val t = Math.toRadians(deg.toDouble())
    return "${n(cx + r * cos(t).toFloat())} ${n(cy + r * sin(t).toFloat())}"
}

/** A band between radii [r0] and [r1], from [start] through [sweep] degrees, clockwise
 *  from +x. */
private fun ringSector(cx: Float, cy: Float, r0: Float, r1: Float, start: Float, sweep: Float): String {
    val large = if (sweep > 180f) 1 else 0
    return "M${pt(cx, cy, r1, start)}A${n(r1)} ${n(r1)} 0 $large 1 ${pt(cx, cy, r1, start + sweep)}" +
        "L${pt(cx, cy, r0, start + sweep)}A${n(r0)} ${n(r0)} 0 $large 0 ${pt(cx, cy, r0, start)}Z"
}

private fun circlePath(cx: Float, cy: Float, r: Float) =
    "M${n(cx + r)} ${n(cy)}A${n(r)} ${n(r)} 0 1 1 ${n(cx - r)} ${n(cy)}A${n(r)} ${n(r)} 0 1 1 ${n(cx + r)} ${n(cy)}Z"

/** A full ring; draw it with evenOdd so the inner circle is a hole. */
private fun ring(cx: Float, cy: Float, r0: Float, r1: Float) = circlePath(cx, cy, r1) + circlePath(cx, cy, r0)

private fun polygon(pts: List<Pair<Float, Float>>) =
    pts.mapIndexed { i, (x, y) -> (if (i == 0) "M" else "L") + "${n(x)} ${n(y)}" }.joinToString("") + "Z"

// ---- the five approaches ----

/** Bauhaus tiles: each tile grows from its prime corners. */
private fun modules(name: String, rng: Random, cols: Int, reach: Int, texMode: Int): Art {
    val margin = 90f
    val gap = U * 0.012f
    val m = (U - 2 * margin - (cols - 1) * gap) / cols
    val grid = oddGrid(rng, reach)
    val ts = rng.nextInt(1, 999)
    // 0 flat, 1 grain on everything, 2 stipple shaded by tile kind
    fun tex(kind: Int): Tex? = when (texMode) {
        1 -> grain(ts)
        2 -> stipple(floatArrayOf(0.95f, 0.72f, 0.5f, 0.95f)[kind], ts)
        else -> null
    }
    val shapes = mutableListOf<Shape>()
    for (r in 0 until cols) for (c in 0 until cols) {
        val x = margin + c * (m + gap)
        val y = margin + r * (m + gap)
        val tl = grid.prime(c, r)
        val tr = grid.prime(c + 1, r)
        val br = grid.prime(c + 1, r + 1)
        val bl = grid.prime(c, r + 1)
        val n = listOf(tl, tr, br, bl).count { it }
        val accent = grid.norm(c, r) % 13 == 0L
        val role = if (accent) Role.ACCENT else Role.INK
        when {
            n == 1 -> shapes += when {
                tl -> Sector(x, y, m, 0f, 90f, role, tex = tex(1))
                tr -> Sector(x + m, y, m, 90f, 90f, role, tex = tex(1))
                br -> Sector(x + m, y + m, m, 180f, 90f, role, tex = tex(1))
                else -> Sector(x, y + m, m, 270f, 90f, role, tex = tex(1))
            }
            n == 2 && tl && tr -> shapes += Sector(x + m / 2, y, m / 2, 0f, 180f, role, tex = tex(2))
            n == 2 && tr && br -> shapes += Sector(x + m, y + m / 2, m / 2, 90f, 180f, role, tex = tex(2))
            n == 2 && br && bl -> shapes += Sector(x + m / 2, y + m, m / 2, 180f, 180f, role, tex = tex(2))
            n == 2 && bl && tl -> shapes += Sector(x, y + m / 2, m / 2, 270f, 180f, role, tex = tex(2))
            n == 2 && tl && br -> {
                shapes += Sector(x, y, m / 2, 0f, 90f, role, tex = tex(1))
                shapes += Sector(x + m, y + m, m / 2, 180f, 90f, role, tex = tex(1))
            }
            n == 2 -> {
                shapes += Sector(x + m, y, m / 2, 90f, 90f, role, tex = tex(1))
                shapes += Sector(x, y + m, m / 2, 270f, 90f, role, tex = tex(1))
            }
            n == 3 -> shapes += Disc(x + m / 2, y + m / 2, m / 2, role, tex = tex(3))
            n == 4 -> shapes += Disc(x + m / 2, y + m / 2, m / 2, Role.ACCENT, tex = tex(0))
        }
    }
    return Art(name, shapes)
}

/** The Gaussian primes in a window: a square per prime. */
private fun lattice(name: String, rng: Random, half: Int, centred: Boolean, texMode: Int): Art {
    val margin = 80f
    val n = 2 * half + 1
    val cell = (U - 2 * margin) / n
    val a0 = if (centred) 0 else rng.nextInt(-400, 400)
    val b0 = if (centred) 0 else rng.nextInt(-400, 400)
    val ts = rng.nextInt(1, 999)
    val shapes = mutableListOf<Shape>()
    for (j in -half..half) for (i in -half..half) {
        val a = a0 + i
        val b = b0 + j
        if (!gaussianPrime(a, b)) continue
        val x = margin + (i + half) * cell
        val y = margin + (half - j) * cell
        val onAxis = a == 0 || b == 0
        // Primes whose norm is 1 mod 8 against those 5 mod 8: two families the eye
        // can tell apart by size.
        val big = gnorm(a, b) % 8 == 1L
        val s = cell * (if (big) 0.86f else 0.56f)
        val role = if (onAxis || (!centred && gnorm(a, b) % 29 == 0L)) Role.ACCENT else Role.INK
        val tex = if (texMode == 1) stipple(if (big) 0.9f else 0.6f, ts) else null
        shapes += if (texMode == 2) Disc(x + cell / 2, y + cell / 2, s / 2, role)
        else Box(x + (cell - s) / 2, y + (cell - s) / 2, s, s, role, tex = tex)
    }
    return Art(name, shapes)
}

/** Overprinted rectangles, a bar and a disc on a 12-column grid, placed by the primes
 *  nearest a seeded point. */
private fun overlay(name: String, rng: Random, withDisc: Boolean, barOut: Boolean): Art {
    val margin = 110f
    val g = (U - 2 * margin) / 12
    val a0 = rng.nextInt(-200, 200)
    val b0 = rng.nextInt(-200, 200)
    val p = primesNear(a0, b0, 12).map { (a, b) -> Math.floorMod(a, 12) to Math.floorMod(b, 12) }
    val ts = rng.nextInt(1, 999)
    fun u(v: Int) = margin + v * g
    val shapes = mutableListOf<Shape>()
    // Two large fields, overlapping, each 7 to 9 units on a side.
    val f1x = p[0].first % 3; val f1y = p[0].second % 3
    val f1w = 7 + p[1].first % 3; val f1h = 7 + p[1].second % 3
    shapes += Box(u(f1x), u(f1y), f1w * g, f1h * g, Role.INK, 0.16f, grain(ts))
    val f2x = min(12 - 7, f1x + 2 + p[2].first % 2); val f2y = min(12 - 7, f1y + 1 + p[2].second % 3)
    val f2w = min(12 - f2x, 7 + p[3].first % 3); val f2h = min(12 - f2y, 7 + p[3].second % 3)
    shapes += Box(u(f2x), u(f2y), f2w * g, f2h * g, Role.INK, 0.38f, grain(ts + 1))
    // Two or three small blocks, one of them the accent.
    for (k in 0 until 3) {
        val (pa, pb) = p[4 + k]
        val w = 2 + pa % 2; val h = 2 + pb % 3
        val x = min(12 - w, pa % 10); val y = min(12 - h, pb % 10)
        if (k == 2 && pa % 2 == 0) continue
        shapes += Box(u(x), u(y), w * g, h * g, if (k == 0) Role.ACCENT else Role.INK, if (k == 0) 0.92f else 0.8f, grain(ts + 2 + k))
    }
    // A bar: one unit wide, crossing the fields, past the frame if asked.
    val (ba, bb) = p[8]
    val bx = 1 + ba % 9
    val top = if (barOut) margin * 0.35f else u(1 + bb % 2)
    val bottom = if (barOut) U - margin * 0.1f else u(10 + bb % 2)
    shapes += Box(u(bx), top, g, bottom - top, Role.INK, 0.72f, grain(ts + 6))
    if (withDisc) {
        val (da, db) = p[9]
        val r = g * (1.4f + (da % 3) * 0.5f)
        shapes += Disc(u(2 + da % 8) + g / 2, u(2 + db % 8) + g / 2, r, Role.ACCENT, 0.85f, grain(ts + 7))
    }
    return Art(name, shapes)
}

/** Ruled lines with a mark for every run of primes along each line. */
private fun score(name: String, rng: Random, lines: Int, cols: Int, withDots: Boolean): Art {
    val mx = 70f
    val my = 110f
    val pitch = (U - 2 * my) / (lines - 1)
    val step = (U - 2 * mx) / cols
    val grid = oddGrid(rng, 300)
    val shapes = mutableListOf<Shape>()
    data class Run(val line: Int, val from: Int, val to: Int)
    val runs = mutableListOf<Run>()
    for (l in 0 until lines) {
        val y = my + l * pitch
        shapes += Box(mx, y - 0.75f, U - 2 * mx, 1.5f, Role.INK, 0.32f)
        var j = 0
        while (j < cols) {
            if (!grid.prime(l, j)) { j++; continue }
            var k = j
            while (k + 1 < cols && grid.prime(l, k + 1)) k++
            runs += Run(l, j, k)
            j = k + 1
        }
    }
    val longest = runs.maxByOrNull { it.to - it.from }
    val thick = pitch * 0.26f
    runs.forEach { r ->
        val y = my + r.line * pitch
        val x0 = mx + r.from * step
        val len = (r.to - r.from + 1) * step
        val role = if (r == longest) Role.ACCENT else Role.INK
        if (withDots && r.from == r.to) shapes += Disc(x0 + step / 2, y, thick * 0.62f, role)
        else shapes += Box(x0 + step * 0.12f, y - thick / 2, len - step * 0.24f, thick, role)
    }
    // Bar lines every eight steps, as in a score.
    for (c in 0..cols step 8) shapes += Box(mx + c * step - 0.75f, my - pitch * 0.4f, 1.5f, U - 2 * my + pitch * 0.8f, Role.INK, 0.18f)
    return Art(name, shapes)
}

/** Squares nested into one corner, each band stippled denser than the last. */
private fun nest(name: String, rng: Random, corner: Int, concentric: Boolean): Art {
    val margin = 90f
    val a0 = rng.nextInt(20, 300)
    val b0 = rng.nextInt(1, 300)
    val primes = primesNear(a0, b0, 40)
    val gap = 7f
    val ts = rng.nextInt(1, 999)
    val shapes = mutableListOf<Shape>()
    var x0 = margin; var y0 = margin; var x1 = U - margin; var y1 = U - margin
    val accentBand = 2 + (primes[0].first % 3)
    var k = 0
    while (x1 - x0 > 90f && k < primes.size) {
        val (a, b) = primes[k]
        val t = 26f + (gnorm(a, b) % 7) * 9f      // band width
        val keep = 0.22f + 0.7f * k / 10f
        val role = if (k == accentBand) Role.ACCENT else Role.INK
        val tex = stipple(min(0.97f, keep), ts + k)
        // The band is the square minus the next one, drawn as the strips that differ.
        val nx0 = if (concentric || corner == 1 || corner == 2) x0 + t else x0
        val ny0 = if (concentric || corner == 2 || corner == 3) y0 + t else y0
        val nx1 = if (concentric || corner == 0 || corner == 3) x1 - t else x1
        val ny1 = if (concentric || corner == 0 || corner == 1) y1 - t else y1
        if (ny0 > y0) shapes += Box(x0, y0, x1 - x0, ny0 - y0 - gap, role, tex = tex)
        if (ny1 < y1) shapes += Box(x0, ny1 + gap, x1 - x0, y1 - ny1 - gap, role, tex = tex)
        val sy0 = if (ny0 > y0) ny0 else y0
        val sy1 = if (ny1 < y1) ny1 else y1
        if (nx0 > x0) shapes += Box(x0, sy0, nx0 - x0 - gap, sy1 - sy0, role, tex = tex)
        if (nx1 < x1) shapes += Box(nx1 + gap, sy0, x1 - nx1 - gap, sy1 - sy0, role, tex = tex)
        x0 = nx0; y0 = ny0; x1 = nx1; y1 = ny1
        k++
    }
    shapes += Box(x0, y0, x1 - x0, y1 - y0, Role.INK, tex = stipple(0.97f, ts + 99))
    return Art(name, shapes)
}

/** Truchet tiles: two quarter-arc bands per tile, centred on opposite corners, so each
 *  joins the midpoints of two edges. The tile turns when the primality of its corner
 *  integer and the one diagonally across differ. [lines] concentric bands per arc. */
private fun arcs(name: String, rng: Random, cols: Int, lines: Int, texMode: Int): Art {
    val margin = 90f
    val m = (U - 2 * margin) / cols
    val grid = oddGrid(rng, 200)
    val ts = rng.nextInt(1, 999)
    // Radii symmetric about m / 2, so a band leaves one tile where the next picks it up.
    val bands: List<Pair<Float, Float>> = if (lines == 1) {
        val w = m * (if (texMode == 1) 0.2f else 0.15f)
        listOf(m / 2 - w to m / 2 + w)
    } else {
        (0 until lines).map { k ->
            val r = m * (0.5f + (k - (lines - 1) / 2f) * 0.48f / lines)
            r - m * 0.03f to r + m * 0.03f
        }
    }
    val shapes = mutableListOf<Shape>()
    for (r in 0 until cols) for (c in 0 until cols) {
        val x = margin + c * m
        val y = margin + r * m
        val turned = grid.prime(c, r) != grid.prime(c + 1, r + 1)
        val role = if (grid.norm(c, r) % 13 == 0L) Role.ACCENT else Role.INK
        val tex = if (texMode == 1) stipple(if (grid.prime(c, r)) 0.92f else 0.58f, ts) else null
        val corners = if (turned) listOf(Triple(x + m, y, 90f), Triple(x, y + m, 270f))
        else listOf(Triple(x, y, 0f), Triple(x + m, y + m, 180f))
        for ((cx, cy, start) in corners) for ((r0, r1) in bands) {
            shapes += Outline(ringSector(cx, cy, r0, r1, start, 90f), role = role, tex = tex)
        }
    }
    return Art(name, shapes)
}

/** One ring per Gaussian prime norm, smallest first and evenly spaced, with a mark at
 *  the true argument of every prime of that norm. A seed picks how far out to start.
 *  [quarter] draws the first quadrant from the bottom left corner. */
private fun orbit(name: String, rng: Random, quarter: Boolean, ticks: Boolean): Art {
    val cx = if (quarter) 90f else U / 2
    val cy = if (quarter) U - 90f else U / 2
    val rMax = if (quarter) U - 170f else U / 2 - 70f
    val rings = if (quarter) 44 else 32
    val skip = rng.nextInt(0, 40)
    val byNorm = sortedMapOf<Long, MutableList<Pair<Int, Int>>>()
    for (a in -64..64) for (b in -64..64) if (gaussianPrime(a, b)) byNorm.getOrPut(gnorm(a, b)) { mutableListOf() } += a to b
    val norms = byNorm.keys.drop(skip).take(rings)
    val r0 = rMax * 0.14f
    val pitch = (rMax - r0) / (rings - 1)
    val shapes = mutableListOf<Shape>()
    norms.forEachIndexed { k, nm ->
        val r = r0 + k * pitch
        if (!ticks) {
            shapes += if (quarter) Outline(ringSector(cx, cy, r - 0.8f, r + 0.8f, 270f, 90f), alpha = 0.3f)
            else Outline(ring(cx, cy, r - 0.8f, r + 0.8f), evenOdd = true, alpha = 0.3f)
        }
        for ((a, b) in byNorm.getValue(nm)) {
            if (quarter && (a < 0 || b < 0)) continue
            val t = atan2(b.toDouble(), a.toDouble())
            val ux = cos(t).toFloat()
            val uy = -sin(t).toFloat()
            val px = cx + r * ux
            val py = cy + r * uy
            val onAxis = a == 0 || b == 0
            if (ticks && !onAxis) {
                val h = pitch * 0.42f
                val w = pitch * 0.2f
                shapes += Outline(polygon(listOf(
                    px - h * ux - w * uy to py - h * uy + w * ux,
                    px + h * ux - w * uy to py + h * uy + w * ux,
                    px + h * ux + w * uy to py + h * uy - w * ux,
                    px - h * ux + w * uy to py - h * uy - w * ux,
                )))
            } else {
                shapes += Disc(px, py, pitch * (if (onAxis) 0.42f else 0.34f), if (onAxis) Role.ACCENT else Role.INK)
            }
        }
    }
    return Art(name, shapes)
}

/** De Stijl division: the square cut again and again on a grid, the direction, place
 *  and depth of every cut read from a stream of Gaussian primes. Mode 0 is thick rules
 *  and a few solid cells, 1 grained cells with no rules, 2 a finer cut with hairlines
 *  and stippled cells. */
private fun division(name: String, rng: Random, mode: Int): Art {
    val margin = 90f
    val units = if (mode == 2) 24 else 12
    val g = (U - 2 * margin) / units
    val primes = primesNear(rng.nextInt(-300, 300), rng.nextInt(-300, 300), 300)
    val ts = rng.nextInt(1, 999)
    var next = 0
    fun draw() = primes[next++ % primes.size]
    data class Cell(val x: Int, val y: Int, val w: Int, val h: Int, val depth: Int)
    data class Cut(val vertical: Boolean, val at: Int, val from: Int, val to: Int)
    val leaves = mutableListOf<Cell>()
    val cuts = mutableListOf<Cut>()
    val maxDepth = if (mode == 2) 7 else 4
    val fractions = floatArrayOf(1 / 3f, 1 / 2f, 2 / 3f, 1 / 4f, 3 / 4f, 2 / 5f, 3 / 5f)
    fun split(c: Cell) {
        val (a, b) = draw()
        if (c.depth >= maxDepth || (c.depth >= 2 && gnorm(a, b) % 3 == 0L)) { leaves += c; return }
        val vertical = when {
            c.w >= 2 * c.h -> true
            c.h >= 2 * c.w -> false
            else -> Math.floorMod(a, 2) == 0
        }
        val len = if (vertical) c.w else c.h
        if (len < 2) { leaves += c; return }
        val at = (fractions[Math.floorMod(b, fractions.size)] * len).toInt().coerceIn(1, len - 1)
        if (vertical) {
            cuts += Cut(true, c.x + at, c.y, c.y + c.h)
            split(Cell(c.x, c.y, at, c.h, c.depth + 1)); split(Cell(c.x + at, c.y, c.w - at, c.h, c.depth + 1))
        } else {
            cuts += Cut(false, c.y + at, c.x, c.x + c.w)
            split(Cell(c.x, c.y, c.w, at, c.depth + 1)); split(Cell(c.x, c.y + at, c.w, c.h - at, c.depth + 1))
        }
    }
    split(Cell(0, 0, units, units, 0))
    fun u(v: Int) = margin + v * g
    val shapes = mutableListOf<Shape>()
    // Fills: the largest cell whose prime falls right takes the accent, a few others ink.
    val picks = leaves.map { it to gnorm(draw().first, draw().second) % 9 }
    val accent = picks.filter { it.second < 2L }.maxByOrNull { it.first.w * it.first.h }?.first
        ?: leaves.maxBy { it.w * it.h }
    val alphas = floatArrayOf(0.1f, 0.18f, 0.3f, 0.46f, 0.66f, 0.86f)
    picks.forEachIndexed { i, (c, k) ->
        val inset = if (mode == 1) 5f else 0f
        val x = u(c.x) + inset; val y = u(c.y) + inset
        val w = c.w * g - 2 * inset; val h = c.h * g - 2 * inset
        when {
            c == accent -> shapes += Box(x, y, w, h, Role.ACCENT, tex = if (mode == 0) null else grain(ts))
            mode == 0 && k == 3L -> shapes += Box(x, y, w, h, Role.INK)
            mode == 1 && k != 8L -> shapes += Box(x, y, w, h, Role.INK, alphas[(k % 6).toInt()], grain(ts + i % 5))
            mode == 2 && k in 3L..6L -> shapes += Box(x, y, w, h, Role.INK, tex = stipple(0.25f + 0.17f * (k - 3), ts + i % 5))
        }
    }
    if (mode != 1) {
        val t = if (mode == 0) 12f else 3f
        for (cut in cuts) {
            val from = u(cut.from) - t / 2; val len = (cut.to - cut.from) * g + t
            shapes += if (cut.vertical) Box(u(cut.at) - t / 2, from, t, len) else Box(from, u(cut.at) - t / 2, len, t)
        }
        val side = U - 2 * margin + t
        shapes += Box(margin - t / 2, margin - t / 2, side, t)
        shapes += Box(margin - t / 2, U - margin - t / 2, side, t)
        shapes += Box(margin - t / 2, margin - t / 2, t, side)
        shapes += Box(U - margin - t / 2, margin - t / 2, t, side)
    }
    return Art(name, shapes)
}

/** A halftone screen over the odd sub-lattice. A prime is a full dot; any other point
 *  is sized by how many of its four nearest odd neighbours are prime. The tone is then
 *  faded across the image: [fade] 0 left to right, 1 out from the top right corner, 2
 *  out from the centre. [diamond] puts the dots at the lattice's true positions, a grid
 *  turned 45 degrees. */
private fun halftone(name: String, rng: Random, cols: Int, fade: Int, square: Boolean, diamond: Boolean): Art {
    val margin = 90f
    val a0 = rng.nextInt(-300, 300)
    var b0 = rng.nextInt(-300, 300)
    if (Math.floorMod(a0 + b0, 2) == 0) b0 += 1
    fun tone(a: Int, b: Int): Float {
        if (gaussianPrime(a, b)) return 1f
        val near = listOf(a + 1 to b + 1, a + 1 to b - 1, a - 1 to b + 1, a - 1 to b - 1).count { (x, y) -> gaussianPrime(x, y) }
        return 0.12f + 0.6f * near / 4f
    }
    fun shade(x: Float, y: Float): Float = when (fade) {
        0 -> 1f - (x - margin) / (U - 2 * margin) * 0.92f
        1 -> 1f - min(1f, sqrt((U - margin - x) * (U - margin - x) + (y - margin) * (y - margin)) / (U * 1.15f))
        else -> 1f - min(1f, sqrt((x - U / 2) * (x - U / 2) + (y - U / 2) * (y - U / 2)) / (U * 0.6f))
    }
    val shapes = mutableListOf<Shape>()
    fun dot(x: Float, y: Float, a: Int, b: Int, maxR: Float) {
        val r = maxR * sqrt(tone(a, b) * shade(x, y))
        if (r < 0.8f) return
        val role = if (gaussianPrime(a, b) && gnorm(a, b) % 13 == 0L) Role.ACCENT else Role.INK
        shapes += if (square) Box(x - r, y - r, 2 * r, 2 * r, role) else Disc(x, y, r, role)
    }
    if (diamond) {
        val step = (U - 2 * margin) / (2 * cols)
        for (v in 0..2 * cols) for (u in 0..2 * cols) {
            if ((u + v) % 2 != 0) continue
            dot(margin + u * step, margin + v * step, a0 + u, b0 - v, step * 0.7f)
        }
    } else {
        val grid = OddGrid(a0, b0)
        val m = (U - 2 * margin) / cols
        for (j in 0 until cols) for (i in 0 until cols) {
            dot(margin + (i + 0.5f) * m, margin + (j + 0.5f) * m, grid.a(i, j), grid.b(i, j), m * (if (square) 0.46f else 0.5f))
        }
    }
    return Art(name, shapes)
}

/** Bands across [from, to] along one axis, read off one row of the odd sub-lattice: a
 *  band for every prime and a hairline for every composite, the longest run of primes
 *  in the accent. Each band spans [x0, x1] the other way. */
private fun barcode(
    grid: OddGrid, row: Int, count: Int, vertical: Boolean,
    from: Float, to: Float, x0: Float, x1: Float, accent: Boolean, tex: Tex?,
): List<Shape> {
    val pitch = (to - from) / count
    val bits = (0 until count).map { grid.prime(row, it) }
    var best = 0 to -1; var k = 0
    while (k < count) {
        if (!bits[k]) { k++; continue }
        var e = k
        while (e + 1 < count && bits[e + 1]) e++
        if (e - k > best.second - best.first) best = k to e
        k = e + 1
    }
    return (0 until count).map { i ->
        val (off, thick) = if (bits[i]) pitch * 0.1f to pitch * 0.8f else pitch * 0.44f to pitch * 0.12f
        val role = if (accent && i in best.first..best.second) Role.ACCENT else Role.INK
        val p = from + i * pitch + off
        if (vertical) Box(p, x0, thick, x1 - x0, role, tex = tex) else Box(x0, p, x1 - x0, thick, role, tex = tex)
    }
}

/** Swiss-poster forms filled with barcodes. Mode 0 is one disc of horizontal bands, 1
 *  two half discs, one banded each way, 2 a square of bands with a disc cut out of it
 *  and banded across in the accent, all with printed grain. */
private fun stripes(name: String, rng: Random, mode: Int): Art {
    val grid = oddGrid(rng, 300)
    val ts = rng.nextInt(1, 999)
    val shapes = mutableListOf<Shape>()
    when (mode) {
        0 -> {
            val r = 360f
            shapes += ClipGroup(circlePath(U / 2, U / 2, r), false,
                barcode(grid, 0, 64, false, U / 2 - r, U / 2 + r, U / 2 - r, U / 2 + r, true, null))
        }
        1 -> {
            val r = 300f
            val left = "M${n(U / 2)} ${n(380f - r)}A${n(r)} ${n(r)} 0 0 0 ${n(U / 2)} ${n(380f + r)}Z"
            val right = "M${n(U / 2)} ${n(620f - r)}A${n(r)} ${n(r)} 0 0 1 ${n(U / 2)} ${n(620f + r)}Z"
            shapes += ClipGroup(left, false, barcode(grid, 0, 30, true, U / 2 - r, U / 2, 80f, 680f, true, null))
            shapes += ClipGroup(right, false, barcode(grid, 1, 54, false, 620f - r, 620f + r, U / 2, U / 2 + r, false, null))
        }
        else -> {
            val s0 = 110f; val s1 = U - 110f
            val cr = 230f
            val cx = s0 + 2 * (s1 - s0) / 3 - 40f
            val cy = s0 + (s1 - s0) / 3 + 40f
            val field = "M${n(s0)} ${n(s0)}H${n(s1)}V${n(s1)}H${n(s0)}Z" + circlePath(cx, cy, cr)
            shapes += ClipGroup(field, true, barcode(grid, 0, 40, false, s0, s1, s0, s1, false, null), grain(ts))
            shapes += ClipGroup(circlePath(cx, cy, cr), false,
                barcode(grid, 1, 18, true, cx - cr, cx + cr, cy - cr, cy + cr, false, null).map {
                    (it as Box).copy(role = Role.ACCENT)
                }, grain(ts + 1))
        }
    }
    return Art(name, shapes)
}

// ---- vector ----

/** The SVG. Themable (no ink given): no background, colours as classes reading CSS
 *  custom properties with the light palette as fallback, ids prefixed with the art's
 *  name so several can sit inline on one page. Baked (ink given): literal colours on a
 *  ground, sized for the PNG preview. */
private fun svg(art: Art, baked: PaperInk? = null): String {
    val sb = StringBuilder()
    val size = if (baked != null) """ width="$SIZE" height="$SIZE"""" else ""
    sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${U.toInt()} ${U.toInt()}"$size>""")
    if (baked == null) {
        sb.appendLine("""<style>.gen-i{fill:var(--gen-ink,${LIGHT.ink})}.gen-a{fill:var(--gen-accent,${LIGHT.accent})}</style>""")
    } else {
        sb.appendLine("""<rect width="${U.toInt()}" height="${U.toInt()}" fill="${baked.ground}"/>""")
    }
    fun flat(list: List<Shape>): List<Shape> = list.flatMap { if (it is ClipGroup) flat(it.shapes) + it else listOf(it) }
    val texes = flat(art.shapes).mapNotNull { it.tex }.distinct()
    val clips = art.shapes.filterIsInstance<ClipGroup>()
    if (texes.isNotEmpty() || clips.isNotEmpty()) {
        sb.appendLine("<defs>")
        texes.forEach { t ->
            val cut = 0.5f + 0.3f * (0.5f - t.keep)
            sb.appendLine(
                """<filter id="${art.name}-${t.id}" x="0" y="0" width="1" height="1" filterUnits="objectBoundingBox" color-interpolation-filters="sRGB">""" +
                    """<feTurbulence type="fractalNoise" baseFrequency="${n(t.freq)}" numOctaves="2" seed="${t.seed}" stitchTiles="stitch" result="n"/>""" +
                    """<feColorMatrix in="n" type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 ${n(t.hard)} ${n(-t.hard * cut)}" result="m"/>""" +
                    """<feComposite in="SourceGraphic" in2="m" operator="in"/></filter>"""
            )
        }
        clips.forEachIndexed { i, c ->
            val rule = if (c.evenOdd) """ clip-rule="evenodd"""" else ""
            sb.appendLine("""<clipPath id="${art.name}-c$i"><path d="${c.clip}"$rule/></clipPath>""")
        }
        sb.appendLine("</defs>")
    }
    fun draw(s: Shape) {
        if (s is ClipGroup) {
            val filter = s.tex?.let { """<g filter="url(#${art.name}-${it.id})">""" } ?: ""
            sb.appendLine("""<g clip-path="url(#${art.name}-c${clips.indexOf(s)})">$filter""")
            s.shapes.forEach(::draw)
            sb.appendLine(if (s.tex != null) "</g></g>" else "</g>")
            return
        }
        val paint = if (baked == null) {
            """class="${if (s.role == Role.ACCENT) "gen-a" else "gen-i"}""""
        } else {
            """fill="${if (s.role == Role.ACCENT) baked.accent else baked.ink}""""
        }
        val alpha = if (s.alpha < 1f) """ fill-opacity="${n(s.alpha)}"""" else ""
        val filter = s.tex?.let { """ filter="url(#${art.name}-${it.id})"""" } ?: ""
        val attrs = "$paint$alpha$filter"
        sb.appendLine(
            when (s) {
                is Box -> """<rect x="${n(s.x)}" y="${n(s.y)}" width="${n(s.w)}" height="${n(s.h)}" $attrs/>"""
                is Disc -> """<circle cx="${n(s.cx)}" cy="${n(s.cy)}" r="${n(s.r)}" $attrs/>"""
                is Sector -> """<path d="${sectorPath(s)}" $attrs/>"""
                is Outline -> """<path d="${s.d}"${if (s.evenOdd) """ fill-rule="evenodd"""" else ""} $attrs/>"""
                is ClipGroup -> ""
            }
        )
    }
    art.shapes.forEach(::draw)
    sb.appendLine("</svg>")
    return sb.toString()
}

private fun sectorPath(s: Sector): String {
    val r0 = Math.toRadians(s.start.toDouble())
    val r1 = Math.toRadians((s.start + s.sweep).toDouble())
    val x1 = s.cx + s.r * cos(r0).toFloat()
    val y1 = s.cy + s.r * sin(r0).toFloat()
    val x2 = s.cx + s.r * cos(r1).toFloat()
    val y2 = s.cy + s.r * sin(r1).toFloat()
    val large = if (s.sweep > 180f) 1 else 0
    return "M${n(s.cx)} ${n(s.cy)}L${n(x1)} ${n(y1)}A${n(s.r)} ${n(s.r)} 0 $large 1 ${n(x2)} ${n(y2)}Z"
}

/** Compact number for SVG attributes; Locale.ROOT so it never writes a decimal comma. */
private fun n(v: Float) = "%.2f".format(Locale.ROOT, v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }

// ---- main ----

fun main() {
    println("seed=$SEED")
    val master = Random(SEED)
    val gart = Gart.of("rational", SIZE, SIZE)

    val makers: List<Pair<String, (String, Random) -> Art>> = listOf(
        "rationalModules01" to { nm, r -> modules(nm, r, 8, 40, 0) },
        "rationalModules02" to { nm, r -> modules(nm, r, 10, 120, 1) },
        "rationalModules03" to { nm, r -> modules(nm, r, 6, 20, 2) },
        "rationalLattice01" to { nm, r -> lattice(nm, r, 30, true, 0) },
        "rationalLattice02" to { nm, r -> lattice(nm, r, 22, false, 1) },
        "rationalLattice03" to { nm, r -> lattice(nm, r, 40, true, 2) },
        "rationalOverlay01" to { nm, r -> overlay(nm, r, false, false) },
        "rationalOverlay02" to { nm, r -> overlay(nm, r, true, false) },
        "rationalOverlay03" to { nm, r -> overlay(nm, r, true, true) },
        "rationalScore01" to { nm, r -> score(nm, r, 12, 48, false) },
        "rationalScore02" to { nm, r -> score(nm, r, 18, 64, true) },
        "rationalScore03" to { nm, r -> score(nm, r, 8, 32, true) },
        "rationalNest01" to { nm, r -> nest(nm, r, 0, false) },
        "rationalNest02" to { nm, r -> nest(nm, r, 2, false) },
        "rationalNest03" to { nm, r -> nest(nm, r, 0, true) },
        "rationalArcs01" to { nm, r -> arcs(nm, r, 8, 1, 0) },
        "rationalArcs02" to { nm, r -> arcs(nm, r, 10, 3, 0) },
        "rationalArcs03" to { nm, r -> arcs(nm, r, 6, 1, 1) },
        "rationalOrbit01" to { nm, r -> orbit(nm, r, false, false) },
        "rationalOrbit02" to { nm, r -> orbit(nm, r, true, false) },
        "rationalOrbit03" to { nm, r -> orbit(nm, r, false, true) },
        "rationalDivision01" to { nm, r -> division(nm, r, 0) },
        "rationalDivision02" to { nm, r -> division(nm, r, 1) },
        "rationalDivision03" to { nm, r -> division(nm, r, 2) },
        "rationalHalftone01" to { nm, r -> halftone(nm, r, 36, 0, false, false) },
        "rationalHalftone02" to { nm, r -> halftone(nm, r, 28, 1, true, false) },
        "rationalHalftone03" to { nm, r -> halftone(nm, r, 24, 2, false, true) },
        "rationalStripes01" to { nm, r -> stripes(nm, r, 0) },
        "rationalStripes02" to { nm, r -> stripes(nm, r, 1) },
        "rationalStripes03" to { nm, r -> stripes(nm, r, 2) },
    )

    makers.forEachIndexed { i, (name, make) ->
        val art = make(name, Random(master.nextLong()))
        File("$name.svg").writeText(svg(art))

        // The preview: the same SVG with the light palette baked in, rendered by Skia,
        // then a light grain for the paper.
        val g = gart.gartvas()
        val dom = SVGDOM(Data.makeFromBytes(svg(art, LIGHT).toByteArray()))
        dom.setContainerSize(SIZE.toFloat(), SIZE.toFloat())
        dom.render(g.canvas)
        val out = gart.gartvas()
        out.canvas.drawImage(g.snapshot(), 0f, 0f, Paint().apply { imageFilter = createNoiseGrainFilter(0.05f, gart.d) })
        gart.saveImage(out, "$name.png")
        println("  [${i + 1}/${makers.size}] $name: ${art.shapes.size} shapes")
    }
    println("done: ${makers.size} svg + png pairs")
}
