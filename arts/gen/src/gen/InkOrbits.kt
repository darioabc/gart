package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.pathOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*       // brings all Random extension fns into scope
import kotlin.math.sqrt
import kotlin.random.Random

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

// N-body / Monochrome / Minimal:
//   3–5 bodies, long trails, high-contrast background (ivory + near-black ink).
//   Background dominates; sparse coverage.

private const val G = 1200.0       // gravitational constant (scaled to canvas units)
private const val DT = 0.08        // time step
private const val STEPS = 4000     // trail length — long trails per Minimal directive
private const val SOFTENING = 4.0  // softening to avoid singularity at close encounters

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("InkOrbits", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // Fill background with ivory (Monochrome/ink theme)
    c.clear(CssColors.ivory)

    // --- Build initial conditions ---
    // Number of bodies: 3–5 (Minimal directive)
    val nBodies = rng.rndi(3, 6)

    // Place bodies in a ring near the canvas center with random angles,
    // give them tangential velocities for roughly circular orbits.
    val scale = SIZE * 0.22   // orbital radius as fraction of canvas
    val centerX = d.cx.toDouble()
    val centerY = d.cy.toDouble()

    // Body state arrays: position (x,y), velocity (vx,vy), mass
    val bx   = DoubleArray(nBodies)
    val by   = DoubleArray(nBodies)
    val bvx  = DoubleArray(nBodies)
    val bvy  = DoubleArray(nBodies)
    val mass = DoubleArray(nBodies)

    for (i in 0 until nBodies) {
        val angle = 2.0 * Math.PI * i / nBodies + rng.rndf(-0.3f, 0.3f)
        val r = scale * rng.rndf(0.6f, 1.0f)
        bx[i] = centerX + r * kotlin.math.cos(angle)
        by[i] = centerY + r * kotlin.math.sin(angle)
        mass[i] = rng.rndf(60f, 140f).toDouble()
    }

    // Compute center-of-mass velocity so the system stays roughly centered
    // Give each body a tangential kick proportional to sqrt(G*totalMass/r)
    val totalMass = mass.sum()
    for (i in 0 until nBodies) {
        val dx = bx[i] - centerX
        val dy = by[i] - centerY
        val r = sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
        val v = sqrt(G * totalMass * 0.15 / r)  // scaled circular velocity
        // Tangential direction: perpendicular to radius vector
        bvx[i] = -dy / r * v + rng.rndf(-0.5f, 0.5f) * v * 0.3
        bvy[i] =  dx / r * v + rng.rndf(-0.5f, 0.5f) * v * 0.3
    }

    // Remove bulk drift: subtract mass-weighted mean velocity
    val cmvx = (0 until nBodies).sumOf { mass[it] * bvx[it] } / totalMass
    val cmvy = (0 until nBodies).sumOf { mass[it] * bvy[it] } / totalMass
    for (i in 0 until nBodies) { bvx[i] -= cmvx; bvy[i] -= cmvy }

    // --- Simulate and accumulate trails ---
    // Each body gets a List<Point> trajectory; we draw once at the end.
    val trails = Array(nBodies) { mutableListOf<org.jetbrains.skia.Point>() }

    // Record the initial positions
    for (i in 0 until nBodies) {
        trails[i].add(org.jetbrains.skia.Point(bx[i].toFloat(), by[i].toFloat()))
    }

    // Temporary acceleration buffers
    val ax = DoubleArray(nBodies)
    val ay = DoubleArray(nBodies)

    repeat(STEPS) {
        // Velocity-Verlet: first half-kick, then drift, then recompute forces, then half-kick
        // --- compute accelerations ---
        ax.fill(0.0); ay.fill(0.0)
        for (i in 0 until nBodies) {
            for (j in i + 1 until nBodies) {
                val dx = bx[j] - bx[i]
                val dy = by[j] - by[i]
                val dist2 = dx * dx + dy * dy + SOFTENING * SOFTENING
                val dist = sqrt(dist2)
                val f = G / (dist2 * dist)   // G / r^3
                val fij = f * mass[j]
                val fji = f * mass[i]
                ax[i] += fij * dx;  ay[i] += fij * dy
                ax[j] -= fji * dx;  ay[j] -= fji * dy
            }
        }
        // --- update velocities and positions (Euler for simplicity) ---
        for (i in 0 until nBodies) {
            bvx[i] += ax[i] * DT
            bvy[i] += ay[i] * DT
            bx[i]  += bvx[i] * DT
            by[i]  += bvy[i] * DT
            trails[i].add(org.jetbrains.skia.Point(bx[i].toFloat(), by[i].toFloat()))
        }
    }

    // --- Draw trails as thin dark polylines ---
    // Minimal/sparse: very thin stroke, ink-black, background dominates.
    // Use slightly different darkness per body for subtle differentiation.
    val inkColors = listOf(
        0xFF0A0A0A.toInt(),   // near black
        0xFF1A1A1A.toInt(),   // dark charcoal
        0xFF2B2B2B.toInt(),   // dark gray
        0xFF3C3C3C.toInt(),   // medium dark gray
        0xFF4D4D4D.toInt(),   // gray
    )
    val strokeWidth = SIZE * 0.0006f   // hairline relative to canvas size

    for (i in 0 until nBodies) {
        val pts = trails[i]
        if (pts.size < 2) continue

        // Draw as a polyline using pathOf
        val paint = strokeOf(inkColors[i % inkColors.size], strokeWidth)
        val path = pathOf(pts)
        c.drawPath(path, paint)

        // Mark body's final resting dot — tiny filled circle in same ink
        val last = pts.last()
        val dotRadius = SIZE * 0.003f
        val dotPaint = dev.oblac.gart.gfx.fillOf(inkColors[i % inkColors.size])
        c.drawCircle(last.x, last.y, dotRadius, dotPaint)
    }

    gart.saveImage(g)
}
