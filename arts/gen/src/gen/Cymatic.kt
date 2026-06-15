package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.math.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD #8 · Chladni cymatics plate ───────────────────────────────────────────
// The standing-wave equation of a vibrating square plate: a sum of antisymmetric
// modes cos(nπx)cos(mπy) − cos(mπx)cos(nπy). Sand collects on the nodal lines where
// the sum is ~0; here those lines glow gold (coolors "Navy & Gold") over a deep navy
// field whose tone follows the vibration amplitude. Showcases: math / superposition.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("Cymatic", SIZE, SIZE)
    val d = gart.d
    val pal = Coolors.navyGold
    val deep = 0xFF000814.toInt()
    val sand = 0xFFFFD60A.toInt()
    val fieldRamp = pal.expand(256)

    // 3–6 randomly weighted modes
    val modes = rng.rndi(3, 7)
    val nn = IntArray(modes) { rng.rndi(1, 8) }
    val mm = IntArray(modes) { rng.rndi(1, 8) }
    val ww = FloatArray(modes) { rng.rndf(-1f, 1f) }
    println("  $modes modes")

    val pif = PI.toFloat()
    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        val y = py.toFloat() / d.h
        for (px in 0 until d.w) {
            val x = px.toFloat() / d.w
            var f = 0f
            for (k in 0 until modes) {
                val a = nn[k] * pif; val b = mm[k] * pif
                f += ww[k] * (cos(a * x) * cos(b * y) - cos(b * x) * cos(a * y))
            }
            val amp = abs(f)
            // nodal lines: where |f| is tiny → sand grains glow
            val nodal = (1f - smoothstep01(amp, 0.0f, 0.06f))   // 1 on the line, →0 off it
            val baseT = (amp * 0.5f).coerceIn(0f, 1f)
            val base = fieldRamp.bound(baseT * (fieldRamp.size - 1))
            val col = mix(base, sand, nodal)
            gm[px, py] = darken(col, 0.30f + 0.70f * (baseT * 0.6f + nodal))
        }
    }

    // dark-ground glow looks great here: bloom the gold lines
    val finalv = bloom(gart, gm.image(), deep, SIZE / 320f, grain = 0.05f)
    gart.saveImage(finalv)
}

private fun smoothstep01(x: Float, e0: Float, e1: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun mix(a: Int, b: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
    val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
    val r = (ar + (br - ar) * tt).toInt()
    val g = (ag + (bg - ag) * tt).toInt()
    val bl = (ab + (bb - ab) * tt).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
}
