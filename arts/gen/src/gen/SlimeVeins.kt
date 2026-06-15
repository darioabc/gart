package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.math.*
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── Physarum · slime-mold vein network ────────────────────────────────────────
// ~60k agents crawl a toroidal trail field, each sensing three points ahead and
// steering toward the strongest pheromone, depositing as they go. The field decays
// and diffuses every step, so transport veins self-organise into a dense capillary
// network. The final field is log-tone-mapped through "Molten" and bloomed on
// near-black — a glowing biological circulatory map. Showcases: agent simulation.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("SlimeVeins", SIZE, SIZE)
    val d = gart.d
    val w = d.w
    val h = d.h
    val wf = w.toFloat()
    val hf = h.toFloat()

    val agentCount = if (SIZE >= 1024) 60000 else 22000
    val steps = 180

    // Sensor / motion params (radians + pixels).
    val sensorAngle = 0.62f          // ~35°
    val sensorDist = SIZE * 0.011f
    val turnAngle = 0.46f
    val stepLen = SIZE * 0.0016f * 1.0f
    val depositAmt = 1.0f
    val decay = 0.92f

    // Flat agent state.
    val ax = FloatArray(agentCount)
    val ay = FloatArray(agentCount)
    val ah = FloatArray(agentCount)
    // Seed agents in a central disc, headings outward-ish for an initial burst.
    val seedR = SIZE * 0.30f
    for (i in 0 until agentCount) {
        val a = rng.nextFloat() * TAUf
        val r = kotlin.math.sqrt(rng.nextFloat()) * seedR
        ax[i] = d.cx + cos(a) * r
        ay[i] = d.cy + sin(a) * r
        ah[i] = rng.nextFloat() * TAUf
    }

    var trail = FloatArray(w * h)
    var tmp = FloatArray(w * h)

    fun wrapX(x: Int): Int { var v = x % w; if (v < 0) v += w; return v }
    fun wrapY(y: Int): Int { var v = y % h; if (v < 0) v += h; return v }

    fun sample(px: Float, py: Float): Float {
        val xi = wrapX(px.toInt())
        val yi = wrapY(py.toInt())
        return trail[yi * w + xi]
    }

    repeat(steps) {
        // ── agent update ──────────────────────────────────────────────────────
        for (i in 0 until agentCount) {
            val x = ax[i]
            val y = ay[i]
            val hd = ah[i]

            // three sensors ahead
            val fl = hd - sensorAngle
            val fc = hd
            val fr = hd + sensorAngle
            val sc = sample(x + cos(fc) * sensorDist, y + sin(fc) * sensorDist)
            val sl = sample(x + cos(fl) * sensorDist, y + sin(fl) * sensorDist)
            val sr = sample(x + cos(fr) * sensorDist, y + sin(fr) * sensorDist)

            var nh = hd
            if (sc >= sl && sc >= sr) {
                // keep heading
            } else if (sl > sr) {
                nh = hd - turnAngle
            } else if (sr > sl) {
                nh = hd + turnAngle
            } else {
                // equal L/R but both beat centre → random kick
                nh = hd + (if (rng.nextBoolean()) turnAngle else -turnAngle)
            }

            var nx = x + cos(nh) * stepLen
            var ny = y + sin(nh) * stepLen
            // wrap
            if (nx < 0f) nx += wf else if (nx >= wf) nx -= wf
            if (ny < 0f) ny += hf else if (ny >= hf) ny -= hf

            ax[i] = nx
            ay[i] = ny
            ah[i] = nh

            // deposit
            val di = wrapY(ny.toInt()) * w + wrapX(nx.toInt())
            trail[di] += depositAmt
        }

        // ── decay + 3x3 box-blur diffuse (separable into tmp then back) ────────
        // horizontal pass: trail → tmp
        for (yy in 0 until h) {
            val base = yy * w
            for (xx in 0 until w) {
                val l = trail[base + (if (xx == 0) w - 1 else xx - 1)]
                val c = trail[base + xx]
                val r = trail[base + (if (xx == w - 1) 0 else xx + 1)]
                tmp[base + xx] = (l + c + r) * (1f / 3f)
            }
        }
        // vertical pass + decay: tmp → trail
        for (yy in 0 until h) {
            val up = (if (yy == 0) h - 1 else yy - 1) * w
            val cu = yy * w
            val dn = (if (yy == h - 1) 0 else yy + 1) * w
            for (xx in 0 until w) {
                val v = (tmp[up + xx] + tmp[cu + xx] + tmp[dn + xx]) * (1f / 3f)
                trail[cu + xx] = v * decay
            }
        }
    }

    // ── tone-map the field → Molten ramp on near-black ────────────────────────
    var peak = 0f
    for (v in trail) if (v > peak) peak = v
    if (peak <= 0f) peak = 1f
    val logPeak = ln(1f + peak)

    val ramp = Coolors.molten.expand(256)
    val ground = 0xFF06040A.toInt()
    val gm = Gartmap(gart.gartvas())
    for (i in trail.indices) {
        val v = trail[i]
        if (v <= 0f) { gm.pixels[i] = ground; continue }
        val t = (ln(1f + v) / logPeak).coerceIn(0f, 1f)
        val col = ramp.bound(t * (ramp.size - 1))
        gm.pixels[i] = darken(col, 0.10f + 0.90f * t)
    }

    val finalv = bloom(gart, gm.image(), ground, SIZE / 260f, grain = 0.06f)
    gart.saveImage(finalv)
}
