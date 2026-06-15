package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.math.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── DLA · Diffusion-Limited Aggregation frost ─────────────────────────────────
// A few seed crystals at centre grow by accretion: tens of thousands of walkers are
// released from a spawn ring and random-walk on a coarse occupancy grid until they
// touch the cluster and freeze. Each stuck cell records its accretion order, painted
// through the "Sea Blue" ramp so you read the dendritic frost as concentric growth
// rings — bright young tips, deep-indigo ancient core — bloomed on near-black.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("DlaFrost", SIZE, SIZE)
    val d = gart.d

    // Coarse simulation grid: walking on a sub-sampled lattice keeps the cluster
    // dense and the dots chunky at render scale. ~2px cells at 1024.
    val cell = max(1, SIZE / 512)
    val gw = d.w / cell
    val gh = d.h / cell
    val gcx = gw / 2
    val gcy = gh / 2

    // occupancy: -1 empty, else accretion order index.
    val occ = IntArray(gw * gh) { -1 }
    fun idx(x: Int, y: Int) = y * gw + x

    // Seed: a small central cluster + a few satellites so dendrites branch early.
    var order = 0
    fun stick(x: Int, y: Int) {
        if (x in 0 until gw && y in 0 until gh && occ[idx(x, y)] == -1) {
            occ[idx(x, y)] = order++
        }
    }
    stick(gcx, gcy)
    repeat(4) {
        val a = rng.rndf(0f, TAUf)
        val r = rng.rndf(2f, 6f)
        stick((gcx + cos(a) * r).toInt(), (gcy + sin(a) * r).toInt())
    }

    // Spawn on a ring that tracks the cluster's current reach; walkers that wander
    // past a kill radius are respawned. Ring slowly grows with the aggregate.
    val maxParticles = if (SIZE >= 1024) 42000 else 18000
    var clusterR = 8f                       // current cluster radius (grid units)
    val maxR = min(gw, gh) * 0.48f

    fun occupiedNeighbour(x: Int, y: Int): Boolean {
        if (x > 0 && occ[idx(x - 1, y)] != -1) return true
        if (x < gw - 1 && occ[idx(x + 1, y)] != -1) return true
        if (y > 0 && occ[idx(x, y - 1)] != -1) return true
        if (y < gh - 1 && occ[idx(x, y + 1)] != -1) return true
        if (x > 0 && y > 0 && occ[idx(x - 1, y - 1)] != -1) return true
        if (x < gw - 1 && y > 0 && occ[idx(x + 1, y - 1)] != -1) return true
        if (x > 0 && y < gh - 1 && occ[idx(x - 1, y + 1)] != -1) return true
        if (x < gw - 1 && y < gh - 1 && occ[idx(x + 1, y + 1)] != -1) return true
        return false
    }

    var stuck = 5
    var launched = 0
    while (stuck < maxParticles && clusterR < maxR) {
        // spawn just outside current reach
        val spawnR = min(clusterR + 5f, maxR + 2f)
        val killR = min(spawnR * 1.8f + 12f, (min(gw, gh) * 0.5f))
        val a = rng.rndf(0f, TAUf)
        var wx = (gcx + cos(a) * spawnR).toInt()
        var wy = (gcy + sin(a) * spawnR).toInt()
        launched++

        var steps = 0
        val maxSteps = 6000
        while (steps < maxSteps) {
            steps++
            // random 8-neighbour step
            wx += rng.rndi(-1, 2)
            wy += rng.rndi(-1, 2)

            // out of bounds or beyond kill radius → respawn this walker
            val dx = wx - gcx
            val dy = wy - gcy
            if (wx < 1 || wy < 1 || wx >= gw - 1 || wy >= gh - 1 ||
                dx * dx + dy * dy > killR * killR
            ) {
                val na = rng.rndf(0f, TAUf)
                wx = (gcx + cos(na) * spawnR).toInt()
                wy = (gcy + sin(na) * spawnR).toInt()
                continue
            }

            if (occupiedNeighbour(wx, wy)) {
                stick(wx, wy)
                stuck++
                val rr = sqrt((dx * dx + dy * dy).toFloat())
                if (rr > clusterR) clusterR = rr
                break
            }
        }
    }
    println("  stuck=$stuck launched=$launched clusterR=$clusterR")

    // Render: accretion order → Sea Blue ramp. Older (low order) = deep indigo core,
    // newest tips = bright cyan. Slight radial falloff keeps the core from blooming flat.
    val ramp = Coolors.seaBlue.expand(256)
    val ground = 0xFF03040A.toInt()
    val gm = Gartmap(gart.gartvas())
    for (i in gm.pixels.indices) gm.pixels[i] = ground

    val maxOrder = max(1, order - 1)
    for (gy in 0 until gh) {
        for (gx in 0 until gw) {
            val o = occ[idx(gx, gy)]
            if (o == -1) continue
            // newest accreted = hottest. Use sqrt so young rings get more ramp range.
            val t = sqrt(o.toFloat() / maxOrder).coerceIn(0f, 1f)
            val col = ramp.bound(t * (ramp.size - 1))
            // map coarse cell back to canvas block
            val px0 = gx * cell
            val py0 = gy * cell
            for (sy in 0 until cell) {
                val yy = py0 + sy
                if (yy >= d.h) break
                val rowBase = yy * d.w
                for (sx in 0 until cell) {
                    val xx = px0 + sx
                    if (xx >= d.w) break
                    gm.pixels[rowBase + xx] = darken(col, 0.30f + 0.70f * t)
                }
            }
        }
    }

    val finalv = bloom(gart, gm.image(), ground, SIZE / 240f, grain = 0.06f)
    gart.saveImage(finalv)
}
