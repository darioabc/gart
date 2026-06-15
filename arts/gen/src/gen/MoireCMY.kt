package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.math.*
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD #6 · Moiré interference of three ink screens ──────────────────────────
// Three concentric ring-gratings at slightly offset centres and frequencies, each
// printed in one ink from the coolors "Patriot Gold" palette, multiplied together
// like overlapping transparencies. The tiny frequency mismatch blooms into giant
// moiré rosettes. Pure trig op-art. Showcases: math/trig + per-pixel Gartmap paint.
private class Screen(val cx: Float, val cy: Float, val freq: Float, val phase: Float, val ink: Int)

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("MoireCMY", SIZE, SIZE)
    val d = gart.d

    // three inks: red, deep-blue, gold from Patriot Gold
    val inks = intArrayOf(0xFFD62828.toInt(), 0xFF003049.toInt(), 0xFFF77F00.toInt())
    val baseFreq = TAUf * rng.rndf(26f, 40f) / SIZE
    val screens = Array(3) { i ->
        Screen(
            cx = d.cx + rng.rndf(-0.10f, 0.10f) * SIZE,
            cy = d.cy + rng.rndf(-0.10f, 0.10f) * SIZE,
            freq = baseFreq * (1f + i * rng.rndf(0.012f, 0.03f)),  // tiny mismatch → moiré
            phase = rng.rndf(0f, TAUf),
            ink = inks[i]
        )
    }

    val cream = 0xFFEAE2B7.toInt()
    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            var rr = 1f; var gg = 1f; var bb = 1f   // start white, multiply down
            for (s in screens) {
                val dist = hypot(px - s.cx, py - s.cy)
                // ink coverage of this ring screen at this pixel, 0..1
                val cov = ((sin(dist * s.freq + s.phase) + 1f) * 0.5f)
                val coverage = smooth(cov, 0.42f, 0.58f)   // crisp-ish rings
                val ir = ((s.ink shr 16) and 0xFF) / 255f
                val ig = ((s.ink shr 8) and 0xFF) / 255f
                val ib = (s.ink and 0xFF) / 255f
                rr *= (1f - coverage * (1f - ir))
                gg *= (1f - coverage * (1f - ig))
                bb *= (1f - coverage * (1f - ib))
            }
            // tint the unprinted paper toward the cream ground
            val cr2 = ((cream shr 16) and 0xFF) / 255f
            val cg2 = ((cream shr 8) and 0xFF) / 255f
            val cb2 = (cream and 0xFF) / 255f
            val fr = (rr * cr2 * 255).toInt().coerceIn(0, 255)
            val fg = (gg * cg2 * 255).toInt().coerceIn(0, 255)
            val fb = (bb * cb2 * 255).toInt().coerceIn(0, 255)
            gm[px, py] = argb(255, fr, fg, fb)
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.05f)
    gart.saveImage(finalv)
    println("  done (baseFreq=$baseFreq)")
}

private fun smooth(x: Float, e0: Float, e1: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
