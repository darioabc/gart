package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.color.ColorRamp
import dev.oblac.gart.color.ColorStop
import dev.oblac.gart.color.argb
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.PI
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

/**
 * FluidDye — Earthy / muted, Organic / soft
 *
 * Technique: Fluid / Navier-Stokes approximation via curl-noise dye advection.
 *   - A dye Float grid accumulates ~6 soft Gaussian blobs seeded from rng.
 *   - Divergence-free velocity = curl of a two-octave OpenSimplex potential
 *     (∂Φ/∂y, -∂Φ/∂x) evaluated with a central-difference epsilon.
 *   - Dye is advected backwards along the velocity field for 80 steps,
 *     with dt=0.8 per step, accumulating smooth swirls.
 *   - Final intensity mapped through antiqueWhite → tan → peru → sienna ramp.
 *
 * Style × Technique cell (Fluid/Navier-Stokes × Organic/soft):
 *   "Smooth color field (speed→hue); particle trails with fade"
 *   → implemented as smooth dye intensity → earthy color ramp, low-alpha rendering.
 */
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("FluidDye", SIZE, SIZE)
    val g = gart.gartvas()
    val d = gart.d

    // --- Parameters (Organic/soft: smooth, wide swirls, low noise frequency) ---
    val W = d.w
    val H = d.h
    val STEPS = 80          // advection steps per pixel
    val DT = 0.8f           // advection timestep (pixels per step)
    val NOISE_SCALE = 0.0018 // low frequency → wide smooth swirls
    val EPS = 1.5           // central-difference epsilon for curl
    val OCTAVES = 2
    val DECAY = 0.55f       // dye decay per advection step (organic fade)
    val NUM_SEEDS = 6       // dye blobs

    // Two noise instances for potential octaves (different seeds)
    val noiseA = OpenSimplexNoise(SEED)
    val noiseB = OpenSimplexNoise(SEED xor -0x61c8864680b583ebL)

    // Curl-noise potential: two octaves of simplex, summed
    fun potential(x: Double, y: Double): Double {
        val s = NOISE_SCALE
        return noiseA.random2D(x * s, y * s) +
               0.5 * noiseB.random2D(x * s * 2.1, y * s * 2.1 + 3.7)
    }

    // Divergence-free velocity via curl: (∂Φ/∂y, -∂Φ/∂x)
    fun velX(x: Double, y: Double): Float {
        return ((potential(x, y + EPS) - potential(x, y - EPS)) / (2.0 * EPS)).toFloat()
    }
    fun velY(x: Double, y: Double): Float {
        return (-(potential(x + EPS, y) - potential(x - EPS, y)) / (2.0 * EPS)).toFloat()
    }

    // --- Dye grid (Float, range 0..1) ---
    val dye = FloatArray(W * H) { 0f }

    // Seed dye: Gaussian blobs at random positions
    val blobRadius = SIZE * 0.09f
    repeat(NUM_SEEDS) {
        val bx = rng.rndf(0.1f * W, 0.9f * W)
        val by = rng.rndf(0.1f * H, 0.9f * H)
        val strength = rng.rndf(0.6f, 1.0f)
        val r2inv = 1f / (blobRadius * blobRadius)
        for (y in 0 until H) {
            for (x in 0 until W) {
                val dx = x - bx
                val dy = y - by
                val d2 = dx * dx + dy * dy
                val v = strength * kotlin.math.exp((-d2 * r2inv).toDouble()).toFloat()
                val idx = y * W + x
                if (v > dye[idx]) dye[idx] = v.coerceIn(0f, 1f)
            }
        }
    }

    // --- Advect dye backwards along velocity field ---
    // For each output pixel, trace backwards STEPS*DT pixels along velocity.
    // We store the result in a new array to avoid read-write aliasing each step.
    var src = dye.copyOf()
    val dst = FloatArray(W * H)

    // Helper: bilinear sample from a float grid with clamp-to-edge
    fun sampleBilinear(grid: FloatArray, fx: Float, fy: Float): Float {
        val x0 = fx.toInt().coerceIn(0, W - 1)
        val y0 = fy.toInt().coerceIn(0, H - 1)
        val x1 = (x0 + 1).coerceIn(0, W - 1)
        val y1 = (y0 + 1).coerceIn(0, H - 1)
        val tx = (fx - x0).coerceIn(0f, 1f)
        val ty = (fy - y0).coerceIn(0f, 1f)
        val c00 = grid[y0 * W + x0]
        val c10 = grid[y0 * W + x1]
        val c01 = grid[y1 * W + x0]
        val c11 = grid[y1 * W + x1]
        return c00 * (1 - tx) * (1 - ty) +
               c10 * tx * (1 - ty) +
               c01 * (1 - tx) * ty +
               c11 * tx * ty
    }

    // Single advection pass: each pixel samples src at (x - vx*DT, y - vy*DT)
    fun advectOnce(input: FloatArray, output: FloatArray) {
        for (y in 0 until H) {
            for (x in 0 until W) {
                val vx = velX(x.toDouble(), y.toDouble()) * 120f  // scale velocity to pixel space
                val vy = velY(x.toDouble(), y.toDouble()) * 120f
                val sx = x - vx * DT
                val sy = y - vy * DT
                output[y * W + x] = (sampleBilinear(input, sx, sy) * DECAY).coerceIn(0f, 1f)
            }
        }
    }

    // Run advection
    for (step in 0 until STEPS) {
        advectOnce(src, dst)
        dst.copyInto(src)
    }
    // src now holds the final advected dye

    // --- Color ramp: antiqueWhite → tan → peru → sienna (earthy muted) ---
    val ramp = ColorRamp(
        listOf(
            ColorStop(CssColors.antiqueWhite, 0.00f),
            ColorStop(CssColors.tan,          0.30f),
            ColorStop(CssColors.peru,         0.65f),
            ColorStop(CssColors.sienna,       1.00f)
        )
    )

    // --- Write pixels via Gartmap ---
    // Gartmap(d) creates a pure in-memory buffer we can write then push to canvas.
    val map = Gartmap(d)
    for (y in 0 until H) {
        for (x in 0 until W) {
            val intensity = src[y * W + x].coerceIn(0f, 1f)
            map[x, y] = ramp.colorAt(intensity)
        }
    }
    map.drawToCanvas(g)  // push pixel buffer → Gartvas (g is the bound Gartvas)

    gart.saveImage(g)
}
