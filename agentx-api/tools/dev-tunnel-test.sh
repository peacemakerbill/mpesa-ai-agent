#!/usr/bin/env bash
# Checks tools/dev-tunnel.sh without a network, an account or a real zrok2 or ngrok: it uses small stand-ins.
# Run from anywhere: bash agentx-api/tools/dev-tunnel-test.sh
set -u
TOOL="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/dev-tunnel.sh"
WORK="$(mktemp -d)"
PASS=0; FAIL=0
cleanup() { [ -n "${API_PID:-}" ] && kill "$API_PID" 2>/dev/null; [ -n "${WORK:-}" ] && rm -rf "${WORK:?}"; }
trap cleanup EXIT

ok() { PASS=$((PASS + 1)); printf 'ok    %s\n' "$1"; }
bad() { FAIL=$((FAIL + 1)); printf 'FAIL  %s\n      %s\n' "$1" "$2"; }
expect() {   # name, actual, wanted
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "wanted [$3] got [$2]"; fi
}
has() { case "$2" in *"$3"*) ok "$1" ;; *) bad "$1" "[$2] does not contain [$3]" ;; esac; }

# Every case gets its own home folder, tool folder, .env and state folder, and a PATH that holds only the stand-ins and the system tools
fresh() {
  CASE="$WORK/case$((++N))"; mkdir -p "$CASE/home" "$CASE/bin" "$CASE/tools" "$CASE/state"
  : > "$CASE/env"
  SYSPATH="/usr/bin:/bin"
}
run() {   # args... ; uses the variables set by the case
  HOME="$CASE/home" AGENTX_ENV_FILE="$CASE/env" AGENTX_TOOLS_DIR="$CASE/tools" TMPDIR="$CASE/state" PATH="$CASE/bin:$SYSPATH" \
    DEV_TUNNEL_WAIT=5 env -u AGENTX_STK_CALLBACK_URL -u AGENTX_PUBLIC_BASE_URL -u APP_ENV -u DEV_TUNNEL -u ZROK_ENABLE_TOKEN -u NGROK_AUTHTOKEN \
    "$@" bash "$TOOL" "$ARG"
}
plan() { ARG=plan run "$@" | grep -E '^ACTION=' | cut -d= -f2; }

N=0
fake_zrok() {   # $1 folder to put it in
  cat > "$1/zrok2" <<'ZEOF'
#!/usr/bin/env bash
case "$1" in
  enable) mkdir -p "$HOME/.zrok2"; echo '{}' > "$HOME/.zrok2/environment.json"; echo "enabled with $2" >> "$HOME/zrok-calls"; exit 0 ;;
  share) echo "share $*" >> "$HOME/zrok-calls"
         echo "[ INFO] connecting to https://api-v2.zrok.io"
         echo "[ INFO] access your zrok share at the following endpoints:"
         echo "https://abc123xyz.share.zrok.io"
         sleep 30 ;;
esac
ZEOF
  chmod +x "$1/zrok2"
}

# a tiny web server that answers like ngrok's local API (on a free port)
API_PORT=$((45000 + RANDOM % 1000))
start_fake_ngrok_api() {
  mkdir -p "$WORK/api/api"
  printf '%s' '{"tunnels":[{"name":"command_line","public_url":"https://fake-1234.ngrok-free.app","proto":"https","config":{"addr":"http://localhost:8084","inspect":true}}],"uri":"/api/tunnels"}' > "$WORK/api/api/tunnels"
  python3 -m http.server "$API_PORT" --bind 127.0.0.1 --directory "$WORK/api" >/dev/null 2>&1 &
  API_PID=$!
  sleep 1
}

# ---- decisions that need nothing installed
fresh; echo 'AGENTX_STK_CALLBACK_URL=https://mine.example.org' > "$CASE/env"
expect "a callback address in .env means no tunnel" "$(plan)" skip-configured

fresh
expect "a callback address in the environment means no tunnel" "$(plan AGENTX_STK_CALLBACK_URL=https://mine.example.org)" skip-configured

fresh; echo 'AGENTX_PUBLIC_BASE_URL=https://api.example.com' > "$CASE/env"
expect "a public https base address means no tunnel" "$(plan)" skip-public

fresh; echo 'AGENTX_PUBLIC_BASE_URL=http://localhost:8084' > "$CASE/env"
expect "a localhost base address is not enough, so with no account it says so" "$(plan)" no-credentials

fresh; printf 'APP_ENV=production\nZROK_ENABLE_TOKEN=tok\n' > "$CASE/env"
expect "never in production" "$(plan)" skip-env

fresh; printf 'APP_ENV=staging\nZROK_ENABLE_TOKEN=tok\n' > "$CASE/env"
expect "never in staging" "$(plan)" skip-env

fresh; printf 'DEV_TUNNEL=off\nZROK_ENABLE_TOKEN=tok\n' > "$CASE/env"
expect "DEV_TUNNEL=off" "$(plan)" skip-off

fresh
expect "an empty setup says no credentials, and APP_ENV unset counts as development" "$(plan)" no-credentials

fresh; printf 'AGENTX_STK_CALLBACK_URL=\nZROK_ENABLE_TOKEN=tok\n' > "$CASE/env"
expect "a blank callback address in .env does not count as set" "$(plan)" start

# ---- which provider
fresh; echo 'ZROK_ENABLE_TOKEN=tok' > "$CASE/env"
has "a zrok token picks zrok" "$(ARG=plan run | grep PROVIDER)" "zrok"

fresh; echo 'NGROK_AUTHTOKEN=tok' > "$CASE/env"
has "an ngrok token picks ngrok" "$(ARG=plan run | grep PROVIDER)" "ngrok"

fresh; printf 'ZROK_ENABLE_TOKEN=a\nNGROK_AUTHTOKEN=b\n' > "$CASE/env"
has "with both tokens and nothing installed zrok comes first" "$(ARG=plan run | grep PROVIDER)" "zrok"

fresh; printf 'ZROK_ENABLE_TOKEN=a\nNGROK_AUTHTOKEN=b\n' > "$CASE/env"; printf '#!/bin/sh\nexit 0\n' > "$CASE/bin/ngrok"; chmod +x "$CASE/bin/ngrok"
has "with both tokens, an installed ngrok beats an uninstalled zrok" "$(ARG=plan run | grep PROVIDER)" "ngrok"

fresh; printf 'ZROK_ENABLE_TOKEN=a\nNGROK_AUTHTOKEN=b\nDEV_TUNNEL_PROVIDER=ngrok\n' > "$CASE/env"
has "DEV_TUNNEL_PROVIDER forces a choice" "$(ARG=plan run | grep PROVIDER)" "ngrok"

fresh; mkdir -p "$CASE/home/.zrok2"; echo '{}' > "$CASE/home/.zrok2/environment.json"
expect "an already enabled zrok2 needs no token" "$(plan)" start

fresh; mkdir -p "$CASE/home/.config/ngrok"; echo 'authtoken: abc' > "$CASE/home/.config/ngrok/ngrok.yml"
expect "an already configured ngrok needs no token" "$(plan)" start

# ---- starting zrok with a stand-in
fresh; echo 'ZROK_ENABLE_TOKEN=tok123' > "$CASE/env"; fake_zrok "$CASE/bin"
ARG=start out="$(ARG=start run 2>/dev/null)"
expect "zrok: the address is the last line of output" "$(printf '%s' "$out" | tail -n1)" "https://abc123xyz.share.zrok.io"
has "zrok: it enabled the machine with the token from .env" "$(cat "$CASE/home/zrok-calls" 2>/dev/null)" "enabled with tok123"
has "zrok: it shared the right port publicly and headless" "$(cat "$CASE/home/zrok-calls" 2>/dev/null)" "share public --headless 8084"
ARG=stop run >/dev/null 2>&1; sleep 0.3
expect "zrok: stop ends the tunnel" "$(pgrep -f "$CASE/bin/zrok2" | wc -l | tr -d ' ')" "0"

fresh; echo 'SERVER_PORT=9090' > "$CASE/env"; mkdir -p "$CASE/home/.zrok2"; echo '{}' > "$CASE/home/.zrok2/environment.json"; fake_zrok "$CASE/bin"
ARG=start run >/dev/null 2>&1
has "the tunnel follows SERVER_PORT" "$(cat "$CASE/home/zrok-calls" 2>/dev/null)" "headless 9090"
ARG=stop run >/dev/null 2>&1

# ---- a tunnel that cannot start must not stop the service
fresh; echo 'ZROK_ENABLE_TOKEN=tok' > "$CASE/env"; printf '#!/bin/sh\necho "boom" >&2\nexit 1\n' > "$CASE/bin/zrok2"; chmod +x "$CASE/bin/zrok2"
out="$(ARG=start run 2>/dev/null)"; code=$?
expect "a failing zrok2 prints no address" "$out" ""
expect "a failing zrok2 still exits normally so the service starts" "$code" "0"

fresh; printf 'ZROK_ENABLE_TOKEN=tok\nZROK2_DOWNLOAD_URL=file:///nonexistent/zrok2.tar.gz\n' > "$CASE/env"
err="$(ARG=start run 2>&1 >/dev/null)"
has "with no download possible it explains and starts without an address" "$err" "No tunnel address"

fresh; printf 'ZROK_ENABLE_TOKEN=tok\nDEV_TUNNEL_INSTALL=false\n' > "$CASE/env"
err="$(ARG=start run 2>&1 >/dev/null)"
has "install can be switched off" "$err" "DEV_TUNNEL_INSTALL is off"

# ---- installing from a download (a local file stands in for the internet)
fresh; echo 'ZROK_ENABLE_TOKEN=tok' > "$CASE/env"
mkdir -p "$WORK/pkg"; fake_zrok "$WORK/pkg"; tar -czf "$WORK/zrok2.tar.gz" -C "$WORK/pkg" zrok2
echo "ZROK2_DOWNLOAD_URL=file://$WORK/zrok2.tar.gz" >> "$CASE/env"
out="$(ARG=start run 2>/dev/null)"
expect "zrok2 is downloaded into the tool folder when missing" "$(test -x "$CASE/tools/zrok2" && echo yes)" yes
expect "and then used to make the tunnel" "$(printf '%s' "$out" | tail -n1)" "https://abc123xyz.share.zrok.io"
ARG=stop run >/dev/null 2>&1

# ---- ngrok through its local API
start_fake_ngrok_api
fresh; echo 'NGROK_AUTHTOKEN=tok' > "$CASE/env"
printf '#!/bin/sh\nsleep 30\n' > "$CASE/bin/ngrok"; chmod +x "$CASE/bin/ngrok"
out="$(ARG=start run NGROK_API=http://127.0.0.1:$API_PORT 2>/dev/null)"
expect "ngrok: an already running tunnel for this port is reused" "$(printf '%s' "$out" | tail -n1)" "https://fake-1234.ngrok-free.app"

fresh; printf 'NGROK_AUTHTOKEN=tok\nSERVER_PORT=7777\n' > "$CASE/env"
printf '#!/bin/sh\nsleep 30\n' > "$CASE/bin/ngrok"; chmod +x "$CASE/bin/ngrok"
out="$(ARG=start run NGROK_API=http://127.0.0.1:$API_PORT DEV_TUNNEL_WAIT=2 2>/dev/null)"
expect "ngrok: a tunnel for another port is not mistaken for ours" "$out" ""
ARG=stop run >/dev/null 2>&1

printf '\n%s passed, %s failed\n' "$PASS" "$FAIL"
[ "$FAIL" = 0 ]
