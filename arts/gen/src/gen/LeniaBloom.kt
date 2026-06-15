package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.math.*
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import org.jetbrains.skia.Image

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM · Lenia continuous cellular automata ────────────────────────────────
// Lenia (Bert Chan): a continuous-state, continuous-kernel generalisation of Life.
// A smooth ring kernel is convolved against the field each step and run through a
// Gaussian growth law; gliders and lobed "orbium"-like organisms emerge and crawl.
// Simulated cheaply at low res, then upscaled into a Gartmap and coloured through
// the "Molten" ramp over a dark ground so living tissue glows.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("LeniaBloom", SIZE, SIZE)
    val d = gart.d

    // ── simulation grid (resolution independent of SIZE) ──────────────────────
    val N = 320                          // sim grid is N×N, toroidal
    val R = 13                           // kernel radius
    val mu = 0.15f                       // growth centre
    val sigma = 0.045f                   // growth width (razor-thin Orbium sigma=0.017
                                         // makes random seeds decay to zero; widening
                                         // it lands a robust, persistent lobed regime)
    val dt = 0.10f                       // time step
    val steps = 150

    var field = FloatArray(N * N)
    val next = FloatArray(N * N)

    // ── precompute the smooth ring kernel (normalised to sum 1) ───────────────
    // Shell radius beta=0.5, bell-shaped radial profile peaking at half R.
    val kR = R
    val kSize = 2 * kR + 1
    val kernel = FloatArray(kSize * kSize)
    var kSum = 0f
    for (dy in -kR..kR) for (dx in -kR..kR) {
        val r = hypot(dx.toFloat(), dy.toFloat()) / kR     // 0..~1.41
        var w = 0f
        if (r > 0.0f && r < 1.0f) {
            // single-bump kernel: exp(-(r-0.5)^2 / (2*0.15^2))
            val a = (r - 0.5f)
            w = exp(-(a * a) / (2f * 0.15f * 0.15f))
        }
        kernel[(dy + kR) * kSize + (dx + kR)] = w
        kSum += w
    }
    for (i in kernel.indices) kernel[i] /= kSum

    // ── seed a handful of random blobs, off-centre with empty space ───────────
    fun seedBlob(cxN: Int, cyN: Int, rad: Int, dens: Float) {
        for (dy in -rad..rad) for (dx in -rad..rad) {
            if (dx * dx + dy * dy > rad * rad) continue
            val x = ((cxN + dx) % N + N) % N
            val y = ((cyN + dy) % N + N) % N
            val fall = 1f - hypot(dx.toFloat(), dy.toFloat()) / rad
            field[y * N + x] = min(1f, field[y * N + x] + dens * fall * rng.rndf(0.5f, 1.0f))
        }
    }
    // cluster the organisms toward the lower-left third — leave the rest open
    val blobs = rng.rndi(5, 9)
    repeat(blobs) {
        val bx = rng.rndi(N / 6, N * 3 / 5)
        val by = rng.rndi(N * 2 / 5, N * 9 / 10)
        seedBlob(bx, by, rng.rndi(R, R * 2), rng.rndf(0.6f, 1.0f))
    }

    // ── run the CA ─────────────────────────────────────────────────────────
    fun growth(u: Float): Float {
        val a = (u - mu)
        return 2f * exp(-(a * a) / (2f * sigma * sigma)) - 1f
    }
    repeat(steps) {
        for (y in 0 until N) {
            for (x in 0 until N) {
                var u = 0f
                var ki = 0
                for (dy in -kR..kR) {
                    val yy = ((y + dy) % N + N) % N * N
                    for (dx in -kR..kR) {
                        val xx = ((x + dx) % N + N) % N
                        u += field[yy + xx] * kernel[ki++]
                    }
                }
                val v = field[y * N + x] + dt * growth(u)
                next[y * N + x] = if (v < 0f) 0f else if (v > 1f) 1f else v
            }
        }
        System.arraycopy(next, 0, field, 0, field.size)
    }

    // measure the living field range for a tighter tone map
    var fmax = 1e-6f
    for (v in field) if (v > fmax) fmax = v
    println("  lenia ran $steps steps, fmax=$fmax")

    // ── upscale into a Gartmap, colour through Molten over dark ──────────────
    val ramp = Coolors.molten.expand(256)
    val gm = Gartmap(gart.gartvas())
    val ground = 0xFF0A0306.toInt()
    val scale = SIZE.toFloat() / N
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            // bilinear sample of the sim field (toroidal)
            val sx = px / scale
            val sy = py / scale
            val x0 = floor(sx).toInt(); val y0 = floor(sy).toInt()
            val fx = sx - x0; val fy = sy - y0
            val x1 = (x0 + 1) % N; val y1 = (y0 + 1) % N
            val xa = ((x0 % N) + N) % N; val ya = ((y0 % N) + N) % N
            val v00 = field[ya * N + xa]; val v10 = field[ya * N + x1]
            val v01 = field[y1 * N + xa]; val v11 = field[y1 * N + x1]
            val v = (v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy
            val t = (v / fmax).coerceIn(0f, 1f)
            val col = if (t < 0.04f) ground
            else ramp.bound((sqrt(t) * (ramp.size - 1)))
            gm[px, py] = col
        }
    }

    val img: Image = gm.image()
    val finalv = bloom(gart, img, ground, SIZE * 0.006f, grain = 0.06f)
    gart.saveImage(finalv)
}
