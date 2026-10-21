---
name: rational
description: Generate modernist decorations and process illustrations in the Rational family (squares, circles and lines in ink with one warm accent, every placement read from the Gaussian primes) as themable SVG plus a PNG preview, for Dario's folio site. Use when asked for more Rational pieces, a new style or variant in that family, illustrations for a folio section or process step, or a review sheet of the set.
---

# Rational

A family of flat, rationalist pieces (Bauhaus, De Stijl, Swiss poster) made for the
folio site. Each one is a themable SVG that sits on the page's own paper in light and
dark mode, plus a PNG preview. Variance comes from the Gaussian primes: a seed only
picks where in the plane to look, and everything drawn follows from which integers
there are prime, their norms and their arguments.

| What | Where |
|---|---|
| Generators, SVG writer, render loop | `arts/gen/src/gen/Rational.kt` |
| Rendered pieces (committed) | `out/rational<Style><NN>.svg` and `.png` |
| Style catalogue: names, meaning, variant captions | `.claude/skills/rational/catalogue.json` |
| Contact sheets, light and dark | `.claude/skills/rational/sheet.mjs` |
| One-page HTML review, light and dark side by side | `.claude/skills/rational/review.mjs` |

## The styles

Decorations, three variants each: **Modules** (Bauhaus tiles grown from prime
corners), **Lattice** (the primes themselves), **Overlay** (overprinted Albers fields),
**Score** (runs of primes as marks on ruled lines), **Nest** (stippled nested squares),
**Arcs** (Truchet bands), **Orbit** (a ring per prime norm, a mark at each prime's
argument), **Division** (De Stijl cuts), **Halftone** (dots sized by prime
neighbourhood, faded), **Stripes** (barcode bands in discs and fields).

Process illustrations, one each, made for the folio's "How I work" steps:

- Iterating on journeys and designs: **Journey** (the longest open path through the
  arcs, in the accent), **Rounds** (frames closing in on one point), **Versions** (a
  contact sheet, the same tiles stepping along the lattice panel by panel), **Drafts**
  (translucent squares circling in on the final one).
- Building components in the repo: **Parts** (Truchet tiles as separate pieces in
  slots, a few lifted out), **Assembly** (a De Stijl layout with parts fitted to each
  cell), **Tree** (a file tree whose depth walks with the primes), **Kit** (a specimen
  sheet of the parts, a family to a row).

Double diamond, one each, loose readings of the process that diverges and converges
twice (the "How I work" intro). They share `inDiamonds()`, two diamonds side by side
with the pinch at the centre: **Pulse** (waveform bars), **Fan** (strings opening and
closing on three points), **Branches** (a 1-2-4-2-1 bracket, twice), **Rhombs** (two
grained lozenges on a ticked axis), **Twin** (a halftone shaped like the two
diamonds), **Knots** (Truchet arcs gathered into the two diamonds).

`catalogue.json` holds the full description of every style and its variants; keep it
in step with the makers in `main()`.

## The SVG contract

Every SVG the folio uses must keep these, or it stops theming:

1. `viewBox="0 0 1000 1000"`, no width or height, **no background**. Geometry is in
   that 1000-unit square (`U`); keep a margin of about 90 units.
2. Two colours only, as classes reading CSS custom properties with the light palette
   as fallback: `.gen-i{fill:var(--gen-ink,#1d1f20)}` and
   `.gen-a{fill:var(--gen-accent,#c84e22)}`. Opacity goes in `fill-opacity`.
3. Filter and clip ids are prefixed with the piece name (`rationalNest01-t42h14f32s7`),
   so several SVGs can sit inline on one page without clashing.
4. Numbers are written with `Locale.ROOT` (`n()`), never a decimal comma.

On the folio the page sets the two properties from its role tokens:
`--gen-ink: var(--c-text); --gen-accent: var(--c-accent)`. Light is ink `#1d1f20` and
rust `#c84e22` on paper `#ece5d1`; dark is cream `#f2f0eb` and rust `#ec8f62` on
`#161718`. **The SVG themes only when it is inline in the page** (or pulled in with
`<use>`): as an `<img>` or a CSS background it cannot see the page's custom properties
and stays in the light fallback. Figma imports it in the light fallback too.

## Motion

A piece can also be written as `out/<name>-motion.svg`: the same file with CSS
animation inside. Wrap shapes in `Motion(anim, shapes, extra)`: `Spin(cx, cy,
seconds, reverse)` turns the group about a point, `Drift(dx, dy, seconds)` slides it
one span and loops (put a copy one span behind in `extra`, drawn only in motion;
`scroll()` does that for barcode bands). The still SVG and the PNG flatten the group
away, so adding motion never changes them: check with `cmp` as for a refactor.

The motion CSS is a class per moving group (`<name>-m<i>`, `transform-box:
view-box` so origins and translations are in viewBox units), keyframes named after
the piece, and `@media (prefers-reduced-motion:reduce){.<name>-m{animation:none}}`,
which leaves the still frame. Classes, not inline styles, so that rule can win.
Inline in a page the `<style>` applies document-wide, hence the prefixes.

Mind the pixel grid. A hairline thinner than a CSS pixel that drifts slowly flips
between a crisp line and a two-row smear each time it crosses a pixel, so a field of
them flickers on 1x screens (Stripes at hero size: 0.6 px bands, about 4 Hz).
`hiDpi = true` on a Motion keeps it still below 1.5 dppx; `scroll()` sets it. Turns,
fades and breathing do not have the problem.

Pace it for a background: Orbit's rings move 6 to 14 units a second along the ring
(a turn takes 30 s to 7 min), neighbours in opposite directions; Stripes scroll 10
units a second. Today Orbit 01 and 03 (not the quarter, which turning would empty),
Stripes 01 and 02, Score (marks scroll along their lines), Arcs (prime tiles turn a
quarter at a time; `Turn`), Rhombs (lozenges breathe; `Breathe`) and Halftone 03 (a
wave of light from the centre; `Fade`) have motion. The folio uses only the hero's
Orbit 01 and Stripes 01 so far. gart can also record frames to MP4 or GIF
(`Movie.kt`, `video/`), but a video cannot take the page's colours, so the web uses
the SVG.

## Shapes and textures

`Box`, `Disc`, `Sector` (a pie slice, degrees clockwise from +x), `Outline` (any path,
with `evenOdd` for holes) and `ClipGroup` (shapes drawn inside a clip path, with an
optional texture on the group). Path helpers: `ringSector`, `ring`, `circlePath`,
`rectPath`, `framePath`, `segment` (a straight stroke as a quad), `polygon`,
`sectorPath`.

Size marks for where the piece will be shown. The folio's process slots are about
216 to 332 px for the 1000-unit square, so one unit is 0.2 to 0.33 px: strokes under
about 5 units (Fan's strings) go faint at the smallest size.

Texture is an SVG filter on the shape's own colour, so it themes too: feTurbulence,
thresholded into an alpha mask, composited `in` the shape. `stipple(keep, seed)` is
coarse, crisp speckle where `keep` is the share left showing; `grain(seed)` is a fine
printed grain. Use texture on fills at least ~20 units across: stipple on small marks
makes them vanish, grain on hairlines breaks them into dashes.

## Gaussian prime toolkit

- `gaussianPrime(a, b)`: the norm a² + b² is a rational prime, or the integer sits on
  an axis at a prime of the form 4k + 3. `gnorm(a, b)` is the norm.
- Every prime except 1 + i has a + b odd. `OddGrid` lays that odd sub-lattice out as a
  square grid (cell (i, j) is (a0 + i + j) + (b0 + i - j)i), so grid neighbours are
  diagonal lattice neighbours and densities are useful. `oddGrid(rng, reach)` seeds
  it.
- Density among odd points is about 8 / (π ln N): roughly 28% near norm 10⁴, thinning
  as you move out. Tune thresholds to that; it is why "prime xor prime" turns a
  Truchet tile roughly 35-40% of the time.
- Centred on 0 the primes have eightfold symmetry (Lattice, Orbit); move the window off
  0 to break it.
- `primesNear(a0, b0, count)` lists primes nearest a point, and `PrimeStream` reads them
  one after another for decisions (Division, Assembly). Norms modulo small numbers give
  families (mod 8 splits 1 from 5) and rarity for the accent (mod 13); arguments give
  directions (Drafts, Orbit); runs along a line give rhythm (Score, Stripes).
- Design the rule from the meaning: Journey is the longest path, Versions a sliding
  window, Tree a walk in depth. One accent idea per piece (the longest run, the kept
  panel, the newest file) reads better than scattered accents.

## Render

```bash
export JAVA_HOME=$(brew --prefix openjdk@21)
GART_SEED=173 GART_SIZE=800 .claude/skills/generate-art/render.sh arts:gen gen.RationalKt out/
```

The committed set is rendered with `GART_SEED=173 GART_SIZE=800`. First compile takes
about a minute. `GART_ONLY=Journey,Kit` (an environment variable, matched against the
piece names) renders a subset; every maker still takes its seed from the master in
order, so a subset renders exactly as a full run does.

**Append new makers at the end of the list in `main()`.** Inserting one shifts every
later piece's seed and changes it. After any refactor, copy `out/rational*.svg` aside,
re-render, and `cmp` them: pieces you did not mean to change must be byte-identical.

The PNG is a preview: the same SVG with the light palette baked in (Skia's `SVGDOM`
ignores `<style>` and CSS variables), rendered by Skia, plus a light grain. The dark
look only exists in a browser.

## Review

```bash
# Light sheet from the PNGs, dark sheet from the SVGs in a browser.
PLAYWRIGHT=/abs/path/to/node_modules/playwright/index.mjs \
  node .claude/skills/rational/sheet.mjs out work/rational Journey,Kit --name sheet-new --cols 4
# Every piece, light and dark side by side, in one HTML page (no dependencies).
node .claude/skills/rational/review.mjs out work/rational/review.html
```

gart has no `package.json`, so point `PLAYWRIGHT` at another checkout's install by
absolute path (on Dario's machine `~/Sites/Claude/fiuto/node_modules/playwright/index.mjs`);
do not symlink `node_modules`. `work/` is scratch and stays untracked.

Always look at both sheets yourself before showing them: Skia (light PNG) and the
browser (dark) differ in places. Thin bands cut by a clip look stepped at small sizes;
that is the geometry, not a bug.

## Radial rays (RadialRays.kt)

A sibling family in the same package: rays round a disc, dotted, turning solid at
three widths where a hidden field is positive (`radialRaysNN`), and the same cut into
bands that slide sideways (`radialRaysShiftNN`; the S in the middle is the disc cut by
a band edge). The committed set is `GART_SEED=173 GART_SIZE=2000`, a different size
from the Rational set, and it reproduces byte for byte:

```bash
GART_SEED=173 GART_SIZE=2000 .claude/skills/generate-art/render.sh arts:gen gen.RadialRaysKt out/
```

Each shifted piece also writes `radialRaysShiftNN-motion.svg` for the web: no ground,
the rays and disc on `--gen-ink` and `--gen-accent` (the preset's colours as
fallback), and each band sliding to the mirror of its offset and back on its own
period, still on 1x screens and under reduced motion. Folio's quote block uses
`radialRaysShift05-motion` (Figma 79:52807).

## Adding a style

1. Write `private fun <style>(name: String, rng: Random, ...params): Art` in
   `Rational.kt`, reusing the helpers above, with a KDoc that says what the rule is.
2. Append its makers to `main()` as `rational<Style>01`, `02`, ...
3. Add it to `catalogue.json` with a one-sentence rule and a caption per variant.
4. Render (with `GART_ONLY` while iterating), build a sheet, look at light and dark.
5. Commit the code, the catalogue and `out/rational<Style>*.svg|png` together.

## Traps

- Package `gen` holds every piece, and a private top-level class still compiles to
  `gen.<Name>`, so class names must be unique across the package (`Ink` clashed, hence
  `PaperInk`; `Cell` would, hence `DivCell`). Private top-level functions are fine.
- A member `fun norm()` that calls a top-level `norm()` calls itself; the top-level one
  is `gnorm`.
- The main gart checkout is often on another session's branch with uncommitted work;
  make changes in a worktree off `origin/main`.
