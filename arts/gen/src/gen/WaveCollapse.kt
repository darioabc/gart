package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.Palette
import dev.oblac.gart.gfx.drawArc
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.random.Random
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Point
import org.jetbrains.skia.Rect

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM · Wave-Function-Collapse circuit tapestry ───────────────────────────
// A genuine WFC / model-synthesis run over a square grid of "pipe/circuit" tiles.
// Each tile carries N/E/S/W socket booleans; adjacency requires touching edges to
// agree (both open or both closed). Standard observe → collapse → propagate loop:
// pick the min-entropy undecided cell, collapse to a weighted-random tile, propagate
// the open/closed constraints to neighbours via a stack. On contradiction we don't
// crash — we fall back to the least-constrained pick for that cell and keep going.
// The collapsed field is rendered as crisp ink line-work (thick rounded pipe strokes,
// filled junction nodes) tinted from "Patriot Gold" by a low-freq simplex field so
// regions harmonise into a dense circuit-board labyrinth with emergent connected paths.

// Tile = sockets in order N, E, S, W (true == open / pipe exits that edge), plus weight.
private class Tile(
    val n: Boolean, val e: Boolean, val s: Boolean, val w: Boolean,
    val weight: Float,
)

// Base prototypes; we rotate them to fill the full socket space.
private fun buildTiles(): List<Tile> {
    val out = ArrayList<Tile>()
    fun add(n: Boolean, e: Boolean, s: Boolean, w: Boolean, weight: Float) {
        // emit the 4 rotations, de-duplicated by socket signature
        var (cn, ce, cs, cw) = listOf(n, e, s, w)
        val seen = HashSet<Int>()
        repeat(4) {
            val sig = (if (cn) 1 else 0) or (if (ce) 2 else 0) or (if (cs) 4 else 0) or (if (cw) 8 else 0)
            if (seen.add(sig)) out.add(Tile(cn, ce, cs, cw, weight))
            // rotate 90° clockwise: N<-W, E<-N, S<-E, W<-S
            val nn = cw; val ne = cn; val ns = ce; val nw = cs
            cn = nn; ce = ne; cs = ns; cw = nw
        }
    }
    add(false, false, false, false, 0.6f)  // blank (rest space)
    add(true, false, true, false, 2.2f)    // straight
    add(true, true, false, false, 2.4f)    // corner / elbow
    add(true, true, true, false, 1.3f)     // T-junction
    add(true, true, true, true, 0.7f)      // cross
    add(true, false, false, false, 1.0f)   // terminal / stub
    return out
}

private lateinit var rng: Random
private lateinit var noise: OpenSimplexNoise
private lateinit var ramp: Palette
private lateinit var tiles: List<Tile>

// open-edge of tile t in direction dir (0=N,1=E,2=S,3=W)
private fun openOf(t: Tile, dir: Int): Boolean = when (dir) {
    0 -> t.n; 1 -> t.e; 2 -> t.s; else -> t.w
}

private const val N = 0; private const val E = 1; private const val S = 2; private const val W = 3
private val DX = intArrayOf(0, 1, 0, -1)
private val DY = intArrayOf(-1, 0, 1, 0)
private val OPP = intArrayOf(S, W, N, E)

fun main() {
    println("seed=$SEED")
    rng = Random(SEED)

    val gart = Gart.of("WaveCollapse", SIZE, SIZE)
    val d = gart.d
    noise = OpenSimplexNoise(SEED)
    ramp = Coolors.patriotGold.expand(256)
    tiles = buildTiles()
    val tcount = tiles.size

    val ground = 0xFFEFE7CC.toInt()
    val ink = 0xFF1A2330.toInt()

    // ── grid ────────────────────────────────────────────────────────────────
    val cells = 48
    // possibility set per cell: a boolean mask over tiles
    val poss = Array(cells * cells) { BooleanArray(tcount) { true } }
    val collapsed = IntArray(cells * cells) { -1 }

    fun idx(x: Int, y: Int) = y * cells + x
    fun inB(x: Int, y: Int) = x in 0 until cells && y in 0 until cells

    fun count(i: Int): Int = poss[i].count { it }

    // propagate constraints from a just-changed cell using a stack
    fun propagate(start: Int) {
        val stack = ArrayDeque<Int>()
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val ci = stack.removeLast()
            val cx = ci % cells; val cy = ci / cells
            for (dir in 0 until 4) {
                val nx = cx + DX[dir]; val ny = cy + DY[dir]
                if (!inB(nx, ny)) continue
                val ni = idx(nx, ny)
                if (collapsed[ni] >= 0) continue
                // which "open" states for the shared edge are still supported by ci?
                var allowOpen = false; var allowClosed = false
                val cm = poss[ci]
                for (t in 0 until tcount) {
                    if (!cm[t]) continue
                    if (openOf(tiles[t], dir)) allowOpen = true else allowClosed = true
                    if (allowOpen && allowClosed) break
                }
                if (allowOpen && allowClosed) continue // neighbour unconstrained on this edge
                // restrict neighbour: its facing edge (OPP[dir]) must match what's allowed
                var changed = false
                val nm = poss[ni]
                for (t in 0 until tcount) {
                    if (!nm[t]) continue
                    val no = openOf(tiles[t], OPP[dir])
                    val ok = (no && allowOpen) || (!no && allowClosed)
                    if (!ok) { nm[t] = false; changed = true }
                }
                if (changed) {
                    // contradiction guard: never let a cell empty out
                    if (nm.none { it }) {
                        // fallback — re-open the least-bad single option so we never crash
                        var best = 0; var bestW = -1f
                        for (t in 0 until tcount) {
                            val no = openOf(tiles[t], OPP[dir])
                            val ok = (no && allowOpen) || (!no && allowClosed)
                            if (ok && tiles[t].weight > bestW) { bestW = tiles[t].weight; best = t }
                        }
                        nm[best] = true
                    }
                    stack.addLast(ni)
                }
            }
        }
    }

    fun collapseCell(i: Int) {
        val mask = poss[i]
        // weighted random over remaining options, biased a touch by the simplex
        // field so regions favour denser or sparser circuitry coherently.
        val cx = (i % cells).toFloat(); val cy = (i / cells).toFloat()
        val field = noise.random2D(cx * 0.06f, cy * 0.06f) * 0.5f + 0.5f
        var total = 0f
        for (t in 0 until tcount) if (mask[t]) {
            val openCount = (if (tiles[t].n) 1 else 0) + (if (tiles[t].e) 1 else 0) +
                (if (tiles[t].s) 1 else 0) + (if (tiles[t].w) 1 else 0)
            val densityBias = 1f + (field - 0.5f) * 0.6f * (openCount - 2)
            total += tiles[t].weight * densityBias.coerceAtLeast(0.05f)
        }
        var r = rng.nextFloat() * total
        var chosen = -1
        for (t in 0 until tcount) if (mask[t]) {
            val openCount = (if (tiles[t].n) 1 else 0) + (if (tiles[t].e) 1 else 0) +
                (if (tiles[t].s) 1 else 0) + (if (tiles[t].w) 1 else 0)
            val densityBias = 1f + (field - 0.5f) * 0.6f * (openCount - 2)
            r -= tiles[t].weight * densityBias.coerceAtLeast(0.05f)
            if (r <= 0f) { chosen = t; break }
        }
        if (chosen < 0) chosen = (0 until tcount).first { mask[it] }
        for (t in 0 until tcount) mask[t] = t == chosen
        collapsed[i] = chosen
    }

    // border cells must have closed outer edges so paths read as contained
    for (x in 0 until cells) for (y in 0 until cells) {
        val m = poss[idx(x, y)]
        for (t in 0 until tcount) {
            if (!m[t]) continue
            if ((y == 0 && tiles[t].n) || (y == cells - 1 && tiles[t].s) ||
                (x == 0 && tiles[t].w) || (x == cells - 1 && tiles[t].e)) m[t] = false
        }
        if (m.none { it }) m[0] = true // blank tile is index 0
    }
    for (x in 0 until cells) for (y in 0 until cells) {
        if (x == 0 || y == 0) propagate(idx(x, y))
    }

    // ── observe loop ──────────────────────────────────────────────────────────
    var remaining = cells * cells
    // some may already be forced to a single option from border propagation
    for (i in 0 until cells * cells) if (count(i) == 1 && collapsed[i] < 0) {
        collapsed[i] = (0 until tcount).first { poss[i][it] }; remaining--
    }
    var guard = cells * cells * 4
    while (remaining > 0 && guard-- > 0) {
        // min-entropy: fewest remaining options (>1), tiny noise to break ties
        var bestI = -1; var bestC = Int.MAX_VALUE; var bestNoise = 0f
        for (i in 0 until cells * cells) {
            if (collapsed[i] >= 0) continue
            val c = count(i)
            val jitter = rng.nextFloat() * 0.5f
            if (c < bestC || (c == bestC && jitter > bestNoise)) {
                bestC = c; bestI = i; bestNoise = jitter
            }
        }
        if (bestI < 0) break
        collapseCell(bestI)
        remaining--
        propagate(bestI)
        // sweep up any cells pinned to a single option by propagation
        for (i in 0 until cells * cells) if (collapsed[i] < 0 && count(i) == 1) {
            collapsed[i] = (0 until tcount).first { poss[i][it] }; remaining--
        }
    }
    // any stragglers (shouldn't happen): force blank
    for (i in 0 until cells * cells) if (collapsed[i] < 0) {
        collapsed[i] = (0 until tcount).firstOrNull { poss[i][it] } ?: 0
    }

    // ── render ────────────────────────────────────────────────────────────────
    val buf = gart.gartvas()
    val c = buf.canvas
    c.clear(ground)

    val cellSize = SIZE.toFloat() / cells
    val pipeW = cellSize * 0.34f
    val nodeR = cellSize * 0.20f

    fun colorAt(gx: Int, gy: Int): Int {
        val nv = (noise.random2D(gx * 0.045f + 11f, gy * 0.045f).toFloat() * 0.5f + 0.5f).coerceIn(0f, 1f)
        return ramp.bound(nv * (ramp.size - 1))
    }

    for (gy in 0 until cells) for (gx in 0 until cells) {
        val t = tiles[collapsed[idx(gx, gy)]]
        val ox = gx * cellSize; val oy = gy * cellSize
        val mx = ox + cellSize / 2f; val my = oy + cellSize / 2f
        val col = colorAt(gx, gy)
        // soft drop of ink underlay for crispness, then colour
        val under = strokeOf(ink, pipeW * 1.32f).apply {
            mode = PaintMode.STROKE; strokeCap = PaintStrokeCap.ROUND; isAntiAlias = true
        }
        val pipe = strokeOf(col, pipeW).apply {
            mode = PaintMode.STROKE; strokeCap = PaintStrokeCap.ROUND; isAntiAlias = true
        }
        val open = booleanArrayOf(t.n, t.e, t.s, t.w)
        val ends = arrayOf(
            Point(mx, oy), Point(ox + cellSize, my), Point(mx, oy + cellSize), Point(ox, my)
        )
        val openCount = open.count { it }
        // draw underlay then colour, both passes
        for (pass in 0..1) {
            val paint = if (pass == 0) under else pipe
            if (openCount == 2 && ((open[N] && open[S]) || (open[E] && open[W]))) {
                // straight: single line through centre
                if (open[N] && open[S]) c.drawLine(ends[N], ends[S], paint)
                else c.drawLine(ends[E], ends[W], paint)
            } else if (openCount == 2) {
                // corner: arc between the two open edges for a smooth elbow
                // determine which corner and draw a quarter arc centred on it
                val (a, b) = run {
                    val ix = (0..3).filter { open[it] }
                    Pair(ix[0], ix[1])
                }
                drawElbow(c, ox, oy, cellSize, a, b, paint)
            } else {
                // T, cross, terminal, blank: spokes from centre to each open edge
                for (dir in 0 until 4) if (open[dir]) c.drawLine(Point(mx, my), ends[dir], paint)
            }
        }
        // junction node on T/cross, small cap on terminal
        if (openCount >= 3) {
            c.drawCircle(mx, my, nodeR, fillOf(ink))
            c.drawCircle(mx, my, nodeR * 0.6f, fillOf(col))
        } else if (openCount == 1) {
            c.drawCircle(mx, my, nodeR * 0.72f, fillOf(ink))
        }
    }

    // bold framing border
    val frame = strokeOf(ink, SIZE * 0.016f).apply { mode = PaintMode.STROKE; isAntiAlias = true }
    val inset = SIZE * 0.012f
    c.drawRect(Rect.makeLTRB(inset, inset, SIZE - inset, SIZE - inset), frame)
    val frameGold = strokeOf(0xFFFCBF49.toInt(), SIZE * 0.005f).apply { mode = PaintMode.STROKE; isAntiAlias = true }
    val inset2 = SIZE * 0.022f
    c.drawRect(Rect.makeLTRB(inset2, inset2, SIZE - inset2, SIZE - inset2), frameGold)

    val finalv = grainOnly(gart, buf.snapshot(), grain = 0.055f)
    gart.saveImage(finalv)
    println("  WFC done (${cells}x$cells grid, $tcount tiles)")
}

// quarter-circle elbow connecting two adjacent open edges of a cell
private fun drawElbow(c: Canvas, ox: Float, oy: Float, s: Float, a: Int, b: Int, paint: org.jetbrains.skia.Paint) {
    val r = s / 2f
    // corner shared by directions a,b → arc centre at that corner, radius r
    val set = setOf(a, b)
    val (cxp, cyp, start) = when {
        set == setOf(N, E) -> Triple(ox + s, oy, 90f)        // centre top-right
        set == setOf(E, S) -> Triple(ox + s, oy + s, 180f)   // centre bottom-right
        set == setOf(S, W) -> Triple(ox, oy + s, 270f)       // centre bottom-left
        else -> Triple(ox, oy, 0f)                            // N,W centre top-left
    }
    c.drawArc(Rect.makeLTRB(cxp - r, cyp - r, cxp + r, cyp + r), start, 90f, false, paint)
}
