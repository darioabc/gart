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

// ── WILD · Weighted-Voronoi stippled drapery ──────────────────────────────────
// Hanging cloth fills the frame: vertical folds are modelled as a horizontal phase
// field warped by low-frequency simplex noise so the creases meander like real fabric.
// Bright lit ridges shed dots while dark valleys gather them, and gȧrt's Lloyd-relaxed
// weighted Voronoi stippler renders the cloth as pointillist drapery. Same family as
// VoronoiOrb. Showcases: stipple/VoronoiStippling.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val gart = Gart.of("VoronoiDrapery", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)

    val src = Gartmap(gart.gartvas())
    val white = 0xFFFFFFFF.toInt()
    // ~6 broad vertical folds; each is a one-side-lit cylinder so the cloth reads
    // as chiaroscuro drapery, not stripes. warp + sag make the creases meander/bow.
    val folds = 6f
    val twoPi = (PI * 2.0).toFloat()
    val lightDir = 0.9f                            // light from upper-left of each fold
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val u = px / SIZE.toFloat()           // 0..1 across
            val v = py / SIZE.toFloat()           // 0..1 down
            // Low-frequency horizontal warp: folds meander, drifting down the cloth.
            val warp = noise.random3D(u * 1.6f, v * 0.9f, 7.0f).toFloat() - 0.5f
            val warp2 = noise.random3D(u * 0.7f, v * 1.4f, 13.0f).toFloat() - 0.5f
            // Gentle vertical sag: fold centrelines bow slowly down the cloth so it
            // hangs rather than running as rigid vertical bars.
            val sag = sin(v * 2.3f + 0.6f) * 0.55f + sin(v * 1.1f) * 0.35f
            // Phase field: primary fold train + meander + sag.
            val phase = u * folds * twoPi + warp * 2.2f + warp2 * 1.1f + sag
            // Asymmetric cylinder shading: light one side, shadow the turning-away
            // side. cos(phase + light) gives a rounded, one-sided falloff (-1..1).
            val lit = (cos(phase + lightDir) + 1f) * 0.5f   // 0 shadow .. 1 lit crest
            // Push through a contrast curve for rounded cylinder shading: broad
            // creases, broad crests, soft mid transitions. Gentler exponent keeps
            // the falloff wide so dense dots form bands, not hairlines.
            val s = lit * lit * (3f - 2f * lit)             // smoothstep
            val crest = s.pow(1.15f)                        // wide soft falloff
            // Top-to-bottom gradient: cloth pools darker toward the bottom.
            val drop = 1f - 0.18f * v
            // Low-amplitude simplex fabric grain over the weave.
            val grain = noise.random3D(u * 9.0f, v * 9.0f, 21.0f).toFloat()
            // Compressed range: deep creases ~0.10, lit crests ~0.60 — the whole
            // cloth stays mid-tone so dots cover everywhere; creases remain densest.
            var lum = (0.10f + 0.50f * crest) * drop * (0.96f + 0.04f * grain)
            // Edges fall off softly; interior stays filled cloth, no cream margins.
            val edge = (min(u, 1f - u) * 9f).coerceIn(0f, 1f)
            lum = lum * (0.92f + 0.08f * edge) + (1f - edge) * 0.30f
            lum = lum.coerceIn(0f, 1f)
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
        // Tint by local brightness: deep creases read molten-red/dark (low index),
        // lit ridge crests read cream/steel-blue (high index).
        val u = dot.x / SIZE
        val v = dot.y / SIZE
        val warp = noise.random3D(u * 1.6f, v * 0.9f, 7.0f).toFloat() - 0.5f
        val warp2 = noise.random3D(u * 0.7f, v * 1.4f, 13.0f).toFloat() - 0.5f
        val sag = sin(v * 2.3f + 0.6f) * 0.55f + sin(v * 1.1f) * 0.35f
        val phase = u * folds * twoPi + warp * 2.2f + warp2 * 1.1f + sag
        val lit = (cos(phase + lightDir) + 1f) * 0.5f
        val s = lit * lit * (3f - 2f * lit)
        val crest = s.pow(1.15f)
        // Match the compressed field: creases ~0.10 → low molten index (dark/flag
        // red), crests ~0.60 → mid/high index (cream/steel-blue).
        val t = (0.10f + 0.50f * crest) * (1f - 0.18f * v)
        val col = ramp.bound((t.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
