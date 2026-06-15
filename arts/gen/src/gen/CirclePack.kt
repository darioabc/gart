package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM #5 · Greedy circle packing ──────────────────────────────────────────
// Throw points at the canvas; each grows the largest circle that fits without
// touching its neighbours or the frame. Big discs claim the open space first, then
// ever-smaller ones cram the gaps — the dense, all-over tessellation of foam or fish
// roe. Discs are tinted by a slow noise field through the coolors "Sunset" ramp, on
// deep teal, so colour drifts in continents across the packing.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("CirclePack", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val ramp = Coolors.sunset.expand(256)
    val ground = 0xFF0E2A33.toInt()        // deep teal (darkest Sunset stop, deepened)

    val attempts = 42000
    val maxR = SIZE * 0.090f
    val minR = SIZE * 0.0055f
    val gap = SIZE * 0.0045f

    val cxs = ArrayList<Float>(); val cys = ArrayList<Float>(); val crs = ArrayList<Float>()
    repeat(attempts) {
        val x = rng.rndf(0f, d.wf); val y = rng.rndf(0f, d.hf)
        var r = min(min(x, d.wf - x), min(y, d.hf - y)).coerceAtMost(maxR)
        for (i in crs.indices) {
            val dd = hypot(x - cxs[i], y - cys[i]) - crs[i] - gap
            if (dd < r) r = dd
            if (r < minR) break
        }
        if (r >= minR) { cxs.add(x); cys.add(y); crs.add(r) }
    }
    println("  ${crs.size} circles packed")

    val out = gart.gartvas()
    val c = out.canvas
    c.clear(ground)
    val outline = strokeOf(ground, SIZE * 0.0016f)
    for (i in crs.indices) {
        val nv = (noise.random2D(cxs[i] * 0.0022f, cys[i] * 0.0022f).toFloat() * 0.5f + 0.5f).coerceIn(0f, 1f)
        val col = ramp.bound(nv * (ramp.size - 1))
        c.drawCircle(cxs[i], cys[i], crs[i], fillOf(col))
        c.drawCircle(cxs[i], cys[i], crs[i], outline)
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
