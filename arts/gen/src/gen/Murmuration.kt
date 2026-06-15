package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.color.lerpColor
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Point

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM · Starling murmuration ──────────────────────────────────────────────
// Thousands of boids fill a sweeping teardrop "flock" mask. A curl/simplex flow field
// makes them swirl into the streaky filaments a real murmuration shows, while a soft
// containment force pulls any boid that drifts past the silhouette back inside — so
// the cloud holds its shape and frays into wisps at the rim. Each step leaves a low-
// alpha ink dash, accumulating density into tone. Ink on wheat (coolors "Ink & Ember").
private class Boid(var x: Float, var y: Float, var vx: Float, var vy: Float)

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val noise = OpenSimplexNoise(SEED)

    val gart = Gart.of("Murmuration", SIZE, SIZE)
    val d = gart.d
    val S = SIZE.toFloat()

    // ── subject mask: a swept teardrop/comma, banked, big in the frame ──────────
    val bcx = rng.rndf(0.46f, 0.54f); val bcy = rng.rndf(0.44f, 0.54f)
    val tilt = rng.rndf(-0.7f, 0.4f)
    val ct = cos(tilt); val st = sin(tilt)
    fun mask(nx: Float, ny: Float): Float {
        val dx0 = nx - bcx; val dy0 = ny - bcy
        val lx = dx0 * ct - dy0 * st
        val ly = dx0 * st + dy0 * ct
        // fat body ellipse
        val bx = lx / 0.30f; val by = ly / 0.20f
        var m = smoothstep(1.05f, 0.0f, bx * bx + by * by)
        // sweeping tail: a curved comma flicking up-right, tapering
        val u = lx / 0.42f
        if (u in -0.2f..1.25f) {
            val camber = 0.16f * u * u - 0.02f * u          // tail curls
            val thick = 0.17f * (1f - smoothstep(-0.2f, 1.25f, u)) + 0.012f
            m = max(m, smoothstep(thick, thick * 0.25f, kotlin.math.abs(ly - camber)) * 0.92f)
        }
        return m.coerceIn(0f, 1f)
    }

    // ── seed boids uniformly INSIDE the mask ────────────────────────────────────
    val count = if (SIZE >= 1024) 6500 else 3600
    val boids = ArrayList<Boid>(count)
    var guard = 0
    while (boids.size < count && guard < count * 80) {
        guard++
        val tx = rng.rndf(0.04f, 0.96f); val ty = rng.rndf(0.04f, 0.96f)
        if (mask(tx, ty) > rng.rndf(0.15f, 1f)) {
            val a = rng.rndf(0f, TAUf)
            boids.add(Boid(tx * S, ty * S, cos(a), sin(a)))
        }
    }
    println("  ${boids.size} boids")

    val out = gart.gartvas()
    val c = out.canvas
    c.clear(0xFFEFD6AC.toInt())   // wheat sky
    val ink = 0xFF04151F.toInt()
    val ember = 0xFFC44900.toInt()

    val steps = 240
    val flowScale = 0.0026f
    val speed = S * 0.0019f

    for (step in 0 until steps) {
        val gust = noise.random2D(step * 0.012f, 71.0f).toFloat() * 0.5f
        for (b in boids) {
            val nx = b.x / S; val ny = b.y / S
            val m = mask(nx, ny)

            // 1) curl/flow wind → the swirling filaments
            val ang = noise.random2D(b.x * flowScale, b.y * flowScale).toFloat() * TAUf * 2f + gust
            var ax = cos(ang); var ay = sin(ang)

            // 2) soft containment: only boids OUTSIDE the mask are pulled back toward
            //    the body centre — inside boids swirl freely so the cloud fills the shape
            val outside = (1f - m)
            ax += (bcx - nx) * 7.0f * outside * outside
            ay += (bcy - ny) * 7.0f * outside * outside

            val al = hypot(ax, ay).coerceAtLeast(1e-4f)
            ax /= al; ay /= al

            b.vx = b.vx * 0.62f + ax * 0.38f
            b.vy = b.vy * 0.62f + ay * 0.38f
            val px = b.x; val py = b.y
            b.x += b.vx * speed
            b.y += b.vy * speed

            // draw the motion dash: density (inside-ness) drives tone & weight
            val inside = mask(b.x / S, b.y / S)
            val a0 = (6 + 40 * inside).toInt().coerceIn(4, 64)
            val w = (S * 0.0010f) * (0.6f + 1.0f * inside)
            val col = if (inside > 0.75f && ((b.x.toInt() xor b.y.toInt()) and 63) == 0)
                lerpColor(ink, ember, 0.55f) else ink
            if (hypot(b.x - px, b.y - py) < S * 0.04f) {
                val paint = strokeOf(col, w).alpha(a0)
                paint.strokeCap = PaintStrokeCap.ROUND
                c.drawLine(Point(px, py), Point(b.x, b.y), paint)
            }
        }
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.055f)
    gart.saveImage(finalv)
}
