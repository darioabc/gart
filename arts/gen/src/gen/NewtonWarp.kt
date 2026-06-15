package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.pixels.conformalWarp
import dev.oblac.gart.SampleMode
import dev.oblac.gart.math.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD #2 · Newton fractal, run through a conformal log-polar warp ───────────
// Per pixel we solve z^k - 1 = 0 by Newton's method, colour the basin of the root
// it converges to (hue) and darken by iteration count (the fractal filigree on the
// boundaries). Then the whole plane is fed through gȧrt's conformalWarp, which
// re-maps it into a logarithmic spiral ring — a hypnotic kaleidoscopic eye.
// Showcases: complex math from first principles + pixels/conformalWarp.

private fun cpow(re: Double, im: Double, k: Int): Pair<Double, Double> {
    var rr = 1.0; var ii = 0.0
    repeat(k) {
        val nr = rr * re - ii * im
        val ni = rr * im + ii * re
        rr = nr; ii = ni
    }
    return rr to ii
}

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("NewtonWarp", SIZE, SIZE)
    val d = gart.d

    val k = rng.rndi(3, 7)                       // z^k - 1
    val maxIter = 48
    val tol = 1e-6
    println("  polynomial z^$k - 1")

    // k distinct root hues pulled from the coolors "Electric Grape" ramp.
    val ramp = Coolors.electricGrape
    val roots = Array(k) { i ->
        val ang = TAUf * i / k
        doubleArrayOf(cos(ang.toDouble()), sin(ang.toDouble()))
    }

    val src = Gartmap(gart.gartvas())
    val zoom = rng.rndf(1.8f, 2.8f)
    for (py in 0 until d.h) {
        val ci = map(py, 0, d.h, zoom, -zoom).toDouble()
        for (px in 0 until d.w) {
            val cr = map(px, 0, d.w, -zoom, zoom).toDouble()
            var zr = cr; var zi = ci
            var iter = 0
            while (iter < maxIter) {
                // f = z^k - 1 ; f' = k z^(k-1)
                val (fk, fik) = cpow(zr, zi, k)
                val fr = fk - 1.0; val fi = fik
                val (dk, dik) = cpow(zr, zi, k - 1)
                val dr = k * dk; val di = k * dik
                val den = dr * dr + di * di
                if (den < 1e-18) break
                // z = z - f/f'
                val qr = (fr * dr + fi * di) / den
                val qi = (fi * dr - fr * di) / den
                zr -= qr; zi -= qi
                if (hypot(qr, qi) < tol) break
                iter++
            }
            // Which root did we land on?
            var best = 0; var bestD = Double.MAX_VALUE
            for (r in 0 until k) {
                val dx = zr - roots[r][0]; val dy = zi - roots[r][1]
                val dd = dx * dx + dy * dy
                if (dd < bestD) { bestD = dd; best = r }
            }
            // iteration count gives the filigree; an angular band breaks up the big
            // flat basins so the conformal warp never magnifies a dead uniform patch.
            val shade = 1f - (iter.toFloat() / maxIter) * 0.85f
            val theta = atan2(zi, zr).toFloat()
            val band = 0.5f + 0.5f * sin(theta * 3f + iter * 0.25f)
            val f = (0.22f + 0.78f * shade) * (0.65f + 0.35f * band)
            src[px, py] = darken(ramp[best % ramp.size], f.coerceIn(0f, 1f))
        }
    }

    // Conformal log-polar warp into a spiral ring.
    val warped = conformalWarp(
        src,
        rInner = rng.rndf(0.18f, 0.4f).toDouble(),
        rOuter = rng.rndf(2.0f, 3.2f).toDouble(),
        sampleMode = SampleMode.TILE
    )
    gart.saveImage(warped.image())
    println("  done (zoom=$zoom)")
}
