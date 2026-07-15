#!/usr/bin/env bash

set -euo pipefail

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="${JAR_FILE:-$APP_HOME/spring-ai-alibaba-data-agent-management-1.0.0-SNAPSHOT.jar}"
CONFIG_FILE="${CONFIG_FILE:-$APP_HOME/config/application.yml}"
LOG_DIR="${LOG_DIR:-$APP_HOME/logs}"
LOG_FILE="${LOG_FILE:-$LOG_DIR/dataagent.log}"
PID_FILE="${PID_FILE:-$APP_HOME/dataagent.pid}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2g}"

if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "Java executable not found: $JAVA_BIN"
  exit 1
fi

JAVA_MAJOR="$($JAVA_BIN -version 2>&1 | awk -F '[\".]' '/version/ {print ($2 == 1 ? $3 : $2); exit}')"
if [[ -z "$JAVA_MAJOR" || "$JAVA_MAJOR" -lt 17 ]]; then
  echo "JDK 17 or newer is required. Current version: $JAVA_MAJOR"
  exit 1
fi

if [[ ! -f "$JAR_FILE" ]]; then
  echo "JAR file not found: $JAR_FILE"
  exit 1
fi

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "Configuration file not found: $CONFIG_FILE"
  exit 1
fi

if [[ -f "$PID_FILE" ]]; then
  OLD_PID="$(cat "$PID_FILE")"
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "DataAgent is already running, PID: $OLD_PID"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

mkdir -p "$LOG_DIR"

nohup "$JAVA_BIN" $JAVA_OPTS \
  -jar "$JAR_FILE" \
  --spring.config.additional-location="file:$CONFIG_FILE" \
  >> "$LOG_FILE" 2>&1 &

PID=$!
echo "$PID" > "$PID_FILE"
sleep 2

if kill -0 "$PID" 2>/dev/null; then
  echo "DataAgent started successfully, PID: $PID"
  echo "Log file: $LOG_FILE"
else
  rm -f "$PID_FILE"
  echo "DataAgent failed to start. Check the log: $LOG_FILE"
  exit 1
fi
