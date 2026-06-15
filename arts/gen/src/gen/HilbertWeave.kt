package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.lerpColor
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.math.*
import dev.oblac.gart.noise.OpenSimplexNoise
import kotlin.math.hypot
import kotlin.random.Random
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Point

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── SYSTEM · Hilbert-curve tonal weave ─────────────────────────────────────────
// A single continuous Hilbert space-filling curve (order 7 → 128×128 = 16384 verts)
// is walked as ONE polyline that never crosses itself, then each segment is drawn
// with a per-segment STROKE WIDTH + COLOUR modulated by an underlying field: a
// radial "subject" disc (an off-axis luminous orb with a darker rim) combined with
// layered OpenSimplex noise. Thick & saturated where the field is high, thin & pale
// where it is low — so the one line renders a tonal image. Palette coolors "Sea Blue"
// over a deep navy ground; bloom finish so the bright lobe glows.

/** Standard Hilbert d2xy: map distance d along the order-n curve to (x,y) in [0,2^n). */
private fun d2xy(n: Int, dIn: Int): Pair<Int, Int> {
    var rx: Int; var ry: Int; var d = dIn
    var x = 0; var y = 0
    var s = 1
    while (s < n) {
        rx = 1 and (d / 2)
        ry = 1 and (d xor rx)
        // rotate quadrant
        if (ry == 0) {
            if (rx == 1) {
                x = s - 1 - x
                y = s - 1 - y
            }
            val t = x; x = y; y = t
        }
        x += s * rx
        y += s * ry
        d /= 4
        s *= 2
    }
    return Pair(x, y)
}

fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val noise = OpenSimplexNoise(SEED)

    val gart = Gart.of("HilbertWeave", SIZE, SIZE)
    val d = gart.d
    val S = SIZE.toFloat()

    val order = 7                 // 128×128 grid
    val gridN = 1 shl order       // 128
    val total = gridN * gridN     // 16384 points
    println("  order $order · $total verts")

    // map grid cell → canvas with a margin
    val margin = S * 0.05f
    val span = S - 2f * margin
    val cell = span / (gridN - 1)

    // ── underlying field: off-axis luminous orb + layered simplex ──────────────
    val ocx = 0.42f + rng.rndf(-0.04f, 0.04f)   // orb centre (normalised)
    val ocy = 0.46f + rng.rndf(-0.04f, 0.04f)
    val orbR = 0.34f
    fun field(nx: Float, ny: Float): Float {
        val dx = nx - ocx; val dy = ny - ocy
        val r = hypot(dx, dy) / orbR
        // bright core, darker rim ring, soft falloff outside
        var subj = smoothstep(1.15f, 0.15f, r)            // 1 at core → 0 past edge
        val rim = smoothstep(0.06f, 0.0f, kotlin.math.abs(r - 0.92f)) * 0.5f
        subj = (subj - rim).coerceIn(0f, 1f)
        // multi-octave noise texture
        val n1 = noise.random2D(nx * 3.2f, ny * 3.2f).toFloat()
        val n2 = noise.random2D(nx * 7.5f + 11f, ny * 7.5f - 4f).toFloat()
        val tex = 0.5f + 0.32f * n1 + 0.18f * n2
        // weave the orb with texture; keep the background genuinely dark for contrast
        return (0.05f + 1.05f * subj * tex + 0.07f * (tex - 0.5f)).coerceIn(0f, 1f)
    }

    val out = gart.gartvas()
    val c = out.canvas
    val ground = 0xFF03045E.toInt()   // deep navy (seaBlue[0])
    c.clear(ground)

    val pal = Coolors.seaBlue
    val ramp = pal.expand(256)        // navy → cyan → ice gradient

    // precompute curve points in canvas space
    val pts = Array(total) { i ->
        val (gx, gy) = d2xy(gridN, i)
        Point(margin + gx * cell, margin + gy * cell)
    }

    // draw segment-by-segment with per-segment width + colour from the field.
    // wMax stays BELOW the cell pitch so neighbouring passes never fully merge —
    // the furrows between them keep the single woven line legible.
    val wMax = cell * 0.82f
    val wMin = cell * 0.10f
    for (i in 0 until total - 1) {
        val a = pts[i]; val b = pts[i + 1]
        val mx = (a.x + b.x) * 0.5f; val my = (a.y + b.y) * 0.5f
        val f = field(mx / S, my / S)
        val fe = smoothstep(0f, 1f, f)
        val w = mix(wMin, wMax, fe)
        // colour: low field → deep navy (dim), high field → bright cyan/ice,
        // pushed brighter still in the very highlights for the glow lobe.
        var col = ramp.bound(fe * (ramp.size - 1))
        if (fe > 0.8f) col = lerpColor(col, 0xFFCAF0F8.toInt(), (fe - 0.8f) / 0.2f)
        if (fe < 0.22f) col = lerpColor(ground, col, 0.55f) // sink darks into ground
        val paint = strokeOf(col, w)
        paint.strokeCap = PaintStrokeCap.ROUND
        c.drawLine(a, b, paint)
    }

    // grain (not bloom) keeps the woven line crisp instead of melting it to a blob
    val finalv = grainOnly(gart, out.snapshot(), grain = 0.05f)
    gart.saveImage(finalv)
}
