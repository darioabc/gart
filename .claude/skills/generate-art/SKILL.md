---
name: generate-art
description: Generate a PNG image using the gȧrt generative-art framework. Use when the user wants to create, generate, render, or produce an image/PNG/artwork from a description (e.g. "generate a png of concentric circles", "make some generative art", "render an image"). Scaffolds a Kotlin gart piece, then renders it headlessly to a PNG.
---

# Generate Art (PNG)

Scaffold a new gȧrt piece from the user's description and render it to a PNG —
end to end, no window required.

## Workflow

1. **Pick a name.** Derive a short lowerCamelCase name from the request, e.g.
   `concentricCircles`. The Kotlin file is `arts/gen/src/gen/<Name>.kt` (capitalized,
   e.g. `ConcentricCircles.kt`); its main class is `gen.<Name>Kt`.

2. **Write the Kotlin file** in `arts/gen/src/gen/`. Start from
   `arts/gen/src/gen/Template.kt` and replace the drawing code. Rules:
   - `package gen`
   - A top-level `fun main()`.
   - Create the canvas: `val gart = Gart.of("<name>", <w>, <h>)`, then
     `val g = gart.gartvas(); val c = g.canvas; val d = gart.d`.
   - Draw on `c` (a Skia `Canvas`) using gart's gfx/color/math helpers.
   - End with `gart.saveImage(g)` — writes `<name>.png` to the working directory.
   - **Never** call `gart.window()`, `showImage()`, `movie()`, or anything that
     opens a Swing window — it throws in headless environments.

3. **Render** with the helper script (handles compile + headless run):
   ```bash
   .claude/skills/generate-art/render.sh arts:gen gen.<Name>Kt out/
   ```
   The PNG lands in `out/<name>.png` (the dir is created if needed). Omit the
   last arg to write to the repo root instead.

4. **Show the result** to the user with the SendUserFile tool, pointing at the
   generated `<name>.png`.

## The gart drawing API (essentials)

Imports live under `dev.oblac.gart.*`. Common building blocks:

- **Colors**: `dev.oblac.gart.color.CssColors` (e.g. `CssColors.black`, `.navy`),
  and `dev.oblac.gart.color.Palettes` (e.g. `Palettes.colormap092.expand(256)`
  returns an indexable list of colors).
- **Fills/strokes**: `dev.oblac.gart.gfx.fillOf(color)`, `fillOfRed()`,
  `strokeOf(color, width)`.
- **Shapes** (extension fns on `Canvas`): `c.clear(color)`,
  `c.drawCircle(center, radius, paint)`, `c.drawPoint(x, y, paint)`,
  plus lines, rects, paths, polygons under `dev.oblac.gart.gfx`.
- **Geometry**: `d.center`, `d.w`, `d.h` from the `Dimension`.
- **Math/noise**: `dev.oblac.gart.math` — `map(v, inLo, inHi, outLo, outHi)`,
  noise functions, vectors, complex numbers, etc.

When unsure which helper exists, grep `gart/src/main/kotlin/dev/oblac/gart/gfx/`,
`.../color/`, and `.../math/` rather than guessing names.

## Reference example

`arts/z/src/z/Z1.kt` is a complete per-pixel piece: it loops over every pixel,
maps coordinates to a math range, computes a value, indexes a palette, and draws
a point — a good pattern to imitate for field/formula-based art.

## Notes

- First render compiles the project and may download dependencies (Skiko, etc.),
  so it can be slow; subsequent renders are fast.
- The `arts:gen` module is a reusable sandbox already registered in
  `settings.gradle.kts`. Add as many pieces to `arts/gen/src/gen/` as you like;
  each top-level `fun main()` is independently renderable by its main class.
