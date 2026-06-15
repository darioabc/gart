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

// Canvas size: render-voronoi.sh sets GART_SIZE=1920 (passed as a -D JVM prop).
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
// Seed: pass GART_SEED=<long> to reproduce; always printed to stdout.
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── VoronoiOrb family · "Phosphor Bloom" ──────────────────────────────────────
// A bioluminescent jellyfish breathing alone in the abyss. The translucent bell is a
// soft dome lit from above, ribbed by faint meridians; from its scalloped rim a curtain
// of sinuous tentacles drifts down, thinning and dissolving into the dark as it falls.
// A slow current warps the whole creature sideways, and a sparse drift of marine snow
// hangs in the water around it. The luminance field (DARK = denser dots) drives gȧrt's
// Lloyd-relaxed weighted Voronoi stippler; dots are tinted by depth so the crown glows
// steel-blue and the trailing tendrils smoulder down to molten red.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val gart = Gart.of("VoronoiJellyfish", SIZE, SIZE)
    val d = gart.d
    val noise = OpenSimplexNoise(SEED)
    val cx = d.cx; val cy = d.cy

    // Bell geometry: dome sits high in the frame, tentacles fall below it.
    val bx = cx
    val by = d.h * 0.36f
    val rx = d.w * 0.27f
    val ry = d.h * 0.20f
    val rimY = by + ry * 0.30f          // where the bell rim opens and tentacles begin
    val TENTACLES = 9
    // a lazy current that pushes everything sideways as it sinks
    fun drift(py: Float): Float {
        val s = ((py - rimY) / d.h).coerceAtLeast(0f)
        val sway = noise.random2D(s * 2.4f, 7.3f).toFloat()
        return (rx * 0.55f) * s * (0.4f + 0.6f * (0.5f + 0.5f * sway))
    }

    // ── (1) LUMINANCE FIELD — DARK = denser dots, white = bare ground.
    val src = Gartmap(gart.gartvas())
    val white = 0xFFFFFFFF.toInt()
    val twoPi = 2f * PI.toFloat()
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val fx = px.toFloat()
            val fy = py.toFloat()
            val sideDrift = drift(fy)

            var density = 0f

            // (a) the bell — a lit dome with meridian ribs, only the upper cap is solid
            val nx = (fx - bx) / rx
            val nyTop = (fy - by) / ry
            val rDome = hypot(nx, nyTop)
            if (rDome < 1.05f && nyTop < 0.45f) {
                val edge = (1f - smoothstep(0f, 1f, (rDome - 0.85f) / 0.20f))   // soft outer falloff
                val cap = (1f - smoothstep(0f, 1f, (nyTop - 0.20f) / 0.25f))    // fade toward the rim
                // light from above: the crown catches it, the underside stays deep
                val lit = (0.55f - 0.55f * nyTop).coerceIn(0f, 1f)
                // ribbed meridians running over the dome
                val ang = atan2(nx, -nyTop + 0.001f)
                val ribs = 0.55f + 0.45f * cos(ang * 7f).pow(2f)
                val relief = 0.75f + 0.25f * noise.random3D(nx * 2.6f, nyTop * 2.6f, 0.4f).toFloat()
                density = max(density, edge * cap * (0.35f + 0.65f * lit) * ribs * relief)
            }

            // (b) the tentacles — sinuous threads from the rim, tapering into the dark
            if (fy > rimY - ry * 0.2f) {
                val depth = ((fy - rimY) / (d.h - rimY)).coerceIn(0f, 1f)
                for (i in 0 until TENTACLES) {
                    val u = (i + 0.5f) / TENTACLES                 // 0..1 across the rim
                    val baseX = bx + (u - 0.5f) * 2f * (rx * 0.82f)
                    val phase = u * twoPi * 1.7f
                    val freq = 5.5f + 2.0f * noise.random2D(u * 5f, 1.1f).toFloat()
                    val wob = noise.random3D(u * 4f, depth * 3.2f, 2.7f).toFloat()
                    val wave = sin(depth * freq + phase) * (rx * 0.085f) * (0.5f + depth)
                    val tx = baseX + sideDrift + wave + (rx * 0.05f) * wob
                    val width = (rx * 0.020f) * (1f - 0.75f * depth)   // taper with depth
                    val dist = abs(fx - tx)
                    val core = exp(-(dist * dist) / (2f * width * width))
                    val fade = (1f - depth).pow(1.4f)                  // dissolve as it sinks
                    density = max(density, core * fade)
                }
            }

            // (c) a few thick oral arms frilling out of the bell's mouth
            if (fy > rimY) {
                val depth = ((fy - rimY) / (d.h * 0.55f)).coerceIn(0f, 1f)
                for (i in 0 until 4) {
                    val u = (i + 0.5f) / 4f
                    val baseX = bx + (u - 0.5f) * 2f * (rx * 0.28f)
                    val wave = sin(depth * 3.4f + u * 6f) * (rx * 0.14f) * depth
                    val tx = baseX + sideDrift * 0.7f + wave
                    val width = (rx * 0.05f) * (1f - 0.85f * depth)
                    val dist = abs(fx - tx)
                    val core = exp(-(dist * dist) / (2f * width * width))
                    val frill = 0.6f + 0.4f * sin(depth * 26f + u * 9f)
                    density = max(density, 0.85f * core * (1f - depth).pow(1.6f) * frill)
                }
            }

            // (d) marine snow — a sparse drift of motes suspended in the water
            val snow = noise.random3D(fx * 0.045f, fy * 0.045f, 9.0f).toFloat()
            if (snow > 0.86f) {
                density = max(density, 0.18f * (snow - 0.86f) / 0.14f)
            }

            val lum = (1f - density).coerceIn(0f, 1f)
            if (lum > 0.985f) {
                src[px, py] = white
            } else {
                val g = (lum * 255).toInt().coerceIn(0, 255)
                src[px, py] = argb(255, g, g, g)
            }
        }
    }

    // ── (2) STIPPLE — keep these params (density/dot-size scale with SIZE).
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

    // ── (3) COMPOSITE — molten ramp on papaya-whip ground. Tint by depth + glow.
    val ramp = Coolors.molten.expand(256)
    val out = gart.gartvas()
    val c = out.canvas
    c.clear(0xFFFDF0D5.toInt())   // papaya-whip ground
    for (dot in dots) {
        // crown reads cool steel-blue (high index), trailing tendrils smoulder
        // down through cream to molten red (low index); a soft glow lifts the bell.
        val depth = ((dot.y - by) / (d.h - by)).coerceIn(0f, 1f)
        val nx = (dot.x - bx) / rx
        val nyTop = (dot.y - by) / ry
        val bellGlow = (1f - hypot(nx, nyTop)).coerceIn(0f, 1f)
        val shimmer = 0.08f * noise.random3D(dot.x * 0.01f, dot.y * 0.01f, 4.0f).toFloat()
        val t = (0.80f - 0.70f * depth + 0.20f * bellGlow + shimmer)
        val col = ramp.bound((t.coerceIn(0f, 1f) * (ramp.size - 1)))
        c.drawCircle(dot.x, dot.y, dot.radius, fillOf(col))
    }

    // ── (4) FINISH
    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
