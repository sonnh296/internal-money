#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/Users/mac2019/Library/Java/JavaVirtualMachines/corretto-21.0.6/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
LOG_DIR="$ROOT/.run-logs"
mkdir -p "$LOG_DIR"

start_service() {
  local name="$1"
  local dir="$2"
  local profile="${3:-}"
  local extra="${4:-}"
  echo "Starting $name..."
  (
    cd "$ROOT/$dir"
    if [ -n "$profile" ]; then
      nohup mvn -q spring-boot:run -Dspring-boot.run.profiles="$profile" $extra \
        >"$LOG_DIR/${name}.log" 2>&1 &
    else
      nohup mvn -q spring-boot:run $extra \
        >"$LOG_DIR/${name}.log" 2>&1 &
    fi
    echo $! >"$LOG_DIR/${name}.pid"
  )
}

start_service auth AuthUser dev
start_service customer CustomerService dev
start_service account AccountService dev
start_service biller BillerService ""
start_service payment PaymentOrchestrator ""
start_service billpay-worker BillPayWorkerService ""
start_service settlement SettlementService ""
start_service eft EFTService ""
start_service eft-worker EFTWorkerService ""
start_service gateway ApiGateway ""
start_service pos pos/boost ""

echo "Waiting for core services..."
sleep 45
for port in 8094 8083 8084 8088 8086 8090 8080 8082; do
  if curl -sf "http://localhost:${port}/actuator/health" >/dev/null 2>&1 \
    || curl -sf "http://localhost:${port}/api/v1/health" >/dev/null 2>&1; then
    echo "  OK :$port"
  else
    echo "  -- :$port (still starting — see .run-logs)"
  fi
done
echo "Done. Logs: $LOG_DIR"
