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
    val gart = Gart.of("VoronoiDune", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val cx = d.cx; val cy = d.cy

    // ── Wind-ridged dunes · raking-light stipple ───────────────────────────────────
    // Stacked dune ridges from summed simplex octaves form a height field; a low raking
    // light from the left brightens windward faces and casts the lee slopes into shadow,
    // so dots gather in the slip-faces like grains of dark sand.
    val src = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val u = px / SIZE.toFloat()
            val v = py / SIZE.toFloat()
            // ridged multi-octave height: 1 - |noise| folds make sharp dune crests
            var h = 0f; var amp = 0.55f; var fx = 2.2f; var fy = 3.4f
            for (o in 0 until 4) {
                val n = noise.random3D(u * fx, v * fy, o * 1.7f).toFloat()
                h += amp * (1f - abs(2f * n - 1f))
                amp *= 0.5f; fx *= 1.9f; fy *= 1.9f
            }
            // approximate the leeward slope from the height gradient along x
            val nE = noise.random3D((u + 0.012f) * 2.2f, v * 3.4f, 0f).toFloat()
            val nW = noise.random3D((u - 0.012f) * 2.2f, v * 3.4f, 0f).toFloat()
            val slope = (nE - nW)                      // +ve = facing away from left light
            var lum = (0.32f + 0.70f * h) - 0.9f * slope
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
        // tint by local brightness of the source field: shadowed slip-faces run dark-red
        val lum = (src[dot.x.toInt().coerceIn(0, d.w - 1), dot.y.toInt().coerceIn(0, d.h - 1)] and 0xFF) / 255f
        val t = lum
        val col = ramp.bound((t.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
