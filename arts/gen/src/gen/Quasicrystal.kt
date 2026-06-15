package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.math.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── Quasicrystal interference field ────────────────────────────────────────────
// Sum of N=7 plane waves cos(k·(cosθ·x + sinθ·y) + φ) with θ evenly spaced over π
// produces a quasiperiodic (Penrose-like) tiling with 7-fold symmetry. A second,
// slightly rotated and rescaled wave set is multiplied in for moiré depth. The summed
// field is sharpened with smoothstep so the bands read as crisp quasicrystal cells,
// then mapped through retroPop expanded to 256. Per-pixel Gartmap, grainOnly finish.
// Showcases: quasiperiodic wave superposition.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("Quasicrystal", SIZE, SIZE)
    val d = gart.d

    val N = 7                                  // odd → true quasicrystal symmetry
    val k = rng.rndf(22f, 32f)                 // spatial frequency (waves across frame)
    val phases = FloatArray(N) { rng.rndf(0f, TAUf) }

    // second wave set: rotated + scaled for moiré beat
    val rot = rng.rndf(0.06f, 0.16f)           // small rotation offset
    val kScale = rng.rndf(1.04f, 1.12f)        // slight frequency mismatch
    val phases2 = FloatArray(N) { rng.rndf(0f, TAUf) }

    // precompute wave directions
    val dirA = Array(N) { i -> val a = PI.toFloat() * i / N; floatArrayOf(cos(a), sin(a)) }
    val dirB = Array(N) { i -> val a = PI.toFloat() * i / N + rot; floatArrayOf(cos(a), sin(a)) }

    val ramp = Coolors.retroPop.expand(256)
    val gm = Gartmap(gart.gartvas())

    val invHalf = 2f / SIZE                     // map pixel → [-1,1]

    for (py in 0 until d.h) {
        val y = (py - d.cy) * invHalf
        for (px in 0 until d.w) {
            val x = (px - d.cx) * invHalf

            // first quasicrystal field, accumulated as cos sum then folded to [0,1]
            var s1 = 0f
            for (i in 0 until N) {
                val dd = dirA[i]
                s1 += cos(k * (dd[0] * x + dd[1] * y) + phases[i])
            }
            // normalise N-wave sum (range roughly [-N,N]) → [0,1]
            var v1 = (s1 / N + 1f) * 0.5f

            // second, moiré set
            var s2 = 0f
            for (i in 0 until N) {
                val dd = dirB[i]
                s2 += cos(k * kScale * (dd[0] * x + dd[1] * y) + phases2[i])
            }
            var v2 = (s2 / N + 1f) * 0.5f

            // crisp bands: smoothstep sharpens the soft cosine ridges into tiling cells
            v1 = smoothstep(0.40f, 0.60f, v1)
            v2 = smoothstep(0.42f, 0.58f, v2)

            // multiply the two fields for interference depth, then re-sharpen subtly
            var t = v1 * 0.62f + v2 * 0.38f
            // add a faint radial vignette to seat the focal centre
            val rr = (x * x + y * y)
            t = (t * (1f - 0.18f * rr)).coerceIn(0f, 1f)
            // contour emphasis: a thin dark seam where the field crosses mid grey
            val seam = smoothstep(0.0f, 0.06f, kotlin.math.abs(t - 0.5f))

            var col = ramp.bound(t * (ramp.size - 1))
            if (seam < 1f) col = darken(col, 0.35f + 0.65f * seam)
            gm[px, py] = col
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.05f)
    gart.saveImage(finalv)
    println("  done (k=$k rot=$rot kScale=$kScale)")
}
