package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.angle.Degrees
import dev.oblac.gart.flow.Flow2
import dev.oblac.gart.flow.FlowField
import dev.oblac.gart.flow.PointTracer
import dev.oblac.gart.gfx.toPath
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import dev.oblac.gart.noise.poissonDiskSampling
import org.jetbrains.skia.PaintStrokeCap
import kotlin.math.atan2
import kotlin.random.Random

// 16:9 — GART_SIZE sets HEIGHT; width derived. Draft: GART_SIZE=540 -> 960x540.
private val HEIGHT: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1080
private val WIDTH: Int = HEIGHT * 16 / 9
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

/** Flow-field streamlines through a simplex-noise vector field, finished with the Ink glow. */
private class Flo(
    val tag: String, val spacing: Float, val freq: Double, val angleScale: Float,
    val vortex: Float, val steps: Int, val width: Float, val alpha: Int,
)

private val VARIANTS = listOf(
    Flo("laminar", 22f, 0.0009, 1.6f, 0.0f, 1300, 2.4f, 50),
    Flo("turbulent", 18f, 0.0030, 3.2f, 0.0f, 800, 2.0f, 44),
    Flo("vortex", 20f, 0.0014, 1.2f, 0.85f, 1100, 2.2f, 48),
)

fun main() {
    println("seed=$SEED")
    val master = Random(SEED)
    val gart = Gart.of("flowInk", WIDTH, HEIGHT)
    val d = gart.d
    val cx = d.cx; val cy = d.cy

    VARIANTS.forEachIndexed { vi, cfg ->
        val rng = Random(master.nextLong())
        val simplex = OpenSimplexNoise(rng.nextLong())
        val flowField = FlowField.of(d) { x, y ->
            val raw = simplex.random2D(x * cfg.freq, y * cfg.freq).toFloat()
            val n = raw * 360f * cfg.angleScale
            val ang: Float = if (cfg.vortex > 0f) {
                val base = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat() + 90f
                base * cfg.vortex + n * (1f - cfg.vortex)
            } else n
            Flow2(Degrees.of(ang), 1f)
        }
        val seeds = poissonDiskSampling(d.rect, cfg.spacing, random = rng)
        val buf = gart.gartvas()
        seeds.forEachIndexed { i, s ->
            val pts = PointTracer(d, flowField).trace(s, cfg.steps)
            if (pts.size < 2) return@forEachIndexed
            val color = when {
                i % 6 == 0 -> Ink.WHITE
                i % 2 == 0 -> Ink.TEAL
                else -> Ink.PURPLE
            }
            val a = if (color == Ink.PURPLE) (cfg.alpha * 1.7f).toInt() else cfg.alpha
            val paint = Ink.stroke(color, a, cfg.width).apply { strokeCap = PaintStrokeCap.ROUND }
            buf.canvas.drawPath(pts.toPath(), paint)
        }
        val finalv = Ink.finish(gart, buf, 0.09f)
        gart.saveImage(finalv, "flowInk${vi + 1}.png")
        println("  flowInk${vi + 1}.png (${cfg.tag}, seeds=${seeds.size})")
    }
    println("done")
}
