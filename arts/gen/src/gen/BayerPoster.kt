package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.dither.ditherOrdered8By8Bayer
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD+ #2 · Ordered Bayer-screen poster ─────────────────────────────────────
// A smooth diagonal gradient warped by low-frequency simplex is painted across the
// coolors "Ink & Ember" ramp, then crushed by an 8×8 ordered Bayer matrix at a tiny
// colour count — the rigid woven crosshatch of vintage screen-print. Showcases:
// dither engine (ordered Bayer, the opposite texture to Risodream's error diffusion).
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("BayerPoster", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val ramp = Coolors.inkEmber.expand(256)

    val theta = rng.rndf(0f, TAUf)
    val ca = cos(theta); val sa = sin(theta)

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        val ny = py.toFloat() / d.h - 0.5f
        for (px in 0 until d.w) {
            val nx = px.toFloat() / d.w - 0.5f
            val proj = (nx * ca + ny * sa) + 0.5f                 // diagonal gradient
            val warp = noise.random2D(px * 0.0018f, py * 0.0018f).toFloat()
            val t = (proj + 0.35f * warp).coerceIn(0f, 1f)
            gm[px, py] = ramp.bound(t * (ramp.size - 1))
        }
    }

    ditherOrdered8By8Bayer(gm, pixelSize = 1, colorCount = 4)

    gart.saveImage(gm.image())
    println("  done (Bayer 8x8, 4 levels)")
}
