package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.color.ColorRamp
import dev.oblac.gart.color.ColorStop
import dev.oblac.gart.color.argb
import dev.oblac.gart.color.lerpColor
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.math.*       // brings all Random extension fns into scope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import kotlin.random.Random
import org.jetbrains.skia.Rect

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

// --- Physarum parameters (Style: Organic/soft x Technique: Physarum) ---
// Organic/soft: wide diffusion kernel, smooth trail map, soft color gradient LUT
// Neon on black: scale brightness by field intensity so troughs stay black, peaks glow
private const val GRID_SCALE = 2          // trail grid is SIZE/GRID_SCALE square
private const val N_AGENTS = 6000
private const val N_STEPS = 120
private const val SENSOR_ANGLE = 0.4f     // radians — how far left/right to sense
private const val SENSOR_DIST = 9f        // pixels in grid space
private const val STEP_SIZE = 1.5f        // px per step in grid space
private const val DEPOSIT = 5f            // trail deposit per step
private const val DECAY = 0.9f            // per-step multiplicative decay
private const val TURN_SPEED = 0.15f      // max random rotation (organic wobble)

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("PhysarumWeb", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // Clear to black
    c.clear(CssColors.black)

    // Trail grid dimensions
    val gw = SIZE / GRID_SCALE
    val gh = SIZE / GRID_SCALE

    // Float trail grid — initialised to zero
    val trail = Array(gh) { FloatArray(gw) { 0f } }
    val scratch = Array(gh) { FloatArray(gw) { 0f } }

    // Agent state: posX, posY, heading (radians) — all in grid space
    val ax = FloatArray(N_AGENTS) { rng.rndf(0f, gw.toFloat()) }
    val ay = FloatArray(N_AGENTS) { rng.rndf(0f, gh.toFloat()) }
    val ah = FloatArray(N_AGENTS) { rng.rndf(0f, (2.0 * Math.PI).toFloat()) }

    // Neon-on-black ramp: black at 0, cyan-teal at 0.3, hot magenta at 0.6, near-white at 1
    // Each stop: argb(alpha, r, g, b)  — confirmed API from dev.oblac.gart.color.color.kt
    val neonRamp = ColorRamp(
        listOf(
            ColorStop(argb(255, 0, 0, 0),         0.00f),  // pure black
            ColorStop(argb(255, 0, 20, 60),        0.08f),  // near-black blue hint
            ColorStop(argb(255, 0, 200, 255),      0.30f),  // neon cyan
            ColorStop(argb(255, 180, 0, 255),      0.55f),  // electric violet
            ColorStop(argb(255, 255, 40, 180),     0.75f),  // hot magenta
            ColorStop(argb(255, 255, 220, 255),    1.00f),  // near-white lavender peak
        )
    )

    // Simulation loop
    repeat(N_STEPS) {
        // --- Agent sense-rotate-move-deposit ---
        for (i in 0 until N_AGENTS) {
            val px = ax[i]; val py = ay[i]; val h = ah[i]

            // Sample trail at center, left, right sensor positions
            fun sampleAt(angle: Float): Float {
                val sx = px + cos(angle) * SENSOR_DIST
                val sy = py + sin(angle) * SENSOR_DIST
                val gx = sx.toInt().coerceIn(0, gw - 1)
                val gy = sy.toInt().coerceIn(0, gh - 1)
                return trail[gy][gx]
            }

            val fwd   = sampleAt(h)
            val left  = sampleAt(h - SENSOR_ANGLE)
            val right = sampleAt(h + SENSOR_ANGLE)

            // Organic wobble: small random nudge regardless (soft style)
            val wobble = rng.rndf(-TURN_SPEED, TURN_SPEED)

            val newH = when {
                fwd >= left && fwd >= right -> h + wobble          // go straight (with wobble)
                left > right               -> h - SENSOR_ANGLE + wobble
                right > left               -> h + SENSOR_ANGLE + wobble
                else                       -> h + wobble
            }
            ah[i] = newH

            // Move
            val nx = (px + cos(newH) * STEP_SIZE).coerceIn(0f, (gw - 1).toFloat())
            val ny = (py + sin(newH) * STEP_SIZE).coerceIn(0f, (gh - 1).toFloat())
            ax[i] = nx; ay[i] = ny

            // Deposit
            val gx = nx.toInt().coerceIn(0, gw - 1)
            val gy = ny.toInt().coerceIn(0, gh - 1)
            trail[gy][gx] += DEPOSIT
        }

        // --- Wide diffusion: 3×3 box blur (organic/soft) + decay ---
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                var sum = 0f; var count = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx).coerceIn(0, gw - 1)
                        val ny = (y + dy).coerceIn(0, gh - 1)
                        sum += trail[ny][nx]; count++
                    }
                }
                scratch[y][x] = (sum / count) * DECAY
            }
        }
        // Swap scratch -> trail
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                trail[y][x] = scratch[y][x]
            }
        }
    }

    // --- Find max trail value for normalisation ---
    var maxVal = 0f
    for (y in 0 until gh) {
        for (x in 0 until gw) {
            if (trail[y][x] > maxVal) maxVal = trail[y][x]
        }
    }
    if (maxVal == 0f) maxVal = 1f

    // --- Render: each grid cell -> a rect on the canvas ---
    val cellW = SIZE.toFloat() / gw
    val cellH = SIZE.toFloat() / gh

    for (y in 0 until gh) {
        for (x in 0 until gw) {
            val t = (trail[y][x] / maxVal).coerceIn(0f, 1f)
            // Neon-on-black: use a power curve so background truly stays black
            // and only peaks glow (organic soft ramp, not hard-edge)
            val tGamma = (t * t * t)    // gamma=3 — troughs collapse to black
            if (tGamma < 0.005f) continue  // skip fully black cells (performance)

            val color = neonRamp.colorAt(tGamma)

            val left   = x * cellW
            val top    = y * cellH
            val right  = left + cellW + 0.5f  // tiny overlap avoids grid gaps
            val bottom = top  + cellH + 0.5f

            c.drawRect(Rect(left, top, right, bottom), fillOf(color))
        }
    }

    gart.saveImage(g)
}
