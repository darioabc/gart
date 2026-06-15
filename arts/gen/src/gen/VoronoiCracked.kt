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

// ── WILD · Weighted-Voronoi stippled cracked stone ────────────────────────────
// A Lambert-shaded boulder with mottled simplex rock relief is rasterised to a
// luminance field, then a hand-rolled Worley F2-F1 cellular pass carves a network
// of dark cracks (cell boundaries) across it. gȧrt's Lloyd-relaxed weighted
// Voronoi stippler scatters dots whose density tracks the shading, so the crack
// veins read as dense dark stipple. Dots tinted by local brightness from "Molten".
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val gart = Gart.of("VoronoiCracked", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val cx = d.cx; val cy = d.cy

    val R = SIZE * 0.40f
    // light direction (unit-ish), lit toward upper-left
    val lx = -0.45f; val ly = -0.55f; val lz = 0.70f

    // Random feature points for the Worley crack pattern, in normalized disc space
    // (roughly -1..1). A few sit just outside so cells reach the limb cleanly.
    val cells = (16 + rng.nextInt(7)) // ~16-22 seed points
    val fpx = FloatArray(cells)
    val fpy = FloatArray(cells)
    run {
        var i = 0
        while (i < cells) {
            val ang = rng.nextFloat() * (2f * PI.toFloat())
            // bias toward filling the disc; allow a little overshoot past the limb
            val rad = sqrt(rng.nextFloat()) * 1.12f
            fpx[i] = cos(ang) * rad
            fpy[i] = sin(ang) * rad
            i++
        }
    }

    val src = Gartmap(gart.gartvas())
    val white = 0xFFFFFFFF.toInt()
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val nx = (px - cx) / R
            val ny = (py - cy) / R
            val r2 = nx * nx + ny * ny
            if (r2 > 1f) {
                // faint contact shadow under the stone on the papaya-whip ground
                val sx = nx
                val sy = (ny - 0.92f) * 2.6f       // squashed ellipse just below
                val sd = sx * sx + sy * sy
                if (ny > 0f && sd < 1f) {
                    val sh = (1f - sd).coerceIn(0f, 1f)
                    val g = (255 - (sh * 70f)).toInt().coerceIn(0, 255)
                    src[px, py] = argb(255, g, g, g)
                } else {
                    src[px, py] = white
                }
                continue
            }
            val nz = sqrt(1f - r2)

            // mottled rock surface: a couple of octaves of simplex relief
            val rA = noise.random3D(nx * 2.4f, ny * 2.4f, 0.0f).toFloat()
            val rB = noise.random3D(nx * 6.1f, ny * 6.1f, 3.7f).toFloat()
            val relief = 0.65f * rA + 0.35f * rB

            val lambert = (nx * lx + ny * ly + nz * lz).coerceIn(0f, 1f)
            var lum = (0.15f + 0.85f * lambert) * (0.78f + 0.22f * relief)

            // ── Worley F2-F1: distance to the two nearest feature points ──
            var f1 = Float.MAX_VALUE
            var f2 = Float.MAX_VALUE
            for (i in 0 until cells) {
                val dx = nx - fpx[i]
                val dy = ny - fpy[i]
                val dd = dx * dx + dy * dy
                if (dd < f1) { f2 = f1; f1 = dd } else if (dd < f2) { f2 = dd }
            }
            val edge = sqrt(f2) - sqrt(f1)   // small near cell boundaries = a crack

            // organic varying crack width from simplex noise
            val wig = noise.random3D(nx * 4.5f, ny * 4.5f, 9.1f).toFloat()
            val thresh = 0.045f + 0.05f * wig          // ~0.045..0.095
            if (edge < thresh) {
                // sharp darkening, deepest right on the crack centerline
                val crack = (edge / thresh).coerceIn(0f, 1f)
                lum *= (0.18f + 0.62f * crack)
            }

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
        // tint by local brightness of the luminance field: dark cracks -> low
        // ramp index (molten-red / navy), lit stone top -> high index (cream/steel).
        val g = (src[dot.x.toInt().coerceIn(0, d.w - 1), dot.y.toInt().coerceIn(0, d.h - 1)] and 0xFF)
        val t = g / 255f
        val col = ramp.bound((t.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
