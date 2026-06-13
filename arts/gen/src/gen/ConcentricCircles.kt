package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.color.toStrokePaint
import dev.oblac.gart.gfx.drawCircle

/**
 * Concentric circles in shades of blue on a cream background.
 *
 * Rings gradate from a soft `powderBlue` at the rim to a deep `navy` core,
 * with the cream background showing through the gaps between strokes.
 */
fun main() {
    val gart = Gart.of("concentricCircles", 1024, 1024)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    // cream background
    c.clear(CssColors.cornsilk)

    // blue ramp, light (rim) -> dark (core)
    val blues = intArrayOf(
        CssColors.powderBlue,
        CssColors.lightBlue,
        CssColors.lightSkyBlue,
        CssColors.skyBlue,
        CssColors.deepSkyBlue,
        CssColors.cornflowerBlue,
        CssColors.steelBlue,
        CssColors.royalBlue,
        CssColors.navy,
    )

    val rings = 26
    val maxR = 480f
    val minR = 16f
    val strokeW = 11f

    for (i in 0 until rings) {
        val t = i.toFloat() / (rings - 1)        // 0 at rim .. 1 at core
        val r = maxR - (maxR - minR) * t
        val idx = (t * (blues.size - 1)).toInt() // rim -> powderBlue, core -> navy
        c.drawCircle(d.center, r, blues[idx].toStrokePaint(strokeW))
    }

    gart.saveImage(g) // -> concentricCircles.png in the current directory
}
