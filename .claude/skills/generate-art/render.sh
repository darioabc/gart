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

# [IMP #7] Preflight: verify JDK is available before doing anything else.
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  echo "ERROR: JAVA_HOME not set or JDK not found." >&2
  echo "Fix: export JAVA_HOME=\$(brew --prefix openjdk@21)" >&2
  echo "     then add that line to ~/.bashrc or ~/.zshrc" >&2
  exit 1
fi

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

# [IMP #4] Optional draft-size override: set GART_SIZE=512 in the shell env to render
# at 512px for a fast iteration pass. render.sh converts the env var to a JVM -D property.
GART_SIZE_ARG=""
if [ -n "${GART_SIZE:-}" ]; then
  GART_SIZE_ARG="-DGART_SIZE=${GART_SIZE}"
fi

# [IMP #1] Optional seed: set GART_SEED=<long> in the shell env to reproduce a result.
# render.sh converts the env var to a JVM -D property.
GART_SEED_ARG=""
if [ -n "${GART_SEED:-}" ]; then
  GART_SEED_ARG="-DGART_SEED=${GART_SEED}"
fi

# classpath.txt contains absolute paths, so this works from any directory.
# GART_SIZE_ARG and GART_SEED_ARG are unquoted intentionally: empty string
# collapses to nothing under word-splitting; quoting would pass a blank arg to java.
java -Djava.awt.headless=true ${GART_SIZE_ARG} ${GART_SEED_ARG} @"$ARGFILE" "$MAIN"
