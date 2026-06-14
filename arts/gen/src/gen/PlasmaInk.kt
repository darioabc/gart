package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.shader.createNoiseGrainFilter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.jetbrains.skia.Paint

// 16:9 — GART_SIZE sets HEIGHT; width derived.
private val HEIGHT: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1080
private val WIDTH: Int = HEIGHT * 16 / 9
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

/**
 * Plasma — a per-pixel sum-of-sines field mapped through a fiuto colour ramp
 * (navy → purple → teal → white → …). High variance via random frequencies/phases per
 * image. A light noise-grain pass adds texture. Not stroke-based, so it skips the bloom.
 */
fun main() {
    println("seed=$SEED")
    val master = Random(SEED)
    val gart = Gart.of("plasmaInk", WIDTH, HEIGHT)
    val d = gart.d
    val w = d.w; val h = d.h
    val cx = w / 2f; val cy = h / 2f
    val scale = 1f / (h * 0.18f)

    repeat(10) { vi ->
        val rng = Random(master.nextLong())
        val f1x = rng.f(0.4f, 1.6f); val f1y = rng.f(0.4f, 1.6f); val p1 = rng.f(0f, 6.28f)
        val f2 = rng.f(0.6f, 2.4f); val p2 = rng.f(0f, 6.28f)
        val f3 = rng.f(0.5f, 2.0f); val p3 = rng.f(0f, 6.28f)
        val f4 = rng.f(0.8f, 3.0f); val p4 = rng.f(0f, 6.28f)
        val warp = rng.f(0.6f, 2.6f)
        val cycles = rng.f(1.0f, 2.3f)

        val gm = Gartmap(d)
        val px = gm.pixels
        var idx = 0
        for (y in 0 until h) {
            val yy = (y - cy) * scale
            for (x in 0 until w) {
                val xx = (x - cx) * scale
                val r = sqrt(xx * xx + yy * yy)
                var v = sin(xx * f1x + p1) + sin(yy * f1y + p1)
                v += sin((xx + yy) * f2 + p2)
                v += sin(r * f4 * warp + p4)
                v += cos((xx * cos(p3) - yy * sin(p3)) * f3 + p3)
                val tt = ((v / 4f) * 0.5f + 0.5f) * cycles
                px[idx++] = Ink.ramp(Ink.PLASMA_STOPS, tt - tt.toInt().toFloat())
            }
        }

        val g = gart.gartvas()
        gm.drawToCanvas(g)
        gm.close()
        val finalv = gart.gartvas()
        finalv.canvas.drawImage(g.snapshot(), 0f, 0f, Paint().apply {
            imageFilter = createNoiseGrainFilter(0.06f, d)
        })
        gart.saveImage(finalv, "plasmaInk${vi + 1}.png")
        println("  plasmaInk${vi + 1}.png")
    }
    println("done")
}

private fun Random.f(a: Float, b: Float) = a + nextFloat() * (b - a)
