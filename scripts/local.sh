#!/usr/bin/env bash
# Run the Bitly stack locally: LocalStack + Redis (Docker), Spring Boot backend, Next.js frontend.
#
#   ./scripts/local.sh [command]
#
#   start    (default) start everything in the background and wait until it's healthy
#   stop     stop backend + frontend (containers keep running, data kept)
#   down     stop everything including containers (data kept)
#   status   show what's running
#   logs     follow logs: ./scripts/local.sh logs [backend|frontend]
#   test     run every suite: JUnit unit + integration, Vitest, Postman (newman) and browser e2e (needs a running stack)
#   reset    delete all local data (DynamoDB table, Redis counter/cache) and start fresh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN="$ROOT/.run"
JAR="$ROOT/backend/target/bitly-0.0.1-SNAPSHOT.jar"
BACKEND_URL="http://localhost:8090"
FRONTEND_URL="http://localhost:3001"
mkdir -p "$RUN"
cd "$ROOT"

step() { echo "==> $*"; }
ok()   { echo "  ✓ $*"; }
die()  { echo "  ✗ $*" >&2; exit 1; }

pid_of() { [[ -f "$RUN/$1.pid" ]] && cat "$RUN/$1.pid" || true; }
alive()  { local pid; pid="$(pid_of "$1")"; [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; }

kill_tree() {
  local pid=$1 child
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do kill_tree "$child"; done
  kill "$pid" 2>/dev/null || true
}

stop_proc() {
  local pid; pid="$(pid_of "$1")"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    kill_tree "$pid"
    for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
    ok "stopped $1"
  fi
  rm -f "$RUN/$1.pid"
}

wait_for() {
  local name=$1 url=$2 tries=${3:-90}
  for _ in $(seq 1 "$tries"); do
    curl -fsS -o /dev/null "$url" 2>/dev/null && { ok "$name is up at $url"; return; }
    sleep 1
  done
  die "$name did not come up; see $RUN/$name.log"
}

start_infra() {
  step "Starting LocalStack (DynamoDB) + Redis"
  docker compose up -d --wait localstack redis >/dev/null
  ok "localstack :4567, redis :6380"
}

start_backend() {
  alive backend && { ok "backend already running"; return; }
  step "Building backend"
  (cd backend && mvn -q -DskipTests package)
  step "Starting backend"
  nohup java -jar "$JAR" >"$RUN/backend.log" 2>&1 &
  echo $! >"$RUN/backend.pid"
  wait_for backend "$BACKEND_URL/actuator/health"
}

start_frontend() {
  alive frontend && { ok "frontend already running"; return; }
  step "Starting frontend"
  [[ -d frontend/node_modules ]] || (cd frontend && npm ci)
  # Redirect the whole subshell: otherwise it keeps our stdout open and callers piping us hang.
  (cd frontend && exec nohup npx next dev -p 3001) >"$RUN/frontend.log" 2>&1 &
  echo $! >"$RUN/frontend.pid"
  wait_for frontend "$FRONTEND_URL"
}

cmd_start() {
  start_infra
  start_backend
  start_frontend
  echo
  echo "  App:      $FRONTEND_URL"
  echo "  API:      $BACKEND_URL  (POST /urls, GET /{code})"
  echo "  Postman:  postman/Bitly.postman_collection.json + Bitly-Local environment"
}

cmd_stop() { stop_proc frontend; stop_proc backend; }

cmd_status() {
  docker compose ps --format 'table {{.Service}}\t{{.Status}}'
  for p in backend frontend; do
    if alive "$p"; then ok "$p running (pid $(pid_of "$p"))"; else echo "  - $p stopped"; fi
  done
}

cmd_test() {
  step "Backend: JUnit unit + integration (Testcontainers)"
  (cd backend && mvn -q verify)
  ok "backend tests passed"
  step "Frontend: Vitest"
  (cd frontend && npm test --silent)
  step "API: Postman collection via newman"
  curl -fsS -o /dev/null "$BACKEND_URL/actuator/health" || die "backend not running; run ./scripts/local.sh start first"
  npx --yes newman run postman/Bitly.postman_collection.json -e postman/Bitly-Local.postman_environment.json
  step "UI: browser e2e smoke test (headless Chrome)"
  (cd frontend && npm run e2e --silent)
}

cmd_reset() {
  cmd_stop
  docker compose down -v
  cmd_start
}

case "${1:-start}" in
  start)   cmd_start ;;
  stop)    cmd_stop ;;
  down)    cmd_stop; docker compose down ;;
  restart) cmd_stop; cmd_start ;;
  status)  cmd_status ;;
  logs)    if [[ -n "${2:-}" ]]; then tail -f "$RUN/$2.log"; else tail -f "$RUN"/backend.log "$RUN"/frontend.log; fi ;;
  test)    cmd_test ;;
  reset)   cmd_reset ;;
  *)       sed -n '2,13p' "$0"; exit 1 ;;
esac
