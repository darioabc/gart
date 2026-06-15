package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.math.*
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── Gray–Scott coral grown inside a crescent-moon silhouette ────────────────────
// A two-chemical reaction–diffusion system is stepped only INSIDE a bold parametric
// mask (a crescent moon = big disc minus an offset disc). Chemicals seed, react and
// diffuse within the mask and are clamped to zero outside, so living coral texture
// fills a real subject. V is tone-mapped through the coolors "Ink & Ember" ramp with
// thin dark iso-contours; outside the mask stays clean paper with negative space.
// Showcases: reaction–diffusion shaped into a composed silhouette.
private const val GRID = 260

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("CoralMask", SIZE, SIZE)
    val d = gart.d

    // ── crescent-moon mask on normalised grid coords (0..1) ────────────────────
    // outer disc centred slightly left; bite taken by an offset disc to the right.
    val cxA = 0.46f; val cyA = 0.50f; val rA = 0.34f
    val cxB = 0.62f; val cyB = 0.44f; val rB = 0.30f
    fun insideMask(gx: Int, gy: Int): Boolean {
        val nx = gx.toFloat() / (GRID - 1)
        val ny = gy.toFloat() / (GRID - 1)
        val inA = (nx - cxA) * (nx - cxA) + (ny - cyA) * (ny - cyA) <= rA * rA
        val outB = (nx - cxB) * (nx - cxB) + (ny - cyB) * (ny - cyB) >= rB * rB
        return inA && outB
    }

    val n = GRID * GRID
    val mask = BooleanArray(n) { i -> insideMask(i % GRID, i / GRID) }

    val u = FloatArray(n) { 1f }
    val v = FloatArray(n) { 0f }

    // seed V patches, but only inside the mask
    var seeded = 0
    var attempts = 0
    val wantSeeds = rng.rndi(14, 26)
    while (seeded < wantSeeds && attempts < 2000) {
        attempts++
        val sx = rng.rndi(6, GRID - 6); val sy = rng.rndi(6, GRID - 6)
        if (!mask[sy * GRID + sx]) continue
        val rad = rng.rndi(2, 6)
        for (yy in -rad..rad) for (xx in -rad..rad) {
            val gx = sx + xx; val gy = sy + yy
            if (gx in 0 until GRID && gy in 0 until GRID &&
                xx * xx + yy * yy <= rad * rad && mask[gy * GRID + gx]
            ) {
                v[gy * GRID + gx] = 1f
            }
        }
        seeded++
    }

    // coral / mitosis regime
    val feed = rng.rndf(0.034f, 0.040f)
    val kill = rng.rndf(0.059f, 0.063f)
    val du = 0.16f; val dv = 0.08f
    val steps = 4200
    println("  feed=$feed kill=$kill steps=$steps seeds=$seeded")

    val u2 = FloatArray(n); val v2 = FloatArray(n)
    repeat(steps) {
        for (y in 0 until GRID) {
            val ym = (y - 1 + GRID) % GRID; val yp = (y + 1) % GRID
            for (x in 0 until GRID) {
                val i = y * GRID + x
                if (!mask[i]) { u2[i] = 1f; v2[i] = 0f; continue }
                val xm = (x - 1 + GRID) % GRID; val xp = (x + 1) % GRID
                val uu = u[i]; val vv = v[i]
                // Neumann-style laplacian: neighbours outside the mask reflect self
                fun us(idx: Int) = if (mask[idx]) u[idx] else uu
                fun vs(idx: Int) = if (mask[idx]) v[idx] else vv
                val lapU = us(y * GRID + xm) + us(y * GRID + xp) + us(ym * GRID + x) + us(yp * GRID + x) - 4f * uu
                val lapV = vs(y * GRID + xm) + vs(y * GRID + xp) + vs(ym * GRID + x) + vs(yp * GRID + x) - 4f * vv
                val uvv = uu * vv * vv
                u2[i] = (uu + du * lapU - uvv + feed * (1f - uu)).coerceIn(0f, 1f)
                v2[i] = (vv + dv * lapV + uvv - (kill + feed) * vv).coerceIn(0f, 1f)
            }
        }
        System.arraycopy(u2, 0, u, 0, n)
        System.arraycopy(v2, 0, v, 0, n)
    }

    // ── render: clean paper ground, coral only inside the silhouette ───────────
    val ramp = Coolors.inkEmber.expand(256)
    val paper = 0xFFF4ECDD.toInt()
    val levels = 8
    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        val gyf = py.toFloat() / d.h * (GRID - 1)
        for (px in 0 until d.w) {
            val gxf = px.toFloat() / d.w * (GRID - 1)
            // mask test at this canvas point (nearest grid cell)
            val mgx = gxf.toInt().coerceIn(0, GRID - 1)
            val mgy = gyf.toInt().coerceIn(0, GRID - 1)
            if (!mask[mgy * GRID + mgx]) { gm[px, py] = paper; continue }

            val vv = sampleMasked(v, mask, gxf, gyf)
            var col = ramp.bound((vv.coerceIn(0f, 1f) * (ramp.size - 1)))
            // thin iso-contours where the level band changes
            val band = floor(vv * levels)
            val bandR = floor(sampleMasked(v, mask, (gxf + 1.2f).coerceAtMost(GRID - 1f), gyf) * levels)
            val bandD = floor(sampleMasked(v, mask, gxf, (gyf + 1.2f).coerceAtMost(GRID - 1f)) * levels)
            if (band != bandR || band != bandD) col = darken(col, 0.30f)
            gm[px, py] = col
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.05f)
    gart.saveImage(finalv)
}

// bilinear V sample that ignores out-of-mask cells (treats them as 0 so the coral
// edge meets the silhouette cleanly)
private fun sampleMasked(v: FloatArray, mask: BooleanArray, gx: Float, gy: Float): Float {
    val x0 = gx.toInt().coerceIn(0, GRID - 1); val x1 = (x0 + 1).coerceAtMost(GRID - 1)
    val y0 = gy.toInt().coerceIn(0, GRID - 1); val y1 = (y0 + 1).coerceAtMost(GRID - 1)
    val fx = gx - x0; val fy = gy - y0
    fun s(xx: Int, yy: Int): Float { val i = yy * GRID + xx; return if (mask[i]) v[i] else 0f }
    val a = s(x0, y0); val b = s(x1, y0)
    val c = s(x0, y1); val e = s(x1, y1)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + e * fx) * fy
}
