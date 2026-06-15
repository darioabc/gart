package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Point

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM · Substrate crack propagation ───────────────────────────────────────
// Jared Tarbell's "Substrate": cracks crawl in straight lines until they strike an
// existing crack, where they stop and spawn a child at a perpendicular angle off a
// random already-drawn crack. The result is a fractured rectilinear mosaic of cells.
// Each crack lays translucent perpendicular sand strokes as it grows so the cells
// fill with soft tonal washes; crisp ink lines ride on top. Ember on dark ink.
private class Crack(var x: Float, var y: Float, var ang: Float, val colT: Float) {
    var alive = true
    var px = x; var py = y                 // previous pixel position (for line segs)
}

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("SubstrateCracks", SIZE, SIZE)
    val d = gart.d

    val out = gart.gartvas()
    val c = out.canvas
    val ground = 0xFF04151F.toInt()        // ink ground
    c.clear(ground)

    val ramp = Coolors.inkEmber.expand(256)

    // ── occupancy grid: angle of the crack occupying each cell (NaN = empty) ──
    // Coarser than full res keeps memory/cost sane but still partitions cleanly.
    val gw = (SIZE * 0.5f).toInt()         // grid resolution
    val gh = gw
    val gscale = gw.toFloat() / SIZE
    val occ = FloatArray(gw * gh) { Float.NaN }

    fun gridIdx(x: Float, y: Float): Int {
        val gx = (x * gscale).toInt()
        val gy = (y * gscale).toInt()
        if (gx < 0 || gy < 0 || gx >= gw || gy >= gh) return -1
        return gy * gw + gx
    }

    val cracks = ArrayList<Crack>()
    val step = SIZE * 0.0016f               // advance distance per tick
    val maxCracks = 520
    val washW = SIZE * 0.018f               // half-length of perpendicular wash stroke

    fun spawn(seedFromExisting: Boolean) {
        var x: Float; var y: Float; var ang: Float
        if (seedFromExisting && cracks.isNotEmpty()) {
            // pick a random occupied grid cell, sprout perpendicular to its crack
            var tries = 0
            var idx = -1
            while (tries < 40) {
                val i = rng.nextInt(gw * gh)
                if (!occ[i].isNaN()) { idx = i; break }
                tries++
            }
            if (idx < 0) { spawn(false); return }
            val gx = idx % gw; val gy = idx / gw
            x = (gx / gscale) + rng.rndf(-2f, 2f)
            y = (gy / gscale) + rng.rndf(-2f, 2f)
            val base = occ[idx]
            ang = base + (if (rng.rndb()) PI.toFloat() / 2f else -PI.toFloat() / 2f) +
                rng.rndf(-0.16f, 0.16f)
        } else {
            x = rng.rndf(SIZE * 0.1f, SIZE * 0.9f)
            y = rng.rndf(SIZE * 0.1f, SIZE * 0.9f)
            ang = rng.rndf(0f, TAUf)
        }
        cracks.add(Crack(x, y, ang, rng.rndf(0.45f, 1.0f)))
    }

    // a few seed cracks at random angles
    repeat(3) { spawn(false) }

    val inkPaint = strokeOf(0xFFEFD6AC.toInt(), SIZE * 0.0011f)
    inkPaint.strokeCap = PaintStrokeCap.ROUND
    inkPaint.isAntiAlias = true

    var guard = 0
    while (cracks.count { it.alive } > 0 && guard < 60000) {
        guard++
        var spawnedThisTick = 0
        // snapshot the count: spawn() appends to `cracks`, so iterate by index over the
        // cracks that exist at tick start (new ones are processed next tick) — a
        // for-each over the live list would throw ConcurrentModificationException.
        val tickCount = cracks.size
        for (ci in 0 until tickCount) {
            val cr = cracks[ci]
            if (!cr.alive) continue
            cr.px = cr.x; cr.py = cr.y
            cr.x += cos(cr.ang) * step
            cr.y += sin(cr.ang) * step

            // out of frame → die
            if (cr.x < 0f || cr.y < 0f || cr.x >= SIZE || cr.y >= SIZE) {
                cr.alive = false
                if (cracks.size < maxCracks) { spawn(true); spawnedThisTick++ }
                continue
            }
            val idx = gridIdx(cr.x, cr.y)
            if (idx < 0) { cr.alive = false; continue }

            // collision with an existing, different crack → stop + spawn child
            val here = occ[idx]
            if (!here.isNaN()) {
                cr.alive = false
                if (cracks.size < maxCracks) { spawn(true); spawnedThisTick++ }
                continue
            }
            occ[idx] = cr.ang

            // lay a translucent perpendicular sand wash so the cell fills with tone
            val perp = cr.ang + PI.toFloat() / 2f
            val wl = washW * rng.rndf(0.3f, 1.0f)
            val wx0 = cr.x + cos(perp) * wl
            val wy0 = cr.y + sin(perp) * wl
            val wx1 = cr.x - cos(perp) * wl
            val wy1 = cr.y - sin(perp) * wl
            val wcol = ramp.bound((0.4f + 0.5f * cr.colT) * (ramp.size - 1))
            val wp: Paint = strokeOf(wcol, SIZE * 0.0009f).alpha(10)
            wp.strokeCap = PaintStrokeCap.ROUND
            c.drawLine(Point(wx0, wy0), Point(wx1, wy1), wp)

            // crisp ink line segment for this advance
            val ink: Paint = strokeOf(
                ramp.bound((0.55f + 0.45f * cr.colT) * (ramp.size - 1)),
                SIZE * 0.0012f
            ).alpha(220)
            ink.strokeCap = PaintStrokeCap.ROUND
            ink.isAntiAlias = true
            c.drawLine(Point(cr.px, cr.py), Point(cr.x, cr.y), ink)
        }
        // keep the field replenished while there's headroom
        if (cracks.count { it.alive } < 6 && cracks.size < maxCracks) {
            repeat(2) { spawn(true) }
        }
    }
    println("  ${cracks.size} cracks, guard=$guard")

    val finalv = bloom(gart, out.snapshot(), ground, SIZE * 0.0035f, grain = 0.06f)
    gart.saveImage(finalv)
}
