package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.math.*
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM #4 · Abelian sandpile ───────────────────────────────────────────────
// Drop a huge stack of grains on one cell; whenever a cell holds ≥4 it topples one
// grain to each neighbour. The avalanche always relaxes to the same intricate
// four-state fractal with perfect dihedral symmetry — a deterministic mandala that
// looks designed but is pure arithmetic. Four states → four coolors "Patriot Gold"
// inks on vanilla paper.
private const val GRID = 511          // odd → single centre cell

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("Sandpile", SIZE, SIZE)
    val d = gart.d

    val grains = rng.rndi(80000, 150000)
    println("  $grains grains on a ${GRID}² lattice")
    val g = IntArray(GRID * GRID)
    val c0 = GRID / 2
    g[c0 * GRID + c0] = grains

    // relax: sweep an expanding active window until every cell holds < 4
    var lo = c0 - 1; var hi = c0 + 1
    val next = IntArray(GRID * GRID)
    var unstable = true
    var sweeps = 0
    while (unstable) {
        unstable = false; sweeps++
        System.arraycopy(g, 0, next, 0, g.size)
        var nlo = lo; var nhi = hi
        for (y in lo..hi) {
            val row = y * GRID
            for (x in lo..hi) {
                val v = g[row + x]
                if (v >= 4) {
                    val give = v / 4
                    next[row + x] -= give * 4
                    next[row + x - 1] += give
                    next[row + x + 1] += give
                    next[row + x - GRID] += give
                    next[row + x + GRID] += give
                    unstable = true
                    if (x - 1 < nlo) nlo = x - 1; if (x + 1 > nhi) nhi = x + 1
                    if (y - 1 < nlo) nlo = y - 1; if (y + 1 > nhi) nhi = y + 1
                }
            }
        }
        System.arraycopy(next, 0, g, 0, g.size)
        lo = nlo.coerceAtLeast(1); hi = nhi.coerceAtMost(GRID - 2)
    }
    println("  relaxed in $sweeps sweeps, radius ${hi - c0}")

    // four states → four inks; empty surround = vanilla paper
    val ink = intArrayOf(0xFF003049.toInt(), 0xFFD62828.toInt(), 0xFFF77F00.toInt(), 0xFFFCBF49.toInt())
    val paper = 0xFFEAE2B7.toInt()

    // crop to the pile's bounding box (+ margin) so the mandala fills the frame
    var minx = GRID; var maxx = 0; var miny = GRID; var maxy = 0
    for (y in 0 until GRID) for (x in 0 until GRID) {
        if (g[y * GRID + x] > 0) {
            if (x < minx) minx = x; if (x > maxx) maxx = x
            if (y < miny) miny = y; if (y > maxy) maxy = y
        }
    }
    val margin = ((maxx - minx) * 0.06f).toInt() + 4
    val half = (maxOf(maxx - c0, c0 - minx, maxy - c0, c0 - miny) + margin)
    val lo2 = (c0 - half).coerceAtLeast(0); val hi2 = (c0 + half).coerceAtMost(GRID - 1)
    val span = (hi2 - lo2 + 1)

    val gm = Gartmap(gart.gartvas())
    for (py in 0 until d.h) {
        val sy = (lo2 + py * span / d.h).coerceIn(0, GRID - 1)
        for (px in 0 until d.w) {
            val sx = (lo2 + px * span / d.w).coerceIn(0, GRID - 1)
            val v = g[sy * GRID + sx]
            gm[px, py] = if (v <= 0) paper else ink[v.coerceIn(0, 3)]
        }
    }

    val finalv = grainOnly(gart, gm.image(), grain = 0.04f)
    gart.saveImage(finalv)
}
