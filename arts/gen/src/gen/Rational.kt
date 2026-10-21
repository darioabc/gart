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
 * Then eight process illustrations in the same vocabulary, for the folio's "How I
 * work" steps: journey, rounds, versions and drafts (iterating on journeys and
 * designs), parts, assembly, tree and kit (building components in the repo); and six
 * loose readings of the double diamond: pulse, fan, branches, rhombs, twin, knots.
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

/** Shapes that move in the motion SVG (<name>-motion.svg): a CSS animation on their
 *  group, off under prefers-reduced-motion. The still SVG and the PNG draw [shapes]
 *  where they stand at time zero and leave the group out, so they are unchanged.
 *  Only in motion: [copy] draws the shapes a second time at that offset, for the copy
 *  a scrolling loop brings in, and [clip] keeps the moving group inside a path.
 *  [hiDpi] keeps the group still on screens under 1.5 device pixels per CSS pixel:
 *  there a hairline thinner than a pixel, moving slowly, flips between a crisp line and
 *  a two-row smear each time it crosses a pixel, and a field of them flickers. */
private data class Motion(
    val anim: Anim, val shapes: List<Shape>, val copy: Pair<Float, Float>? = null, val clip: String? = null,
    val hiDpi: Boolean = false,
) : Shape {
    override val role: Role = Role.INK
    override val alpha: Float = 1f
    override val tex: Tex? = null
}

/** [delay] is how far into its cycle an animation already is when the page opens. */
private sealed interface Anim { val seconds: Float; val delay: Float }

/** A full turn about (cx, cy) every [seconds], clockwise unless [reverse]. */
private data class Spin(
    val cx: Float, val cy: Float, override val seconds: Float, val reverse: Boolean, override val delay: Float = 0f,
) : Anim

/** A slide by (dx, dy) every [seconds], then a jump back: seamless when the group
 *  carries a copy of itself one (dx, dy) behind. */
private data class Drift(val dx: Float, val dy: Float, override val seconds: Float, override val delay: Float = 0f) : Anim

/** A quarter turn about (cx, cy), a hold, another quarter turn, a hold, every
 *  [seconds]. Made for Truchet tiles: a tile turned 90 degrees is the other tile and
 *  still joins its neighbours, and one turned 180 is itself, so the loop is seamless. */
private data class Turn(val cx: Float, val cy: Float, override val seconds: Float, override val delay: Float) : Anim

/** Eases to scale (sx, sy) about (cx, cy) and back every [seconds]. */
private data class Breathe(
    val cx: Float, val cy: Float, val sx: Float, val sy: Float, override val seconds: Float, override val delay: Float,
) : Anim

/** Eases to opacity [low] and back every [seconds]. */
private data class Fade(val low: Float, override val seconds: Float, override val delay: Float) : Anim

private class Art(val name: String, val shapes: List<Shape>)

private fun hasMotion(list: List<Shape>): Boolean = list.any { it is Motion || (it is ClipGroup && hasMotion(it.shapes)) }

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

private fun rectPath(x: Float, y: Float, w: Float, h: Float) = "M${n(x)} ${n(y)}h${n(w)}v${n(h)}h${n(-w)}Z"

/** A rectangular frame [t] thick; draw it with evenOdd. */
private fun framePath(x: Float, y: Float, w: Float, h: Float, t: Float) =
    rectPath(x, y, w, h) + rectPath(x + t, y + t, w - 2 * t, h - 2 * t)

/** A straight stroke [w] wide from (x1, y1) to (x2, y2), as a filled quad. */
private fun segment(x1: Float, y1: Float, x2: Float, y2: Float, w: Float): String {
    val l = sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    val nx = -(y2 - y1) / l * w / 2
    val ny = (x2 - x1) / l * w / 2
    return polygon(listOf(x1 + nx to y1 + ny, x2 + nx to y2 + ny, x2 - nx to y2 - ny, x1 - nx to y1 - ny))
}

private fun polygon(pts: List<Pair<Float, Float>>) =
    pts.mapIndexed { i, (x, y) -> (if (i == 0) "M" else "L") + "${n(x)} ${n(y)}" }.joinToString("") + "Z"

// ---- the ten approaches ----

/** One Bauhaus tile at (x, y), side m, grown from the prime corners of grid cell
 *  (c, r): one prime is a quarter disc, two adjacent a half disc, two opposite two
 *  small quarters, three a disc, four an accent disc. [tex] takes the tile kind (0 the
 *  four-prime disc, 1 quarters, 2 halves, 3 discs). [force] overrides the role. */
private fun moduleTile(
    grid: OddGrid, c: Int, r: Int, x: Float, y: Float, m: Float, tex: (Int) -> Tex?, force: Role? = null,
): List<Shape> {
    val shapes = mutableListOf<Shape>()
    val tl = grid.prime(c, r)
    val tr = grid.prime(c + 1, r)
    val br = grid.prime(c + 1, r + 1)
    val bl = grid.prime(c, r + 1)
    val n = listOf(tl, tr, br, bl).count { it }
    val role = force ?: if (grid.norm(c, r) % 13 == 0L) Role.ACCENT else Role.INK
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
    return shapes
}

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
        shapes += moduleTile(grid, c, r, margin + c * (m + gap), margin + r * (m + gap), m, ::tex)
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
    val marks = runs.map { r ->
        val y = my + r.line * pitch
        val x0 = mx + r.from * step
        val len = (r.to - r.from + 1) * step
        val role = if (r == longest) Role.ACCENT else Role.INK
        if (withDots && r.from == r.to) Disc(x0 + step / 2, y, thick * 0.62f, role)
        else Box(x0 + step * 0.12f, y - thick / 2, len - step * 0.24f, thick, role)
    }
    // In motion the marks scroll left along their lines, about 11 units a second, like
    // a score being played; the ruled lines and bar lines stay put.
    val span = U - 2 * mx
    shapes += Motion(Drift(-span, 0f, span / 11f), marks, copy = span to 0f, clip = rectPath(mx, 0f, span, U))
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
        val tile = mutableListOf<Shape>()
        for ((cx, cy, start) in corners) for ((r0, r1) in bands) {
            tile += Outline(ringSector(cx, cy, r0, r1, start, 90f), role = role, tex = tex)
        }
        // In motion the tiles on a prime turn a quarter at a time, so the curves keep
        // re-routing: scattered by norm with three lines, as a diagonal sweep with one.
        shapes += if (!grid.prime(c, r)) tile
        else if (lines > 1) listOf(Motion(Turn(x + m / 2, y + m / 2, 16f, (grid.norm(c, r) % 32) * 0.5f), tile))
        else listOf(Motion(Turn(x + m / 2, y + m / 2, 14f, 14f - (c + r) * 0.4f), tile))
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
        val marks = mutableListOf<Shape>()
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
                marks += Outline(polygon(listOf(
                    px - h * ux - w * uy to py - h * uy + w * ux,
                    px + h * ux - w * uy to py + h * uy + w * ux,
                    px + h * ux + w * uy to py + h * uy - w * ux,
                    px - h * ux + w * uy to py - h * uy - w * ux,
                )))
            } else {
                marks += Disc(px, py, pitch * (if (onAxis) 0.42f else 0.34f), if (onAxis) Role.ACCENT else Role.INK)
            }
        }
        // In motion each ring's marks turn about the centre, neighbours in opposite
        // directions, at a pace along the ring of 6 to 14 units a second set by the
        // ring's norm. The quarter stays still: turning would carry it out of frame.
        shapes += if (quarter) marks
        else listOf(Motion(Spin(cx, cy, (2 * Math.PI * r / (6 + (nm % 5) * 2)).toFloat(), k % 2 == 1), marks))
    }
    return Art(name, shapes)
}

/** Orbit at avatar size, to read at 32 pixels: the same rule, a ring per Gaussian
 *  prime norm with a mark at the true argument of each prime of that norm, but a few
 *  rings from the smallest norms, marks two pixels across, the origin in the accent,
 *  and each ring turning in 8 to 20 seconds, neighbours in opposite directions.
 *  [lines] draws each ring faintly under its marks; [mark] scales the marks; [ticks]
 *  draws each as a short radial bar instead of a dot. */
private fun orbitAvatar(
    name: String, rng: Random, rings: Int = 4, lines: Boolean = true, mark: Float = 1f, ticks: Boolean = false,
): Art {
    val c = U / 2
    val byNorm = sortedMapOf<Long, MutableList<Pair<Int, Int>>>()
    for (a in -12..12) for (b in -12..12) if (gaussianPrime(a, b)) byNorm.getOrPut(gnorm(a, b)) { mutableListOf() } += a to b
    val norms = byNorm.keys.drop(rng.nextInt(0, 3)).take(rings)
    val r0 = 160f
    val pitch = (440f - r0) / (rings - 1)
    val shapes = mutableListOf<Shape>(Disc(c, c, 64f, Role.ACCENT))
    norms.forEachIndexed { k, nm ->
        val r = r0 + k * pitch
        if (lines) shapes += Outline(ring(c, c, r - 10f, r + 10f), evenOdd = true, alpha = 0.3f)
        val marks = byNorm.getValue(nm).map { (a, b) ->
            val t = atan2(b.toDouble(), a.toDouble())
            val onAxis = a == 0 || b == 0
            val role = if (onAxis) Role.ACCENT else Role.INK
            val ux = cos(t).toFloat()
            val uy = -sin(t).toFloat()
            val px = c + r * ux
            val py = c + r * uy
            if (ticks) {
                val h = pitch * 0.3f * mark
                val w = 22f * mark
                Outline(polygon(listOf(
                    px - h * ux - w * uy to py - h * uy + w * ux,
                    px + h * ux - w * uy to py + h * uy + w * ux,
                    px + h * ux + w * uy to py + h * uy - w * ux,
                    px - h * ux + w * uy to py - h * uy - w * ux,
                )), role = role)
            } else {
                Disc(px, py, (if (onAxis) 46f else 38f) * mark, role)
            }
        }
        shapes += Motion(Spin(c, c, 8f + (nm % 5) * 3f, k % 2 == 1), marks)
    }
    return Art(name, shapes)
}

/** A piece clipped to the disc that fills the square, for a round avatar. */
private fun roundCut(art: Art): Art = Art(art.name, listOf(ClipGroup(circlePath(U / 2, U / 2, U / 2), false, art.shapes)))

/** De Stijl division: the square cut again and again on a grid, the direction, place
 *  and depth of every cut read from a stream of Gaussian primes. Mode 0 is thick rules
 *  and a few solid cells, 1 grained cells with no rules, 2 a finer cut with hairlines
 *  and stippled cells. */
private fun division(name: String, rng: Random, mode: Int): Art {
    val margin = 90f
    val units = if (mode == 2) 24 else 12
    val g = (U - 2 * margin) / units
    val stream = PrimeStream(primesNear(rng.nextInt(-300, 300), rng.nextInt(-300, 300), 300))
    val ts = rng.nextInt(1, 999)
    fun draw() = stream.draw()
    val (leaves, cuts) = divide(stream, units, if (mode == 2) 7 else 4)
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
        shapes += rules(cuts, margin, g, t)
    }
    return Art(name, shapes)
}

/** The rules of a division: one per cut, plus the frame, each [t] thick. */
private fun rules(cuts: List<DivCut>, margin: Float, g: Float, t: Float): List<Shape> {
    fun u(v: Int) = margin + v * g
    val shapes = mutableListOf<Shape>()
    for (cut in cuts) {
        val from = u(cut.from) - t / 2; val len = (cut.to - cut.from) * g + t
        shapes += if (cut.vertical) Box(u(cut.at) - t / 2, from, t, len) else Box(from, u(cut.at) - t / 2, len, t)
    }
    val side = U - 2 * margin + t
    shapes += Box(margin - t / 2, margin - t / 2, side, t)
    shapes += Box(margin - t / 2, U - margin - t / 2, side, t)
    shapes += Box(margin - t / 2, margin - t / 2, t, side)
    shapes += Box(U - margin - t / 2, margin - t / 2, t, side)
    return shapes
}

/** Primes read one after another, wrapping round. */
private class PrimeStream(private val primes: List<Pair<Int, Int>>) {
    private var next = 0
    fun draw() = primes[next++ % primes.size]
}

/** A cell and a cut of a division, in grid units. */
private data class DivCell(val x: Int, val y: Int, val w: Int, val h: Int, val depth: Int)
private data class DivCut(val vertical: Boolean, val at: Int, val from: Int, val to: Int)

/** Cuts a [units] square again and again: each cut's direction, place and whether to
 *  stop come from the next prime in [stream]. Long cells cut across their length. */
private fun divide(stream: PrimeStream, units: Int, maxDepth: Int): Pair<List<DivCell>, List<DivCut>> {
    val leaves = mutableListOf<DivCell>()
    val cuts = mutableListOf<DivCut>()
    val fractions = floatArrayOf(1 / 3f, 1 / 2f, 2 / 3f, 1 / 4f, 3 / 4f, 2 / 5f, 3 / 5f)
    fun split(c: DivCell) {
        val (a, b) = stream.draw()
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
            cuts += DivCut(true, c.x + at, c.y, c.y + c.h)
            split(DivCell(c.x, c.y, at, c.h, c.depth + 1)); split(DivCell(c.x + at, c.y, c.w - at, c.h, c.depth + 1))
        } else {
            cuts += DivCut(false, c.y + at, c.x, c.x + c.w)
            split(DivCell(c.x, c.y, c.w, at, c.depth + 1)); split(DivCell(c.x, c.y + at, c.w, c.h - at, c.depth + 1))
        }
    }
    split(DivCell(0, 0, units, units, 0))
    return leaves to cuts
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
    fun shade(x: Float, y: Float): Float = when (fade) {
        0 -> 1f - (x - margin) / (U - 2 * margin) * 0.92f
        1 -> 1f - min(1f, sqrt((U - margin - x) * (U - margin - x) + (y - margin) * (y - margin)) / (U * 1.15f))
        else -> 1f - min(1f, sqrt((x - U / 2) * (x - U / 2) + (y - U / 2) * (y - U / 2)) / (U * 0.6f))
    }
    val shapes = mutableListOf<Shape>()
    fun dot(x: Float, y: Float, a: Int, b: Int, maxR: Float) {
        val r = maxR * sqrt(primeTone(a, b) * shade(x, y))
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
        // In motion a slow wave of light runs out from the centre: the dots are grouped
        // in rings by distance, each ring fading a little later than the one inside it.
        // The grouping reorders the dots in the still SVG too; they do not overlap, so it
        // draws the same.
        val rings = 12
        val period = 7f
        val byRing = shapes.groupBy {
            val d = it as Disc
            min(rings - 1, (sqrt((d.cx - U / 2) * (d.cx - U / 2) + (d.cy - U / 2) * (d.cy - U / 2)) / (U * 0.6f) * rings).toInt())
        }.toSortedMap()
        shapes.clear()
        byRing.forEach { (k, dots) -> shapes += Motion(Fade(0.28f, period, (period - k * 0.45f).mod(period)), dots) }
    } else {
        val grid = OddGrid(a0, b0)
        val m = (U - 2 * margin) / cols
        for (j in 0 until cols) for (i in 0 until cols) {
            dot(margin + (i + 0.5f) * m, margin + (j + 0.5f) * m, grid.a(i, j), grid.b(i, j), m * (if (square) 0.46f else 0.5f))
        }
    }
    return Art(name, shapes)
}

/** How prime a point's neighbourhood is: 1 for a prime, otherwise a share of its four
 *  nearest odd neighbours (the diagonal ones) that are prime. */
private fun primeTone(a: Int, b: Int): Float {
    if (gaussianPrime(a, b)) return 1f
    val near = listOf(a + 1 to b + 1, a + 1 to b - 1, a - 1 to b + 1, a - 1 to b - 1).count { (x, y) -> gaussianPrime(x, y) }
    return 0.12f + 0.6f * near / 4f
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

/** Bands that, in motion, scroll by one span (dx, dy) at 10 units a second and loop,
 *  with a copy of themselves one span behind. */
private fun scroll(bands: List<Shape>, dx: Float, dy: Float): Motion =
    Motion(Drift(dx, dy, sqrt(dx * dx + dy * dy) / 10f), bands, copy = -dx to -dy, hiDpi = true)

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
            val bands = barcode(grid, 0, 64, false, U / 2 - r, U / 2 + r, U / 2 - r, U / 2 + r, true, null)
            shapes += ClipGroup(circlePath(U / 2, U / 2, r), false, listOf(scroll(bands, 0f, -2 * r)))
        }
        1 -> {
            val r = 300f
            val left = "M${n(U / 2)} ${n(380f - r)}A${n(r)} ${n(r)} 0 0 0 ${n(U / 2)} ${n(380f + r)}Z"
            val right = "M${n(U / 2)} ${n(620f - r)}A${n(r)} ${n(r)} 0 0 1 ${n(U / 2)} ${n(620f + r)}Z"
            shapes += ClipGroup(left, false, listOf(scroll(barcode(grid, 0, 30, true, U / 2 - r, U / 2, 80f, 680f, true, null), r, 0f)))
            shapes += ClipGroup(right, false, listOf(scroll(barcode(grid, 1, 54, false, 620f - r, 620f + r, U / 2, U / 2 + r, false, null), 0f, 2 * r)))
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

// ---- process illustrations (folio "How I work") ----
// Each reads one step of a design process in the same vocabulary: iterating on
// journeys and designs (journey, rounds, versions, drafts) and building components in
// the repo (parts, assembly, tree, kit).

/** Iterating, as a journey: the Truchet arcs as fine lines, and the longest open path
 *  through them, edge to edge, as a wide accent band with a dot at each end. */
private fun journey(name: String, rng: Random, cols: Int): Art {
    val margin = 90f
    val m = (U - 2 * margin) / cols
    val grid = oddGrid(rng, 200)
    // Edge midpoints are the graph's nodes: the top edge of tile (c, r) is H(c, r), its
    // left edge V(c, r). Each arc joins two of them.
    fun h(c: Int, r: Int) = r * cols + c
    fun v(c: Int, r: Int) = (cols + 1) * cols + r * (cols + 1) + c
    fun at(node: Int): Pair<Float, Float> {
        val hs = (cols + 1) * cols
        return if (node < hs) (margin + (node % cols) * m + m / 2) to (margin + (node / cols) * m)
        else (margin + ((node - hs) % (cols + 1)) * m) to (margin + ((node - hs) / (cols + 1)) * m + m / 2)
    }
    data class Arc(val cx: Float, val cy: Float, val start: Float, val a: Int, val b: Int)
    val arcs = mutableListOf<Arc>()
    for (r in 0 until cols) for (c in 0 until cols) {
        val x = margin + c * m
        val y = margin + r * m
        if (grid.prime(c, r) != grid.prime(c + 1, r + 1)) {
            arcs += Arc(x + m, y, 90f, h(c, r), v(c + 1, r))
            arcs += Arc(x, y + m, 270f, h(c, r + 1), v(c, r))
        } else {
            arcs += Arc(x, y, 0f, h(c, r), v(c, r))
            arcs += Arc(x + m, y + m, 180f, h(c, r + 1), v(c + 1, r))
        }
    }
    val touching = mutableMapOf<Int, MutableList<Int>>()
    arcs.forEachIndexed { i, a -> touching.getOrPut(a.a) { mutableListOf() } += i; touching.getOrPut(a.b) { mutableListOf() } += i }
    // Components: every node meets at most two arcs, so each is a loop or an open path
    // whose ends sit on the boundary.
    val seen = BooleanArray(arcs.size)
    var best = emptyList<Int>()
    var ends = emptyList<Int>()
    for (i in arcs.indices) {
        if (seen[i]) continue
        val comp = mutableListOf<Int>()
        val queue = ArrayDeque(listOf(i)); seen[i] = true
        while (queue.isNotEmpty()) {
            val k = queue.removeFirst(); comp += k
            for (node in listOf(arcs[k].a, arcs[k].b)) for (j in touching.getValue(node)) if (!seen[j]) { seen[j] = true; queue += j }
        }
        val open = comp.flatMap { listOf(arcs[it].a, arcs[it].b) }.filter { touching.getValue(it).size == 1 }
        if (open.isNotEmpty() && comp.size > best.size) { best = comp; ends = open }
    }
    val shapes = mutableListOf<Shape>()
    val line = m * 0.035f
    arcs.forEachIndexed { i, a ->
        if (i !in best) shapes += Outline(ringSector(a.cx, a.cy, m / 2 - line, m / 2 + line, a.start, 90f))
    }
    val band = m * 0.13f
    for (i in best) {
        val a = arcs[i]
        shapes += Outline(ringSector(a.cx, a.cy, m / 2 - band, m / 2 + band, a.start, 90f), role = Role.ACCENT)
    }
    for (e in ends) { val (x, y) = at(e); shapes += Disc(x, y, band * 1.6f, Role.ACCENT) }
    return Art(name, shapes)
}

/** Iterating, as rounds: square frames, each the last one shrunk toward a point the
 *  primes pick, stippled denser round by round, ending on a solid accent square. */
private fun rounds(name: String, rng: Random, count: Int): Art {
    val primes = primesNear(rng.nextInt(20, 300), rng.nextInt(1, 300), count + 2)
    val ts = rng.nextInt(1, 999)
    val s0 = U - 180f
    val tx = U / 2 + (Math.floorMod(primes[0].first, 5) - 2) * 70f
    val ty = U / 2 + (Math.floorMod(primes[0].second, 5) - 2) * 70f
    val shapes = mutableListOf<Shape>()
    var f = 1f
    for (k in 0..count) {
        val size = s0 * f
        val x = tx + (90f - tx) * f
        val y = ty + (90f - ty) * f
        if (k == count) {
            shapes += Box(x, y, size, size, Role.ACCENT)
        } else {
            val t = max(8f, size * 0.075f)
            shapes += Outline(framePath(x, y, size, size, t), evenOdd = true, tex = stipple(0.32f + 0.62f * k / (count - 1), ts + k))
        }
        val (a, b) = primes[k + 1]
        f *= 0.7f + (gnorm(a, b) % 5) * 0.025f
    }
    return Art(name, shapes)
}

/** Iterating, as versions: a contact sheet of nine panels, each the same window of
 *  Bauhaus tiles moved one step along the lattice, so shapes walk from panel to panel.
 *  The last panel, the one kept, is in the accent. */
private fun versions(name: String, rng: Random): Art {
    val margin = 90f
    val gutter = 30f
    val panel = (U - 2 * margin - 2 * gutter) / 3
    val pad = 16f
    val gap = 4f
    val m = (panel - 2 * pad - 2 * gap) / 3
    val grid = oddGrid(rng, 60)
    val shapes = mutableListOf<Shape>()
    for (k in 0 until 9) {
        val px = margin + (k % 3) * (panel + gutter)
        val py = margin + (k / 3) * (panel + gutter)
        shapes += Outline(framePath(px, py, panel, panel, 1.5f), evenOdd = true, alpha = 0.35f)
        for (r in 0 until 3) for (c in 0 until 3) {
            shapes += moduleTile(grid, c + k, r, px + pad + c * (m + gap), py + pad + r * (m + gap), m, { null },
                force = if (k == 8) Role.ACCENT else Role.INK)
        }
    }
    return Art(name, shapes)
}

/** Iterating, as drafts: the same square laid down again and again in translucent
 *  grain, each draft nearer the final place, the final one in the accent. Offsets
 *  shrink each round and turn by the golden angle from the argument of successive
 *  primes, so the drafts circle in on the final place rather than trail behind it. */
private fun drafts(name: String, rng: Random, count: Int): Art {
    val primes = primesNear(rng.nextInt(-200, 200), rng.nextInt(-200, 200), count)
    val ts = rng.nextInt(1, 999)
    val side = 380f
    val fx = (U - side) / 2
    val fy = (U - side) / 2
    val shapes = mutableListOf<Shape>()
    for (k in 0 until count) {
        val last = k == count - 1
        val (a, b) = primes[k]
        val t = atan2(b.toDouble(), a.toDouble()) + k * 2.39996
        val d = if (last) 0f else 200f * Math.pow(0.66, k.toDouble()).toFloat()
        shapes += Box(fx + d * cos(t).toFloat(), fy + d * sin(t).toFloat(), side, side,
            if (last) Role.ACCENT else Role.INK, if (last) 0.88f else 0.15f, grain(ts + k))
    }
    return Art(name, shapes)
}

/** Building, as parts: Truchet tiles pulled apart into separate pieces, each in a
 *  hairline slot. A few lift out of their slots, the first in the accent. */
private fun parts(name: String, rng: Random, cols: Int): Art {
    val margin = 90f
    val gap = 30f
    val m = (U - 2 * margin - (cols - 1) * gap) / cols
    val grid = oddGrid(rng, 200)
    val lift = m * 0.17f
    val lifted = (0 until cols * cols).filter { grid.norm(it % cols, it / cols) % 5 == 0L }.ifEmpty { listOf(cols * cols / 2) }
    val shapes = mutableListOf<Shape>()
    val band = m * 0.12f
    for (r in 0 until cols) for (c in 0 until cols) {
        val i = r * cols + c
        var x = margin + c * (m + gap)
        var y = margin + r * (m + gap)
        shapes += Outline(framePath(x, y, m, m, 1.5f), evenOdd = true, alpha = 0.35f)
        if (i in lifted) { x += lift; y -= lift }
        val role = if (i == lifted.first()) Role.ACCENT else Role.INK
        val corners = if (grid.prime(c, r) != grid.prime(c + 1, r + 1)) listOf(Triple(x + m, y, 90f), Triple(x, y + m, 270f))
        else listOf(Triple(x, y, 0f), Triple(x + m, y + m, 180f))
        for ((cx, cy, start) in corners) shapes += Outline(ringSector(cx, cy, m / 2 - band, m / 2 + band, start, 90f), role = role)
    }
    return Art(name, shapes)
}

/** Building, as assembly: a De Stijl division whose cells hold parts fitted to their
 *  shape (a disc, a quarter from a corner, a half on the long side, a solid or
 *  stippled block), the largest round part in the accent. */
private fun assembly(name: String, rng: Random): Art {
    val margin = 90f
    val units = 12
    val g = (U - 2 * margin) / units
    val stream = PrimeStream(primesNear(rng.nextInt(-300, 300), rng.nextInt(-300, 300), 300))
    val ts = rng.nextInt(1, 999)
    val (leaves, cuts) = divide(stream, units, 5)
    val t = 6f
    val kinds = leaves.map { val (a, b) = stream.draw(); (gnorm(a, b) % 6).toInt() to Math.floorMod(a, 4) }
    val accent = leaves.indices.filter { kinds[it].first <= 2 }.maxByOrNull { min(leaves[it].w, leaves[it].h) }
    val shapes = mutableListOf<Shape>()
    leaves.forEachIndexed { i, c ->
        val pad = t / 2 + 10f
        val x = margin + c.x * g + pad; val y = margin + c.y * g + pad
        val w = c.w * g - 2 * pad; val h = c.h * g - 2 * pad
        val role = if (i == accent) Role.ACCENT else Role.INK
        val (kind, corner) = kinds[i]
        when (kind) {
            0 -> shapes += Disc(x + w / 2, y + h / 2, min(w, h) / 2, role)
            1 -> {
                val r = min(w, h)
                shapes += when (corner) {
                    0 -> Sector(x, y, r, 0f, 90f, role)
                    1 -> Sector(x + w, y, r, 90f, 90f, role)
                    2 -> Sector(x + w, y + h, r, 180f, 90f, role)
                    else -> Sector(x, y + h, r, 270f, 90f, role)
                }
            }
            2 -> shapes += if (w >= h) Sector(x + w / 2, y + h, min(w / 2, h), 180f, 180f, role)
                else Sector(x, y + h / 2, min(h / 2, w), 270f, 180f, role)
            3 -> shapes += Box(x, y, w, h, Role.INK)
            4 -> shapes += Box(x, y, w, h, Role.INK, tex = stipple(0.55f, ts + i % 4))
        }
    }
    shapes += rules(cuts, margin, g, t)
    return Art(name, shapes)
}

/** Building, as a repo tree: rows of a file tree, each indented one level deeper,
 *  level, or back out as the primes along its lattice row say. Folders are squares,
 *  files discs, each with a bar for its name; hairline guides join a folder to its
 *  children. The deepest file is the new component, in the accent. */
private fun tree(name: String, rng: Random, rows: Int): Art {
    val my = 110f
    val pitch = (U - 2 * my) / (rows - 1)
    val step = 64f
    val grid = oddGrid(rng, 300)
    val depth = IntArray(rows)
    for (k in 1 until rows) depth[k] = when {
        (grid.prime(k, 0) || grid.prime(k, 1)) && depth[k - 1] < 4 -> depth[k - 1] + 1
        grid.prime(k, 2) -> depth[k - 1]
        else -> max(0, depth[k - 1] - 1)
    }
    fun x(d: Int) = 120f + d * step
    fun y(k: Int) = my + k * pitch
    val folder = BooleanArray(rows) { k -> k + 1 < rows && depth[k + 1] > depth[k] }
    val newest = (0 until rows).filter { !folder[it] }.maxByOrNull { depth[it] } ?: rows - 1
    val shapes = mutableListOf<Shape>()
    val mark = pitch * 0.42f
    val hair = 1.6f
    for (k in 0 until rows) {
        if (!folder[k]) continue
        val kids = (k + 1 until rows).takeWhile { depth[it] > depth[k] }.filter { depth[it] == depth[k] + 1 }
        val gx = x(depth[k])
        shapes += Box(gx - hair / 2, y(k) + mark / 2, hair, y(kids.last()) - y(k) - mark / 2, Role.INK, 0.45f)
        for (c in kids) shapes += Box(gx, y(c) - hair / 2, step - mark / 2 - 8f, hair, Role.INK, 0.45f)
    }
    for (k in 0 until rows) {
        val role = if (k == newest) Role.ACCENT else Role.INK
        val cx = x(depth[k])
        shapes += if (folder[k]) Box(cx - mark / 2, y(k) - mark / 2, mark, mark, role) else Disc(cx, y(k), mark * 0.4f, role)
        val start = cx + mark / 2 + 14f
        val len = min(150f + (grid.norm(k, 3) % 11) * 38f, U - 110f - start)
        val th = pitch * 0.22f
        shapes += Box(start, y(k) - th / 2, len, th, role)
    }
    return Art(name, shapes)
}

/** Building, as a kit: a specimen sheet of the parts, a family to a row (discs,
 *  halves, quarters, blocks), each in a hairline cell. The primes turn each row,
 *  stipple some parts and pick the one accent. */
private fun kit(name: String, rng: Random): Art {
    val margin = 110f
    val gap = 26f
    val cell = (U - 2 * margin - 3 * gap) / 4
    val pad = 20f
    val grid = oddGrid(rng, 120)
    val ts = rng.nextInt(1, 999)
    val pick = Math.floorMod(grid.a(0, 0) * 7 + grid.b(0, 0), 16)
    val shapes = mutableListOf<Shape>()
    for (r in 0 until 4) {
        val turn = Math.floorMod(grid.b(r, 0), 4)
        for (c in 0 until 4) {
            val cx0 = margin + c * (cell + gap)
            val cy0 = margin + r * (cell + gap)
            shapes += Outline(framePath(cx0, cy0, cell, cell, 1.5f), evenOdd = true, alpha = 0.35f)
            val x = cx0 + pad; val y = cy0 + pad; val s = cell - 2 * pad
            val role = if (r * 4 + c == pick) Role.ACCENT else Role.INK
            val tex = if (role == Role.INK && grid.prime(r, c + 1)) stipple(0.62f, ts + r) else null
            val v = (c + turn) % 4
            shapes += when (r) {
                0 -> when (v) {
                    0 -> Disc(x + s / 2, y + s / 2, s / 2, role, tex = tex)
                    1 -> Outline(ring(x + s / 2, y + s / 2, s * 0.3f, s / 2), evenOdd = true, role = role, tex = tex)
                    2 -> Disc(x + s / 2, y + s / 2, s * 0.3f, role, tex = tex)
                    else -> Outline(ring(x + s / 2, y + s / 2, s * 0.44f, s / 2) + circlePath(x + s / 2, y + s / 2, s * 0.3f), evenOdd = true, role = role, tex = tex)
                }
                // A half disc standing on one side of the cell.
                1 -> when (v) {
                    0 -> Sector(x + s / 2, y + s, s / 2, 180f, 180f, role, tex = tex)
                    1 -> Sector(x, y + s / 2, s / 2, 270f, 180f, role, tex = tex)
                    2 -> Sector(x + s / 2, y, s / 2, 0f, 180f, role, tex = tex)
                    else -> Sector(x + s, y + s / 2, s / 2, 90f, 180f, role, tex = tex)
                }
                2 -> when (v) {
                    0 -> Sector(x, y, s, 0f, 90f, role, tex = tex)
                    1 -> Sector(x + s, y, s, 90f, 90f, role, tex = tex)
                    2 -> Sector(x + s, y + s, s, 180f, 90f, role, tex = tex)
                    else -> Sector(x, y + s, s, 270f, 90f, role, tex = tex)
                }
                else -> when (v) {
                    0 -> Box(x, y, s, s, role, tex = tex)
                    1 -> Outline(framePath(x, y, s, s, s * 0.18f), evenOdd = true, role = role, tex = tex)
                    2 -> Box(x, y + s / 4, s, s / 2, role, tex = tex)
                    else -> Box(x + s / 4, y, s / 2, s, role, tex = tex)
                }
            }
        }
    }
    return Art(name, shapes)
}

// ---- the double diamond (folio "How I work" intro) ----
// Loose readings of discover, define, develop, deliver: two openings and closings that
// meet at one point. All share the same shape: two diamonds side by side across the
// middle of the square, the pinch between them at the centre.

private const val DD_X0 = 90f
private const val DD_X1 = U - 90f
private const val DD_Y = U / 2

/** How far inside the double diamond a point is: 1 at a diamond's centre, 0 on its
 *  edge and outside. Each diamond is a quarter of [DD_X0, DD_X1] each side of its
 *  centre and [h] tall. */
private fun inDiamonds(x: Float, y: Float, h: Float): Float {
    val w = (DD_X1 - DD_X0) / 4
    fun one(cx: Float) = 1f - (abs(x - cx) / w + abs(y - DD_Y) / (h / 2))
    return max(0f, max(one(DD_X0 + w), one(DD_X1 - w)))
}

/** Pulse: a row of bars like a waveform, each as tall as the double diamond is at
 *  that point, scaled by how prime its lattice neighbourhood is. An accent dot holds
 *  the pinch. */
private fun pulse(name: String, rng: Random, bars: Int): Art {
    val grid = oddGrid(rng, 200)
    val step = (DD_X1 - DD_X0) / bars
    val h = 720f
    val shapes = mutableListOf<Shape>()
    for (i in 0 until bars) {
        val x = DD_X0 + (i + 0.5f) * step
        val env = inDiamonds(x, DD_Y, h) * h
        val len = max(step * 0.55f, env * primeTone(grid.a(0, i), grid.b(0, i)))
        shapes += Box(x - step * 0.28f, DD_Y - len / 2, step * 0.56f, len)
    }
    shapes += Disc(U / 2, DD_Y, step * 0.9f, Role.ACCENT)
    return Art(name, shapes)
}

/** Fan: strings that open from a point and close on the next, twice. Each diamond has
 *  a row of candidate strings and keeps the ones whose lattice point is prime, with a
 *  node where each turns. The meeting point between the two is the accent. */
private fun fan(name: String, rng: Random, count: Int): Art {
    val grid = oddGrid(rng, 200)
    val w = (DD_X1 - DD_X0) / 4
    val pts = listOf(DD_X0 + 20f, U / 2, DD_X1 - 20f)
    val spread = 330f
    val shapes = mutableListOf<Shape>()
    shapes += Outline(segment(pts[0], DD_Y, pts[2], DD_Y, 3f), alpha = 0.35f)
    for (d in 0 until 2) {
        val mx = DD_X0 + w + d * 2 * w
        for (j in 0 until count) {
            if (!grid.prime(d, j)) continue
            val y = DD_Y + (j - (count - 1) / 2f) / ((count - 1) / 2f) * spread
            shapes += Outline(segment(pts[d], DD_Y, mx, y, 4f))
            shapes += Outline(segment(mx, y, pts[d + 1], DD_Y, 4f))
            shapes += Disc(mx, y, 8f)
        }
    }
    shapes += Disc(pts[0], DD_Y, 16f)
    shapes += Disc(pts[2], DD_Y, 16f)
    shapes += Disc(pts[1], DD_Y, 26f, Role.ACCENT)
    return Art(name, shapes)
}

/** Branches: a bracket that splits one into two into four and joins back, twice, in
 *  square elbows. Prime nodes are discs, the rest small squares; the one node in the
 *  middle is the accent. */
private fun branches(name: String, rng: Random): Art {
    val grid = oddGrid(rng, 200)
    val counts = intArrayOf(1, 2, 4, 2, 1, 2, 4, 2, 1)
    val dx = (DD_X1 - DD_X0) / (counts.size - 1)
    val t = 8f
    fun x(c: Int) = DD_X0 + c * dx
    // Four nodes sit 200 apart; a pair sits at the middle of the two it splits into.
    fun y(c: Int, i: Int) = DD_Y + (i - (counts[c] - 1) / 2f) * (800f / counts[c])
    val shapes = mutableListOf<Shape>()
    fun elbow(x1: Float, y1: Float, x2: Float, y2: Float) {
        val xm = (x1 + x2) / 2
        shapes += Box(x1, y1 - t / 2, xm - x1, t)
        shapes += Box(xm - t / 2, min(y1, y2) - t / 2, t, abs(y2 - y1) + t)
        shapes += Box(xm, y2 - t / 2, x2 - xm, t)
    }
    for (c in 0 until counts.size - 1) {
        for (i in 0 until counts[c]) {
            if (counts[c + 1] > counts[c]) {
                elbow(x(c), y(c, i), x(c + 1), y(c + 1, 2 * i)); elbow(x(c), y(c, i), x(c + 1), y(c + 1, 2 * i + 1))
            } else {
                elbow(x(c), y(c, i), x(c + 1), y(c + 1, i / 2))
            }
        }
    }
    for (c in counts.indices) for (i in 0 until counts[c]) {
        val (nx, ny) = x(c) to y(c, i)
        shapes += when {
            c == counts.size / 2 -> Box(nx - 26f, ny - 26f, 52f, 52f, Role.ACCENT)
            grid.prime(c, i) -> Disc(nx, ny, 22f)
            else -> Box(nx - 14f, ny - 14f, 28f, 28f)
        }
    }
    return Art(name, shapes)
}

/** Rhombs: two lozenges overprinted in grain on a ruled axis, overlapping where one
 *  phase hands over to the next, with the axis ticked at every prime along a lattice
 *  row and an accent square where they meet. Heights from prime norms. */
private fun rhombs(name: String, rng: Random): Art {
    val primes = primesNear(rng.nextInt(20, 300), rng.nextInt(1, 300), 4)
    val grid = oddGrid(rng, 200)
    val ts = rng.nextInt(1, 999)
    val shapes = mutableListOf<Shape>()
    val w = 235f
    val overlap = 40f + (gnorm(primes[0].first, primes[0].second) % 4) * 20f
    val c1 = U / 2 - w + overlap / 2
    val c2 = U / 2 + w - overlap / 2
    val h1 = 240f + (gnorm(primes[1].first, primes[1].second) % 5) * 22f
    val h2 = 240f + (gnorm(primes[2].first, primes[2].second) % 5) * 22f
    fun lozenge(cx: Float, h: Float) = polygon(listOf(cx - w to DD_Y, cx to DD_Y - h, cx + w to DD_Y, cx to DD_Y + h))
    // In motion the two lozenges open and close in turn, half a cycle apart.
    shapes += Motion(Breathe(c1, DD_Y, 0.94f, 0.82f, 9f, 0f), listOf(Outline(lozenge(c1, h1), alpha = 0.2f, tex = grain(ts))))
    shapes += Motion(Breathe(c2, DD_Y, 0.94f, 0.82f, 9f, 4.5f), listOf(Outline(lozenge(c2, h2), alpha = 0.42f, tex = grain(ts + 1))))
    val ticks = 40
    val step = (DD_X1 - DD_X0) / ticks
    shapes += Box(DD_X0, DD_Y - 1.5f, DD_X1 - DD_X0, 3f, Role.INK, 0.6f)
    for (j in 0..ticks) if (grid.prime(0, j)) shapes += Box(DD_X0 + j * step - 2f, DD_Y - 14f, 4f, 28f)
    shapes += Box(U / 2 - 24f, DD_Y - 24f, 48f, 48f, Role.ACCENT)
    return Art(name, shapes)
}

/** Twin: a halftone over the true odd lattice (a grid turned 45 degrees) whose tone is
 *  the double diamond: dots swell toward each diamond's centre and are gone outside.
 *  Primes are full dots; a few take the accent. */
private fun twin(name: String, rng: Random, cols: Int): Art {
    val a0 = rng.nextInt(-300, 300)
    var b0 = rng.nextInt(-300, 300)
    if (Math.floorMod(a0 + b0, 2) == 0) b0 += 1
    val step = (DD_X1 - DD_X0) / (2 * cols)
    val shapes = mutableListOf<Shape>()
    for (v in 0..2 * cols) for (u in 0..2 * cols) {
        if ((u + v) % 2 != 0) continue
        val x = DD_X0 + u * step
        val y = DD_X0 + v * step
        val a = a0 + u
        val b = b0 - v
        val r = step * 0.72f * sqrt(primeTone(a, b) * min(1f, inDiamonds(x, y, 780f) * 1.5f))
        if (r < 0.8f) continue
        shapes += Disc(x, y, r, if (gaussianPrime(a, b) && gnorm(a, b) % 13 == 0L) Role.ACCENT else Role.INK)
    }
    return Art(name, shapes)
}

/** Knots: Truchet arcs only inside the double diamond, the band swelling toward each
 *  diamond's centre, so the curves gather into two knots joined at the pinch. */
private fun knots(name: String, rng: Random, cols: Int): Art {
    val m = (DD_X1 - DD_X0) / cols
    val grid = oddGrid(rng, 200)
    val shapes = mutableListOf<Shape>()
    for (r in 0 until cols) for (c in 0 until cols) {
        val x = DD_X0 + c * m
        val y = DD_X0 + r * m
        val inside = inDiamonds(x + m / 2, y + m / 2, 820f)
        if (inside <= 0.04f) continue
        val band = m * (0.05f + 0.13f * min(1f, inside * 1.4f))
        val role = if (grid.norm(c, r) % 13 == 0L) Role.ACCENT else Role.INK
        val corners = if (grid.prime(c, r) != grid.prime(c + 1, r + 1)) listOf(Triple(x + m, y, 90f), Triple(x, y + m, 270f))
        else listOf(Triple(x, y, 0f), Triple(x + m, y + m, 180f))
        for ((cx, cy, start) in corners) shapes += Outline(ringSector(cx, cy, m / 2 - band, m / 2 + band, start, 90f), role = role)
    }
    return Art(name, shapes)
}

// ---- vector ----

/** The SVG. Themable (no ink given): no background, colours as classes reading CSS
 *  custom properties with the light palette as fallback, ids prefixed with the art's
 *  name so several can sit inline on one page. Baked (ink given): literal colours on a
 *  ground, sized for the PNG preview. */
private fun svg(art: Art, baked: PaperInk? = null, motion: Boolean = false): String {
    val sb = StringBuilder()
    val size = if (baked != null) """ width="$SIZE" height="$SIZE"""" else ""
    sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${U.toInt()} ${U.toInt()}"$size>""")
    if (baked == null) {
        sb.append("""<style>.gen-i{fill:var(--gen-ink,${LIGHT.ink})}.gen-a{fill:var(--gen-accent,${LIGHT.accent})}""")
        if (motion) sb.append(motionCss(art))
        sb.appendLine("</style>")
    } else {
        sb.appendLine("""<rect width="${U.toInt()}" height="${U.toInt()}" fill="${baked.ground}"/>""")
    }
    fun flat(list: List<Shape>): List<Shape> = list.flatMap {
        when (it) {
            is ClipGroup -> flat(it.shapes) + it
            is Motion -> flat(it.shapes)
            else -> listOf(it)
        }
    }
    val moving = motions(art.shapes)
    val texes = flat(art.shapes).mapNotNull { it.tex }.distinct()
    val clips = art.shapes.filterIsInstance<ClipGroup>()
    val motionClips = if (motion) moving.filter { it.clip != null } else emptyList()
    if (texes.isNotEmpty() || clips.isNotEmpty() || motionClips.isNotEmpty()) {
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
        motionClips.forEach { m ->
            sb.appendLine("""<clipPath id="${art.name}-mc${moving.indexOf(m)}"><path d="${m.clip}"/></clipPath>""")
        }
        sb.appendLine("</defs>")
    }
    fun draw(s: Shape) {
        if (s is Motion) {
            if (!motion) { s.shapes.forEach(::draw); return }
            val i = moving.indexOf(s)
            if (s.clip != null) sb.appendLine("""<g clip-path="url(#${art.name}-mc$i)">""")
            sb.appendLine("""<g class="${art.name}-m ${art.name}-m$i">""")
            s.shapes.forEach(::draw)
            s.copy?.let { (x, y) ->
                sb.appendLine("""<g transform="translate(${n(x)} ${n(y)})">""")
                s.shapes.forEach(::draw)
                sb.appendLine("</g>")
            }
            sb.appendLine(if (s.clip != null) "</g></g>" else "</g>")
            return
        }
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
                is ClipGroup, is Motion -> ""
            }
        )
    }
    art.shapes.forEach(::draw)
    sb.appendLine("</svg>")
    return sb.toString()
}

private fun motions(list: List<Shape>): List<Motion> = list.flatMap {
    when (it) {
        is Motion -> listOf(it)
        is ClipGroup -> motions(it.shapes)
        else -> emptyList()
    }
}

/** The motion SVG's animation rules: a class per moving group (transforms in user
 *  units about the viewBox), keyframes named after the piece so several pieces can sit
 *  inline on one page, and everything still under prefers-reduced-motion. */
private fun motionCss(art: Art): String {
    val sb = StringBuilder()
    val name = art.name
    val moving = motions(art.shapes)
    if (moving.any { it.anim is Spin }) sb.append("@keyframes $name-spin{to{transform:rotate(360deg)}}")
    if (moving.any { it.anim is Turn }) {
        sb.append("@keyframes $name-turn{0%,40%{transform:rotate(0)}50%,90%{transform:rotate(90deg)}to{transform:rotate(180deg)}}")
    }
    val lows = moving.mapNotNull { (it.anim as? Fade)?.low }.distinct()
    lows.forEachIndexed { k, low -> sb.append("@keyframes $name-fade$k{50%{opacity:${n(low)}}}") }
    moving.forEachIndexed { i, m ->
        val a = m.anim
        // A negative delay starts the cycle part way through, so nothing waits to move.
        val at = if (a.delay > 0f) " ${n(-a.delay)}s" else ""
        sb.append(".$name-m$i{transform-box:view-box;")
        when (a) {
            is Spin -> sb.append("transform-origin:${n(a.cx)}px ${n(a.cy)}px;animation:$name-spin ${n(a.seconds)}s linear$at infinite${if (a.reverse) " reverse" else ""}}")
            is Drift -> {
                sb.append("animation:$name-d$i ${n(a.seconds)}s linear$at infinite}")
                sb.append("@keyframes $name-d$i{to{transform:translate(${n(a.dx)}px,${n(a.dy)}px)}}")
            }
            is Turn -> sb.append("transform-origin:${n(a.cx)}px ${n(a.cy)}px;animation:$name-turn ${n(a.seconds)}s ease-in-out$at infinite}")
            is Breathe -> {
                sb.append("transform-origin:${n(a.cx)}px ${n(a.cy)}px;animation:$name-b$i ${n(a.seconds)}s ease-in-out$at infinite}")
                sb.append("@keyframes $name-b$i{50%{transform:scale(${n(a.sx)},${n(a.sy)})}}")
            }
            is Fade -> sb.append("animation:$name-fade${lows.indexOf(a.low)} ${n(a.seconds)}s ease-in-out$at infinite}")
        }
    }
    val lowDpi = moving.indices.filter { moving[it].hiDpi }
    if (lowDpi.isNotEmpty()) {
        sb.append("@media (max-resolution:1.5dppx),(-webkit-max-device-pixel-ratio:1.5){")
        sb.append(lowDpi.joinToString(",") { ".$name-m$it" }).append("{animation:none}}")
    }
    sb.append("@media (prefers-reduced-motion:reduce){.$name-m{animation:none}}")
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
        "rationalJourney01" to { nm, r -> journey(nm, r, 9) },
        "rationalRounds01" to { nm, r -> rounds(nm, r, 7) },
        "rationalVersions01" to { nm, r -> versions(nm, r) },
        "rationalDrafts01" to { nm, r -> drafts(nm, r, 6) },
        "rationalParts01" to { nm, r -> parts(nm, r, 5) },
        "rationalAssembly01" to { nm, r -> assembly(nm, r) },
        "rationalTree01" to { nm, r -> tree(nm, r, 15) },
        "rationalKit01" to { nm, r -> kit(nm, r) },
        "rationalPulse01" to { nm, r -> pulse(nm, r, 56) },
        "rationalFan01" to { nm, r -> fan(nm, r, 36) },
        "rationalBranches01" to { nm, r -> branches(nm, r) },
        "rationalRhombs01" to { nm, r -> rhombs(nm, r) },
        "rationalTwin01" to { nm, r -> twin(nm, r, 22) },
        "rationalKnots01" to { nm, r -> knots(nm, r, 12) },
        // Avatars: three families at a size that reads at 32 pixels, for the portfolio
        // assistant. Arcs and Halftone are their own makers on a coarse grid, cut round.
        "rationalAvatarOrbit01" to { nm, r -> orbitAvatar(nm, r) },
        "rationalAvatarArcs01" to { nm, r -> roundCut(arcs(nm, r, 3, 1, 0)) },
        "rationalAvatarHalftone01" to { nm, r -> roundCut(halftone(nm, r, 3, 2, false, true)) },
        // Orbit with no ring lines: only the marks turning, at three, four and five rings,
        // and with radial ticks for marks.
        "rationalAvatarOrbit02" to { nm, r -> orbitAvatar(nm, r, lines = false) },
        "rationalAvatarOrbit03" to { nm, r -> orbitAvatar(nm, r, rings = 3, lines = false, mark = 1.3f) },
        "rationalAvatarOrbit04" to { nm, r -> orbitAvatar(nm, r, rings = 5, lines = false, mark = 0.8f) },
        "rationalAvatarOrbit05" to { nm, r -> orbitAvatar(nm, r, lines = false, ticks = true) },
    )

    // GART_ONLY=Journey,Kit renders only the makers whose names contain one of those
    // words. Every maker still takes its seed from the master in order, so a subset
    // renders exactly as it does in a full run.
    val only = System.getenv("GART_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
    var rendered = 0
    makers.forEachIndexed { i, (name, make) ->
        val seed = master.nextLong()
        if (only != null && only.none { name.contains(it, ignoreCase = true) }) return@forEachIndexed
        rendered++
        val art = make(name, Random(seed))
        File("$name.svg").writeText(svg(art))
        if (hasMotion(art.shapes)) File("$name-motion.svg").writeText(svg(art, motion = true))

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
    println("done: $rendered svg + png pairs")
}
