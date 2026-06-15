---
name: wild-batch
description: Generate a BATCH of bold, distinct generative-art pieces in the gȧrt repo, each a different generative SYSTEM, using a multi-agent orchestration pass. Use when the user wants several/many pieces at once, says "go wild", "make it madder / more complex", "explore new styles", "a batch", "ten more", or wants variety rather than a single render. Orchestrates subagents to write the pieces in parallel, then compiles and renders them straight to high-res (1920²) centrally — no draft pass, no per-image review. For a single one-off image, prefer the `generate-art` skill instead.
---

# Wild Batch — orchestrated generative-art drops

Produce N pieces (default **5–10**) for the gȧrt repo, where **every piece is a
DISTINCT generative system and a different visual world**. You act as the
**orchestrator**: keep the main chat as a clean control loop, fan the piece-writing
out to subagents, then compile and render them straight to high-res centrally. This
is a **fire-and-forget** flow — render at full 1920² with **no draft pass and no
per-image review**; the only gate is that each piece compiles and renders.

This skill assumes the `arts/gen` sandbox, `Coolors.kt` (palettes), and `WildFx.kt`
(finishing helpers) already exist (see `references/gart-api.md`). If they don't, read
an existing piece and the `generate-art` skill first.

## 0. Orient (do this first, in the main thread)
Read, in order: `CLAUDE.md` (taste, palettes, render workaround), `arts/gen/src/gen/Coolors.kt`,
`arts/gen/src/gen/WildFx.kt`, this skill's `references/gart-api.md`, and 2–3 reference
pieces — a good spread is `VoronoiOrb.kt`, `DiffGrowth.kt`, `CollageQuad.kt`. Then `ls
arts/gen/src/gen/` to see **what's already been made — never repeat a system**.

## 1. Pick the systems
Choose one distinct generative system per piece, each a different algorithm/world,
**composition-first** (focal point / silhouette / structure / density — not a centered
soft blob). Honor the standing rules in `CLAUDE.md`. Avoid the rut of "smooth field →
print filter". Assign each piece a coolors palette from `Coolors.kt` (favor Dario's
four: `inkEmber`, `patriotGold`, `navyGold`, `molten`). See
`references/system-menu.md` for candidates and what's already been used.

## 2. Orchestrate the writing (subagents, in parallel)
Spawn `general-purpose` subagents via the Task/Agent tool — group **2–3 pieces per
agent**, launched together so they run concurrently. Build each agent prompt from
`references/subagent-template.md` (paste the house-rules + API cheat-sheet, then the
per-piece specs with concrete algorithm hints). Hard rules for agents:
- They **only CREATE their new `.kt` files** under `arts/gen/src/gen/`. They must NOT
  run gradle and NOT edit existing files (the shared build is yours — avoids collisions).
- Mirror the scaffolding in `VoronoiOrb.kt` exactly (SIZE/SEED props, seeded `rng`,
  `Gart.of("<Name>", …)`, prints `seed=`, `gart.saveImage`, no `window()`, file name ==
  Gart.of name).
- Each piece uses a coolors palette and a `WildFx` finish (`bloom` for dark grounds,
  `grainOnly` for light).
- They return a concise summary (files, one-liner each, palette, API doubts) — not dumps.
Keep a running checklist in the chat so context stays legible.

## 3. Compile + render at 1920² (you, centrally — no review)
1. Apply the skiko render workaround: set `val version = "0.148.1"` in
   `gart/build.gradle.kts` (Maven Central; the pinned `0.148.3` is network-blocked).
2. Compile once: `./gradlew :arts:gen:classes :arts:gen:writeClasspath -q --no-configuration-cache`.
   Fix any compile errors (usually missing imports — see cheat-sheet) inline; re-compile
   until clean. This is the **only** correctness gate.
3. Render every piece straight to **high-res 1920²** — no draft pass, no per-image review:
   ```
   GART_SIZE=1920 .claude/skills/generate-art/render.sh arts:gen gen.<Name>Kt out/
   ```
   for each `<Name>`. A piece is done when the render exits 0 and writes its PNG.

## 4. Ship
- **Revert** `gart/build.gradle.kts` to `0.148.3` and confirm `git diff` shows it clean.
- Commit the new `.kt` files **and** their `out/*.png` (the `out/` gallery is tracked)
  to the dev branch named in the task. Open a **draft PR**. Show the images to the user
  (SendUserFile), grouped, with a one-line technique+palette caption each.
