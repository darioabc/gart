package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.math.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.Point

// 16:9 — GART_SIZE sets HEIGHT; width derived.
private val HEIGHT: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1080
private val WIDTH: Int = HEIGHT * 16 / 9
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

private val HALF_PI = (PI / 2).toFloat()

/**
 * Lissajous figures: x = sin(a·t + δ), y = sin(b·t), phase-swept into a glowing bundle.
 * Colour is banded across the sweep (early copies teal, late copies purple) so the two
 * fiuto accents land at different orientations and both stay visible. Some variants damp
 * (exp decay) into spirals. High variance across the 10 via the a:b frequency ratio.
 */
private class Lis(
    val tag: String, val a: Float, val b: Float, val phase: Float,
    val decay: Float, val copies: Int, val phaseStep: Float, val alpha: Int,
)

private val VARIANTS = listOf(
    Lis("1:2", 1f, 2f, HALF_PI, 0.0f, 90, 0.040f, 16),
    Lis("2:3", 2f, 3f, HALF_PI, 0.0f, 100, 0.036f, 16),
    Lis("3:4", 3f, 4f, 0.4f, 0.0f, 110, 0.034f, 15),
    Lis("3:5", 3f, 5f, 0.7f, 0.0f, 110, 0.032f, 15),
    Lis("4:5", 4f, 5f, HALF_PI, 0.0f, 120, 0.030f, 15),
    Lis("5:6", 5f, 6f, 0.3f, 0.0f, 120, 0.030f, 14),
    Lis("5:7", 5f, 7f, 0.9f, 0.0f, 130, 0.028f, 14),
    Lis("7:9", 7f, 9f, 0.5f, 0.0f, 130, 0.026f, 13),
    Lis("damped-3:2", 3f, 2f, 0.2f, 0.012f, 150, 0.045f, 14),
    Lis("damped-4:7", 4f, 7f, 0.6f, 0.015f, 150, 0.045f, 13),
)

fun main() {
    println("seed=$SEED")
    val master = Random(SEED)
    val gart = Gart.of("lissaInk", WIDTH, HEIGHT)
    val d = gart.d
    val cx = d.cx; val cy = d.cy
    val amp = d.h * 0.46f
    val iterations = 1400
    val tStep = 0.01f

    VARIANTS.forEachIndexed { vi, cfg ->
        val rng = Random(master.nextLong())
        val p0 = rng.nextDouble(-PI, PI).toFloat()
        val buf = gart.gartvas()
        val c = buf.canvas
        // Nested concentric Lissajous: scale grows small->full across copies, so the
        // teal (inner) and purple (outer) colour bands separate radially and both read.
        repeat(cfg.copies) { k ->
            val frac = k.toFloat() / (cfg.copies - 1).coerceAtLeast(1)
            val s = amp * (0.22f + 0.78f * frac)
            val dp = p0 + k * cfg.phaseStep
            val color = when {
                frac < 0.42f -> Ink.TEAL
                frac < 0.58f -> Ink.WHITE
                else -> Ink.PURPLE
            }
            val al = if (color == Ink.PURPLE) (cfg.alpha * 1.8f).toInt() else cfg.alpha
            val paint = Ink.stroke(color, al, 1f)
            var prev: Point? = null
            var t = 0f
            repeat(iterations) {
                val e = if (cfg.decay > 0f) exp(-cfg.decay * t) else 1f
                val pt = Point(
                    cx + s * e * sin(cfg.a * t + cfg.phase + dp),
                    cy + s * e * sin(cfg.b * t),
                )
                prev?.let { c.drawLine(it, pt, paint) }
                prev = pt
                t += tStep
            }
        }
        val finalv = Ink.finish(gart, buf, 0.09f)
        gart.saveImage(finalv, "lissaInk${vi + 1}.png")
        println("  lissaInk${vi + 1}.png (${cfg.tag})")
    }
    println("done")
}
