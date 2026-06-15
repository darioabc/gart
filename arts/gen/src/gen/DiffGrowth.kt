package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Point

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM #1 · Differential growth ────────────────────────────────────────────
// A closed ring of nodes that repel their neighbours, spring back to their two
// topological neighbours, and split when an edge stretches too far. Left to run, the
// loop has to fold and crowd itself into the frame — the convoluted winding of a
// brain coral / intestine, every run a different fold. Ink on wheat (coolors "Ink &
// Ember"), the filament tinted along its length.
private class Node(var x: Float, var y: Float)

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val noise = OpenSimplexNoise(SEED)

    val gart = Gart.of("DiffGrowth", SIZE, SIZE)
    val d = gart.d

    val edge = SIZE * 0.0095f           // target node spacing
    val repR = edge * 2.6f             // repulsion radius
    val maxEdge = edge * 1.4f
    val maxNodes = 6000
    val steps = 320
    val repForce = edge * 0.55f
    val springK = 0.45f                // rest-length spring (keeps spacing ~edge)

    // seed: small jittered circle in the centre
    var nodes = ArrayList<Node>()
    val n0 = 64
    val r0 = SIZE * 0.05f
    for (i in 0 until n0) {
        val a = TAUf * i / n0
        nodes.add(Node(d.cx + cos(a) * r0 + rng.rndf(-2f, 2f), d.cy + sin(a) * r0 + rng.rndf(-2f, 2f)))
    }

    val cell = repR
    repeat(steps) { step ->
        val n = nodes.size
        // spatial hash for neighbour queries
        val grid = HashMap<Long, MutableList<Int>>()
        for (i in 0 until n) {
            val key = (nodes[i].x / cell).toLong() * 100000L + (nodes[i].y / cell).toLong()
            grid.getOrPut(key) { ArrayList() }.add(i)
        }
        val fx = FloatArray(n); val fy = FloatArray(n)
        for (i in 0 until n) {
            val ni = nodes[i]
            val gx = (ni.x / cell).toLong(); val gy = (ni.y / cell).toLong()
            // repulsion from nearby nodes
            for (ox in -1..1) for (oy in -1..1) {
                val bucket = grid[(gx + ox) * 100000L + (gy + oy)] ?: continue
                for (j in bucket) {
                    if (j == i) continue
                    val dx = ni.x - nodes[j].x; val dy = ni.y - nodes[j].y
                    val dist = hypot(dx, dy)
                    if (dist in 0.001f..repR) {
                        val f = (1f - dist / repR)
                        fx[i] += dx / dist * f * repForce
                        fy[i] += dy / dist * f * repForce
                    }
                }
            }
            // rest-length springs to the two topological neighbours: pull together if an
            // edge is longer than `edge`, push apart if shorter — keeps even spacing while
            // global repulsion is free to expand and buckle the whole curve.
            val pa = nodes[(i - 1 + n) % n]; val pb = nodes[(i + 1) % n]
            for (nb in arrayOf(pa, pb)) {
                val dx = nb.x - ni.x; val dy = nb.y - ni.y
                val dist = hypot(dx, dy).coerceAtLeast(0.001f)
                val f = (dist - edge) * springK
                fx[i] += dx / dist * f
                fy[i] += dy / dist * f
            }
            // a little curl from a flow field so folds wander organically
            val ang = noise.random2D(ni.x * 0.0016f, ni.y * 0.0016f).toFloat() * TAUf
            fx[i] += cos(ang) * edge * 0.05f
            fy[i] += sin(ang) * edge * 0.05f
        }
        for (i in 0 until n) {
            nodes[i].x = (nodes[i].x + fx[i]).coerceIn(SIZE * 0.04f, SIZE * 0.96f)
            nodes[i].y = (nodes[i].y + fy[i]).coerceIn(SIZE * 0.04f, SIZE * 0.96f)
        }
        // split long edges (rebuild list)
        if (nodes.size < maxNodes) {
            val grown = ArrayList<Node>(nodes.size + 64)
            for (i in nodes.indices) {
                val a = nodes[i]; val b = nodes[(i + 1) % nodes.size]
                grown.add(a)
                if (hypot(a.x - b.x, a.y - b.y) > maxEdge && grown.size < maxNodes)
                    grown.add(Node((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f))
            }
            nodes = grown
        }
    }
    println("  ${nodes.size} nodes")

    val out = gart.gartvas()
    val c = out.canvas
    c.clear(0xFFEFD6AC.toInt())          // wheat paper
    val ramp = Coolors.inkEmber.expand(256)
    val w = SIZE * 0.0042f
    val n = nodes.size
    for (i in 0 until n) {
        val a = nodes[i]; val b = nodes[(i + 1) % n]
        val t = i.toFloat() / n
        val paint: Paint = strokeOf(ramp.bound(t * (ramp.size - 1)), w)
        paint.strokeCap = PaintStrokeCap.ROUND
        c.drawLine(Point(a.x, a.y), Point(b.x, b.y), paint)
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
