package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.grow.Growth
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import kotlin.random.Random

// 16:9 — GART_SIZE sets HEIGHT; width derived.
private val HEIGHT: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1080
private val WIDTH: Int = HEIGHT * 16 / 9
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

/**
 * Differential growth — a closed polyline wrinkles outward, bounded by maxRadius so it
 * stays in frame. Colour bands by growth time (teal core -> white -> purple rim), which
 * keeps both fiuto accents spatially separated and visible. NB: Growth uses global RNG
 * internally, so it is not byte-for-byte reproducible by GART_SEED.
 */
private class Gro(
    val tag: String, val maxEdge: Float, val rejection: Float, val attraction: Float,
    val brownian: Float, val seedR: Float, val seedN: Int, val steps: Int, val alpha: Int,
)

private val VARIANTS = listOf(
    Gro("coral", 6f, 14f, 0.15f, 0.6f, 30f, 3, 600, 14),
    Gro("lobes", 7f, 18f, 0.18f, 0.5f, 40f, 4, 560, 13),
    Gro("dense", 6f, 12f, 0.13f, 0.55f, 26f, 3, 520, 12),
)

fun main() {
    println("seed=$SEED")
    val gart = Gart.of("growInk", WIDTH, HEIGHT)
    val d = gart.d
    val bound = d.h * 0.46f

    VARIANTS.forEachIndexed { vi, cfg ->
        val growth = Growth(
            maxEdgeLength = cfg.maxEdge,
            rejectionRadius = cfg.rejection,
            attractionStrength = cfg.attraction,
            rejectionStrength = 0.5f,
            brownianStrength = cfg.brownian,
            centerX = d.cx,
            centerY = d.cy,
            maxRadius = bound,
        )
        growth.seedCircle(d.cx, d.cy, cfg.seedR, cfg.seedN)
        val buf = gart.gartvas()
        val c = buf.canvas
        var step = 0
        while (step < cfg.steps && !growth.done && growth.size < 60_000) {
            growth.step()
            val t = step.toFloat() / cfg.steps
            val color = when {
                t < 0.40f -> Ink.TEAL
                t < 0.60f -> Ink.WHITE
                else -> Ink.PURPLE
            }
            val a = if (color == Ink.PURPLE) (cfg.alpha * 1.7f).toInt() else cfg.alpha
            c.drawPath(growth.toPath(), Ink.stroke(color, a, 0.8f).apply {
                strokeCap = PaintStrokeCap.ROUND
                strokeJoin = PaintStrokeJoin.ROUND
            })
            step++
        }
        val finalv = Ink.finish(gart, buf, 0.09f)
        gart.saveImage(finalv, "growInk${vi + 1}.png")
        println("  growInk${vi + 1}.png (${cfg.tag}, nodes=${growth.size}, steps=$step)")
    }
    println("done")
}
