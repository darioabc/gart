package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.color.lerpColor
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.math.*       // Random extension fns (rndf, etc.)
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Canvas size: GART_SIZE=512 for a fast draft; default 1024 (see render.sh).
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: GART_SEED=<long> to reproduce; printed to stdout.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

// ── Plasma · Neon on black · Organic/soft ────────────────────────────────────
// Smooth low-frequency composite sine field (3–6 gentle bands across the canvas —
// organic, not tight fringes). Hue cycles a 3-stop neon ramp (magenta→cyan→lime);
// BRIGHTNESS scales with the field so wave troughs fall to black → glowing neon
// bands on black, not a flat wash.
private const val TAU = 6.2831855f
private val STOP0 = 0xFFFF00FF.toInt() // magenta
private val STOP1 = 0xFF00FFFF.toInt() // cyan
private val STOP2 = 0xFFCCFF00.toInt() // electric lime

private fun softClamp(t: Float): Float { val tc = t.coerceIn(0f, 1f); return tc * tc * (3f - 2f * tc) }

private fun neonColor(t: Float): Int =
    if (t < 0.5f) lerpColor(STOP0, STOP1, t * 2f)
    else lerpColor(STOP1, STOP2, (t - 0.5f) * 2f)

/** Scale an ARGB color's RGB toward black by factor f (alpha stays opaque). */
private fun toBlack(color: Int, f: Float): Int {
    val r = (((color shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
    val g = (((color shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
    val b = ((color and 0xFF) * f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("NeonPlasma", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    c.clear(CssColors.black)

    // 3–6 gentle bands: smooth/organic but with visible plasma structure.
    val fa = rng.rndf(3f, 6f) * TAU
    val fb = rng.rndf(3f, 6f) * TAU
    val fc = rng.rndf(2f, 4f) * TAU
    val fd = rng.rndf(3f, 7f) * TAU
    val pa = rng.rndf(0f, TAU); val pb = rng.rndf(0f, TAU)
    val pc = rng.rndf(0f, TAU); val pd = rng.rndf(0f, TAU)

    for (py in 0 until d.h) {
        val ny = py.toFloat() / SIZE
        val dy = ny - 0.5f
        for (px in 0 until d.w) {
            val nx = px.toFloat() / SIZE
            val dx = nx - 0.5f
            val dist = sqrt(dx * dx + dy * dy)

            // Hue and brightness use DIFFERENT wave pairs so they decorrelate —
            // otherwise low-hue (magenta) always lands in dark troughs and never shows.
            val hueWave = (sin(nx * fa + pa) + sin((nx + ny) * fc + pc)) * 0.5f  // [-1,1]
            val glowWave = (cos(ny * fb + pb) + sin(dist * fd + pd)) * 0.5f      // [-1,1]

            val t = (hueWave + 1f) * 0.5f                  // [0,1] → magenta→cyan→lime
            val glow = softClamp((glowWave + 1f) * 0.5f)   // brightness; troughs → black
            c.drawPoint(px.toFloat(), py.toFloat(), fillOf(toBlack(neonColor(t), 0.06f + 0.94f * glow)))
        }
    }

    gart.saveImage(g)
}
