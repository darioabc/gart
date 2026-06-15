package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.Point

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD #10 · Kaleidoscopic flow-field mandala ────────────────────────────────
// Particles drift through a simplex flow field; every segment they leave is stamped
// into N rotational sectors (and their mirror) around the centre, weaving a symmetric
// mandala. Pastel inks (coolors "Pastel Dream") glow on a plum ground via SCREEN
// bloom. Showcases: flow field + reflective symmetry + SCREEN bloom + accent.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("KaleidoFlow", SIZE, SIZE)
    val d = gart.d
    val ground = 0xFF140A1E.toInt()
    val noise = OpenSimplexNoise(SEED)
    val pal = Coolors.pastelDream

    val fold = intArrayOf(6, 8, 10, 12).random(rng)
    val particles = 520
    val steps = 240
    val noiseScale = 0.0026f
    val speed = SIZE * 0.0017f
    println("  $fold-fold symmetry")

    val cx = d.cx; val cy = d.cy
    val buf = gart.gartvas()
    val bc = buf.canvas

    // precompute the rotation matrices for the fold
    val rc = FloatArray(fold); val rs = FloatArray(fold)
    for (k in 0 until fold) { val a = TAUf * k / fold; rc[k] = cos(a); rs[k] = sin(a) }

    repeat(particles) { pi ->
        // seed near the centre disk so strokes radiate outward
        var x = cx + rng.rndf(-0.22f, 0.22f) * SIZE
        var y = cy + rng.rndf(-0.22f, 0.22f) * SIZE
        val baseCol = pal[pi % pal.size]
        val accent = pi % 9 == 0
        val col = if (accent) 0xFFFF4D6D.toInt() else baseCol
        val a0 = if (accent) 60 else 30

        for (s in 0 until steps) {
            val ang = noise.random2D(x * noiseScale, y * noiseScale).toFloat() * TAUf * 2f
            val nx = x + cos(ang) * speed
            val ny = y + sin(ang) * speed
            // local coords relative to centre
            val lx0 = x - cx; val ly0 = y - cy
            val lx1 = nx - cx; val ly1 = ny - cy
            val paint = strokeOf(col, 1.1f).alpha(a0)
            for (k in 0 until fold) {
                // rotated copy
                val ax0 = lx0 * rc[k] - ly0 * rs[k]; val ay0 = lx0 * rs[k] + ly0 * rc[k]
                val ax1 = lx1 * rc[k] - ly1 * rs[k]; val ay1 = lx1 * rs[k] + ly1 * rc[k]
                bc.drawLine(Point(cx + ax0, cy + ay0), Point(cx + ax1, cy + ay1), paint)
                // mirrored copy (reflect across X)
                bc.drawLine(Point(cx - ax0, cy + ay0), Point(cx - ax1, cy + ay1), paint)
            }
            x = nx; y = ny
            if (x < 0 || x > d.wf || y < 0 || y > d.hf) return@repeat
        }
    }

    val finalv = bloom(gart, buf.snapshot(), ground, SIZE / 210f, grain = 0.07f)
    gart.saveImage(finalv)
}
