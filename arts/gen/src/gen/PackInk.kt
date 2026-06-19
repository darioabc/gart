package gen

import dev.oblac.gart.Gart
import org.jetbrains.skia.PaintStrokeCap
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// 16:9 — GART_SIZE sets HEIGHT; width derived.
private val HEIGHT: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1080
private val WIDTH: Int = HEIGHT * 16 / 9
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

/** Circle packing — largest-fit non-overlapping circles drawn as nested glowing rings. */
private class Pak(
    val tag: String, val target: Int, val minR: Float, val maxR: Float,
    val rings: Int, val width: Float, val alpha: Int,
)

private val VARIANTS = listOf(
    Pak("big", 60, 24f, 240f, 4, 2.4f, 95),
    Pak("dense", 340, 7f, 130f, 2, 1.6f, 80),
    Pak("nested", 150, 13f, 180f, 6, 1.9f, 78),
)

private class C(val x: Float, val y: Float, val r: Float)

fun main() {
    println("seed=$SEED")
    val master = Random(SEED)
    val gart = Gart.of("packInk", WIDTH, HEIGHT)
    val d = gart.d
    val w = d.w.toFloat(); val h = d.h.toFloat()
    val margin = h * 0.05f

    VARIANTS.forEachIndexed { vi, cfg ->
        val rng = Random(master.nextLong())
        val circles = ArrayList<C>()
        var attempts = 0
        val maxAttempts = cfg.target * 500
        while (circles.size < cfg.target && attempts < maxAttempts) {
            attempts++
            val x = (margin + rng.nextFloat() * (w - 2 * margin))
            val y = (margin + rng.nextFloat() * (h - 2 * margin))
            var r = min(min(x - margin, w - margin - x), min(y - margin, h - margin - y))
            for (c in circles) {
                val gap = sqrt((x - c.x) * (x - c.x) + (y - c.y) * (y - c.y)) - c.r - 2f
                if (gap < r) r = gap
                if (r < cfg.minR) break
            }
            if (r >= cfg.minR) circles.add(C(x, y, min(r, cfg.maxR)))
        }
        val buf = gart.gartvas()
        val c = buf.canvas
        circles.forEachIndexed { i, circ ->
            val color = when {
                i % 6 == 0 -> Ink.WHITE
                i % 2 == 0 -> Ink.TEAL
                else -> Ink.PURPLE
            }
            val a = if (color == Ink.PURPLE) (cfg.alpha * 1.7f).toInt() else cfg.alpha
            val paint = Ink.stroke(color, a, cfg.width).apply { strokeCap = PaintStrokeCap.ROUND }
            for (k in 1..cfg.rings) {
                c.drawCircle(circ.x, circ.y, circ.r * k / cfg.rings, paint)
            }
        }
        val finalv = Ink.finish(gart, buf, 0.09f)
        gart.saveImage(finalv, "packInk${vi + 1}.png")
        println("  packInk${vi + 1}.png (${cfg.tag}, n=${circles.size})")
    }
    println("done")
}
