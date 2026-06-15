package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import dev.oblac.gart.stipple.stippleVoronoi
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD #5 · Weighted-Voronoi stippled eclipse ───────────────────────────────
// A near-total eclipse: a Lambert sphere lit almost from behind so the disc reads as
// a dark moon with a single brilliant crescent rim. The grazing light + a high power
// curve keep the body dark (dense dots) while only the limb flares bright; a soft
// corona just beyond the disc sheds a sparse halo. The luminance field drives gȧrt's
// Lloyd-relaxed weighted Voronoi stippler, and dots are tinted by local brightness
// from the coolors "Molten" palette. Showcases: stipple/VoronoiStippling.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val gart = Gart.of("VoronoiEclipse", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val cx = d.cx; val cy = d.cy

    val R = SIZE * 0.40f
    // light grazing the limb so only a thin crescent on one side catches it
    val lx = 0.85f; val ly = -0.25f; val lz = 0.45f

    // Build grayscale luminance field: DARK pixels attract stipple dots, WHITE = empty ground.
    val src = Gartmap(gart.gartvas())
    val white = 0xFFFFFFFF.toInt()
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val nx = (px - cx) / R
            val ny = (py - cy) / R
            val r2 = nx * nx + ny * ny
            if (r2 > 1f) {
                // faint corona: a soft ring of glow just beyond the limb that fades out
                val rr = sqrt(r2)
                if (rr < 1.18f) {
                    val halo = ((1.18f - rr) / 0.18f).coerceIn(0f, 1f)
                    val coronaRelief = noise.random3D(nx * 3.0f, ny * 3.0f, 1.7f).toFloat()
                    val lum = (0.78f + 0.18f * halo * (0.6f + 0.4f * coronaRelief)).coerceIn(0f, 1f)
                    val g = (lum * 255).toInt().coerceIn(0, 255)
                    src[px, py] = argb(255, g, g, g)
                } else {
                    src[px, py] = white
                }
                continue
            }
            val nz = sqrt(1f - r2)
            // surface relief
            val relief = noise.random3D(nx * 2.4f, ny * 2.4f, 0.0f).toFloat()
            val lambert = (nx * lx + ny * ly + nz * lz).coerceIn(0f, 1f)
            // push the falloff hard so only a thin crescent stays bright; small ambient
            val crescent = lambert.pow(2.2f)
            var lum = (0.08f + 0.92f * crescent) * (0.85f + 0.15f * relief)
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
        // tint by local brightness: dark body leans molten-red (low index),
        // bright crescent + corona lean cream / steel-blue (high index)
        val nx = (dot.x - cx) / R
        val ny = (dot.y - cy) / R
        val r2 = nx * nx + ny * ny
        val t: Float = if (r2 > 1f) {
            val rr = sqrt(r2)
            (0.7f + 0.3f * ((1.18f - rr) / 0.18f).coerceIn(0f, 1f))
        } else {
            val nz = sqrt(1f - r2)
            val lambert = (nx * lx + ny * ly + nz * lz).coerceIn(0f, 1f)
            (0.05f + 0.95f * lambert.pow(1.6f))
        }
        val col = ramp.bound((t.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
