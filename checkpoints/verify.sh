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
  verify

ONLINE_MAINS=(
  "01-main-loop/target/checkpoint-01-main-loop-1.0.0.jar|com.ading.ai.hermes.checkpoint.MainLoopCheckpointApplication"
  "02-tools/target/checkpoint-02-tools-1.0.0.jar|com.ading.ai.hermes.checkpoint.ToolRuntimeCheckpointApplication"
  "03-context-session-memory/target/checkpoint-03-context-session-memory-1.0.0.jar|com.ading.ai.hermes.checkpoint.StateCheckpointApplication"
  "04-recovery-security-entry/target/checkpoint-04-recovery-security-entry-1.0.0.jar|com.ading.ai.hermes.checkpoint.RecoverySecurityCheckpointApplication"
  "04-recovery-security-entry/target/checkpoint-04-recovery-security-entry-1.0.0.jar|com.ading.ai.hermes.checkpoint.ProtocolEntryCheckpointApplication"
  "04-recovery-security-entry/target/checkpoint-04-recovery-security-entry-1.0.0.jar|com.ading.ai.hermes.checkpoint.CronCheckpointApplication"
  "04-recovery-security-entry/target/checkpoint-04-recovery-security-entry-1.0.0.jar|com.ading.ai.hermes.checkpoint.SubAgentCheckpointApplication"
  "04-recovery-security-entry/target/checkpoint-04-recovery-security-entry-1.0.0.jar|com.ading.ai.hermes.checkpoint.GovernedEntryCheckpointApplication"
  "05-observability-learning/target/checkpoint-05-observability-learning-1.0.0.jar|com.ading.ai.hermes.checkpoint.ObservabilityCheckpointApplication"
  "06-advanced-harness/target/checkpoint-06-advanced-harness-1.0.0.jar|com.ading.ai.hermes.checkpoint.AdvancedHarnessCheckpointApplication"
)

for entry in "${ONLINE_MAINS[@]}"; do
  IFS='|' read -r jar _ <<< "$entry"
  test -f "$CHECKPOINT_DIR/$jar"
done

if [ "$MODE" = "--compile-only" ]; then
  echo "all course checkpoint contracts passed (online Main validation not requested)"
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

for entry in "${ONLINE_MAINS[@]}"; do
  IFS='|' read -r jar main_class <<< "$entry"
  echo "running $main_class"
  (cd "$PROJECT_DIR" && "$JAVA_BIN" -cp "$CHECKPOINT_DIR/$jar" "$main_class")
done

echo "all 10 course Main entry points passed with the locally configured real model"
