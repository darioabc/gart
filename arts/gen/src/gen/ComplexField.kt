package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.color.lerpColor
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.math.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

// Cream & blues palette stops
private val COLOR_CREAM    = CssColors.cornsilk
private val COLOR_POWDER   = CssColors.powderBlue
private val COLOR_SKY      = CssColors.skyBlue
private val COLOR_STEEL    = CssColors.steelBlue
private val COLOR_ROYAL    = CssColors.royalBlue
private val COLOR_NAVY     = CssColors.navy

// Organic/soft: smooth gradient ramp, full palette, no banding.
// Style × Technique (Complex/math field × Organic / soft):
//   "Smooth gradient ramp; light Gaussian blur post-render"
// We achieve smoothness by: continuous arg-based t, softly blended multi-stop
// color ramp, and a continuous magnitude-based brightness modulation.

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("ComplexField", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // Clear to cream background
    c.clear(COLOR_CREAM)

    // Domain: map pixels to z in [-1.5, 1.5] x [-1.5i, 1.5i]
    val domainMin = -1.5
    val domainMax =  1.5

    for (j in 0 until d.h) {
        val cy = map(j, 0, d.h, domainMax, domainMin).toDouble()  // flip y so up is positive
        for (i in 0 until d.w) {
            val cx = map(i, 0, d.w, domainMin, domainMax).toDouble()

            // z as a complex number built from first principles (no Complex constructor needed)
            val zr = cx
            val zi = cy

            // Compute w = z^3 - 1 from first principles to avoid ambiguity with infix `to`
            // z^2 = (zr^2 - zi^2) + 2*zr*zi*i
            val z2r = zr * zr - zi * zi
            val z2i = 2.0 * zr * zi
            // z^3 = z^2 * z
            val z3r = z2r * zr - z2i * zi
            val z3i = z2r * zi + z2i * zr
            // w = z^3 - 1
            val wr = z3r - 1.0
            val wi = z3i

            // Argument of w: angle in [-pi, pi]
            val arg = atan2(wi, wr)          // in [-PI, PI]
            // Magnitude of w (log-scaled for organic spread)
            val mag = sqrt(wr * wr + wi * wi)
            val logMag = if (mag > 1e-10) ln(1.0 + mag) else 0.0

            // Map argument to [0,1] for hue position along the ramp
            val argT = ((arg / PI) * 0.5 + 0.5).toFloat()   // [0, 1]

            // Brightness modulation: map log-magnitude smoothly to [0, 1]
            // logMag in roughly [0, ~2.5] for |w| up to ~11
            val brightness = (logMag / 2.5).coerceIn(0.0, 1.0).toFloat()

            // Organic/soft: two-level lerp for a smooth multi-stop ramp.
            // Ramp: cream → powderBlue → skyBlue → steelBlue → royalBlue → navy
            // argT drives position along the ramp; brightness blends cream in at low values.
            val rampColor = multiStopLerp(argT)

            // Blend: low brightness (near-zero magnitude singularity) stays cream
            val finalColor = lerpColor(COLOR_CREAM, rampColor, (brightness * 1.4f).coerceIn(0f, 1f))

            c.drawPoint(i.toFloat(), j.toFloat(), fillOf(finalColor))
        }
    }

    gart.saveImage(g)
}

/**
 * 5-stop smooth ramp: powderBlue → skyBlue → steelBlue → royalBlue → navy.
 * t in [0, 1] wraps around the hue circle via the argument of w.
 */
private fun multiStopLerp(t: Float): Int {
    val stops = arrayOf(
        CssColors.powderBlue,
        CssColors.skyBlue,
        CssColors.steelBlue,
        CssColors.royalBlue,
        CssColors.navy,
        CssColors.cornflowerBlue,  // wrap back toward lighter blue for continuity
    )
    val n = stops.size
    val scaled = t * (n - 1)
    val idx = scaled.toInt().coerceIn(0, n - 2)
    val localT = scaled - idx
    return lerpColor(stops[idx], stops[idx + 1], localT)
}
