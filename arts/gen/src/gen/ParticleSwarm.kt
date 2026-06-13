package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.color.lerpColor
import dev.oblac.gart.color.argb
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

// Style x Technique defaults: Particle system x Dense / maximal
//   >= 2000 particles; small radius; accumulate many frames
//   Neon on black: bg=black; saturated hues; scale brightness by field intensity so troughs stay black

private const val PARTICLE_COUNT = 2500
private const val STEPS = 300
private const val STEP_SIZE = 1.5f
private const val NOISE_SCALE = 0.0025       // spatial frequency of the flow field
private const val POINT_RADIUS = 0.8f        // small for dense accumulation

// Neon hue palette: 6 vivid hues cycling across particles
// Encoded as full-brightness ARGB; alpha is controlled per-draw via Paint.alpha()
private val NEON_COLORS = intArrayOf(
    argb(255, 255, 0, 255),   // magenta
    argb(255, 0, 255, 255),   // cyan
    argb(255, 255, 64, 0),    // orange-red
    argb(255, 0, 255, 64),    // neon green
    argb(255, 180, 0, 255),   // violet
    argb(255, 255, 220, 0)    // yellow
)

// Low alpha per point: trails accumulate into glowing structures
private const val POINT_ALPHA = 18

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("ParticleSwarm", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    val noise = OpenSimplexNoise(SEED)

    // Black background — neon on black theme
    c.clear(CssColors.black)

    val wf = d.wf
    val hf = d.hf

    // Initialise particle positions randomly across the canvas
    val px = FloatArray(PARTICLE_COUNT) { rng.rndf(0f, wf) }
    val py = FloatArray(PARTICLE_COUNT) { rng.rndf(0f, hf) }

    // Pre-build one paint per neon color (reused across all steps)
    val paints = Array(NEON_COLORS.size) { i ->
        fillOf(NEON_COLORS[i]).alpha(POINT_ALPHA)
    }

    for (step in 0 until STEPS) {
        for (i in 0 until PARTICLE_COUNT) {
            val x = px[i]
            val y = py[i]

            // Sample flow angle from simplex noise; scale to [0, TAU]
            val n = noise.random2D(x * NOISE_SCALE, y * NOISE_SCALE)
            // n is in ~[-1,1]; map to [0, TAU]
            val angle = (n.toFloat() + 1f) * TAUf   // range 0..2*TAU — gives full curl

            // Move particle along the flow direction
            val nx = x + cos(angle) * STEP_SIZE
            val ny = y + sin(angle) * STEP_SIZE

            // Wrap around canvas edges for full coverage
            val wx = ((nx % wf) + wf) % wf
            val wy = ((ny % hf) + hf) % hf

            px[i] = wx
            py[i] = wy

            // Neon on black: scale point brightness by |noise| so only peaks glow
            // |n| is in [0,1]; use it to gate the effective alpha
            val intensity = kotlin.math.abs(n.toFloat())   // 0=background (black), 1=peak (bright)
            val effectiveAlpha = (POINT_ALPHA * intensity).toInt().coerceIn(0, 255)
            if (effectiveAlpha < 2) continue   // skip near-zero — lets black dominate in calm zones

            val colorIdx = i % NEON_COLORS.size
            val paint = if (effectiveAlpha == POINT_ALPHA) {
                paints[colorIdx]
            } else {
                fillOf(NEON_COLORS[colorIdx]).alpha(effectiveAlpha)
            }

            c.drawPoint(wx, wy, paint)
        }
    }

    gart.saveImage(g)
}
