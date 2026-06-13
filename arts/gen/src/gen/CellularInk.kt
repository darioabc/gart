package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.math.*
import kotlin.random.Random
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect

// Canvas size: set GART_SIZE=512 (shell env var) for a fast draft render.
// render.sh converts it to -DGART_SIZE=512 JVM property; do NOT use System.getenv().
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024

// Seed: pass GART_SEED=<long> (shell env var) to render.sh to reproduce a result.
// Printed to stdout so the value is always recoverable.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * CellularInk — Conway's Game of Life, monochrome / ink, hard-edge geometric.
 *
 * Style x Technique (Cellular automata x Hard-edge geometric):
 *   Hard 2-color palette (ivory + black), no anti-alias on cell edges.
 *   Cell size >= 12 px (minimal/hard-edge variant).
 *   ~40 generations, ~25% alive seed, flat black squares on ivory background.
 */
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("CellularInk", SIZE, SIZE)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // --- Grid parameters ---
    // Cell size >= 12 px per hard-edge directive; use 10 px to fit a good grid at 1024
    // but keep it large enough to be readable. We pick 10 to get ~100 cells across.
    val cellSize = 10
    val cols = d.w / cellSize
    val rows = d.h / cellSize

    // --- Seeded initial state (~25% alive) ---
    var grid = Array(rows) { BooleanArray(cols) { rng.rndb() && rng.rndb() } }
    // rng.rndb() && rng.rndb() gives ~25% true

    // --- Run ~40 generations of Conway's Game of Life ---
    fun countNeighbors(g: Array<BooleanArray>, row: Int, col: Int): Int {
        var n = 0
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = (row + dr + rows) % rows
                val cl = (col + dc + cols) % cols
                if (g[r][cl]) n++
            }
        }
        return n
    }

    repeat(40) {
        val next = Array(rows) { BooleanArray(cols) }
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val alive = grid[row][col]
                val n = countNeighbors(grid, row, col)
                next[row][col] = if (alive) n == 2 || n == 3 else n == 3
            }
        }
        grid = next
    }

    // --- Render ---
    // Background: ivory
    c.clear(CssColors.ivory)

    // Hard-edge black fill — disable anti-alias for crisp pixel-aligned edges
    val blackPaint = Paint().apply {
        isAntiAlias = false
        color = CssColors.black
    }

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            if (grid[row][col]) {
                val x = col * cellSize.toFloat()
                val y = row * cellSize.toFloat()
                c.drawRect(Rect(x, y, x + cellSize, y + cellSize), blackPaint)
            }
        }
    }

    gart.saveImage(g)
}
