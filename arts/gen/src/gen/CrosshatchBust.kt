package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.sqrt
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD+ #7 · Cross-hatch engraving of a sphere ───────────────────────────────
// A Lambert-shaded sphere rendered the way an engraver would: tone is built only from
// line density. One horizontal screen everywhere, a second (vertical) added in the
// mid-tones, a third (diagonal) in the shadows — the classic banknote / old-master
// cross-hatch. Single ink from coolors "Sunset" on warm paper. Showcases: value-driven
// multi-angle line screens (pure trig).
private fun frac(x: Float): Float = x - kotlin.math.floor(x)

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("CrosshatchBust", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)

    val cx = d.cx + rng.rndf(-0.05f, 0.05f) * SIZE
    val cy = d.cy + rng.rndf(-0.05f, 0.05f) * SIZE
    val R = SIZE * 0.42f
    val lx = rng.rndf(-0.6f, -0.2f); val ly = rng.rndf(-0.7f, -0.3f); val lz = 0.7f
    val sp = SIZE / 130f                       // hatch line spacing
    val ink = 0xFF243A45.toInt(); val paper = 0xFFF3E2BE.toInt()

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val nx = (px - cx) / R; val ny = (py - cy) / R
            val r2 = nx * nx + ny * ny
            var darkness: Float
            if (r2 > 1f) {
                darkness = 0f                  // background paper
            } else {
                val nz = sqrt(1f - r2)
                val lambert = (nx * lx + ny * ly + nz * lz).coerceIn(0f, 1f)
                val tex = noise.random3D(nx * 3f, ny * 3f, 0f).toFloat() * 0.12f
                darkness = (1f - (0.12f + 0.88f * lambert) + tex).coerceIn(0f, 1f)
                darkness *= darkness           // deepen contrast
            }

            var inkHere = false
            // 1) horizontal screen everywhere there's any tone
            if (frac(py / sp) < darkness) inkHere = true
            // 2) vertical screen in the mid-tones
            if (!inkHere && darkness > 0.42f && frac(px / sp) < (darkness - 0.38f) * 1.6f) inkHere = true
            // 3) diagonal screen in the shadows
            if (!inkHere && darkness > 0.68f && frac((px + py) / sp) < (darkness - 0.62f) * 2.2f) inkHere = true

            gm[px, py] = if (inkHere) ink else paper
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.06f)
    gart.saveImage(finalv)
    println("  done (3-pass cross-hatch)")
}
