#!/usr/bin/env bash
# Render a gart piece to a PNG, headlessly (no window required).
#
# Usage: render.sh <module> <main-class> [output-dir]
#   module      Gradle module path, e.g. "arts:gen"
#   main-class  Fully-qualified Kotlin main class, e.g. "gen.MyArtKt"
#               (a top-level `fun main()` in MyArt.kt compiles to MyArtKt)
#   output-dir  Where the PNG should land (default: repo root). Created if missing.
#
# Example:
#   .claude/skills/generate-art/render.sh arts:gen gen.MyArtKt out/
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: render.sh <module> <main-class> [output-dir]" >&2
  exit 2
fi

MODULE="$1"          # e.g. arts:gen
MAIN="$2"            # e.g. gen.MyArtKt
OUT="${3:-.}"

# Repo root = three levels up from .claude/skills/generate-art/
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
MODULE_DIR="$(echo "$MODULE" | tr ':' '/')"
ARGFILE="${ROOT}/${MODULE_DIR}/build/classpath.txt"

echo ">> Compiling :${MODULE} and resolving classpath..."
(cd "$ROOT" && ./gradlew ":${MODULE}:classes" ":${MODULE}:writeClasspath" -q)

mkdir -p "$OUT"
cd "$OUT"
echo ">> Running ${MAIN} (headless), output dir: $(pwd)"
# classpath.txt contains absolute paths, so this works from any directory.
java -Djava.awt.headless=true @"$ARGFILE" "$MAIN"
