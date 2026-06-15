package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD+ #9 · Mis-registered CMYK halftone ────────────────────────────────────
// A coolors "Sea Blue" gradient separated into C/M/Y/K and screened at the classic
// angles — but each plate's origin is nudged a few pixels, the way a cheap press
// drifts out of registration. The colour fringing IS the aesthetic. Showcases: CMYK
// separation + rotated dot screens with deliberate registration error.
private val ANG = doubleArrayOf(15.0, 75.0, 0.0, 45.0).map { (it * PI / 180.0).toFloat() }

private fun screen(px: Float, py: Float, ang: Float, cell: Float, value: Float): Boolean {
    if (value <= 0.001f) return false
    val xr = px * cos(ang) + py * sin(ang)
    val yr = -px * sin(ang) + py * cos(ang)
    val dot = (cos(xr / cell * TAUf) + 1f) * (cos(yr / cell * TAUf) + 1f) * 0.25f
    return dot < value
}

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("CmykMisregister", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val ramp = Coolors.retroPop.expand(256)   // multi-hue → real colour fringing
    val cell = SIZE / 72f
    val cx = d.cx; val cy = d.cy; val maxR = hypot(cx, cy)

    // per-plate registration offset (pixels) — the drift IS the look
    val off = Array(4) { floatArrayOf(rng.rndf(-10f, 10f), rng.rndf(-10f, 10f)) }
    println("  offsets ${off.joinToString { "(${it[0].toInt()},${it[1].toInt()})" }}")

    fun toneAt(px: Float, py: Float): Int {
        val w = noise.random2D(px * 0.0024f, py * 0.0024f).toFloat()
        val rr = (hypot(px - cx, py - cy) / maxR + 0.18f * w).coerceIn(0f, 1f)
        return ramp.bound(rr * (ramp.size - 1))
    }

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val pf = px.toFloat(); val pjf = py.toFloat()
            // sample each plate at its OWN offset position → the colour drifts apart
            val cyanS = toneAt(pf + off[0][0], pjf + off[0][1])
            val magS = toneAt(pf + off[1][0], pjf + off[1][1])
            val yelS = toneAt(pf + off[2][0], pjf + off[2][1])
            val keyS = toneAt(pf + off[3][0], pjf + off[3][1])

            val dc = screen(pf, pjf, ANG[0], cell, cmyk(cyanS, 0))
            val dm = screen(pf, pjf, ANG[1], cell, cmyk(magS, 1))
            val dy = screen(pf, pjf, ANG[2], cell, cmyk(yelS, 2))
            val dk = screen(pf, pjf, ANG[3], cell, cmyk(keyS, 3))

            var or = 1f; var og = 1f; var ob = 1f
            if (dc) or *= 0.18f
            if (dm) og *= 0.18f
            if (dy) ob *= 0.18f
            if (dk) { or *= 0.12f; og *= 0.12f; ob *= 0.12f }
            gm[px, py] = argb(255, (or * 255).toInt().coerceIn(0, 255),
                (og * 255).toInt().coerceIn(0, 255), (ob * 255).toInt().coerceIn(0, 255))
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.04f)
    gart.saveImage(finalv)
}

/** channel of the CMYK decomposition of an ARGB colour. 0=C 1=M 2=Y 3=K */
private fun cmyk(color: Int, ch: Int): Float {
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    val k = 1f - max(r, max(g, b))
    if (ch == 3) return k
    val ik = (1f - k).coerceAtLeast(1e-4f)
    return when (ch) {
        0 -> (1f - r - k) / ik
        1 -> (1f - g - k) / ik
        else -> (1f - b - k) / ik
    }
}
