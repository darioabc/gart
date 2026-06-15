package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.math.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── Magnetic pendulum — fractal basins of attraction ───────────────────────────
// Every pixel is the start position (velocity 0) of a damped pendulum bob pulled by
// 3–4 fixed magnets plus a central restoring spring. The ODE is integrated per-pixel
// until the bob settles near a magnet; the pixel is coloured by WHICH magnet won
// (one hue per magnet from the coolors "Navy & Gold" ramp) and shaded by settle-time
// (faster = brighter) via darken(). The interleaved win-zones form an infinitely
// detailed fractal — chaos hiding in a tiny ODE. Showcases: dynamical-systems basins.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("MagneticBasins", SIZE, SIZE)
    val d = gart.d

    // ── magnets on a regular polygon, slightly jittered + rotated ──────────────
    val magCount = rng.rndi(3, 5)               // 3 or 4 magnets
    val baseRot = rng.rndf(0f, TAUf)
    val ring = rng.rndf(0.28f, 0.40f)           // magnet ring radius in normalised units
    val mx = FloatArray(magCount)
    val my = FloatArray(magCount)
    for (i in 0 until magCount) {
        val a = baseRot + TAUf * i / magCount
        val rr = ring * (1f + rng.rndf(-0.06f, 0.06f))
        mx[i] = cos(a) * rr
        my[i] = sin(a) * rr
    }

    // ── per-magnet hue: pull distinct stops from navyGold, force chroma ─────────
    val pal = Coolors.navyGold.expand(256)
    val hues = IntArray(magCount) { i ->
        // spread across the warm/gold half of the ramp so basins are clearly tinted
        val t = if (magCount <= 1) 0.5f else i.toFloat() / (magCount - 1)
        pal.bound((0.20f + 0.78f * t) * (pal.size - 1))
    }

    // ── physics constants ──────────────────────────────────────────────────────
    val k = 0.18f          // central spring — confines the bob over the magnet field
    val friction = 0.12f   // moderate damping → most starts get captured within maxSteps
    val strength = 0.55f   // magnet pull (dominant near a magnet)
    val soft = 0.005f      // small softening → sharp pull near a magnet
    val dt = 0.08f
    val maxSteps = 420
    val captureR2 = (ring * 0.34f) * (ring * 0.34f)  // proximity capture radius²

    val gm = Gartmap(gart.gartvas())
    val scale = 1f / (SIZE * 0.42f)   // pixels -> normalised world coords

    var px = 0
    while (px < d.w) {
        var py = 0
        while (py < d.h) {
            // start position in normalised coords (centre = origin)
            var x = (px - d.cx) * scale
            var y = (py - d.cy) * scale
            var vx = 0f
            var vy = 0f

            var winner = -1
            var settleStep = maxSteps
            var step = 0
            while (step < maxSteps) {
                // restoring spring toward centre
                var ax = -k * x
                var ay = -k * y
                // magnet attraction (inverse-square-ish with softening)
                var m = 0
                while (m < magCount) {
                    val dx = mx[m] - x
                    val dy = my[m] - y
                    val r2 = dx * dx + dy * dy + soft
                    val inv = strength / (r2 * sqrt(r2))
                    ax += dx * inv
                    ay += dy * inv
                    m++
                }
                // friction
                ax -= friction * vx
                ay -= friction * vy

                // semi-implicit Euler
                vx += ax * dt
                vy += ay * dt
                x += vx * dt
                y += vy * dt

                // capture test: first time the bob enters a magnet's basin it's caught
                // (friction guarantees it stays). The STEP at which this happens is the
                // settle-time — its sensitivity to the start point is the fractal.
                var m2 = 0
                while (m2 < magCount) {
                    val dx = mx[m2] - x
                    val dy = my[m2] - y
                    if (dx * dx + dy * dy < captureR2) {
                        winner = m2
                        settleStep = step
                        break
                    }
                    m2++
                }
                if (winner >= 0) break
                step++
            }

            // if it never settled, assign nearest magnet (basin boundary haze)
            if (winner < 0) {
                var best = Float.MAX_VALUE
                var m3 = 0
                while (m3 < magCount) {
                    val dx = mx[m3] - x
                    val dy = my[m3] - y
                    val dd = dx * dx + dy * dy
                    if (dd < best) { best = dd; winner = m3 }
                    m3++
                }
            }

            // brightness by settle-time: fast convergence = bright, slow = dark
            val tnorm = settleStep.toFloat() / maxSteps
            val bright = 1f - 0.85f * smoothstep(0f, 1f, tnorm)
            val col = darken(hues[winner], 0.20f + 0.80f * bright)
            gm[px, py] = col

            py++
        }
        px++
    }

    // ── overlay the magnets as small bright cores so foci read ─────────────────
    val white = argb(255, 250, 240, 200)
    for (i in 0 until magCount) {
        val cxp = d.cx + mx[i] / scale
        val cyp = d.cy + my[i] / scale
        val rad = (SIZE * 0.010f).toInt().coerceAtLeast(2)
        for (yy in -rad..rad) for (xx in -rad..rad) {
            if (xx * xx + yy * yy <= rad * rad) {
                val gx = (cxp + xx).toInt()
                val gy = (cyp + yy).toInt()
                if (gx in 0 until d.w && gy in 0 until d.h) gm[gx, gy] = white
            }
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.05f)
    gart.saveImage(finalv)
}
