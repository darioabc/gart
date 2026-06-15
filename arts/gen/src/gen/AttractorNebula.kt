package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.attractor.CliffordAttractor
import dev.oblac.gart.math.*
import kotlin.math.ln
import kotlin.random.Random
import org.jetbrains.skia.Point3

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD #1 · Strange-attractor nebula ────────────────────────────────────────
// A Clifford attractor iterated millions of times, accumulated into a density
// histogram, then log-tone-mapped through a CET fire colormap and bloomed into a
// glowing dust cloud. No two seeds land on the same coefficients → a fresh nebula
// every run. Showcases: attractor engine + Palette.expand + SCREEN bloom.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("AttractorNebula", SIZE, SIZE)
    val d = gart.d

    // Random Clifford coefficients in the chaotic-but-pretty band.
    val a = rng.rndf(-2.0f, -1.2f)
    val b = rng.rndf(-2.4f, -1.6f)
    val cc = rng.rndf(0.9f, 1.6f)
    val dd = rng.rndf(-1.2f, -0.5f)
    val att = CliffordAttractor(a, b, cc, dd)
    println("  clifford a=$a b=$b c=$cc d=$dd")

    val iters = SIZE * SIZE * 6          // ~6 samples/pixel → dense smoke
    val warmup = 2000

    // Pass 1 — find the attractor's bounding box.
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    var p = CliffordAttractor.initialPoint
    repeat(iters / 3) { i ->
        p = att.compute(p, 0f)
        if (i > warmup) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
    }
    val pad = 0.06f
    val spanX = (maxX - minX); val spanY = (maxY - minY)
    minX -= spanX * pad; maxX += spanX * pad
    minY -= spanY * pad; maxY += spanY * pad

    // Pass 2 — accumulate a density histogram.
    val hist = IntArray(d.area)
    p = CliffordAttractor.initialPoint
    var peak = 1
    repeat(iters) { i ->
        p = att.compute(p, 0f)
        if (i > warmup) {
            val px = map(p.x, minX, maxX, 1f, (d.w - 2).toFloat()).toInt()
            val py = map(p.y, minY, maxY, 1f, (d.h - 2).toFloat()).toInt()
            val idx = py * d.w + px
            val v = hist[idx] + 1
            hist[idx] = v
            if (v > peak) peak = v
        }
    }

    // Tone-map: log density → coolors "Retro Pop" ramp, dark cores fall to near-black.
    val ramp = Coolors.retroPop.expand(256)
    val logPeak = ln(1f + peak.toFloat())
    val gm = Gartmap(gart.gartvas())
    val ground = 0xFF05060A.toInt()
    for (i in hist.indices) {
        val cnt = hist[i]
        if (cnt == 0) { gm.pixels[i] = ground; continue }
        val t = (ln(1f + cnt.toFloat()) / logPeak).coerceIn(0f, 1f)
        val col = ramp.bound(t * (ramp.size - 1))
        gm.pixels[i] = darken(col, 0.12f + 0.88f * t)
    }

    val finalv = bloom(gart, gm.image(), ground, SIZE / 220f, grain = 0.06f)
    gart.saveImage(finalv)
}
