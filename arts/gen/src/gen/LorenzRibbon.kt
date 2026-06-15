package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.attractor.LorenzAttractor
import dev.oblac.gart.gfx.drawLine
import dev.oblac.gart.gfx.strokeOf
import dev.oblac.gart.gfx.alpha
import dev.oblac.gart.math.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.skia.Point

private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()

// ── WILD #5 · Lorenz attractor as a glowing 3D ribbon ──────────────────────────
// 60k integration steps of the Lorenz system, recentred, rotated in 3D and
// orthographically projected, then drawn as luminous low-alpha segments coloured by
// depth from the coolors "Sea Blue" ramp and bloomed. The butterfly, lit from within.
// Showcases: attractor engine (3D) + hand-rolled 3D rotation + SCREEN bloom.
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)

    val gart = Gart.of("LorenzRibbon", SIZE, SIZE)
    val d = gart.d
    val ground = 0xFF03041A.toInt()
    val ramp = Coolors.seaBlue.expand(256)

    val att = LorenzAttractor()
    val n = 60000
    val pts = att.computeN(LorenzAttractor.initialPoint, 0.005f, n)

    // recentre on the cloud centroid
    var mx = 0f; var my = 0f; var mz = 0f
    for (p in pts) { mx += p.x; my += p.y; mz += p.z }
    mx /= pts.size; my /= pts.size; mz /= pts.size

    // viewing rotation
    val ay = rng.rndf(0.3f, 1.0f); val ax = rng.rndf(-0.5f, 0.2f)
    val cay = cos(ay); val say = sin(ay); val cax = cos(ax); val sax = sin(ax)
    val scale = SIZE * 0.0165f

    // pre-project to 2D + depth
    val sx = FloatArray(pts.size); val sy = FloatArray(pts.size); val depth = FloatArray(pts.size)
    var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
    for (i in pts.indices) {
        var x = pts[i].x - mx; var y = pts[i].y - my; var z = pts[i].z - mz
        // rotate around Y then X
        var rx = x * cay + z * say
        var rz = -x * say + z * cay
        var ry = y * cax - rz * sax
        rz = y * sax + rz * cax
        sx[i] = d.cx + rx * scale
        sy[i] = d.cy - ry * scale
        depth[i] = rz
        if (rz < minZ) minZ = rz; if (rz > maxZ) maxZ = rz
    }

    val buf = gart.gartvas()
    val bc = buf.canvas
    for (i in 1 until pts.size) {
        val t = ((depth[i] - minZ) / (maxZ - minZ)).coerceIn(0f, 1f)
        val col = ramp.bound(t * (ramp.size - 1))
        val w = 0.6f + 2.2f * t            // nearer = fatter
        val a = (22 + 60 * t).toInt().coerceIn(10, 110)
        bc.drawLine(Point(sx[i - 1], sy[i - 1]), Point(sx[i], sy[i]), strokeOf(col, w).alpha(a))
    }

    val finalv = bloom(gart, buf.snapshot(), ground, SIZE / 200f, grain = 0.06f)
    gart.saveImage(finalv)
    println("  done (rotY=$ay rotX=$ax)")
}
