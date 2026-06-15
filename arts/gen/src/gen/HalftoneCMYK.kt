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

// ── WILD #9 · Process-colour halftone of a noise-warped sunset ─────────────────
// A smooth field (coolors "Sunset", warped by simplex) is separated into C/M/Y/K and
// each channel re-screened through its own rotated dot grid at the classic print
// angles (15° / 75° / 0° / 45°), then recombined subtractively. Up close: rosettes
// of dots; from afar: the image. Showcases: CMYK separation + screening, from scratch.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("HalftoneCMYK", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val ramp = Coolors.sunset.expand(256)

    val cell = SIZE / 72f                          // dot pitch (chunky, visible rosettes)
    val ang = doubleArrayOf(15.0, 75.0, 0.0, 45.0).map { it * PI / 180.0 }
    val warpAmp = rng.rndf(0.10f, 0.22f)
    val cx = d.cx; val cy = d.cy
    val maxR = hypot(cx, cy)

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            // underlying continuous-tone colour
            val w1 = noise.random2D(px * 0.0022f, py * 0.0022f).toFloat()
            val w2 = noise.random2D(px * 0.006f + 99f, py * 0.006f).toFloat()
            val rr = (hypot(px - cx, py - cy) / maxR + warpAmp * w1).coerceIn(0f, 1f)
            val base = ramp.bound(((rr * 0.85f + 0.15f * (w2 * 0.5f + 0.5f)).coerceIn(0f, 1f)) * (ramp.size - 1))

            val r = ((base shr 16) and 0xFF) / 255f
            val g = ((base shr 8) and 0xFF) / 255f
            val b = (base and 0xFF) / 255f
            // RGB → CMYK
            val k = 1f - max(r, max(g, b))
            val ik = (1f - k).coerceAtLeast(1e-4f)
            val c = (1f - r - k) / ik
            val m = (1f - g - k) / ik
            val ye = (1f - b - k) / ik

            // screen each channel through its rotated dot grid
            val dc = screen(px, py, ang[0], cell, c)
            val dm = screen(px, py, ang[1], cell, m)
            val dy = screen(px, py, ang[2], cell, ye)
            val dk = screen(px, py, ang[3], cell, k)

            // subtractive recombination on white paper
            // clean single-channel subtractive model → vivid primaries & secondaries
            var or = 1f; var og = 1f; var ob = 1f
            if (dc) or *= 0.18f                                     // cyan absorbs red
            if (dm) og *= 0.18f                                     // magenta absorbs green
            if (dy) ob *= 0.18f                                     // yellow absorbs blue
            if (dk) { or *= 0.12f; og *= 0.12f; ob *= 0.12f }       // key/black

            gm[px, py] = argb(255, (or * 255).toInt().coerceIn(0, 255),
                (og * 255).toInt().coerceIn(0, 255), (ob * 255).toInt().coerceIn(0, 255))
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.04f)
    gart.saveImage(finalv)
}

/** Rotated halftone dot screen: returns true where ink should be laid for [value] (0..1). */
private fun screen(px: Int, py: Int, ang: Double, cell: Float, value: Float): Boolean {
    if (value <= 0.001f) return false
    val ca = cos(ang).toFloat(); val sa = sin(ang).toFloat()
    val xr = px * ca + py * sa
    val yr = -px * sa + py * ca
    val sx = (xr / cell) * TAUf
    val sy = (yr / cell) * TAUf
    // dot amplitude: 1 at cell centre, 0 at corners
    val dot = (cos(sx) + 1f) * (cos(sy) + 1f) * 0.25f
    return dot < value
}
