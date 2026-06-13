package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.drawBorder
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.rndi
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Rect

/**
 * Wizard run: Constructive geometry → Recursive rects · Cream & blues · Minimal / sparse.
 *
 * Recursively subdivides the canvas into rectangles (imitates arts/rects/mondrian).
 * Most cells are left as cream negative space; only ~20% are filled from a
 * powderBlue→royalBlue ramp, and the largest FILLED cell becomes a single navy
 * anchor. Thin, pale borders keep the grid airy.
 */

private val gart = Gart.of("minimalBlueRects", 1024, 1024)

private val background = CssColors.cornsilk            // cream
private val lightBlues = intArrayOf(                   // accents (navy is reserved for the anchor)
    CssColors.powderBlue,
    CssColors.lightBlue,
    CssColors.skyBlue,
    CssColors.cornflowerBlue,
    CssColors.steelBlue,
    CssColors.royalBlue,
)

fun main() {
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    c.clear(background)

    // recursive subdivision into a moderate set of varied cells.
    // Any cell larger than ~12% of the canvas is force-split, so no monolith survives.
    val total = d.rect.width * d.rect.height
    var rects = arrayOf(Rect(0f, 0f, d.rect.width, d.rect.height))
    repeat(5) { rects = subdivide(rects, gap = 80f, total = total) }

    // minimal: fill only ~20% of cells; the rest stay cream
    val fillCount = (rects.size * 0.22f).toInt().coerceIn(3, 6)
    val filled = HashSet<Int>()
    while (filled.size < fillCount && filled.size < rects.size) {
        filled.add(rndi(rects.size))
    }
    // the largest FILLED cell is the single deep-navy anchor
    val anchor = filled.maxByOrNull { rects[it].run { width * height } }

    val hairline = strokeOf(CssColors.lightSteelBlue, 2.5f).also { it.strokeCap = PaintStrokeCap.ROUND }
    val edge = strokeOf(CssColors.navy, 4f).also { it.strokeCap = PaintStrokeCap.ROUND }

    rects.forEachIndexed { i, r ->
        when {
            i == anchor -> c.drawRect(r, fillOf(CssColors.navy))
            i in filled -> c.drawRect(r, fillOf(lightBlues[rndi(lightBlues.size)]))
            // else: left cream (negative space)
        }
        c.drawRect(r, hairline)
    }

    c.drawBorder(d, edge)

    gart.saveImage(g) // -> minimalBlueRects.png
}

/**
 * One subdivision pass. A cell too big (> ~12% of the canvas) is always split along its
 * longer side; a cell small enough is left whole; otherwise it's a random keep/split.
 */
private fun subdivide(rects: Array<Rect>, gap: Float, total: Float): Array<Rect> {
    val out = mutableListOf<Rect>()
    rects.forEach { r ->
        val tooSmall = r.width < gap * 2 || r.height < gap * 2
        val tooBig = r.width * r.height > total * 0.12f
        val splitVertical = when {
            tooBig -> r.width >= r.height        // force split on the longer side
            tooSmall -> { out.add(r); return@forEach }
            else -> when (rndi(10)) {
                in 0..3 -> { out.add(r); return@forEach } // ~40% keep → some larger cells remain
                in 4..6 -> true
                else -> false
            }
        }
        if (splitVertical) {
            val x = r.left + r.width / 2
            out.add(Rect(r.left, r.top, x, r.bottom))
            out.add(Rect(x, r.top, r.right, r.bottom))
        } else {
            val y = r.top + r.height / 2
            out.add(Rect(r.left, r.top, r.right, y))
            out.add(Rect(r.left, y, r.right, r.bottom))
        }
    }
    return out.toTypedArray()
}
