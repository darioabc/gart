package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartvas
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.shader.createNoiseGrainFilter
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint

/**
 * Shared "light in the dark" finishing pass + fiuto tones, used by every gen ink piece
 * so the whole family stays consistent. Tones lifted from the fiuto repo:
 *  - navy ground  #071426 (favicon background)
 *  - teal accent  #00D3BB (logo fill)
 *  - purple accent #5E49D6 (default slides-theme accent)
 */
object Ink {
    val GROUND = 0xFF071426.toInt()
    val TEAL = 0xFF00D3BB.toInt()
    val PURPLE = 0xFF5E49D6.toInt()
    val WHITE = CssColors.white

    fun stroke(color: Int, alpha: Int, width: Float = 1f): Paint =
        strokeOf(color, width).apply { this.alpha = alpha.coerceIn(0, 255); isAntiAlias = true }

    /**
     * Composite a *transparent* stroke buffer over the navy ground with SCREEN-blend
     * Gaussian-blur bloom (so dense areas glow), then a noise-grain texture pass.
     * Returns the final gartvas, ready to saveImage.
     */
    fun finish(gart: Gart, buf: Gartvas, grain: Float = 0.10f): Gartvas {
        val d = gart.d
        val sharp = buf.snapshot()
        val out = gart.gartvas()
        val oc = out.canvas
        oc.clear(GROUND)
        val s = d.h / 170f
        for (sigma in listOf(s, s / 2.5f)) {
            oc.drawImage(sharp, 0f, 0f, Paint().apply {
                imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.DECAL)
                blendMode = BlendMode.SCREEN
            })
        }
        oc.drawImage(sharp, 0f, 0f, Paint().apply { blendMode = BlendMode.SCREEN })
        val finalv = gart.gartvas()
        finalv.canvas.drawImage(out.snapshot(), 0f, 0f, Paint().apply {
            imageFilter = createNoiseGrainFilter(grain, d)
        })
        return finalv
    }

    // ---- colour helpers for per-pixel pieces (plasma) ----

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val ar = (a ushr 16) and 0xFF; val ag = (a ushr 8) and 0xFF; val ab = a and 0xFF
        val br = (b ushr 16) and 0xFF; val bg = (b ushr 8) and 0xFF; val bb = b and 0xFF
        val r = (ar + (br - ar) * t).toInt()
        val g = (ag + (bg - ag) * t).toInt()
        val bl = (ab + (bb - ab) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /** Multi-stop colour ramp; t in [0,1]. */
    fun ramp(stops: List<Int>, t: Float): Int {
        val tt = t.coerceIn(0f, 1f) * (stops.size - 1)
        val i = tt.toInt().coerceAtMost(stops.size - 2)
        return lerp(stops[i], stops[i + 1], tt - i)
    }

    /** navy→purple→teal→white→teal→purple→navy — both fiuto accents, cycles seamlessly. */
    val PLASMA_STOPS = listOf(GROUND, PURPLE, TEAL, WHITE, TEAL, PURPLE, GROUND)
}
