package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.math.*
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Point

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM #2 · Space-colonization venation ────────────────────────────────────
// The Runions space-colonization algorithm: a cloud of "auxin" attractors pulls a
// branching network toward itself, killing each attractor as a branch reaches it.
// The result is a vein / lightning / nerve tree with organic taper — thick trunk,
// fine twigs. Gold growth on deep navy (coolors "Navy & Gold"), bloomed to glow.
private class Branch(val x: Float, val y: Float, val parent: Int)

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("SpaceColonization", SIZE, SIZE)
    val d = gart.d

    val influence = SIZE * 0.16f
    val kill = SIZE * 0.018f
    val seg = SIZE * 0.009f

    // attractor cloud: an elliptical canopy in the upper 75% of the frame
    val nAttr = 2600
    val ax = ArrayList<Float>(nAttr); val ay = ArrayList<Float>(nAttr)
    val ccx = d.cx; val ccy = SIZE * 0.42f
    val rxr = SIZE * 0.42f; val ryr = SIZE * 0.40f
    while (ax.size < nAttr) {
        val px = rng.rndf(-1f, 1f); val py = rng.rndf(-1f, 1f)
        if (px * px + py * py <= 1f) { ax.add(ccx + px * rxr); ay.add(ccy + py * ryr) }
    }

    // root grows up from the bottom centre
    val br = ArrayList<Branch>()
    br.add(Branch(d.cx, SIZE * 0.98f, -1))
    // grow a short trunk toward the canopy before colonization kicks in
    repeat(6) { br.add(Branch(br.last().x, br.last().y - seg, br.size - 1)) }

    val alive = BooleanArray(nAttr) { true }
    var remaining = nAttr
    var guard = 0
    val gcell = influence
    while (remaining > 0 && br.size < 7000 && guard < 1500) {
        guard++
        // bucket branch nodes into a grid so each attractor only checks nearby cells
        val grid = HashMap<Long, MutableList<Int>>()
        for (i in br.indices) {
            val key = (br[i].x / gcell).toLong() * 100000L + (br[i].y / gcell).toLong()
            grid.getOrPut(key) { ArrayList() }.add(i)
        }
        val dirX = HashMap<Int, Float>(); val dirY = HashMap<Int, Float>(); val cnt = HashMap<Int, Int>()
        for (k in 0 until nAttr) {
            if (!alive[k]) continue
            val gx = (ax[k] / gcell).toLong(); val gy = (ay[k] / gcell).toLong()
            var best = -1; var bestD = influence; var killed = false
            for (ox in -1..1) for (oy in -1..1) {
                val bucket = grid[(gx + ox) * 100000L + (gy + oy)] ?: continue
                for (i in bucket) {
                    val dd = hypot(ax[k] - br[i].x, ay[k] - br[i].y)
                    if (dd < bestD) { bestD = dd; best = i }
                    if (dd < kill) killed = true
                }
            }
            if (killed) { alive[k] = false; remaining--; continue }
            if (best >= 0) {
                val dx = ax[k] - br[best].x; val dy = ay[k] - br[best].y
                val L = hypot(dx, dy).coerceAtLeast(0.001f)
                dirX[best] = (dirX[best] ?: 0f) + dx / L
                dirY[best] = (dirY[best] ?: 0f) + dy / L
                cnt[best] = (cnt[best] ?: 0) + 1
            }
        }
        if (cnt.isEmpty()) break
        for ((i, _) in cnt) {
            val L = hypot(dirX[i]!!, dirY[i]!!).coerceAtLeast(0.001f)
            val nx = br[i].x + dirX[i]!! / L * seg + rng.rndf(-0.4f, 0.4f)
            val ny = br[i].y + dirY[i]!! / L * seg + rng.rndf(-0.4f, 0.4f)
            br.add(Branch(nx, ny, i))
        }
    }
    println("  ${br.size} branch nodes, ${nAttr - remaining} attractors reached")

    // taper: thickness from descendant count (Leonardo's rule, roughly)
    val weight = IntArray(br.size) { 1 }
    for (i in br.indices.reversed()) {
        val p = br[i].parent
        if (p >= 0) weight[p] += weight[i]
    }

    val ground = 0xFF000814.toInt()
    val ramp = Coolors.navyGold.expand(256)
    val buf = gart.gartvas()
    val c = buf.canvas
    val maxW = weight.max().toFloat()
    for (i in br.indices) {
        val p = br[i].parent
        if (p < 0) continue
        val tw = sqrt(weight[i] / maxW).coerceIn(0.02f, 1f)
        val width = 0.7f + tw * SIZE * 0.010f
        // trunk → warm gold, twigs → pale; colour by taper
        val col = ramp.bound((0.45f + 0.55f * tw) * (ramp.size - 1))
        val paint: Paint = strokeOf(col, width).alpha((120 + 135 * tw).toInt().coerceIn(80, 255))
        paint.strokeCap = PaintStrokeCap.ROUND
        c.drawLine(Point(br[p].x, br[p].y), Point(br[i].x, br[i].y), paint)
    }

    val finalv = bloom(gart, buf.snapshot(), ground, SIZE / 260f, grain = 0.05f)
    gart.saveImage(finalv)
}
