package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.Palette
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Point

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM · Stochastic L-system thicket ───────────────────────────────────────
// An axiom + branching rewrite rules ("F" → forking growth with [ ] push/pop) are
// iterated to depth, then drawn by a turtle whose angles and segment lengths are
// jittered per draw — so every plant in the row differs. Several stems rooted along
// the bottom edge grow up into a botanical frieze: strokes taper with branch depth,
// glowing flower/leaf dots bloom at tips. Electric-grape glow on near-black, bloomed
// into a luminous silhouette.
private lateinit var rng: Random
private lateinit var noise: OpenSimplexNoise

private class Turtle(var x: Float, var y: Float, var heading: Float, var width: Float, var depth: Int)

// Rewrite a stochastic L-system string. Rules expand "F" (forward+grow) into branchy
// variants chosen at random; "X" is a generator placeholder driving structure.
private fun expand(axiom: String, iterations: Int): String {
    var s = axiom
    repeat(iterations) {
        val sb = StringBuilder(s.length * 3)
        for (ch in s) {
            when (ch) {
                'X' -> sb.append(
                    when (rng.nextInt(4)) {
                        0 -> "F[+X][-X]FX"
                        1 -> "F[-X]F[+X]X"
                        2 -> "F[+X]F[-X][+X]"
                        else -> "F[+FX][-FX]X"
                    }
                )
                'F' -> sb.append(if (rng.nextFloat() < 0.5f) "FF" else "F")
                else -> sb.append(ch)
            }
        }
        s = sb.toString()
    }
    return s
}

private fun drawPlant(
    c: org.jetbrains.skia.Canvas,
    rootX: Float,
    rootY: Float,
    sys: String,
    seg0: Float,
    baseWidth: Float,
    ramp: Palette,
    tips: ArrayList<Triple<Float, Float, Float>>
) {
    val stack = ArrayList<Turtle>()
    var t = Turtle(rootX, rootY, -PIf / 2f, baseWidth, 0)         // pointing up
    val turn = rng.rndf(0.32f, 0.52f)                            // base branch angle (rad)
    for (ch in sys) {
        when (ch) {
            'F' -> {
                // segment length shrinks with depth; jittered + curved by a flow field
                val len = (seg0 * Math.pow(0.86, t.depth.toDouble()).toFloat()) * rng.rndf(0.75f, 1.15f)
                val curl = noise.random2D(t.x * 0.006f, t.y * 0.006f).toFloat() * 0.22f
                val h = t.heading + curl
                val nx = t.x + cos(h) * len
                val ny = t.y + sin(h) * len
                val w = (t.width).coerceAtLeast(0.5f)
                // colour by height → glow brightens as it climbs into the sky
                val climb = (1f - (ny / SIZE)).coerceIn(0f, 1f)
                val color = ramp.bound(climb * (ramp.size - 1))
                val paint = strokeOf(color, w).apply { strokeCap = PaintStrokeCap.ROUND; isAntiAlias = true }
                c.drawLine(Point(t.x, t.y), Point(nx, ny), paint)
                t.x = nx; t.y = ny; t.heading = h
            }
            '+' -> t.heading += turn * rng.rndf(0.6f, 1.4f)
            '-' -> t.heading -= turn * rng.rndf(0.6f, 1.4f)
            '[' -> {
                stack.add(Turtle(t.x, t.y, t.heading, t.width, t.depth))
                t = Turtle(t.x, t.y, t.heading, t.width * 0.72f, t.depth + 1)
            }
            ']' -> {
                // a glowing bloom/leaf where a branch terminates
                tips.add(Triple(t.x, t.y, t.depth.toFloat()))
                if (stack.isNotEmpty()) t = stack.removeAt(stack.size - 1)
            }
        }
    }
    tips.add(Triple(t.x, t.y, t.depth.toFloat()))
}

fun main() {
    println("seed=$SEED")
    rng = Random(SEED)

    val gart = Gart.of("AlienFlora", SIZE, SIZE)
    val d = gart.d
    noise = OpenSimplexNoise(SEED)
    val ground = 0xFF06030E.toInt()
    val ramp = Coolors.electricGrape.expand(256)

    val buf = gart.gartvas()
    val c = buf.canvas
    c.clear(ground)

    // a row of plants forming a frieze; tallest in the middle for a composed silhouette
    val nPlants = rng.nextInt(7, 11)
    val tips = ArrayList<Triple<Float, Float, Float>>()
    var totalSegs = 0
    for (i in 0 until nPlants) {
        val frac = (i + 0.5f) / nPlants
        val rootX = d.wf * frac + rng.rndf(-SIZE * 0.02f, SIZE * 0.02f)
        val rootY = d.hf * rng.rndf(0.98f, 1.02f)
        // central plants taller (silhouette arc) — drives iteration depth + seg length
        val centreBias = 1f - kotlin.math.abs(frac - 0.5f) * 1.3f
        val iters = (4 + (centreBias * 2f)).toInt().coerceIn(3, 6)
        val seg0 = SIZE * (0.026f + 0.02f * centreBias.coerceIn(0f, 1f))
        val baseW = SIZE * (0.006f + 0.004f * centreBias.coerceIn(0f, 1f))
        val sys = expand("X", iters)
        totalSegs += sys.count { it == 'F' }
        drawPlant(c, rootX, rootY, sys, seg0, baseW, ramp, tips)
    }
    println("  $nPlants plants, ~$totalSegs segments, ${tips.size} tips")

    // glowing flower/leaf dots at branch tips — brighter & larger higher up
    for ((tx, ty, depthF) in tips) {
        val climb = (1f - (ty / SIZE)).coerceIn(0f, 1f)
        val color = ramp.bound((0.4f + 0.6f * climb) * (ramp.size - 1))
        val r = (SIZE * 0.004f) * (0.6f + climb * 1.8f) * rng.rndf(0.7f, 1.4f)
        // soft halo + bright core (bloom amplifies these into glows)
        c.drawCircle(tx, ty, r * 2.1f, fillOf(color).alpha(70))
        c.drawCircle(tx, ty, r, fillOf(color))
        c.drawCircle(tx, ty, r * 0.45f, fillOf(0xFFFFFFFF.toInt()).alpha(200))
    }

    val finalv = bloom(gart, buf.snapshot(), ground, SIZE / 220f, grain = 0.05f)
    gart.saveImage(finalv)
    println("  flora done")
}
