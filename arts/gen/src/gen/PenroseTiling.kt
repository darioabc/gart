package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.Palette
import dev.oblac.gart.color.lerpColor
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.gfx.closedPathOf
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.pathOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Point

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM · Penrose P3 rhombus tiling by deflation ────────────────────────────
// The aperiodic Penrose P3 tiling built the classic way: subdivide Robinson
// triangles. Each half-rhombus triangle is type 0 (acute / "thin" half) or type 1
// (obtuse / "thick" half) with three vertices. We seed a fivefold wheel of ~10
// triangles around the centre, then apply the golden-ratio deflation rule 6–7 times
// (each triangle splits into 2–3 children at ratio 1/φ). Final triangles are paired
// back into rhombi, filled from "Navy & Gold" — the two rhombus types get distinct
// gold/blue tones, varied by distance from centre — over a dark navy ground with thin
// gold edge strokes so the fivefold aperiodic structure glows. Bloom finish.

private const val PHI = 1.618033988749895
private val INV_PHI = (1.0 / PHI)

// A Robinson half-triangle. type 0 = thin (acute 36°), type 1 = thick (obtuse 36°).
// Vertices a (apex), b, c following the standard de Bruijn / Bartholdi construction.
private class Tri(val type: Int, val a: Cpx, val b: Cpx, val c: Cpx)

private class Cpx(val re: Double, val im: Double) {
    operator fun plus(o: Cpx) = Cpx(re + o.re, im + o.im)
    operator fun minus(o: Cpx) = Cpx(re - o.re, im - o.im)
    operator fun times(s: Double) = Cpx(re * s, im * s)
}

// linear interpolation a + (b-a)*t in the complex plane
private fun lerpC(a: Cpx, b: Cpx, t: Double) = a + (b - a) * t

private fun subdivide(tris: List<Tri>): List<Tri> {
    val out = ArrayList<Tri>(tris.size * 3)
    for (tr in tris) {
        val a = tr.a; val b = tr.b; val c = tr.c
        if (tr.type == 0) {
            // thin (acute) → 2 children
            val p = lerpC(a, b, INV_PHI)
            out.add(Tri(0, c, p, b))
            out.add(Tri(1, p, c, a))
        } else {
            // thick (obtuse) → 3 children
            val q = lerpC(b, a, INV_PHI)
            val r = lerpC(b, c, INV_PHI)
            out.add(Tri(1, r, c, a))
            out.add(Tri(1, q, r, b))
            out.add(Tri(0, r, q, a))
        }
    }
    return out
}

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("PenroseTiling", SIZE, SIZE)
    val d = gart.d
    val palette: Palette = Coolors.navyGold

    val ground = 0xFF000814.toInt()

    // tones for the two rhombus types, lightened toward gold near centre
    val thinNear = 0xFFFFD60A.toInt()   // bright gold
    val thinFar = 0xFF6E5A12.toInt()    // muted gold
    val thickNear = 0xFF1F6FB8.toInt()  // luminous blue
    val thickFar = 0xFF001D3D.toInt()   // deep navy
    val edgeCol = 0xFFFFC300.toInt()    // gold edge stroke

    // ── seed wheel: 10 thick triangles around centre ──────────────────────────
    val cx = d.cx.toDouble(); val cy = d.cy.toDouble()
    val R = SIZE * 0.72  // generous so the pattern overfills the frame
    val centre = Cpx(cx, cy)
    var tris = ArrayList<Tri>()
    for (i in 0 until 10) {
        var b = Cpx(cos((2 * i - 1) * Math.PI / 10), sin((2 * i - 1) * Math.PI / 10)) * R + centre
        var cc = Cpx(cos((2 * i + 1) * Math.PI / 10), sin((2 * i + 1) * Math.PI / 10)) * R + centre
        if (i % 2 == 0) { val t = b; b = cc; cc = t } // mirror alternate wedges
        tris.add(Tri(1, centre, b, cc))
    }

    // ── deflate ────────────────────────────────────────────────────────────────
    val generations = 7
    var cur: List<Tri> = tris
    repeat(generations) { cur = subdivide(cur) }
    println("  ${cur.size} half-triangles after $generations deflations")

    // ── render ────────────────────────────────────────────────────────────────
    val buf = gart.gartvas()
    val cnv = buf.canvas
    cnv.clear(ground)

    val maxDist = SIZE * 0.62f
    fun pt(z: Cpx) = Point(z.re.toFloat(), z.im.toFloat())

    val edge = strokeOf(edgeCol, (SIZE * 0.0011f).coerceAtLeast(0.6f)).apply {
        mode = PaintMode.STROKE; isAntiAlias = true
    }.alpha(150)

    // draw each half-triangle as a filled triangle; pairing happens visually because
    // matched halves share the long edge and the same type colour → seamless rhombi.
    for (tr in cur) {
        // rhombus centroid distance from canvas centre drives the tone
        val mx = ((tr.a.re + tr.b.re + tr.c.re) / 3.0).toFloat()
        val my = ((tr.a.im + tr.b.im + tr.c.im) / 3.0).toFloat()
        val dist = hypot(mx - d.cx, my - d.cy)
        val t = (dist / maxDist).coerceIn(0f, 1f)
        val jitter = (rng.nextFloat() - 0.5f) * 0.10f
        val tt = (t + jitter).coerceIn(0f, 1f)
        val fill = if (tr.type == 0) lerpColor(thinNear, thinFar, tt)
                   else lerpColor(thickNear, thickFar, tt)

        val path = closedPathOf(listOf(pt(tr.a), pt(tr.b), pt(tr.c)))
        cnv.drawPath(path, fillOf(fill).apply { isAntiAlias = true })
    }

    // gold edges drawn on top: only the two short rhombus edges (a-b and a-c) so the
    // internal long diagonals between paired halves stay invisible → clean rhombi.
    for (tr in cur) {
        val path = pathOf(listOf(pt(tr.b), pt(tr.a), pt(tr.c)))
        cnv.drawPath(path, edge)
    }

    val finalv = bloom(gart, buf.snapshot(), ground, SIZE * 0.004f, grain = 0.05f)
    gart.saveImage(finalv)
    println("  Penrose done")
}
