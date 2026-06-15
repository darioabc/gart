package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.Gartmap
import dev.oblac.gart.math.*
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random
import org.jetbrains.skia.Image

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM · Iterated function system attractor ────────────────────────────────
// The chaos game: pick one of several contractive affine maps at random each step
// and hop the point through it. Millions of hops settle onto a fractal attractor.
// Hit counts accumulate into a density buffer, log-tone-mapped and coloured through
// the "Electric Grape" ramp over dark — filaments glow violet→cyan from sparse→dense.
private class Affine(
    val a: Float, val b: Float, val cc: Float, val dd: Float,
    val e: Float, val f: Float, val p: Float
)

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("BarnsleyIFS", SIZE, SIZE)
    val d = gart.d

    // ── build a stable, contractive affine set ────────────────────────────────
    // Half the time use the classic Barnsley fern; otherwise synthesise 3–4
    // verified-contractive random maps so each seed yields a distinct attractor.
    val maps = ArrayList<Affine>()
    if (rng.rndb()) {
        // classic Barnsley fern
        maps.add(Affine(0f, 0f, 0f, 0.16f, 0f, 0f, 0.01f))
        maps.add(Affine(0.85f, 0.04f, -0.04f, 0.85f, 0f, 1.60f, 0.85f))
        maps.add(Affine(0.20f, -0.26f, 0.23f, 0.22f, 0f, 1.60f, 0.07f))
        maps.add(Affine(-0.15f, 0.28f, 0.26f, 0.24f, 0f, 0.44f, 0.07f))
        println("  using classic Barnsley fern")
    } else {
        val nMaps = rng.rndi(3, 5)
        var totalP = 0f
        val raw = ArrayList<Affine>()
        repeat(nMaps) {
            // contractive: keep linear part scaled so its rough spectral size < 1
            var a: Float; var b: Float; var cc: Float; var dd: Float
            while (true) {
                val scale = rng.rndf(0.35f, 0.62f)
                val rot = rng.rndf(0f, TAUf)
                val shear = rng.rndf(-0.25f, 0.25f)
                a = scale * kotlin.math.cos(rot)
                b = -scale * kotlin.math.sin(rot) + shear
                cc = scale * kotlin.math.sin(rot)
                dd = scale * kotlin.math.cos(rot)
                // crude contractivity check via Frobenius norm < 1.0
                val fro = kotlin.math.sqrt(a * a + b * b + cc * cc + dd * dd)
                if (fro < 0.98f) break
            }
            val e = rng.rndf(-0.5f, 0.5f)
            val f = rng.rndf(-0.5f, 0.5f)
            val pw = rng.rndf(0.5f, 1.5f)
            totalP += pw
            raw.add(Affine(a, b, cc, dd, e, f, pw))
        }
        for (m in raw) maps.add(Affine(m.a, m.b, m.cc, m.dd, m.e, m.f, m.p / totalP))
        println("  synthesised $nMaps contractive maps")
    }

    // cumulative probabilities for selection
    val cum = FloatArray(maps.size)
    var acc = 0f
    for (i in maps.indices) { acc += maps[i].p; cum[i] = acc }
    fun pick(): Affine {
        val r = rng.nextFloat() * acc
        for (i in maps.indices) if (r <= cum[i]) return maps[i]
        return maps.last()
    }

    // ── pre-pass: find attractor bounds ──────────────────────────────────────
    var x = 0f; var y = 0f
    repeat(1000) { val m = pick(); val nx = m.a * x + m.b * y + m.e; val ny = m.cc * x + m.dd * y + m.f; x = nx; y = ny }
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    repeat(40000) {
        val m = pick()
        val nx = m.a * x + m.b * y + m.e
        val ny = m.cc * x + m.dd * y + m.f
        x = nx; y = ny
        if (x < minX) minX = x; if (x > maxX) maxX = x
        if (y < minY) minY = y; if (y > maxY) maxY = y
    }
    val spanX = (maxX - minX).coerceAtLeast(1e-4f)
    val spanY = (maxY - minY).coerceAtLeast(1e-4f)
    // fit into frame with a margin, preserving aspect (uniform scale)
    val margin = SIZE * 0.08f
    val avail = SIZE - 2f * margin
    val scl = (avail / max(spanX, spanY))
    val offX = margin + (avail - spanX * scl) * 0.5f
    val offY = margin + (avail - spanY * scl) * 0.5f
    println("  bounds x[$minX,$maxX] y[$minY,$maxY]")

    // ── full render pass: accumulate density ─────────────────────────────────
    val w = d.w; val h = d.h
    val density = IntArray(w * h)
    val iters = 8_000_000
    x = 0f; y = 0f
    var maxHits = 1
    for (i in 0 until iters) {
        val m = pick()
        val nx = m.a * x + m.b * y + m.e
        val ny = m.cc * x + m.dd * y + m.f
        x = nx; y = ny
        if (i < 20) continue                       // settle onto the attractor
        // map to pixel: y flips so fern grows upward
        val px = ((x - minX) * scl + offX).toInt()
        val py = (h - 1 - ((y - minY) * scl + offY)).toInt()
        if (px < 0 || py < 0 || px >= w || py >= h) continue
        val idx = py * w + px
        val v = density[idx] + 1
        density[idx] = v
        if (v > maxHits) maxHits = v
    }
    println("  rendered, maxHits=$maxHits")

    // ── log tone-map → Electric Grape ramp over dark ─────────────────────────
    val ramp = Coolors.electricGrape.expand(256)
    val ground = 0xFF05030F.toInt()
    val gm = Gartmap(gart.gartvas())
    val logMax = ln((maxHits + 1).toFloat())
    val px = gm.pixels
    for (i in px.indices) {
        val hits = density[i]
        if (hits == 0) { px[i] = ground; continue }
        val t = (ln((hits + 1).toFloat()) / logMax).coerceIn(0f, 1f)
        px[i] = ramp.bound(t * (ramp.size - 1))
    }

    val img: Image = gm.image()
    val finalv = bloom(gart, img, ground, SIZE * 0.003f, grain = 0.04f)
    gart.saveImage(finalv)
}
