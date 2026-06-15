package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.math.*
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── Apollonian gasket ──────────────────────────────────────────────────────────
// Descartes Circle Theorem in its complex form fills every curvilinear triangle with
// its inscribed circle, recursing until radius < ~0.5px. A large negative-curvature
// bounding circle holds three mutually-tangent seeds; each gap spawns the next tangent
// circle (k4 = k1+k2+k3 + 2√(k1k2+k2k3+k3k1), centre via the complex curvature-weighted
// sum). Thousands of nested circles, coloured by log-curvature through Patriot Gold,
// glowing on a deep ground via bloom. Showcases: recursive analytic geometry.

// hand-rolled complex (Double precision)
private class Cx(val re: Double, val im: Double) {
    operator fun plus(o: Cx) = Cx(re + o.re, im + o.im)
    operator fun minus(o: Cx) = Cx(re - o.re, im - o.im)
    operator fun times(o: Cx) = Cx(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun times(s: Double) = Cx(re * s, im * s)
    fun abs() = hypot(re, im)
}

private fun csqrt(z: Cx): Cx {
    val r = z.abs()
    val re = sqrt((r + z.re) / 2.0)
    var im = sqrt((r - z.re) / 2.0)
    if (z.im < 0) im = -im
    return Cx(re, im)
}

// A circle as bend (signed curvature = ±1/r) and bend·centre (curvature-weighted centre).
private class Circ(val bend: Double, val center: Cx) {
    val r: Double get() = abs(1.0 / bend)
}

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("Apollonian", SIZE, SIZE)
    val d = gart.d

    // strokes drawn onto a transparent layer, composited by bloom over a dark ground
    val layer = gart.gartvas()
    val lc = layer.canvas
    lc.clear(0x00000000)

    val ramp = Coolors.patriotGold.expand(256)
    val cx = d.cx.toDouble()
    val cy = d.cy.toDouble()
    val R = SIZE * 0.475                       // bounding radius

    // outer bounding circle: negative curvature (encloses everything)
    val outer = Circ(-1.0 / R, Cx(cx, cy))

    // three mutually-tangent interior seeds inscribed in the bounding circle.
    // For three equal circles tangent to each other and internally tangent to a
    // circle of radius R: inner radius r = R / (1 + 2/√3); centres on a triad.
    val rIn = R / (1.0 + 2.0 / sqrt(3.0))
    val ringR = R - rIn                        // distance of seed centres from centre
    val a0 = rng.rndf(0f, 6.2831855f).toDouble()
    val seeds = ArrayList<Circ>(3)
    for (i in 0 until 3) {
        val ang = a0 + i * (2.0 * Math.PI / 3.0)
        val px = cx + ringR * kotlin.math.cos(ang)
        val py = cy + ringR * kotlin.math.sin(ang)
        seeds.add(Circ(1.0 / rIn, Cx(px, py)))
    }

    // log-curvature range for colour mapping (bend from ~1/R up to ~1/0.5px)
    val minLogK = ln(1.0 / R)
    val maxLogK = ln(1.0 / 0.5)

    var drawn = 0
    val minR = 0.5
    val maxDepth = 5200                        // safety cap on total circles

    fun colorFor(bend: Double): Int {
        val lk = ln(abs(bend)).coerceIn(minLogK, maxLogK)
        val t = ((lk - minLogK) / (maxLogK - minLogK)).toFloat().coerceIn(0f, 1f)
        return ramp.bound(t * (ramp.size - 1))
    }

    fun draw(c: Circ) {
        if (c.bend <= 0) return                // skip the bounding circle itself in fill
        val rr = c.r.toFloat()
        if (rr < 0.4f) return
        val col = colorFor(c.bend)
        // faint fill for body, crisp stroke for the rim
        val w = (rr * 0.10f).coerceIn(0.45f, 3.2f)
        lc.drawCircle(c.center.re.toFloat(), c.center.im.toFloat(), rr,
            fillOf(col).alpha((26 + (110 * (1f / (1f + rr * 0.05f)))).toInt().coerceIn(18, 150)))
        lc.drawCircle(c.center.re.toFloat(), c.center.im.toFloat(), rr,
            strokeOf(col, w).apply { mode = PaintMode.STROKE; isAntiAlias = true }
                .alpha(255))
        drawn++
    }

    // Descartes: given three mutually tangent circles, the fourth bend.
    fun fourthBend(b1: Double, b2: Double, b3: Double): Double {
        val s = b1 * b2 + b2 * b3 + b3 * b1
        return b1 + b2 + b3 + 2.0 * sqrt(abs(s))
    }

    // complex Descartes for the fourth centre (curvature-weighted)
    fun fourthCenter(c1: Circ, c2: Circ, c3: Circ, b4: Double): Cx {
        val z1 = c1.center; val z2 = c2.center; val z3 = c3.center
        val b1 = c1.bend; val b2 = c2.bend; val b3 = c3.bend
        val sum = z1 * b1 + z2 * b2 + z3 * b3
        val inside = (z1 * b1) * (z2 * b2) + (z2 * b2) * (z3 * b3) + (z3 * b3) * (z1 * b1)
        val root = csqrt(inside) * 2.0
        return (sum + root) * (1.0 / b4)
    }

    // recursive gap filling: for each curvilinear triangle between c1,c2,c3 we have
    // already placed c4; recurse into the three new triangles (c1,c2,c4)…
    fun recurse(c1: Circ, c2: Circ, c3: Circ, c4: Circ, depth: Int) {
        if (drawn > maxDepth) return
        if (c4.r < minR) return
        if (depth > 26) return

        // try the two Descartes solutions for the new circle in each sub-gap and keep
        // the one that is the genuinely-new inscribed circle (not c-itself).
        fun next(a: Circ, b: Circ, c: Circ) {
            val b4 = fourthBend(a.bend, b.bend, c.bend)
            if (b4 <= 0 || (1.0 / b4) < minR) return
            val cen = fourthCenter(a, b, c, b4)
            val cand = Circ(b4, cen)
            // reject if it's essentially the same circle we came from
            val dd = (cand.center - c4.center).abs()
            if (dd < cand.r * 0.05 && abs(cand.bend - c4.bend) < 1e-6) return
            // sanity: candidate must sit inside the bounding circle
            val distFromOuter = (cand.center - outer.center).abs()
            if (distFromOuter + cand.r > R + 0.5) return
            draw(cand)
            recurse(a, b, c, cand, depth + 1)
        }
        next(c1, c2, c4)
        next(c2, c3, c4)
        next(c1, c3, c4)
    }

    // seed the gasket: bounding + three seeds → first inner circle
    seeds.forEach { draw(it) }
    val b4 = fourthBend(outer.bend, seeds[0].bend, seeds[1].bend)
    // the inner Descartes companion using all three seeds + outer; the classic central
    // circle is found from the three seeds (positive solution).
    val centralBend = fourthBend(seeds[0].bend, seeds[1].bend, seeds[2].bend)
    val centralCenter = fourthCenter(seeds[0], seeds[1], seeds[2], centralBend)
    val central = Circ(centralBend, centralCenter)
    draw(central)

    // now recurse into every triangle: each pair of seeds + outer, and seeds + central
    recurse(outer, seeds[0], seeds[1], seeds[2], 0)   // gap bounded by outer & two seeds → third seed already there; descend
    recurse(seeds[0], seeds[1], seeds[2], central, 0) // central triad
    // the three lens gaps between the outer ring and each adjacent seed pair
    recurse(outer, seeds[0], seeds[1], central, 0)
    recurse(outer, seeds[1], seeds[2], central, 0)
    recurse(outer, seeds[2], seeds[0], central, 0)
    // also fill the three gaps each seed makes against the outer + its two neighbours
    for (i in 0 until 3) {
        val s = seeds[i]
        val n1 = seeds[(i + 1) % 3]
        val n2 = seeds[(i + 2) % 3]
        recurse(outer, s, n1, central, 0)
        recurse(outer, s, n2, central, 0)
    }

    println("  $drawn circles")

    // draw the bounding rim faintly as a frame
    lc.drawCircle(cx.toFloat(), cy.toFloat(), R.toFloat(),
        strokeOf(ramp.bound(0.05f * (ramp.size - 1)), 2.2f).apply { mode = PaintMode.STROKE }.alpha(90))

    val ground = 0xFF021019.toInt()   // deep space blue ground
    val finalv = bloom(gart, layer.snapshot(), ground, sigma = 7f, grain = 0.06f)
    gart.saveImage(finalv)
}

// kotlin.math.cos/sin used qualified above to avoid extra imports clutter.
