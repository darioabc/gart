package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.drawRectWH
import dev.oblac.gart.math.*
import kotlin.random.Random

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * Gray-Scott reaction-diffusion, hard-edge geometric, neon on black.
 *
 * Style x Technique (Reaction-diffusion x Hard-edge geometric):
 *   Threshold V to binary; solid fill; sharp color boundary.
 *   >= 700 steps; low threshold for dense pattern coverage.
 *
 * Theme (Neon on black):
 *   Black background. A single saturated neon color for V > threshold.
 *   Only peaks glow; background stays black.
 *
 * Gray-Scott parameters:
 *   Du=0.16, Dv=0.08, f=0.037, k=0.06  (coral/mitosis-like regime)
 *   Grid: 220x220, 700 steps, then render.
 */
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("ReactionDiffusion", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // --- Gray-Scott simulation parameters ---
    val GW = 220
    val GH = 220
    val Du = 0.16f
    val Dv = 0.08f
    val f  = 0.037f
    val k  = 0.060f
    val steps = 700
    val dt = 1.0f

    // Allocate U and V grids (flat arrays, row-major: index = y*GW + x)
    val u = FloatArray(GW * GH) { 1.0f }   // U starts at 1 everywhere
    val v = FloatArray(GW * GH) { 0.0f }   // V starts at 0 everywhere
    val uNext = FloatArray(GW * GH)
    val vNext = FloatArray(GW * GH)

    // Seed several small V blobs using rng
    val blobCount = rng.rndi(5, 12)
    repeat(blobCount) {
        val bx = rng.rndi(10, GW - 10)
        val by = rng.rndi(10, GH - 10)
        val br = rng.rndi(3, 7)
        for (dy in -br..br) {
            for (dx in -br..br) {
                if (dx * dx + dy * dy <= br * br) {
                    val nx = (bx + dx).coerceIn(0, GW - 1)
                    val ny = (by + dy).coerceIn(0, GH - 1)
                    val idx = ny * GW + nx
                    u[idx] = 0.5f
                    v[idx] = 0.25f
                }
            }
        }
    }

    // --- Run simulation ---
    repeat(steps) {
        for (y in 0 until GH) {
            for (x in 0 until GW) {
                val idx = y * GW + x

                // Wrap-around neighbors for 3x3 Laplacian
                val xL = if (x == 0) GW - 1 else x - 1
                val xR = if (x == GW - 1) 0 else x + 1
                val yU = if (y == 0) GH - 1 else y - 1
                val yD = if (y == GH - 1) 0 else y + 1

                // 3x3 discrete Laplacian (center weight -1, cardinal 0.2, diagonal 0.05)
                val uC = u[idx]
                val vC = v[idx]

                val lapU = (
                    u[yU * GW + xL] * 0.05f +
                    u[yU * GW + x ] * 0.20f +
                    u[yU * GW + xR] * 0.05f +
                    u[y  * GW + xL] * 0.20f +
                    uC               * -1.0f +
                    u[y  * GW + xR] * 0.20f +
                    u[yD * GW + xL] * 0.05f +
                    u[yD * GW + x ] * 0.20f +
                    u[yD * GW + xR] * 0.05f
                )
                val lapV = (
                    v[yU * GW + xL] * 0.05f +
                    v[yU * GW + x ] * 0.20f +
                    v[yU * GW + xR] * 0.05f +
                    v[y  * GW + xL] * 0.20f +
                    vC               * -1.0f +
                    v[y  * GW + xR] * 0.20f +
                    v[yD * GW + xL] * 0.05f +
                    v[yD * GW + x ] * 0.20f +
                    v[yD * GW + xR] * 0.05f
                )

                val uvv = uC * vC * vC
                uNext[idx] = (uC + dt * (Du * lapU - uvv + f * (1.0f - uC))).coerceIn(0f, 1f)
                vNext[idx] = (vC + dt * (Dv * lapV + uvv - (f + k) * vC)).coerceIn(0f, 1f)
            }
        }
        // Swap buffers
        System.arraycopy(uNext, 0, u, 0, GW * GH)
        System.arraycopy(vNext, 0, v, 0, GH * GW)
    }

    // --- Render: threshold V to binary, neon on black ---
    // Hard-edge geometric: flat fills, no transparency, no smoothing.
    // Neon on black: only cells with V > threshold glow.

    // Pick a single vivid neon color from rng: cyan, magenta, lime, or orange-red
    val neonColors = intArrayOf(
        (0xFF shl 24) or (0x00 shl 16) or (0xFF shl 8) or 0xFF, // cyan
        (0xFF shl 24) or (0xFF shl 16) or (0x00 shl 8) or 0xFF, // magenta
        (0xFF shl 24) or (0x00 shl 16) or (0xFF shl 8) or 0x00, // lime
        (0xFF shl 24) or (0xFF shl 16) or (0x45 shl 8) or 0x00, // orange-red
    )
    val neonColor = neonColors[rng.rndi(neonColors.size)]

    val threshold = 0.18f   // low threshold: more cells lit (dense pattern)

    val fillBlack = fillOf(CssColors.black)
    val fillNeon  = fillOf(neonColor)

    // Each simulation cell maps to a block of pixels on the canvas
    val cellW = d.wf / GW
    val cellH = d.hf / GH

    // Fill background black first
    c.clear(CssColors.black)

    // Draw only the "on" cells (V > threshold) in neon; background stays black
    for (y in 0 until GH) {
        for (x in 0 until GW) {
            val vVal = v[y * GW + x]
            if (vVal > threshold) {
                val px = x * cellW
                val py = y * cellH
                c.drawRectWH(px, py, cellW, cellH, fillNeon)
            }
        }
    }

    gart.saveImage(g)
}
