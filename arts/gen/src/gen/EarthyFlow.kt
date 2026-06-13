package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.math.*       // brings all Random extension fns into scope
import dev.oblac.gart.noise.OpenSimplexNoise
import dev.oblac.gart.smooth.toSmoothQuadraticPath
import kotlin.math.PI
import kotlin.random.Random
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Point

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * EarthyFlow — organic flow field with earthy/muted palette.
 * Technique: Flow field (from first principles — manual particle integration).
 * Theme: Earthy/muted — antiqueWhite background, sienna/peru/darkKhaki/oliveDrab/tan palette.
 * Style: Organic/soft — curved smooth strokes, alpha ≤80, variable width with taper.
 *
 * Organic/soft × Flow field directives:
 *   - Curved Bezier/poly strokes (via toSmoothQuadraticPath)
 *   - Alpha ≤ 80
 *   - Variable width with taper (wider strokes for shorter paths, thinner for longer)
 */
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("EarthyFlow", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // --- Background ---
    c.clear(CssColors.antiqueWhite)

    // --- Earthy palette ---
    val palette = listOf(
        CssColors.sienna,
        CssColors.peru,
        CssColors.darkKhaki,
        CssColors.oliveDrab,
        CssColors.tan
    )

    // --- Noise-based flow field ---
    // Use the seed to create a deterministic noise field
    val noise = OpenSimplexNoise(SEED)
    val noiseScale = 0.0018   // low frequency → large smooth curves (organic feel)
    val TAU = 2.0 * PI

    // Angle at a given point in the field: smooth sin/cos combined with simplex noise
    fun fieldAngle(x: Float, y: Float): Float {
        val nx = noise.random2D(x * noiseScale, y * noiseScale)
        // Multiply by TAU to get full rotation coverage
        return (nx * TAU).toFloat()
    }

    // --- Particle parameters (Organic/soft style) ---
    val numParticles = rng.rndi(250, 350)   // 200–400 per spec, mid-range for density
    val stepsPerParticle = rng.rndi(200, 400)
    val stepSize = SIZE * 0.002f            // small step for smooth curves

    // Draw each particle trail
    repeat(numParticles) {
        // Random seed position spread across the full canvas
        val startX = rng.rndf(0f, SIZE.toFloat())
        val startY = rng.rndf(0f, SIZE.toFloat())

        val points = mutableListOf<Point>()
        var x = startX
        var y = startY

        // Integrate through the flow field
        repeat(stepsPerParticle) {
            points.add(Point(x, y))
            val angle = fieldAngle(x, y)
            x += kotlin.math.cos(angle) * stepSize
            y += kotlin.math.sin(angle) * stepSize
            // Stop if we go too far out of bounds
            if (x < -SIZE * 0.1f || x > SIZE * 1.1f || y < -SIZE * 0.1f || y > SIZE * 1.1f) return@repeat
        }

        if (points.size < 4) return@repeat

        // Variable width: taper — shorter surviving trails get slightly thicker strokes
        val widthBase = rng.rndf(1.2f, 3.5f)
        val strokeWidth = widthBase * (1f - points.size.toFloat() / stepsPerParticle * 0.4f)

        // Pick a color from the earthy palette
        val baseColor = palette[rng.rndi(palette.size)]

        // Alpha ≤ 80 (organic/soft rule)
        val strokeAlpha = rng.rndi(35, 80)
        val paint = strokeOf(baseColor, strokeWidth.coerceAtLeast(0.8f)).alpha(strokeAlpha).apply {
            strokeCap = PaintStrokeCap.ROUND
        }

        // Smooth quadratic Bezier through all points
        val path = points.toSmoothQuadraticPath()
        c.drawPath(path, paint)
    }

    gart.saveImage(g)
}
