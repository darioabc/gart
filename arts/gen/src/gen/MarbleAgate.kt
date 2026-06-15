package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.color.Palette
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.hypot
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM · Domain-warped fBm agate / marble slab ─────────────────────────────
// A sliced-agate per-pixel field. For each pixel we evaluate multi-octave fBm from
// OpenSimplexNoise, then domain-warp it twice — q = fbm(p + warp1), then
// value = fbm(p + 4*q) — to get swirling agate bands. The value is quantised into
// ~18 discrete bands mapped through "Sunset".expand(256) for concentric striations,
// with a thin darker etched line wherever the band index changes between neighbours.
// Band frequency is modulated radially around an OFF-CENTRE focus so the bands tighten
// into a nodule like a geode. Light/mid ground, grainOnly finish.

private lateinit var noise: OpenSimplexNoise

private fun fbm(x: Float, y: Float, octaves: Int): Float {
    var freq = 1f
    var amp = 0.5f
    var sum = 0f
    var norm = 0f
    for (i in 0 until octaves) {
        sum += amp * noise.random2D(x * freq, y * freq)
        norm += amp
        freq *= 2.0f
        amp *= 0.5f
    }
    return sum / norm   // ~[-1,1]
}

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("MarbleAgate", SIZE, SIZE)
    val d = gart.d
    noise = OpenSimplexNoise(SEED)

    val ramp: Palette = Coolors.sunset.expand(256)
    val bands = rng.rndi(16, 22)            // discrete agate striations
    val baseFreq = 1.9f / SIZE              // base spatial frequency of the field

    // off-centre geode focus
    val fx = d.cx + (rng.nextFloat() - 0.5f) * SIZE * 0.42f
    val fy = d.cy + (rng.nextFloat() - 0.5f) * SIZE * 0.42f
    val maxR = hypot(SIZE.toFloat(), SIZE.toFloat())

    // random orientation offsets so each seed lands in a different region of noise
    val ox = rng.rndf(-1000f, 1000f)
    val oy = rng.rndf(-1000f, 1000f)
    val warpOx = rng.rndf(-1000f, 1000f)
    val warpOy = rng.rndf(-1000f, 1000f)

    val gm = Gartmap(gart.gartvas())
    val bandIdx = IntArray(d.w * d.h)
    val bandVal = FloatArray(d.w * d.h)

    // ── pass 1: compute warped fBm + band index per pixel ─────────────────────
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            // distance from the off-centre geode focus, 0 at focus
            val dr = hypot(px - fx, py - fy) / maxR
            val sx = (px + ox)
            val sy = (py + oy)

            // domain warp: two-stage like Inigo Quilez's pattern()
            val qx = fbm(sx * baseFreq + warpOx * baseFreq, sy * baseFreq, 4)
            val qy = fbm(sx * baseFreq + 5.2f, sy * baseFreq + warpOy * baseFreq + 1.3f, 4)

            val warpAmt = SIZE * 0.55f
            var v = fbm(
                sx * baseFreq + warpAmt * baseFreq * qx,
                sy * baseFreq + warpAmt * baseFreq * qy,
                5
            )
            v = v * 0.5f + 0.5f                                // → [0,1]

            // Radial frequency modulation: bands TIGHTEN toward the focus so they pack
            // into a nodule. The ring phase advances faster (smaller divisor) near focus.
            val ringFreq = 7.5f + 26f * (1f - dr) * (1f - dr)
            val phase = v * 2.6f + dr * ringFreq
            val folded = (phase - kotlin.math.floor(phase))   // sawtooth → repeating bands

            val bi = (folded * bands).toInt().coerceIn(0, bands - 1)
            bandIdx[py * d.w + px] = bi
            bandVal[py * d.w + px] = folded
        }
    }

    // ── pass 2: colour each band + etch darker boundary lines ─────────────────
    val lineCol = 0xFF2A1A18.toInt()
    for (py in 0 until d.h) {
        for (px in 0 until d.w) {
            val i = py * d.w + px
            val bi = bandIdx[i]
            // map band through the ramp; add subtle within-band shimmer from the value
            val tonal = (bi.toFloat() / (bands - 1)).coerceIn(0f, 1f)
            val shimmer = (bandVal[i] - (bi.toFloat() / bands)) * 0.6f
            val rampPos = (tonal + shimmer * 0.12f).coerceIn(0f, 1f) * (ramp.size - 1)
            var col = ramp.bound(rampPos)

            // etched contour: darken where band index changes vs right/down neighbour
            val edge = (px + 1 < d.w && bandIdx[i + 1] != bi) ||
                       (py + 1 < d.h && bandIdx[i + d.w] != bi)
            if (edge) col = darken(col, 0.45f)
            gm[px, py] = col
        }
    }

    gm.drawToCanvas()
    val finalv = grainOnly(gart, gm.image(), grain = 0.05f)
    gart.saveImage(finalv)
    println("  agate done ($bands bands, focus=($fx,$fy))")
}
