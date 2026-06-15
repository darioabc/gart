#!/usr/bin/env bash
# Render ONE Voronoi-stipple piece at high resolution (1920x1920), headless.
#
# Self-contained: bakes in the skiko render workaround. The repo pins skiko
# 0.148.3, which lives only on a network-blocked repo (403). This script
# temporarily pins skiko to 0.148.1 (on Maven Central) for the render, then
# ALWAYS reverts the pin on exit (even on error / Ctrl-C) so the swap never
# leaks into your working tree.
#
# Usage: render-voronoi.sh <main-class> [output-dir]
#   main-class  Fully-qualified Kotlin main class, e.g. "gen.VoronoiEclipseKt"
#               (a top-level `fun main()` in VoronoiEclipse.kt compiles to that).
#   output-dir  Where the PNG should land (default: out/). Created if missing.
#
# Optional env:
#   GART_SEED=<long>   reproduce a specific result (the piece prints its seed).
#
# Example:
#   .claude/skills/voronoi/render-voronoi.sh gen.VoronoiEclipseKt out/
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: render-voronoi.sh <main-class> [output-dir]" >&2
  exit 2
fi

MAIN="$1"                       # e.g. gen.VoronoiEclipseKt
OUT="${2:-out/}"

# Repo root = three levels up from .claude/skills/voronoi/
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
GRADLE_FILE="${ROOT}/gart/build.gradle.kts"
RENDER="${ROOT}/.claude/skills/generate-art/render.sh"

# --- skiko render workaround: pin to a Maven-Central version, always revert ---
ORIG_VER="$(grep -oE 'val version = "[0-9.]+"' "$GRADLE_FILE" | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
if [ -z "${ORIG_VER:-}" ]; then
  echo "ERROR: could not find skiko 'val version = \"...\"' in $GRADLE_FILE" >&2
  exit 1
fi
restore_pin() { sed -i "s/val version = \"[0-9.]*\"/val version = \"${ORIG_VER}\"/" "$GRADLE_FILE"; }
trap restore_pin EXIT
echo ">> skiko pin: ${ORIG_VER} -> 0.148.1 (temporary, reverted on exit)"
sed -i 's/val version = "[0-9.]*"/val version = "0.148.1"/' "$GRADLE_FILE"

# --- render exactly one image at high res 1920x1920 ---
GART_SIZE=1920 "$RENDER" arts:gen "$MAIN" "$OUT"
