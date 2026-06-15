package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import dev.oblac.gart.stipple.stippleVoronoi
import kotlin.math.*
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val gart = Gart.of("VoronoiWave", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val cx = d.cx; val cy = d.cy

    // ── Rolling wave field · stippled swell ────────────────────────────────────────
    // A travelling sine swell warped by low-freq simplex makes a corrugated height
    // field; shading the slope (the partial derivative along y) lights crests bright
    // and troughs dark, so dots pool in the troughs like a pointillist seascape.
    val src = Gartmap(gart.gartvas())
    val freq = 5.5f                                   // number of swells across canvas
    val phase = noise.random3D(3.0, 9.0, 1.0).toFloat() * (2f * PI.toFloat())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val u = px / SIZE.toFloat()
            val v = py / SIZE.toFloat()
            // warp the wave horizontally with slow noise so crests meander
            val warp = (noise.random3D(u * 1.6f, v * 1.6f, 0.4f).toFloat() - 0.5f) * 0.9f
            val h = sin((v * freq + warp) * 2f * PI.toFloat() + phase)
            // slope-based shading: rising face catches light, falling face shaded
            val slope = cos((v * freq + warp) * 2f * PI.toFloat() + phase)
            var lum = 0.5f + 0.42f * slope
            // crest highlights add sparkle, troughs deepen
            lum *= (0.78f + 0.22f * (0.5f + 0.5f * h))
            lum = lum.coerceIn(0f, 1f)
            val g = (lum * 255).toInt().coerceIn(0, 255)
            src[px, py] = argb(255, g, g, g)
        }
    }

    val dots = stippleVoronoi(
        src,
        pointCount = (SIZE * SIZE / 140),
        iterations = 18,
        gamma = 1.3f,
        minRadius = SIZE * 0.0009f,
        maxRadius = SIZE * 0.0045f,
        seed = SEED.toInt()
    )
    println("  ${dots.size} stipple dots")

    val ramp = Coolors.molten.expand(256)
    val out = gart.gartvas()
    val c = out.canvas
    c.clear(0xFFFDF0D5.toInt())   // papaya-whip ground
    for (dot in dots) {
        // tint by vertical band so successive swells walk the ramp like deep water
        val t = ((dot.y / SIZE.toFloat()) * 1.4f) % 1f
        val col = ramp.bound((t.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
