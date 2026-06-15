package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.attractor.CliffordAttractor
import dev.oblac.gart.math.*
import dev.oblac.gart.stipple.stippleVoronoi
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD+ #6 · Weighted-Voronoi stippled nebula ────────────────────────────────
// The two favourites crossed: a Peter de Jong strange attractor builds a density
// field, and the Lloyd-relaxed Voronoi stippler scatters dots that cluster where the
// attractor is dense — a pointillist nebula. Dots glow in coolors "Electric Grape"
// over deep space, bloomed. Showcases: attractor density → stipple/VoronoiStippling.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("StippleNebula", SIZE, SIZE)
    val d = gart.d

    // constrained Clifford coefficients → reliably a space-filling butterfly
    val a = rng.rndf(-2.0f, -1.2f); val b = rng.rndf(-2.4f, -1.6f)
    val cc = rng.rndf(0.9f, 1.6f); val dd = rng.rndf(-1.2f, -0.5f)
    val att = CliffordAttractor(a, b, cc, dd)
    println("  clifford a=$a b=$b c=$cc d=$dd")

    val iters = SIZE * SIZE * 4
    val warmup = 2000
    // pass 1: bounds
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
    val spanX = maxX - minX; val spanY = maxY - minY
    minX -= spanX * 0.06f; maxX += spanX * 0.06f; minY -= spanY * 0.06f; maxY += spanY * 0.06f
    // pass 2: histogram
    val hist = IntArray(d.area)
    var peak = 1
    p = CliffordAttractor.initialPoint
    repeat(iters) { i ->
        p = att.compute(p, 0f)
        if (i > warmup) {
            val px = map(p.x, minX, maxX, 1f, (d.w - 2).toFloat()).toInt().coerceIn(0, d.w - 1)
            val py = map(p.y, minY, maxY, 1f, (d.h - 2).toFloat()).toInt().coerceIn(0, d.h - 1)
            val idx = py * d.w + px
            val v = hist[idx] + 1; hist[idx] = v
            if (v > peak) peak = v
        }
    }

    // density → grayscale where DENSE = DARK (so the stippler clusters dots there)
    val logPeak = ln(1f + peak.toFloat())
    val src = Gartmap(gart.gartvas())
    for (i in hist.indices) {
        val t = if (hist[i] == 0) 0f else (ln(1f + hist[i].toFloat()) / logPeak)
        val g = ((1f - t) * 255).toInt().coerceIn(0, 255)   // dense → 0 (black)
        src.pixels[i] = argb(255, g, g, g)
    }

    val dots = stippleVoronoi(
        src,
        pointCount = (SIZE * SIZE / 90),
        iterations = 14,
        gamma = 1.4f,
        brightnessThreshold = 0.97f,
        minRadius = SIZE * 0.0008f,
        maxRadius = SIZE * 0.0040f,
        seed = SEED.toInt()
    )
    println("  ${dots.size} stipple dots")

    val ramp = Coolors.electricGrape.expand(256)
    val ground = 0xFF080615.toInt()
    val buf = gart.gartvas()
    val c = buf.canvas
    for (dot in dots) {
        val ix = dot.x.toInt().coerceIn(0, d.w - 1); val iy = dot.y.toInt().coerceIn(0, d.h - 1)
        val cnt = hist[iy * d.w + ix]
        val t = if (cnt == 0) 0.15f else (ln(1f + cnt.toFloat()) / logPeak).coerceIn(0f, 1f)
        // dense → bright cyan end of the ramp
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(ramp.bound(t * (ramp.size - 1))))
    }

    val finalv = bloom(gart, buf.snapshot(), ground, SIZE / 360f, grain = 0.05f)
    gart.saveImage(finalv)
}
