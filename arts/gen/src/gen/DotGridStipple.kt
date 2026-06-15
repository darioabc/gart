package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import dev.oblac.gart.stipple.stippleDots
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD+ #1 · Newspaper dot-grid halftone of a swirl ──────────────────────────
// A spiral interference field rasterised to a tone map, then gȧrt's grid stippler
// drops one dot per cell sized by local darkness — classic newsprint screen. Duotone
// ink: Prussian blue dots on pale gold (coolors "Navy & Gold"). Showcases: stippleDots.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("DotGridStipple", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)

    val arms = rng.rndi(3, 9)
    val twist = rng.rndf(0.03f, 0.08f)
    val cx = d.cx; val cy = d.cy

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val dx = px - cx; val dy = py - cy
            val r = hypot(dx, dy)
            val ang = atan2(dy, dx)
            val n = noise.random2D(px * 0.0022f, py * 0.0022f).toFloat()
            val v = 0.5f + 0.5f * sin(r * twist - ang * arms + n * 3f)
            val g = (v * 255).toInt().coerceIn(0, 255)
            gm[px, py] = argb(255, g, g, g)
        }
    }

    val pitch = (SIZE / 70).coerceAtLeast(7)
    stippleDots(
        gm,
        dotSize = pitch,
        gap = 0.4f,
        foreground = 0xFF001D3D.toInt(),   // Prussian blue ink
        background = 0xFFFCEFB4.toInt()     // pale gold paper
    )

    val finalv = grainOnly(gart, gm.image(), grain = 0.05f)
    gart.saveImage(finalv)
    println("  done ($arms arms, pitch=$pitch)")
}
