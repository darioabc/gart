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
    val gart = Gart.of("VoronoiVortex", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val cx = d.cx; val cy = d.cy

    // ── Logarithmic spiral vortex · stippled whirlpool ─────────────────────────────
    // Polar coords drive a spiral phase (angle + k·log r); a sharpened sine of that
    // phase carves dark spiral arms that wind into a bright eye, with limb darkening at
    // the rim. Dots crowd the arms; tint walks the ramp along the spiral like a galaxy.
    val R = SIZE * 0.46f
    val arms = 5.0f                                   // spiral arm count
    val twist = 2.3f                                  // how tightly arms wind
    val phase = noise.random3D(7.0, 2.0, 5.0).toFloat() * (2f * PI.toFloat())
    val src = Gartmap(gart.gartvas())
    val white = 0xFFFFFFFF.toInt()
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val dx = (px - cx); val dy = (py - cy)
            val rr = hypot(dx, dy) / R
            if (rr > 1f) { src[px, py] = white; continue }
            val ang = atan2(dy.toDouble(), dx.toDouble()).toFloat()
            val spiral = ang * arms + twist * ln(rr * 6f + 0.05f) + phase
            val band = 0.5f + 0.5f * sin(spiral)
            val sharp = band.pow(2.0f)
            // bright calm eye in the center, darkening arms outward, soft rim falloff
            val eye = smoothstep(0.0f, 0.22f, rr)     // 0 at center -> 1 outside eye
            val limb = 1f - 0.30f * rr * rr
            var lum = (0.20f + 0.80f * sharp) * (0.45f + 0.55f * eye) * limb
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
        // tint by spiral phase so arms cycle the molten ramp like a galaxy
        val ddx = dot.x - cx; val ddy = dot.y - cy
        val rr = (hypot(ddx, ddy) / R).coerceIn(0f, 1f)
        val ang = atan2(ddy.toDouble(), ddx.toDouble()).toFloat()
        val t = ((ang * arms + twist * ln(rr * 6f + 0.05f) + phase) / (2f * PI.toFloat())) % 1f
        val tt = if (t < 0f) t + 1f else t
        val col = ramp.bound((tt.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
