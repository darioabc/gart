package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.dither.ditherFloydSteinberg
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.hypot
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD #3 · Risograph duotone via error-diffusion dithering ──────────────────
// A smooth metaball + simplex field is painted as a continuous ramp across the
// coolors "Ink & Ember" palette, then crushed through Floyd–Steinberg dithering at
// a tiny colour count. Error diffusion turns the smooth gradient into the gritty,
// mis-registered grain of a riso print. Showcases: dither engine (23 algorithms).
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("Risodream", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val ramp = Coolors.inkEmber.expand(256)

    // A handful of soft "charge" centres → a metaball potential field.
    val n = rng.rndi(4, 8)
    val cx = FloatArray(n) { rng.rndf(0.1f, 0.9f) * d.wf }
    val cy = FloatArray(n) { rng.rndf(0.1f, 0.9f) * d.hf }
    val cr = FloatArray(n) { rng.rndf(0.12f, 0.34f) * SIZE }
    val sign = FloatArray(n) { if (rng.rndb()) 1f else -1f }

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            var field = 0f
            for (i in 0 until n) {
                val dist = hypot(px - cx[i], py - cy[i]) + 1f
                field += sign[i] * (cr[i] * cr[i]) / (dist * dist)
            }
            // warp with low-frequency simplex for organic banding
            val warp = noise.random2D(px * 0.0016f, py * 0.0016f)
            var t = (field * 0.5f + warp * 0.6f) * 0.5f + 0.5f
            t = t.coerceIn(0f, 1f)
            gm[px, py] = ramp.bound(t * (ramp.size - 1))
        }
    }

    // Crush to a riso-grade tonal grain: few levels per channel, error-diffused.
    ditherFloydSteinberg(gm, pixelSize = 1, colorCount = 3)

    gart.saveImage(gm.image())
    println("  done ($n charges, dithered to 3 levels)")
}
