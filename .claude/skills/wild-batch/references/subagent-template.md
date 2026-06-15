# Subagent prompt template (for `wild-batch`)

Spawn `general-purpose` agents, **2–3 pieces each**, all in one message so they run in
parallel. Fill the `{{...}}` slots. The house-rules + cheat-sheet block is reused
verbatim per agent.

---

You are contributing {{N}} NEW Kotlin generative-art pieces to the gȧrt repo at
/home/user/gart. Create new files under `arts/gen/src/gen/`. **DO NOT run gradle or any
build. DO NOT edit existing files — only CREATE your assigned new .kt files.** The
orchestrator compiles/renders centrally.

STEP 1 — read these for exact API + house style before writing:
- `arts/gen/src/gen/VoronoiOrb.kt` and `{{one or two more references relevant to the technique}}`
- `arts/gen/src/gen/Coolors.kt` (palettes) and `arts/gen/src/gen/WildFx.kt` (bloom/grainOnly/darken)

MANDATORY scaffolding (copy from VoronoiOrb.kt exactly): SIZE/SEED system-property reads;
`fun main()` prints `seed=$SEED`, uses `val rng = Random(SEED)`, `Gart.of("<Name>", SIZE,
SIZE)`, ends with `gart.saveImage(finalv)`; never `window()`; file name == Gart.of name;
all randomness via `rng.rndi/rndf/rndb`.

API CHEAT-SHEET: {{paste the contents of references/gart-api.md, or its essentials —
canvas, gfx imports, color/Palette (expand needs steps>size), noise, math (max/min from
kotlin.math), Gartmap, bloom/grainOnly/darken, save, skia types}}.

Make each piece MAD, complex, and COMPOSED — strong structure/silhouette/density, NOT a
centered soft blob. Tune for 1024px; must also run at 512. Use a spatial grid
(`HashMap<Long,MutableList<Int>>`) for any O(n²) neighbour query. Use a coolors palette
+ a WildFx finish (`bloom` dark / `grainOnly` light).

YOUR PIECES:
1) `{{Name}}` — {{technique + concrete algorithm hints + palette + finish}}.
2) `{{Name}}` — {{...}}.

When done, reply with: the file names, a one-line description of each, the palette used,
and anything in the API you were unsure about. Do not build.

---

## Why these constraints
- Agents writing **different** new files never collide; the orchestrator owns the single
  gradle build so there's one compile, not five racing daemons.
- The cheat-sheet up front cuts the usual failure (missing imports / `expand(stepsTooSmall)`).
- "Don't build" keeps agents fast and avoids the skiko network workaround leaking into
  their context.
