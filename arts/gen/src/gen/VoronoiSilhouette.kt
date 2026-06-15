package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.argb
import dev.oblac.gart.gfx.fillOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import dev.oblac.gart.stipple.stippleVoronoi
import kotlin.math.*
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD · Weighted-Voronoi stippled cameo silhouette ─────────────────────────
// A classic head-and-shoulders profile is built procedurally: for each row the
// profile boundary edge(py) traces forehead → brow → nose → lips → chin → neck →
// shoulder by summing gaussian bumps/indents onto a base curve. The body side is
// rendered as a dark, subtly-shaded luminance mass; gȧrt's Lloyd-relaxed weighted
// Voronoi stippler then scatters thousands of dots whose density tracks darkness —
// a pointillist cameo. Dots are tinted from the coolors "Molten" palette, with the
// lit profile contour picking up cooler steel-blue/cream highlights.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val gart = Gart.of("VoronoiSilhouette", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)

    // ── Profile geometry (head fills upper ~2/3, facing left) ────────────────
    // Vertical anchors as fractions of frame height.
    val topY = 0.16f * d.h          // crown
    val chinY = 0.62f * d.h         // chin
    val faceH = chinY - topY        // head height
    // Horizontal: the face profile sits left of centre, body fills the right side.
    val baseX = d.w * 0.52f         // nominal column of the face front
    val scale = faceH               // feature scale unit

    // edge(py): the x of the front-of-face / body boundary. Interior = px >= edge.
    fun gauss(t: Float, c: Float, w: Float): Float {
        val z = (t - c) / w
        return exp(-z * z)
    }
    fun edge(py: Float): Float {
        // normalized vertical position over the head band (0 crown .. 1 chin)
        val u = (py - topY) / faceH
        // Base curve: a gentle forehead-to-jaw arc that recedes toward crown & chin.
        // Rounded skull at top, receding toward the chin.
        var x = baseX - scale * 0.07f * sin((u * PI).toFloat())        // overall facial roundness
        // Forehead: small forward bulge near u~0.18
        x -= scale * 0.045f * gauss(u, 0.18f, 0.11f)
        // Brow ridge / nose bridge indent near u~0.34
        x += scale * 0.030f * gauss(u, 0.34f, 0.05f)
        // Nose tip: strong forward protrusion near u~0.46
        x -= scale * 0.115f * gauss(u, 0.46f, 0.045f)
        // Sub-nasal indent (philtrum) near u~0.53
        x += scale * 0.050f * gauss(u, 0.535f, 0.03f)
        // Upper + lower lip bumps near u~0.60 / 0.66
        x -= scale * 0.055f * gauss(u, 0.595f, 0.030f)
        x -= scale * 0.045f * gauss(u, 0.660f, 0.032f)
        // Mouth-chin indent near u~0.72
        x += scale * 0.040f * gauss(u, 0.720f, 0.030f)
        // Chin forward near u~0.82
        x -= scale * 0.060f * gauss(u, 0.820f, 0.060f)
        return x
    }

    // Vertical band extents for body parts.
    val crownTop = topY - scale * 0.02f
    val neckTopY = chinY                       // under the chin the neck begins
    val neckBackX = baseX + scale * 0.30f      // back of the neck column
    val shoulderTopY = 0.78f * d.h             // where shoulders flare out
    val frameBottom = d.h.toFloat()

    // Back of the head/skull: a rounded ellipse so the cranium has a believable bulk.
    val skullCx = baseX + scale * 0.27f
    val skullCy = topY + faceH * 0.42f
    val skullRx = scale * 0.40f
    val skullRy = faceH * 0.55f

    // Render the silhouette as a grayscale luminance field: dark inside (cameo mass),
    // white outside (empty cream ground). DARKER => denser stipple dots.
    val src = Gartmap(gart.gartvas())
    val white = 0xFFFFFFFF.toInt()
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val fy = py.toFloat()
            val fx = px.toFloat()

            var inside = false
            if (fy in crownTop..chinY) {
                // Head/face band: between front profile edge and back-of-skull ellipse.
                val front = edge(fy)
                // back boundary from skull ellipse
                val ey = (fy - skullCy) / skullRy
                val backHalf = if (abs(ey) <= 1f) skullRx * sqrt(1f - ey * ey) else 0f
                val back = skullCx + backHalf
                if (fx in front..back) inside = true
            } else if (fy in neckTopY..frameBottom) {
                // Neck + shoulder band.
                // Front of neck continues from the chin, slanting slightly forward-down.
                val tt = (fy - neckTopY) / (frameBottom - neckTopY)
                val neckFront = edge(chinY) + scale * 0.02f - scale * 0.05f * tt
                if (fy < shoulderTopY) {
                    // straight-ish neck column
                    if (fx in neckFront..neckBackX) inside = true
                } else {
                    // shoulders flare outward to both frame edges
                    val s = ((fy - shoulderTopY) / (frameBottom - shoulderTopY)).coerceIn(0f, 1f)
                    val shoulderFront = neckFront - scale * 0.85f * s * s
                    val shoulderBack = neckBackX + scale * 1.30f * s * s
                    if (fx in shoulderFront..shoulderBack) inside = true
                }
            }

            if (!inside) { src[px, py] = white; continue }

            // Interior shading: cameo mass is mostly DARK but not flat.
            // Distance to the front profile edge -> lighter near the lit facial contour.
            val frontEdge = if (fy <= chinY) edge(fy.coerceIn(crownTop, chinY))
            else edge(chinY) + scale * 0.02f
            val edgeDist = (fx - frontEdge) / (scale * 0.5f)   // 0 at contour, grows inward
            val rim = exp(-(edgeDist * edgeDist) * 2.2f)        // bright lit rim near the contour
            // faint simplex texture so the mass reads as stippled, not pure black
            val tex = (noise.random3D(fx * 0.006f, fy * 0.006f, 0.0f).toFloat() - 0.5f)
            // base dark mass with a gentle vertical gradient (a touch lighter low on body)
            val vGrad = ((fy - topY) / d.h).coerceIn(0f, 1f)
            var lum = 0.08f + 0.34f * rim + 0.06f * vGrad + 0.05f * tex
            lum = lum.coerceIn(0.02f, 0.55f)
            val g = (lum * 255).toInt().coerceIn(0, 255)
            src[px, py] = argb(255, g, g, g)
        }
    }

    val dots = stippleVoronoi(
        src,
        pointCount = (SIZE * SIZE / 140),
        iterations = 18,
        gamma = 1.3f,
        minRadius = SIZE * 0.0009f,
        maxRadius = SIZE * 0.0045f,
        seed = SEED.toInt()
    )
    println("  ${dots.size} stipple dots")

    val ramp = Coolors.molten.expand(256)
    val out = gart.gartvas()
    val c = out.canvas
    c.clear(0xFFFDF0D5.toInt())   // papaya-whip ground
    for (dot in dots) {
        // Tint: silhouette mass = deep molten-red/navy (low-to-mid ramp);
        // dots near the lit profile contour pick up cooler steel-blue/cream highlights.
        val fy = dot.y
        val frontEdge = if (fy <= chinY) edge(fy.coerceIn(crownTop, chinY))
        else edge(chinY) + scale * 0.02f
        val edgeDist = (dot.x - frontEdge) / (scale * 0.5f)
        val rim = exp(-(edgeDist * edgeDist) * 2.2f)
        val vGrad = ((fy - topY) / d.h).coerceIn(0f, 1f)
        val t = (0.18f + 0.72f * rim + 0.12f * vGrad)
        val col = ramp.bound((t.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
