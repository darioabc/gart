package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.color.argb
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.gfx.roundStroke
import dev.oblac.gart.math.*
import kotlin.math.*
import kotlin.random.Random
import org.jetbrains.skia.Point

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * HarmonicSpiro — Harmonograph on black, neon palette.
 *
 * Technique: Spiro / harmonograph (Constructive geometry)
 * Theme:     Neon on black
 * Style:     Organic / soft
 *
 * Style x Technique cell (Spiro/harmonograph x Organic/soft):
 *   Non-integer ratios; low alpha; long integration for petal density.
 *
 * Implementation: self-contained parametric harmonograph.
 *   x(t) = sum_i  Ax_i * sin(fx_i * t + px_i) * exp(-dx_i * t)
 *   y(t) = sum_i  Ay_i * sin(fy_i * t + py_i) * exp(-dy_i * t)
 * with 3 terms per axis, non-integer frequency ratios jittered near small
 * integers, small damping.  t runs from 0 to T_MAX in fine steps, collecting
 * Points and drawing each consecutive pair as a low-alpha line segment.
 * Hue cycles along t for the neon glow effect; brightness is never scaled
 * down — black background provides the "darkness" that makes peaks pop.
 */
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("HarmonicSpiro", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // --- background ---
    c.clear(CssColors.black)

    // --- harmonograph parameters ---
    // 3 sinusoidal terms per axis; frequencies are jittered near small integers
    // to produce non-rational (non-closing) Lissajous-style curves (organic feel)
    val nTerms = 3

    val halfSize = d.wf * 0.42f   // amplitude envelope — slightly inset from edge

    // Amplitudes — randomised per term, decreasing towards higher terms
    val axAmps = FloatArray(nTerms) { i -> rng.rndf(halfSize * 0.4f, halfSize) / (i + 1).toFloat() }
    val ayAmps = FloatArray(nTerms) { i -> rng.rndf(halfSize * 0.4f, halfSize) / (i + 1).toFloat() }

    // Frequencies — jitter near 1, 2, 3 so we get non-integer ratios
    val baseFreqs = floatArrayOf(1f, 2f, 3f)
    val fxFreqs = FloatArray(nTerms) { i -> baseFreqs[i] + rng.rndf(-0.15f, 0.15f) }
    val fyFreqs = FloatArray(nTerms) { i -> baseFreqs[i] + rng.rndf(-0.15f, 0.15f) }

    // Phases — fully random
    val pxPhases = FloatArray(nTerms) { rng.rndf(0f, (2.0 * PI).toFloat()) }
    val pyPhases = FloatArray(nTerms) { rng.rndf(0f, (2.0 * PI).toFloat()) }

    // Damping — very small so the curve survives a long integration
    val dxDamp = FloatArray(nTerms) { rng.rndf(0.0002f, 0.001f) }
    val dyDamp = FloatArray(nTerms) { rng.rndf(0.0002f, 0.001f) }

    // Integration parameters — long range, fine step (organic / dense petal rule)
    val tMax = 800.0        // long enough for damping to bring amplitude near zero
    val dt   = 0.003        // fine step → smooth organic curves

    val cx = d.center.x
    val cy = d.center.y

    // Pre-collect all points (avoids recomputing for colour pass)
    val totalSteps = ((tMax / dt).toInt()) + 1
    val xs = FloatArray(totalSteps)
    val ys = FloatArray(totalSteps)
    var stepIdx = 0
    var t = 0.0
    while (t <= tMax && stepIdx < totalSteps) {
        var px = 0.0
        var py = 0.0
        for (i in 0 until nTerms) {
            val tf = t.toFloat()
            px += axAmps[i] * sin(fxFreqs[i] * tf + pxPhases[i]) * exp((-dxDamp[i] * tf).toDouble())
            py += ayAmps[i] * sin(fyFreqs[i] * tf + pyPhases[i]) * exp((-dyDamp[i] * tf).toDouble())
        }
        xs[stepIdx] = cx + px.toFloat()
        ys[stepIdx] = cy + py.toFloat()
        stepIdx++
        t += dt
    }
    val count = stepIdx

    // Draw as consecutive low-alpha neon line segments, cycling hue along t.
    // Alpha 30/255 ≈ 12% — accumulation over many overdrawing passes creates
    // bright peaks while sparse areas stay near black (neon glow principle).
    val strokeWidth = 1.0f

    for (i in 0 until count - 1) {
        val tNorm = i.toFloat() / (count - 1).toFloat()

        // Hue cycles 1.5× over the integration (non-integer so colour re-enters
        // with a phase offset, giving richer overlapping colours)
        val hue = (tNorm * 540f) % 360f
        val neonColor = hsvToNeonArgb(hue, alpha = 30)

        val paint = strokeOf(neonColor, strokeWidth).roundStroke()

        c.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1], paint)
    }

    gart.saveImage(g)
}

/**
 * Converts a hue (0..360) to a fully-saturated, full-value neon ARGB int.
 * Saturation = 1, Value = 1 — pure spectral colour; alpha is [alpha] (0..255).
 * Uses standard HSV-to-RGB sector formula.
 */
private fun hsvToNeonArgb(hue: Float, alpha: Int): Int {
    val h = hue.coerceIn(0f, 360f)
    val s = 1f
    val v = 1f
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f  -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else     -> Triple(c, 0f, x)
    }
    val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
    return argb(alpha, r, g, b)
}
