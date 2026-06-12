#!/bin/bash
# Flink cluster diagnostics for Linoleum load testing.
# Usage: bash diagnostics.sh
set -euo pipefail

FLINK_URL="${FLINK_URL:-http://localhost:8081}"
FLINK_LOG_DIR="${FLINK_LOG_DIR:-$HOME/systems/flink/flink-1.20.4/log}"

echo "=== CLUSTER OVERVIEW ==="
curl -s "$FLINK_URL/overview" | jq '{
  tms: .taskmanagers,
  slotsTotal: ."slots-total",
  slotsAvail: ."slots-available",
  jobsRunning: ."jobs-running",
  jobsFailed: ."jobs-failed"
}'

JID=$(curl -s "$FLINK_URL/jobs/overview" | jq -r '.jobs[0].jid // empty')
if [ -z "$JID" ]; then
  echo "No running job found."
  exit 1
fi

echo ""
echo "=== JOB: $JID ==="
curl -s "$FLINK_URL/jobs/$JID" | jq '{
  state,
  duration,
  vertices: [.vertices[] | {
    name,
    parallelism,
    status,
    backpressured_ms: .metrics."accumulated-backpressured-time",
    readRecords: .metrics."read-records",
    writeRecords: .metrics."write-records"
  }]
}'

echo ""
echo "=== SOURCE BACKPRESSURE ==="
SOURCE_VID=$(curl -s "$FLINK_URL/jobs/$JID" | jq -r '.vertices[] | select(.name | startswith("Source")) | .id')
if [ -n "$SOURCE_VID" ]; then
  curl -s "$FLINK_URL/jobs/$JID/vertices/$SOURCE_VID/backpressure" | jq '
    [.subtasks[] | {
      subtask: .subtask,
      bp: ."backpressure-level",
      busy: .busyRatio,
      idle: .idleRatio
    }]
  '
else
  echo "Source vertex not found"
fi

echo ""
echo "=== SINK BACKPRESSURE ==="
SINK_VID=$(curl -s "$FLINK_URL/jobs/$JID" | jq -r '.vertices[] | select(.name | startswith("EventTimeSessionWindows")) | .id')
if [ -n "$SINK_VID" ]; then
  curl -s "$FLINK_URL/jobs/$JID/vertices/$SINK_VID/backpressure" | jq '
    [.subtasks[] | {
      subtask: .subtask,
      bp: ."backpressure-level",
      busy: .busyRatio,
      idle: .idleRatio
    }]
  '
else
  echo "Sink vertex not found"
fi

echo ""
echo "=== TASKMANAGERS ==="
curl -s "$FLINK_URL/taskmanagers" | jq '[.taskmanagers[] | {
  id: .id,
  freeSlots: .freeSlots,
  slots: .slotsNumber,
  heapMB: (.metrics.heapUsed // 0 / 1048576 | floor),
  heapMaxMB: (.metrics.heapMax // 0 / 1048576 | floor),
  directMB: (.metrics.directUsed // 0 / 1048576 | floor),
  gc: [.metrics.garbageCollectors[]? | "\(.name):\(.count)"]
}]'

echo ""
echo "=== RECENT TM LOGS (last 5 non-kafka lines, TM-0) ==="
TM0_LOG=$(ls "$FLINK_LOG_DIR"/flink-*-taskexecutor-0-kymera.log 2>/dev/null | head -1)
if [ -n "$TM0_LOG" ]; then
  tail -20 "$TM0_LOG" | grep -v -i -E 'kafka|FetchSession|Disconnect' | tail -5
else
  echo "No TM-0 log found at $FLINK_LOG_DIR"
fi
