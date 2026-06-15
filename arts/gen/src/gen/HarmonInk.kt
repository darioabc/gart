package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartvas
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*       // brings all Random extension fns into scope
import dev.oblac.gart.shader.createNoiseGrainFilter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Point

// Canvas: 16:9 wide. GART_SIZE (shell env var) sets the HEIGHT; width is derived.
// Draft: GART_SIZE=540 -> 960x540. Full-res default: 1080 -> 1920x1080.
private val HEIGHT: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1080
private val WIDTH: Int = HEIGHT * 16 / 9

// Seed: pass GART_SEED=<long> to reproduce. Printed to stdout.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull()
    ?: Random.nextLong()

/**
 * Dense harmonographs with the gȧlléry finishing pass.
 *
 * Technique: harmonograph / spirograph — bespoke multi-harmonic parametric curves
 * (flower-of-life, celtic-knot, rose, damped-pendulum, lissajous) phase-swept into a
 * dense weave, instead of the plain 2-term `harmongraph2`.
 *
 * Palette: "light drawn in the dark" — fiuto-navy ground (#071426), white ink, and the
 * fiuto brand teal (#00D3BB) as the single accent, sourced from the fiuto repo logo.
 *
 * Finishing (this is what makes gȧrt pieces read as "interesting"):
 *  - SCREEN-blend bloom: the stroke buffer is composited over the ground twice through
 *    Gaussian blur so dense cores glow.
 *  - noise-grain shader pass for a printed, non-flat-vector texture.
 *  - auto-fit so every figure fills the frame regardless of its formula.
 */
private val PI_F = PI.toFloat()
private val HALF_PI = (PI / 2).toFloat()
private val THIRD_PI = (PI / 3).toFloat()

// fiuto brand, lifted from public/Logo.svg + favicon.svg
private val GROUND = 0xFF071426.toInt()   // favicon background navy
private val ACCENT = 0xFF00D3BB.toInt()   // brand teal (logo fill)
private val INK = CssColors.white

private typealias Curve = (t: Float, dp: Float) -> Point

// ---- bespoke curve formulas (raw, ~unit scale; auto-fit handles canvas sizing) ----

private fun flowerOfLife(t: Float, dp: Float, harmonics: Int): Point {
    var x = 0f; var y = 0f
    for (k in 0..harmonics) {
        val n = (2 * k + 1).toFloat()
        x += sin(n * t + dp) / n
        y += cos(n * t + dp) / n
    }
    return Point(x, y)
}

private fun celticKnot(t: Float, dp: Float, a: Float, b: Float, c: Float, e: Float): Point {
    val x = sin(a * t + HALF_PI + dp) + 0.5f * sin(b * t + dp)
    val y = cos(c * t + dp) + 0.7f * cos(e * t + THIRD_PI + dp)
    return Point(x, y)
}

private fun rosePolar(t: Float, dp: Float, k: Float, m: Float): Point {
    val r = cos(k * t + dp) + 0.4f * cos(m * t)
    return Point(r * cos(t), r * sin(t))
}

private fun spiralRose(t: Float, dp: Float): Point {
    val x = sin(2 * t + dp) + 0.6f * sin(5 * t) + 0.3f * sin(11 * t + dp)
    val y = cos(3 * t + dp) + 0.6f * cos(7 * t) + 0.3f * cos(13 * t + dp)
    return Point(x, y)
}

private fun damped(t: Float, dp: Float, f1: Float, f2: Float, f3: Float, f4: Float, decay: Float): Point {
    val e = exp(-decay * t)
    val x = (sin(f1 * t + dp) + sin(f2 * t)) * e
    val y = (sin(f3 * t + dp + HALF_PI) + sin(f4 * t)) * e
    return Point(x, y)
}

private fun lissajous(t: Float, dp: Float, a: Float, b: Float, ph: Float): Point =
    Point(sin(a * t + dp), sin(b * t + ph))

// ---- presets ----

private class Preset(
    val tag: String,
    val curve: Curve,
    val tStep: Float,
    val iterations: Int,
    val repeats: Int,
    val phaseStep: Float,
    val alpha: Int,
    val accentEvery: Int, // every Nth phase-copy is drawn in teal (the accent)
)

private val PRESETS = listOf(
    Preset("flower-3", { t, dp -> flowerOfLife(t, dp, 3) }, 0.010f, 700, 520, 0.055f, 24, 4),
    Preset("flower-2", { t, dp -> flowerOfLife(t, dp, 2) }, 0.011f, 650, 480, 0.070f, 26, 5),
    Preset("flower-4", { t, dp -> flowerOfLife(t, dp, 4) }, 0.010f, 720, 500, 0.050f, 22, 4),
    Preset("celtic-A", { t, dp -> celticKnot(t, dp, 3f, 5f, 2f, 7f) }, 0.012f, 620, 540, 0.050f, 24, 4),
    Preset("celtic-B", { t, dp -> celticKnot(t, dp, 2f, 7f, 3f, 5f) }, 0.012f, 600, 560, 0.045f, 24, 4),
    Preset("celtic-C", { t, dp -> celticKnot(t, dp, 4f, 9f, 5f, 3f) }, 0.012f, 600, 560, 0.045f, 22, 5),
    Preset("rose-5-2", { t, dp -> rosePolar(t, dp, 5f, 2f) }, 0.010f, 660, 500, 0.060f, 26, 4),
    Preset("rose-7-3", { t, dp -> rosePolar(t, dp, 7f, 3f) }, 0.010f, 680, 520, 0.050f, 24, 5),
    Preset("rose-9-4", { t, dp -> rosePolar(t, dp, 9f, 4f) }, 0.010f, 700, 520, 0.045f, 22, 5),
    Preset("spiralRose", { t, dp -> spiralRose(t, dp) }, 0.010f, 700, 480, 0.060f, 24, 4),
    Preset("damped-2-3", { t, dp -> damped(t, dp, 2f, 3f, 3f, 2f, 0.03f) }, 0.020f, 600, 600, 0.040f, 22, 4),
    Preset("damped-4-5", { t, dp -> damped(t, dp, 4f, 5f, 5f, 4f, 0.05f) }, 0.020f, 520, 620, 0.045f, 22, 4),
    Preset("liss-3-4", { t, dp -> lissajous(t, dp, 3f, 4f, HALF_PI) }, 0.008f, 1500, 220, 0.070f, 18, 5),
    Preset("liss-5-6", { t, dp -> lissajous(t, dp, 5f, 6f, THIRD_PI) }, 0.008f, 1500, 220, 0.060f, 18, 5),
)

fun main() {
    println("seed=$SEED")
    val master = Random(SEED)

    val gart = Gart.of("harmonInk", WIDTH, HEIGHT)
    val d = gart.d
    val center = d.center
    val targetR = HEIGHT * 0.47f          // height-bound: full motif visible, centered
    val wide = HEIGHT / 170f
    val tight = HEIGHT / 430f

    PRESETS.forEachIndexed { i, p ->
        val rng = Random(master.nextLong())
        val phase0 = rng.nextDouble(-PI, PI).toFloat()
        val alpha = (p.alpha + rng.nextInt(-3, 4)).coerceIn(12, 90)

        val fit = autoFit(p.curve, p.tStep, p.iterations, targetR)
        val white = strokeOf(INK, 1f).apply { this.alpha = alpha; isAntiAlias = true }
        val teal = strokeOf(ACCENT, 1f).apply { this.alpha = (alpha * 1.3f).toInt().coerceAtMost(120); isAntiAlias = true }

        // 1) strokes onto a transparent buffer (overlaps accumulate to bright cores)
        val buf = gart.gartvas()
        repeat(p.repeats) { k ->                       // white pass first
            if (p.accentEvery <= 0 || k % p.accentEvery != 0)
                drawCurve(buf, center, p.curve, p.tStep, p.iterations, phase0 + k * p.phaseStep, fit, white)
        }
        repeat(p.repeats) { k ->                       // teal accent on top
            if (p.accentEvery > 0 && k % p.accentEvery == 0)
                drawCurve(buf, center, p.curve, p.tStep, p.iterations, phase0 + k * p.phaseStep, fit, teal)
        }

        // 2) composite over the ground with SCREEN-blend bloom (glow)
        val sharp = buf.snapshot()
        val out = gart.gartvas()
        val oc = out.canvas
        oc.clear(GROUND)
        oc.drawImage(sharp, 0f, 0f, Paint().apply {
            imageFilter = ImageFilter.makeBlur(wide, wide, FilterTileMode.DECAL); blendMode = BlendMode.SCREEN
        })
        oc.drawImage(sharp, 0f, 0f, Paint().apply {
            imageFilter = ImageFilter.makeBlur(tight, tight, FilterTileMode.DECAL); blendMode = BlendMode.SCREEN
        })
        oc.drawImage(sharp, 0f, 0f, Paint().apply { blendMode = BlendMode.SCREEN }) // crisp lines

        // 3) noise-grain pass for printed texture
        val finalv = gart.gartvas()
        finalv.canvas.drawImage(out.snapshot(), 0f, 0f, Paint().apply {
            imageFilter = createNoiseGrainFilter(0.10f, d)
        })

        val name = "harmonInk%02d.png".format(i + 1)
        gart.saveImage(finalv, name)
        println("  [${i + 1}/${PRESETS.size}] $name  (${p.tag}, alpha=$alpha)")
    }

    println("done: ${PRESETS.size} images")
}

/** Scale the raw curve so its largest excursion lands at [targetR] px → fills the frame. */
private fun autoFit(curve: Curve, tStep: Float, iterations: Int, targetR: Float): Float {
    var maxAbs = 1e-4f
    var t = 0f
    repeat(iterations) {
        val p = curve(t, 0f)
        val m = max(abs(p.x), abs(p.y))
        if (m > maxAbs) maxAbs = m
        t += tStep
    }
    return targetR / maxAbs
}

private fun drawCurve(
    buf: Gartvas, center: Point, curve: Curve,
    tStep: Float, iterations: Int, dp: Float, fit: Float, paint: Paint,
) {
    val c = buf.canvas
    var prev: Point? = null
    var t = 0f
    repeat(iterations) {
        val raw = curve(t, dp)
        val pt = Point(raw.x * fit + center.x, raw.y * fit + center.y)
        prev?.let { c.drawLine(it, pt, paint) }
        prev = pt
        t += tStep
    }
}
