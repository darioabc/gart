package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import dev.oblac.gart.pixels.pixelSorter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD+ #5 · Pixel-sort glitch over a plasma ─────────────────────────────────
// A vivid composite-sine plasma in the coolors "Retro Pop" inks, then gȧrt's pixel
// sorter drags every bright horizontal run into sorted streaks — the data-mosh smear
// of glitch art. Showcases: pixels/pixelSorting on a procedurally generated field.
private fun lum(c: Int): Int {
    val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
    return (0.299f * r + 0.587f * g + 0.114f * b).toInt()
}

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("PixelSortFlow", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val ramp = Coolors.retroPop.expand(256)

    val f1 = rng.rndf(3f, 7f); val f2 = rng.rndf(3f, 7f); val f3 = rng.rndf(2f, 5f)
    val p1 = rng.rndf(0f, TAUf); val p2 = rng.rndf(0f, TAUf)

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        val ny = py.toFloat() / d.h
        for (px in 0 until d.w) {
            val nx = px.toFloat() / d.w
            val warp = noise.random2D(px * 0.003f, py * 0.003f).toFloat()
            val grain = noise.random2D(px * 0.05f, py * 0.05f).toFloat()  // high-freq → sorts into streaks
            val v = (sin(nx * f1 * TAUf + p1) +
                     cos(ny * f2 * TAUf + p2) +
                     sin((nx + ny) * f3 * TAUf + warp * 3f)) / 3f
            val t = (v * 0.5f + 0.5f + 0.18f * grain).coerceIn(0f, 1f)
            gm[px, py] = ramp.bound(t * (ramp.size - 1))
        }
    }

    // sort the brightest runs → horizontal glitch streaks
    val threshold = rng.rndi(70, 105)
    pixelSorter(gm, threshold) { c -> lum(c) }

    gart.saveImage(gm.image())
    println("  done (sort threshold=$threshold)")
}
