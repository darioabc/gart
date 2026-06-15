package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartvas
import dev.oblac.gart.shader.createNoiseGrainFilter
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint

/**
 * Shared finishing helpers for the "go wild" gallery (feature-exploration batch).
 *
 * `bloom` is the gȧrt house move: composite a transparent stroke image over a dark
 * ground through two SCREEN-blended Gaussian passes so dense areas glow, then lay a
 * noise-grain shader on top for a printed, non-flat-vector texture.
 */
fun bloom(gart: Gart, sharp: Image, ground: Int, sigma: Float, grain: Float = 0.08f): Gartvas {
    val out = gart.gartvas()
    val oc = out.canvas
    oc.clear(ground)
    listOf(sigma, sigma / 2.5f).forEach { s ->
        oc.drawImage(sharp, 0f, 0f, Paint().apply {
            imageFilter = ImageFilter.makeBlur(s, s, FilterTileMode.DECAL)
            blendMode = BlendMode.SCREEN
        })
    }
    oc.drawImage(sharp, 0f, 0f, Paint().apply { blendMode = BlendMode.SCREEN }) // crisp top

    val fin = gart.gartvas()
    fin.canvas.drawImage(out.snapshot(), 0f, 0f, Paint().apply {
        imageFilter = createNoiseGrainFilter(grain, gart.d)
    })
    return fin
}

/** Grain-only finish for LIGHT grounds, where SCREEN bloom would wash out. */
fun grainOnly(gart: Gart, src: Image, grain: Float = 0.06f): Gartvas {
    val fin = gart.gartvas()
    fin.canvas.drawImage(src, 0f, 0f, Paint().apply {
        imageFilter = createNoiseGrainFilter(grain, gart.d)
    })
    return fin
}

/** Scale an ARGB color's RGB toward black by factor f (alpha preserved). */
fun darken(color: Int, f: Float): Int {
    val a = (color ushr 24) and 0xFF
    val r = (((color shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
    val g = (((color shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
    val b = ((color and 0xFF) * f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
