package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.dither.ditherAtkinson
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD+ #3 · Atkinson 1-bit dither, duotone ──────────────────────────────────
// A high-contrast ridged-noise + metaball scene reduced to pure 1-bit by the
// Atkinson kernel (the sparse, airy dither of the original Macintosh). Then the two
// levels are recoloured into the coolors "Molten" duotone. Showcases: dither engine
// (Atkinson) — only ~3/4 of the error propagates, so highlights blow clean to paper.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("AtkinsonInk", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)

    val n = rng.rndi(2, 4)
    val bx = FloatArray(n) { rng.rndf(0.30f, 0.70f) * d.wf }
    val by = FloatArray(n) { rng.rndf(0.30f, 0.70f) * d.hf }
    val br = FloatArray(n) { rng.rndf(0.09f, 0.17f) * SIZE }

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            // smooth Gaussian lumps → broad soft tones Atkinson can render as gradients
            var blob = 0f
            for (i in 0 until n) {
                val dx = px - bx[i]; val dy = py - by[i]
                val s = br[i]
                blob += exp(-(dx * dx + dy * dy) / (2f * s * s))
            }
            blob = blob.coerceIn(0f, 1f)
            // ridged multi-octave noise as surface texture (centred around 0)
            var ridge = 0f; var amp = 0.6f; var f = 0.004f
            repeat(4) {
                ridge += amp * (1f - abs(noise.random2D(px * f, py * f).toFloat()))
                amp *= 0.5f; f *= 2f
            }
            val darkness = (blob * 0.95f + (ridge - 0.62f) * 0.42f).coerceIn(0f, 1f)
            val g = ((1f - darkness) * 255).toInt().coerceIn(0, 255)  // dense → dark ink
            gm[px, py] = argb(255, g, g, g)
        }
    }

    ditherAtkinson(gm, pixelSize = 1, colorCount = 2)

    // recolour the 1-bit result into a Molten duotone
    val dark = 0xFF780000.toInt(); val light = 0xFFFDF0D5.toInt()
    for (i in gm.pixels.indices) {
        gm.pixels[i] = if ((gm.pixels[i] and 0xFF) < 128) dark else light
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.05f)
    gart.saveImage(finalv)
    println("  done ($n blobs, Atkinson 1-bit)")
}
