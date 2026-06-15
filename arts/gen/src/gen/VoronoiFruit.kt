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

// ── WILD · Weighted-Voronoi stippled still-life fruit cluster ──────────────────
// A loose pile of overlapping Lambert-shaded spheres (apples / plums) is rasterised
// to a luminance field with a soft contact shadow, then gȧrt's Lloyd-relaxed
// weighted Voronoi stippler scatters thousands of dots whose density tracks the
// shading — a pointillist Cézanne still life. Dots are tinted by a per-fruit bias
// blended with local brightness across the coolors "Molten" palette.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val gart = Gart.of("VoronoiFruit", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)

    // light direction (unit-ish), shared by every sphere
    val lx = -0.5f; val ly = -0.6f; val lz = 0.62f

    // A clustered, bowl-less pile of fruits in the lower-centre of the frame.
    // Larger fruits sit in front (lower / greater cy+radius), smaller ones nestle
    // behind & above. Positions chosen so the discs clearly overlap.
    data class Fruit(val cx: Float, val cy: Float, val radius: Float, val tintBias: Float)
    val fruits = listOf(
        // big front apples (warm, low molten index)
        Fruit(d.cx - 0.14f * SIZE, d.cy + 0.20f * SIZE, 0.20f * SIZE, 0.05f),
        Fruit(d.cx + 0.13f * SIZE, d.cy + 0.24f * SIZE, 0.21f * SIZE, 0.18f),
        // mid plum tucked between the two front apples
        Fruit(d.cx + 0.01f * SIZE, d.cy + 0.10f * SIZE, 0.16f * SIZE, 0.85f),
        // smaller fruits behind / above
        Fruit(d.cx - 0.20f * SIZE, d.cy + 0.02f * SIZE, 0.14f * SIZE, 0.62f),
        Fruit(d.cx + 0.18f * SIZE, d.cy - 0.02f * SIZE, 0.13f * SIZE, 0.40f),
        Fruit(d.cx + 0.00f * SIZE, d.cy - 0.12f * SIZE, 0.12f * SIZE, 0.95f),
    )

    // Contact-shadow band under the cluster: a dim horizontal smudge so the pile
    // sits on a surface. Centre / extents derived from the front fruits.
    val shadowY = d.cy + 0.40f * SIZE
    val shadowHalfW = 0.34f * SIZE
    val shadowHalfH = 0.06f * SIZE

    // Render the cluster as a grayscale luminance field: dark areas attract dots.
    val src = Gartmap(gart.gartvas())
    val white = 0xFFFFFFFF.toInt()
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            // Find the FRONTMOST fruit whose disc contains this pixel
            // (frontmost = greatest cy+radius, i.e. nearest / lowest in the pile).
            var bestFront = -Float.MAX_VALUE
            var hitNx = 0f; var hitNy = 0f; var hitR2 = 0f; var found = false
            for (f in fruits) {
                val nx = (px - f.cx) / f.radius
                val ny = (py - f.cy) / f.radius
                val r2 = nx * nx + ny * ny
                if (r2 > 1f) continue
                val front = f.cy + f.radius
                if (front > bestFront) {
                    bestFront = front
                    hitNx = nx; hitNy = ny; hitR2 = r2; found = true
                }
            }

            if (found) {
                val nz = sqrt(1f - hitR2)
                val relief = noise.random3D(hitNx * 2.4f, hitNy * 2.4f, 0.0f).toFloat()
                val lambert = (hitNx * lx + hitNy * ly + nz * lz).coerceIn(0f, 1f)
                var lum = (0.15f + 0.85f * lambert) * (0.8f + 0.2f * relief)
                lum = lum.coerceIn(0f, 1f)
                val g = (lum * 255).toInt().coerceIn(0, 255)
                src[px, py] = argb(255, g, g, g)
            } else {
                // empty ground: white, except a soft dim contact-shadow pool
                val sx = (px - d.cx) / shadowHalfW
                val sy = (py - shadowY) / shadowHalfH
                val sr2 = sx * sx + sy * sy
                if (sr2 < 1f) {
                    // soft falloff: 1 at centre -> 0 at edge; mid-gray smudge
                    val falloff = (1f - sqrt(sr2)).coerceIn(0f, 1f)
                    val g = (255 - (falloff * 90f)).toInt().coerceIn(0, 255)
                    src[px, py] = argb(255, g, g, g)
                } else {
                    src[px, py] = white
                }
            }
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
        // Recover which fruit (if any) owns this dot to read its tintBias, and use
        // local sphere brightness to pull highlights toward the cream mid-ramp.
        var bestFront = -Float.MAX_VALUE
        var bias = 0.5f; var bright = 0.5f; var onFruit = false
        for (f in fruits) {
            val nx = (dot.x - f.cx) / f.radius
            val ny = (dot.y - f.cy) / f.radius
            val r2 = nx * nx + ny * ny
            if (r2 > 1f) continue
            val front = f.cy + f.radius
            if (front > bestFront) {
                bestFront = front
                bias = f.tintBias
                val nz = sqrt(1f - r2)
                bright = (nx * lx + ny * ly + nz * lz).coerceIn(0f, 1f)
                onFruit = true
            }
        }
        // per-fruit base region of the molten ramp, brightness pulls toward cream.
        val t = if (onFruit) bias * 0.7f + bright * 0.3f else 0.5f
        val col = ramp.bound((t.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
