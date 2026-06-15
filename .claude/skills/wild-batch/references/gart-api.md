# gȧrt API cheat-sheet (for `wild-batch` pieces)

Confirmed-working API for headless pieces in `arts/gen/src/gen/`. Paste the relevant
parts into subagent prompts. When unsure, read an existing piece — the references in
`SKILL.md` exercise all of this.

## Scaffolding (copy from VoronoiOrb.kt)
```kotlin
private val SIZE: Int = System.getProperty("GART_SIZE")?.toIntOrNull() ?: 1024
private val SEED: Long = System.getProperty("GART_SEED")?.toLongOrNull() ?: Random.nextLong()
fun main() {
    println("seed=$SEED")
    val rng = Random(SEED)
    val gart = Gart.of("<Name>", SIZE, SIZE)   // <Name> == file name
    val d = gart.d
    // ... build into a Gartvas/Gartmap ...
    gart.saveImage(finalv)                       // never call window()
}
```
All randomness via `rng`: `rng.rndi(min,max):Int` (max EXCLUSIVE), `rng.rndf(min,max):Float`,
`rng.rndb():Boolean`, `rng.nextInt(n)`, `rng.nextFloat()`.

## Canvas / surface
- `val d = gart.d`: `d.w,d.h` (Int), `d.cx,d.cy` (Float), `d.wf,d.hf`, `d.center` (Point), `d.area`
- `val g = gart.gartvas(); val c = g.canvas; c.clear(colorInt); g.snapshot():Image`
- Skia Canvas: `c.drawCircle(x:Float,y:Float,r:Float,paint)`, `c.drawPoint(x,y,paint)`,
  `c.drawPath(path,paint)`, `c.drawRect(Rect,paint)`, `c.save()/restore()/clipRect(Rect)`
- gfx extensions — **import explicitly**:
  - `import dev.oblac.gart.gfx.drawLine` → `c.drawLine(Point,Point,paint)`
  - `import dev.oblac.gart.gfx.drawArc`  → `c.drawArc(Rect,startDeg:Float,sweepDeg:Float,includeCenter:Boolean,paint)`
  - `import dev.oblac.gart.gfx.fillOf` / `strokeOf` / `alpha` / `pathOf` / `hatchPaint`
- Paints: `fillOf(Int):Paint`, `strokeOf(colorInt:Int,width:Float):Paint`,
  `paint.alpha(a:Int):Paint`, `pathOf(List<Point>):Path`,
  `hatchPaint(color,density,dotWidth,strokeWidth)`. Props: `paint.strokeWidth`,
  `paint.isAntiAlias`, `paint.strokeCap=PaintStrokeCap.ROUND`, `paint.mode=PaintMode.STROKE/FILL`,
  `paint.blendMode`, `paint.imageFilter`.

## Colour & palettes
- `import dev.oblac.gart.color.*` → `lerpColor(a:Int,b:Int,t:Float):Int`, `argb(a,r,g,b):Int`
- `Palette`: `p.expand(steps):Palette` (**steps MUST be > p.size — it only grows**),
  `p[i]`, `p.bound(idx:Number)`, `p.safe(i)`, `p.relative(f:Float)`, `p.size`, `p.map{ }`
- `Coolors.molten / inkEmber / patriotGold / navyGold / sunset / pastelDream / seaBlue /
  tealRose / electricGrape / retroPop` (see Coolors.kt for hexes)

## Noise & math
- `import dev.oblac.gart.noise.OpenSimplexNoise` → `OpenSimplexNoise(SEED).random2D(x,y)` /
  `.random3D(x,y,z)` (Float args→Float, Double→Double)
- `import dev.oblac.gart.math.*` → `map(v,inLo,inHi,outLo,outHi):Float`, `TAUf`, `PIf`,
  `clamp`, `smoothstep(edge0,edge1,x)`, `mix(a:Float,b:Float,t:Float)`; plus `kotlin.math.*`
  (`max`/`min`/`hypot`/`sqrt`/`cos`/`sin`/`abs`/`exp`/`ln` are NOT in gart.math — import from kotlin.math).

## Pixels (per-pixel / filters)
- `import dev.oblac.gart.Gartmap` → `val gm = Gartmap(gart.gartvas())`; `gm[x,y]=colorInt`;
  `gm.pixels:IntArray` (direct); `gm.image():Image`; `gm.drawToCanvas()`
- Dither (`dev.oblac.gart.dither.*`): `ditherFloydSteinberg/Atkinson/BlueNoise/Ordered8By8Bayer(bitmap:Pixels, pixelSize=1, colorCount=N)`
- Stipple (`dev.oblac.gart.stipple.*`): `stippleVoronoi(pixels, pointCount, iterations, …):List<StippleDot(x,y,radius)>`, `stippleDots(bitmap, dotSize, gap, foreground, background)`
- `import dev.oblac.gart.pixels.conformalWarp` → `conformalWarp(src:Gartmap, rInner, rOuter, …):Gartmap`
- `import dev.oblac.gart.pixels.pixelSorter` → `pixelSorter(bitmap, threshold:Int){ c -> value }`
- Attractors (`dev.oblac.gart.attractor.*`): `CliffordAttractor(a,b,c,d)`, `LorenzAttractor()`,
  `PeterDeJongAttractor(...)` — `att.compute(Point3, dt):Point3`, `att.computeN(p,dt,n):List<Point3>`,
  `<Class>.initialPoint`. `Point3` = `org.jetbrains.skia.Point3`.

## Finishing helpers (same package `gen`, NO import)
- `bloom(gart, image:Image, groundInt:Int, sigma:Float, grain:Float=0.08f):Gartvas` — for
  **dark** grounds: two SCREEN-blended Gaussian passes + grain → dense areas glow.
- `grainOnly(gart, image:Image, grain:Float=0.06f):Gartvas` — for **light** grounds
  (SCREEN bloom would wash them out).
- `darken(colorInt:Int, f:Float):Int` — scale RGB toward black by f.

## Save
`gart.saveImage(gartvas)` or `gart.saveImage(image)`. Writes `<Name>.png` to cwd (= `out/`
under render.sh).

## skia types
`org.jetbrains.skia.{Point, Point3, Rect, Paint, PaintMode, PaintStrokeCap, BlendMode,
Image, Path}`; `Rect.makeLTRB(l,t,r,b)`.

## Render (orchestrator only)
Workaround: set `val version = "0.148.1"` in `gart/build.gradle.kts` (Maven Central; the
pinned `0.148.3` lives only on the network-blocked `compose-dev` repo). **Revert before
committing.**
```
./gradlew :arts:gen:classes :arts:gen:writeClasspath -q --no-configuration-cache
GART_SIZE=512 .claude/skills/generate-art/render.sh arts:gen gen.<Name>Kt out/   # draft
.claude/skills/generate-art/render.sh arts:gen gen.<Name>Kt out/                 # full 1024
```
