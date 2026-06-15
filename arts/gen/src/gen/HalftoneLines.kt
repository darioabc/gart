package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD+ #4 · Line-screen halftone engraving ──────────────────────────────────
// Instead of dots, three inks are screened as rotated LINE gratings whose duty cycle
// tracks tone — the look of banknote / stamp engraving. Inks come from coolors
// "Patriot Gold" (red / deep-blue / gold) over vanilla paper. Showcases: line-screen
// halftone built from trig, per-pixel Gartmap.
private class Ink(val angle: Float, val color: Int)

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("HalftoneLines", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)

    val pitch = SIZE / 95f
    val inks = arrayOf(
        Ink((15 + rng.rndf(-6f, 6f)) * (PI / 180).toFloat(), 0xFFD62828.toInt()),
        Ink((75 + rng.rndf(-6f, 6f)) * (PI / 180).toFloat(), 0xFF003049.toInt()),
        Ink((45 + rng.rndf(-6f, 6f)) * (PI / 180).toFloat(), 0xFFF77F00.toInt())
    )
    val paper = 0xFFEAE2B7.toInt()
    val maxR = hypot(d.cx, d.cy)
    // each plate gets its OWN tonal centre so the screens don't all pile up dark in the middle
    val ckx = FloatArray(inks.size) { d.cx + rng.rndf(-0.22f, 0.22f) * SIZE }
    val cky = FloatArray(inks.size) { d.cy + rng.rndf(-0.22f, 0.22f) * SIZE }

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            // tone per ink: a smooth, separately-warped field so the three plates differ
            var or = 1f; var og = 1f; var ob = 1f
            for (k in inks.indices) {
                val ink = inks[k]
                val warp = noise.random2D(px * 0.0019f + k * 40f, py * 0.0019f).toFloat()
                val radial = 1f - (hypot(px - ckx[k], py - cky[k]) / maxR)
                // each plate inks only near its own core → the three colours stay separate
                val tone = (radial * 1.25f + 0.35f * warp - 0.5f).coerceIn(0f, 1f)
                // rotated line screen: ink laid where the grating phase is below tone
                val u = px * cos(ink.angle) + py * sin(ink.angle)
                val line = (sin(u / pitch * TAUf) + 1f) * 0.5f
                if (line < tone) {
                    or *= (1f - 0.7f * (1f - ((ink.color shr 16 and 0xFF) / 255f)))
                    og *= (1f - 0.7f * (1f - ((ink.color shr 8 and 0xFF) / 255f)))
                    ob *= (1f - 0.7f * (1f - ((ink.color and 0xFF) / 255f)))
                }
            }
            val pr = ((paper shr 16 and 0xFF) / 255f)
            val pg = ((paper shr 8 and 0xFF) / 255f)
            val pb = ((paper and 0xFF) / 255f)
            gm[px, py] = argb(255,
                (or * pr * 255).toInt().coerceIn(0, 255),
                (og * pg * 255).toInt().coerceIn(0, 255),
                (ob * pb * 255).toInt().coerceIn(0, 255))
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.05f)
    gart.saveImage(finalv)
    println("  done (line screen, pitch=$pitch)")
}
