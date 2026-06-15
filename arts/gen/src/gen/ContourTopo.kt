package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.floor
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD+ #10 · Topographic contour map ────────────────────────────────────────
// A multi-octave simplex height field rendered as a printed relief map: elevation
// bands filled from the coolors "Pastel Dream" ramp, with thin darker contour lines
// etched wherever the height crosses a level — the hypsometric tint of an atlas.
// Showcases: fractal-noise elevation + hand-traced iso-contours (marching-squares feel).
private const val LEVELS = 16

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("ContourTopo", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val ramp = Coolors.pastelDream.expand(256)

    val baseF = rng.rndf(0.0016f, 0.0028f)
    val ox = rng.rndf(0f, 1000f); val oy = rng.rndf(0f, 1000f)

    fun height(px: Float, py: Float): Float {
        var h = 0f; var amp = 0.55f; var f = baseF
        repeat(6) {
            h += amp * noise.random2D((px + ox) * f, (py + oy) * f).toFloat()
            amp *= 0.5f; f *= 2f
        }
        return (h * 0.5f + 0.5f).coerceIn(0f, 1f)
    }

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val h = height(px.toFloat(), py.toFloat())
            var col = ramp.bound(h * (ramp.size - 1))
            // contour line: this pixel's band differs from neighbour to the right/below
            val band = floor(h * LEVELS)
            val bandR = floor(height(px + 1.3f, py.toFloat()) * LEVELS)
            val bandD = floor(height(px.toFloat(), py + 1.3f) * LEVELS)
            if (band != bandR || band != bandD) col = darken(col, 0.55f)
            // every 4th level: a heavier index contour
            if (band.toInt() % 4 == 0 && (band != bandR || band != bandD)) col = darken(col, 0.35f)
            gm[px, py] = col
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.05f)
    gart.saveImage(finalv)
    println("  done ($LEVELS levels)")
}
