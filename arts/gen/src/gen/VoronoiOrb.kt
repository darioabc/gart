package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import dev.oblac.gart.stipple.stippleVoronoi
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD #4 · Weighted-Voronoi stippled orb ───────────────────────────────────
// A Lambert-shaded sphere with simplex surface relief is rasterised to a luminance
// field, then gȧrt's Lloyd-relaxed weighted Voronoi stippler scatters thousands of
// dots whose density tracks the shading — a pointillist planet. Dots are tinted by
// radius from the coolors "Molten" palette. Showcases: stipple/VoronoiStippling.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("VoronoiOrb", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)

    val cx = d.cx; val cy = d.cy
    val R = SIZE * 0.40f
    // light direction (unit-ish)
    val lx = -0.5f; val ly = -0.6f; val lz = 0.62f

    // Render the orb as a grayscale luminance field: dark areas attract stipple dots.
    val src = Gartmap(gart.gartvas())
    val white = 0xFFFFFFFF.toInt()
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val nx = (px - cx) / R
            val ny = (py - cy) / R
            val r2 = nx * nx + ny * ny
            if (r2 > 1f) { src[px, py] = white; continue }
            val nz = sqrt(1f - r2)
            // surface relief
            val relief = noise.random3D(nx * 2.4f, ny * 2.4f, 0.0f).toFloat()
            val lambert = (nx * lx + ny * ly + nz * lz).coerceIn(0f, 1f)
            var lum = (0.15f + 0.85f * lambert) * (0.8f + 0.2f * relief)
            // terminator + limb darkening already in lambert; clamp
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
    c.clear(0xFFFDF0D5.toInt())   // papaya-whip ground from the same palette
    for (dot in dots) {
        val rr = hypot(dot.x - cx, dot.y - cy) / R
        val col = ramp.bound((rr.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
