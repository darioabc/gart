package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.dither.ditherBlueNoise
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.hypot
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD+ #8 · Blue-noise duotone ──────────────────────────────────────────────
// A soft metaball field thresholded with a void-and-cluster BLUE-NOISE mask — the
// most pleasing halftone there is: no worms, no banding, just an even organic grain.
// Recoloured into the coolors "Teal Rose" duotone. Showcases: dither/BlueNoise
// (void-and-cluster threshold map).
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("BlueNoiseDuotone", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)

    val n = rng.rndi(3, 6)
    val bx = FloatArray(n) { rng.rndf(0.2f, 0.8f) * d.wf }
    val by = FloatArray(n) { rng.rndf(0.2f, 0.8f) * d.hf }
    val br = FloatArray(n) { rng.rndf(0.18f, 0.36f) * SIZE }

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            var blob = 0f
            for (i in 0 until n) {
                val dist = hypot(px - bx[i], py - by[i]) + 1f
                blob += (br[i] * br[i]) / (dist * dist)
            }
            val warp = noise.random2D(px * 0.0014f, py * 0.0014f).toFloat()
            val v = (blob * 0.45f + 0.3f * warp + 0.1f).coerceIn(0f, 1f)
            val g = (v * 255).toInt().coerceIn(0, 255)
            gm[px, py] = argb(255, g, g, g)
        }
    }

    ditherBlueNoise(gm, pixelSize = 1, colorCount = 2, noiseWidth = 96, noiseHeight = 96)

    val dark = 0xFF006D77.toInt(); val light = 0xFFFFDDD2.toInt()
    for (i in gm.pixels.indices) {
        gm.pixels[i] = if ((gm.pixels[i] and 0xFF) < 128) dark else light
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.04f)
    gart.saveImage(finalv)
    println("  done ($n blobs, blue-noise duotone)")
}
