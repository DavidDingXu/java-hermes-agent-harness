#!/usr/bin/env bash
set -euo pipefail

CHECKPOINT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$CHECKPOINT_DIR/.." && pwd)"
MODE="${1:---online}"

if [ "$MODE" != "--online" ] && [ "$MODE" != "--compile-only" ]; then
  echo "usage: $0 [--online|--compile-only]" >&2
  exit 2
fi

mvn --batch-mode --no-transfer-progress \
  -f "$CHECKPOINT_DIR/pom.xml" \
  clean \
  package \
  -DskipTests

JARS=(
  "01-main-loop/target/checkpoint-01-main-loop-1.0.0.jar"
  "02-tools/target/checkpoint-02-tools-1.0.0.jar"
  "03-context-session-memory/target/checkpoint-03-context-session-memory-1.0.0.jar"
  "04-recovery-security-entry/target/checkpoint-04-recovery-security-entry-1.0.0.jar"
  "05-observability-learning/target/checkpoint-05-observability-learning-1.0.0.jar"
  "06-advanced-harness/target/checkpoint-06-advanced-harness-1.0.0.jar"
)

for jar in "${JARS[@]}"; do
  test -f "$CHECKPOINT_DIR/$jar"
done

if [ "$MODE" = "--compile-only" ]; then
  echo "all course checkpoints compiled (online Main validation not requested)"
  exit 0
fi

JAVA_BIN="$(command -v java)"
if [ -n "${JAVA_HOME:-}" ]; then
  if [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
  elif [ -x "$JAVA_HOME/bin/java.exe" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java.exe"
  fi
fi

for jar in "${JARS[@]}"; do
  (cd "$PROJECT_DIR" && "$JAVA_BIN" -jar "$CHECKPOINT_DIR/$jar")
done

echo "all course checkpoints passed with the locally configured real model"
