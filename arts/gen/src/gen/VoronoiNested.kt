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
    val gart = Gart.of("VoronoiNested", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val cx = d.cx; val cy = d.cy

    // ── Nested concentric orbs · stippled agate cross-section ──────────────────────
    // A single large orb built from nested onion-ring shells: a sharpened sine of the
    // (noise-warped) normalized radius makes alternating dense/sparse concentric bands,
    // and a soft Lambert tilt gives the whole sphere dimensional roundness. The Voronoi
    // stippler scatters dots whose density tracks the banding; rings are tinted by index
    // across the full Molten ramp so successive shells cycle the palette like agate.
    val R = SIZE * 0.42f
    val rings = 8.0f                 // number of concentric shells
    val K = rings * 2.0f             // sine repeats: one dark/light pair per ring
    val phase = noise.random3D(11.0, 7.0, 3.0).toFloat() * (2f * PI.toFloat())
    // soft global light tilt for roundness
    val lx = -0.55f; val ly = -0.45f

    val src = Gartmap(gart.gartvas())
    val white = 0xFFFFFFFF.toInt()
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val dx = (px - cx)
            val dy = (py - cy)
            val rrRaw = hypot(dx, dy) / R
            if (rrRaw > 1f) {
                // faint thin outer halo ring just beyond the body
                if (rrRaw in 1.0f..1.03f) {
                    val g = (0.78f * 255).toInt().coerceIn(0, 255)
                    src[px, py] = argb(255, g, g, g)
                } else {
                    src[px, py] = white
                }
                continue
            }
            // warp radius with low-freq simplex noise so rings wobble (agate-like)
            val ang = atan2(dy.toDouble(), dx.toDouble())
            val warp = (noise.random3D(
                cos(ang) * 1.4 + 5.0,
                sin(ang) * 1.4 + 5.0,
                rrRaw.toDouble() * 1.1
            ).toFloat() - 0.5f) * 0.10f
            val rr = (rrRaw + warp).coerceIn(0f, 1f)

            // ringing band: 0..1, then sharpen for crisp dark edges / lighter bodies
            val band = 0.5f + 0.5f * sin(rr * K * PI.toFloat() + phase)
            val sharp = band.pow(2.4f)               // raise contrast -> dark ring edges

            // soft Lambert-ish shading: slightly darker toward one side
            val nx = dx / R; val ny = dy / R
            val lambert = (0.5f + 0.5f * (nx * lx + ny * ly)).coerceIn(0f, 1f)
            val shade = 0.55f + 0.45f * lambert

            // limb darkening so the rim reads as a sphere edge
            val limb = 1f - 0.35f * rrRaw * rrRaw

            var lum = (0.18f + 0.82f * sharp) * shade * limb
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
        val rr = (hypot(dot.x - cx, dot.y - cy) / R).coerceIn(0f, 1f)
        // map ring index across the FULL molten ramp so successive shells cycle the palette
        val ringT = ((rr * rings) % 1f)
        // nudge by local band brightness for variation within a shell
        val nudge = (0.5f + 0.5f * sin(rr * K * PI.toFloat() + phase)) * 0.12f
        val t = (ringT + nudge)
        val col = ramp.bound((t.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
