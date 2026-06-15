package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.math.*
import kotlin.math.floor
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD #7 · Gray–Scott reaction–diffusion, contoured ─────────────────────────
// The classic two-chemical Gray–Scott system stepped a few thousand times from a
// random seeding, then tone-mapped through the coolors "Teal Rose" ramp with dark
// iso-contour lines etched where V crosses level sets — coral / fingerprint growth.
// Showcases: reaction–diffusion done from first principles + Palette + grain.
private const val GRID = 240

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("GrayScottCoral", SIZE, SIZE)
    val d = gart.d

    val n = GRID * GRID
    val u = FloatArray(n) { 1f }
    val v = FloatArray(n) { 0f }

    // seed a few random patches of V
    repeat(rng.rndi(8, 18)) {
        val sx = rng.rndi(10, GRID - 10); val sy = rng.rndi(10, GRID - 10)
        val rad = rng.rndi(3, 9)
        for (yy in -rad..rad) for (xx in -rad..rad) {
            val gx = sx + xx; val gy = sy + yy
            if (gx in 0 until GRID && gy in 0 until GRID && xx * xx + yy * yy <= rad * rad) {
                v[gy * GRID + gx] = 1f
            }
        }
    }

    // a regime that gives coral / mitosis structure
    val feed = rng.rndf(0.034f, 0.042f)
    val kill = rng.rndf(0.058f, 0.063f)
    val du = 0.16f; val dv = 0.08f
    val steps = 4500
    println("  feed=$feed kill=$kill steps=$steps")

    val u2 = FloatArray(n); val v2 = FloatArray(n)
    repeat(steps) {
        for (y in 0 until GRID) {
            val ym = (y - 1 + GRID) % GRID; val yp = (y + 1) % GRID
            for (x in 0 until GRID) {
                val xm = (x - 1 + GRID) % GRID; val xp = (x + 1) % GRID
                val i = y * GRID + x
                val uu = u[i]; val vv = v[i]
                // 4-neighbour laplacian (wrap)
                val lapU = u[y * GRID + xm] + u[y * GRID + xp] + u[ym * GRID + x] + u[yp * GRID + x] - 4f * uu
                val lapV = v[y * GRID + xm] + v[y * GRID + xp] + v[ym * GRID + x] + v[yp * GRID + x] - 4f * vv
                val uvv = uu * vv * vv
                u2[i] = (uu + du * lapU - uvv + feed * (1f - uu)).coerceIn(0f, 1f)
                v2[i] = (vv + dv * lapV + uvv - (kill + feed) * vv).coerceIn(0f, 1f)
            }
        }
        System.arraycopy(u2, 0, u, 0, n)
        System.arraycopy(v2, 0, v, 0, n)
    }

    // render: bilinear sample V across the canvas, colour + iso-contours
    val ramp = Coolors.tealRose.expand(256)
    val levels = 7
    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        val gy = py.toFloat() / d.h * (GRID - 1)
        for (px in 0 until d.w) {
            val gx = px.toFloat() / d.w * (GRID - 1)
            val vv = sampleV(v, gx, gy)
            var col = ramp.bound((vv.coerceIn(0f, 1f) * (ramp.size - 1)))
            // contour: darken where the level band changes vs the right/below sample
            val band = floor(vv * levels)
            val bandR = floor(sampleV(v, (gx + 1.2f).coerceAtMost(GRID - 1f), gy) * levels)
            val bandD = floor(sampleV(v, gx, (gy + 1.2f).coerceAtMost(GRID - 1f)) * levels)
            if (band != bandR || band != bandD) col = darken(col, 0.35f)
            gm[px, py] = col
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.05f)
    gart.saveImage(finalv)
}

private fun sampleV(v: FloatArray, gx: Float, gy: Float): Float {
    val x0 = gx.toInt().coerceIn(0, GRID - 1); val x1 = (x0 + 1).coerceAtMost(GRID - 1)
    val y0 = gy.toInt().coerceIn(0, GRID - 1); val y1 = (y0 + 1).coerceAtMost(GRID - 1)
    val fx = gx - x0; val fy = gy - y0
    val a = v[y0 * GRID + x0]; val b = v[y0 * GRID + x1]
    val c = v[y1 * GRID + x0]; val e = v[y1 * GRID + x1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + e * fx) * fy
}
