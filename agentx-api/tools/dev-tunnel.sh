#!/usr/bin/env bash
# Development only. Gives Safaricom a public https address to post STK Push answers to, without you doing anything.
#
#   tools/dev-tunnel.sh plan    says what it would do and why, starts nothing
#   tools/dev-tunnel.sh start   starts a tunnel if one is needed and prints its address on the last line of standard output
#   tools/dev-tunnel.sh stop    stops the tunnel this script started
#
# It does nothing when:
#   APP_ENV is not development, DEV_TUNNEL is off, AGENTX_STK_CALLBACK_URL is already set,
#   or AGENTX_PUBLIC_BASE_URL is already a public https address.
# Otherwise it picks zrok2 or ngrok, installs it for you (into ~/.agentx/bin, no sudo) when it is missing and an account token is
# available, starts a tunnel to this service and prints the address. Settings are read from the environment first, then from .env.
# Details: docs/DEV_TUNNEL.md

set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_DIR="$(cd "$HERE/.." && pwd)"
ENV_FILE="${AGENTX_ENV_FILE:-$API_DIR/.env}"
BIN_DIR="${AGENTX_TOOLS_DIR:-$HOME/.agentx/bin}"
STATE_DIR="${TMPDIR:-/tmp}/agentx-dev-tunnel"

say() { printf '[dev-tunnel] %s\n' "$*" >&2; }

# A setting from the real environment, or else the last matching line of .env (quotes and a trailing comment removed)
env_get() {
  local key="$1" val=""
  val="${!key-}"
  if [ -z "$val" ] && [ -f "$ENV_FILE" ]; then
    val="$(grep -E "^[[:space:]]*${key}=" "$ENV_FILE" | tail -n1 | sed -E "s/^[[:space:]]*${key}=//; s/[[:space:]]+#.*\$//; s/^['\"]//; s/['\"][[:space:]]*\$//")"
  fi
  printf '%s' "$val"
}

lower() { printf '%s' "$1" | tr 'A-Z' 'a-z'; }

# true for an https address that Safaricom can reach: not localhost, not a private network, has a dot in the host
public_https() {
  local u="$1" host
  case "$u" in https://*) ;; *) return 1 ;; esac
  host="${u#https://}"; host="${host%%/*}"; host="${host%%:*}"
  case "$host" in "" | localhost | *.localhost | 127.* | 0.0.0.0 | 10.* | 192.168.*) return 1 ;; esac
  case "$host" in *.*) return 0 ;; *) return 1 ;; esac
}

os_name() { uname -s | tr 'A-Z' 'a-z'; }
arch_name() {
  case "$(uname -m)" in
    x86_64 | amd64) echo amd64 ;;
    aarch64 | arm64) echo arm64 ;;
    armv7* | armv6* | arm) echo arm ;;
    *) uname -m ;;
  esac
}

find_bin() {
  local name="$1" found
  found="$(command -v "$name" 2>/dev/null)" && { printf '%s' "$found"; return 0; }
  [ -x "$BIN_DIR/$name" ] && { printf '%s' "$BIN_DIR/$name"; return 0; }
  return 1
}
zrok_bin() { find_bin zrok2; }
ngrok_bin() { find_bin ngrok; }

zrok_enabled() { [ -f "$HOME/.zrok2/environment.json" ]; }
ngrok_configured() {
  local f
  for f in "$HOME/.config/ngrok/ngrok.yml" "$HOME/Library/Application Support/ngrok/ngrok.yml" "$HOME/.ngrok2/ngrok.yml"; do
    grep -qs 'authtoken' "$f" 2>/dev/null && return 0
  done
  return 1
}

# ------------------------------------------------------------------ deciding

ACTION=""; PROVIDER=""; REASON=""

choose_provider() {
  local want zready=0 nready=0
  want="$(lower "$(env_get DEV_TUNNEL_PROVIDER)")"; want="${want:-auto}"
  case "$want" in
    zrok | zrok2) PROVIDER=zrok; return ;;
    ngrok) PROVIDER=ngrok; return ;;
  esac
  if zrok_enabled || [ -n "$(env_get ZROK_ENABLE_TOKEN)" ]; then zready=1; fi
  if ngrok_configured || [ -n "$(env_get NGROK_AUTHTOKEN)" ]; then nready=1; fi
  if [ -n "$(zrok_bin)" ] && [ "$zready" = 1 ]; then PROVIDER=zrok
  elif [ -n "$(ngrok_bin)" ] && [ "$nready" = 1 ]; then PROVIDER=ngrok
  elif [ "$zready" = 1 ]; then PROVIDER=zrok
  elif [ "$nready" = 1 ]; then PROVIDER=ngrok
  fi
}

decide() {
  ACTION=""; PROVIDER=""; REASON=""
  local appenv off stk base
  appenv="$(lower "$(env_get APP_ENV)")"; appenv="${appenv:-development}"
  case "$appenv" in development | dev) ;; *)
    ACTION=skip-env; REASON="APP_ENV is $appenv and the tunnel is for development only"; return ;;
  esac
  off="$(lower "$(env_get DEV_TUNNEL)")"
  case "$off" in off | false | 0 | no)
    ACTION=skip-off; REASON="DEV_TUNNEL is $off"; return ;;
  esac
  stk="$(env_get AGENTX_STK_CALLBACK_URL)"
  if [ -n "$stk" ]; then
    ACTION=skip-configured; REASON="AGENTX_STK_CALLBACK_URL is already set to $stk"; return
  fi
  base="$(env_get AGENTX_PUBLIC_BASE_URL)"
  if public_https "$base"; then
    ACTION=skip-public; REASON="AGENTX_PUBLIC_BASE_URL ($base) is already a public https address, so STK answers use it"; return
  fi
  choose_provider
  if [ -z "$PROVIDER" ]; then
    ACTION=no-credentials
    REASON="no tunnel account is set up, so STK answers cannot reach this machine (the result is still fetched from Safaricom about every minute)"
    return
  fi
  ACTION=start; REASON="no public address is configured, so a $PROVIDER tunnel is started"
}

guidance() {
  say "To get a tunnel automatically, add ONE of these to agentx-api/.env and start again:"
  say "  ZROK_ENABLE_TOKEN=your zrok account token   (zrok2 is installed for you if missing)"
  say "  NGROK_AUTHTOKEN=your ngrok authtoken        (ngrok is installed for you if missing)"
  say "Or set AGENTX_STK_CALLBACK_URL to an address you already have. DEV_TUNNEL=off silences this."
}

# ------------------------------------------------------------------ installing (inside your home folder, no sudo)

download_and_extract() {   # url, binary name
  local url="$1" name="$2" tmp found
  command -v curl >/dev/null 2>&1 || { say "curl is needed to download $name"; return 1; }
  command -v tar >/dev/null 2>&1 || { say "tar is needed to unpack $name"; return 1; }
  tmp="$(mktemp -d)" || return 1
  say "Downloading $name from $url"
  if ! curl -fsSL "$url" -o "${tmp:?}/pkg.tgz"; then
    say "The download failed."; rm -rf "${tmp:?}"; return 1
  fi
  if ! tar -xzf "${tmp:?}/pkg.tgz" -C "${tmp:?}" 2>/dev/null; then
    say "The download was not a tar.gz file."; rm -rf "${tmp:?}"; return 1
  fi
  found="$(find "${tmp:?}" -type f -name "$name" | head -n1)"
  if [ -z "$found" ]; then
    say "$name was not inside the download."; rm -rf "${tmp:?}"; return 1
  fi
  mkdir -p "${BIN_DIR:?}" && cp "$found" "${BIN_DIR:?}/$name" && chmod +x "${BIN_DIR:?}/$name"
  rm -rf "${tmp:?}"
  say "Installed $name in $BIN_DIR"
}

install_allowed() {
  case "$(lower "$(env_get DEV_TUNNEL_INSTALL)")" in false | off | 0 | no)
    say "$1 is not installed and DEV_TUNNEL_INSTALL is off."; return 1 ;;
  esac
}

install_zrok() {
  install_allowed zrok2 || return 1
  local url ver
  url="$(env_get ZROK2_DOWNLOAD_URL)"
  if [ -z "$url" ]; then
    ver="$(env_get ZROK2_VERSION)"
    if [ -z "$ver" ]; then
      ver="$(curl -fsSL "https://api.github.com/repos/openziti/zrok/releases?per_page=40" 2>/dev/null \
        | grep -o '"tag_name": *"v2[^"]*"' | sed -E 's/.*"(v2[^"]*)"/\1/' | grep -v -e '-' | head -n1)"
    fi
    if [ -z "$ver" ]; then
      say "Could not find the latest zrok2 release. Set ZROK2_VERSION (for example v2.0.0) or install it yourself:"
      say "  curl -sSf https://get.openziti.io/install.bash | sudo bash -s zrok2"
      return 1
    fi
    url="https://github.com/openziti/zrok/releases/download/${ver}/zrok2_${ver#v}_$(os_name)_$(arch_name).tar.gz"
  fi
  download_and_extract "$url" zrok2 || {
    say "Install zrok2 yourself instead: curl -sSf https://get.openziti.io/install.bash | sudo bash -s zrok2"; return 1; }
}

install_ngrok() {
  install_allowed ngrok || return 1
  local url
  url="$(env_get NGROK_DOWNLOAD_URL)"
  [ -n "$url" ] || url="https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-$(os_name)-$(arch_name).tgz"
  download_and_extract "$url" ngrok || { say "Install ngrok yourself from https://ngrok.com/download"; return 1; }
}

# ------------------------------------------------------------------ starting

wait_seconds() { local w; w="$(env_get DEV_TUNNEL_WAIT)"; case "$w" in '' | *[!0-9]*) echo 40 ;; *) echo "$w" ;; esac; }

strip_colour() { sed -e $'s/\x1b\\[[0-9;]*[A-Za-z]//g'; }

# the public address zrok printed: an https address that is not zrok's own API or documentation
zrok_url_from() {
  local candidates pick
  candidates="$(strip_colour < "$1" | grep -Eo 'https://[A-Za-z0-9._-]+' | grep -Ev '://(api|docs|www|github|netfoundry|openziti)|api-v[0-9]' | sort -u)"
  pick="$(printf '%s\n' "$candidates" | grep -E 'zrok|share' | head -n1)"
  [ -n "$pick" ] || pick="$(printf '%s\n' "$candidates" | grep . | head -n1)"
  printf '%s' "$pick"
}

write_state() { mkdir -p "${STATE_DIR:?}" && printf '%s' "$2" > "${STATE_DIR:?}/$1"; }

start_zrok() {
  local port="$1" bin token log pid url waited limit
  bin="$(zrok_bin)" || bin=""
  if [ -z "$bin" ]; then
    install_zrok || return 1
    bin="$(zrok_bin)" || { say "zrok2 is still not available."; return 1; }
  fi
  mkdir -p "${STATE_DIR:?}"; log="$STATE_DIR/zrok-$port.log"
  if ! zrok_enabled; then
    token="$(env_get ZROK_ENABLE_TOKEN)"
    if [ -z "$token" ]; then
      say "zrok2 is installed but this machine is not enabled. Set ZROK_ENABLE_TOKEN in .env or run: zrok2 enable YOUR_TOKEN"
      return 1
    fi
    say "Enabling zrok2 on this machine"
    if ! "$bin" enable "$token" > "$STATE_DIR/zrok-enable.log" 2>&1 < /dev/null; then
      say "zrok2 enable failed:"; tail -n 5 "$STATE_DIR/zrok-enable.log" >&2; return 1
    fi
  fi
  say "Starting a zrok2 tunnel to port $port"
  # shellcheck disable=SC2046
  nohup "$bin" share public --headless $(env_get ZROK_SHARE_ARGS) "$port" > "$log" 2>&1 < /dev/null &
  pid=$!
  write_state "$port.pid" "$pid"
  limit="$(wait_seconds)"; waited=0
  while [ "$waited" -lt "$limit" ]; do
    url="$(zrok_url_from "$log")"
    if [ -n "$url" ]; then write_state "$port.url" "$url"; printf '%s\n' "$url"; return 0; fi
    kill -0 "$pid" 2>/dev/null || { say "zrok2 stopped. Its output:"; tail -n 8 "$log" | strip_colour >&2; return 1; }
    sleep 1; waited=$((waited + 1))
  done
  say "zrok2 did not show an address within ${limit}s. Its output so far:"; tail -n 8 "$log" | strip_colour >&2
  return 1
}

ngrok_api() { printf '%s' "${NGROK_API:-http://127.0.0.1:4040}"; }

# the https address of the running ngrok whose local side is this port ("localhost:8084", "http://localhost:8084")
ngrok_url_for_port() {
  local port="$1" json
  json="$(curl -fsS --max-time 2 "$(ngrok_api)/api/tunnels" 2>/dev/null)" || return 0
  printf '%s' "$json" | sed -E 's/"name": *"/\n"name":"/g' | grep -E "\"addr\": *\"[^\"]*:${port}\"" | grep -Eo '"public_url": *"https://[^"]+"' | head -n1 \
    | sed -E 's/.*"public_url": *"//; s/"$//'
  return 0
}

start_ngrok() {
  local port="$1" bin log pid url waited limit token
  # reuse a tunnel that is already running for this port: ngrok's free plan allows only one agent
  url="$(ngrok_url_for_port "$port")"
  if [ -n "$url" ]; then
    say "ngrok is already tunnelling port $port, using it"
    write_state "$port.url" "$url"; printf '%s\n' "$url"; return 0
  fi
  bin="$(ngrok_bin)" || bin=""
  if [ -z "$bin" ]; then
    install_ngrok || return 1
    bin="$(ngrok_bin)" || { say "ngrok is still not available."; return 1; }
  fi
  token="$(env_get NGROK_AUTHTOKEN)"
  if ! ngrok_configured && [ -z "$token" ]; then
    say "ngrok has no authtoken. Set NGROK_AUTHTOKEN in .env or run: ngrok config add-authtoken YOUR_TOKEN"
    return 1
  fi
  if [ -n "$token" ]; then export NGROK_AUTHTOKEN="$token"; fi
  mkdir -p "${STATE_DIR:?}"; log="$STATE_DIR/ngrok-$port.log"
  say "Starting an ngrok tunnel to port $port"
  nohup "$bin" http "$port" --log=stdout > "$log" 2>&1 < /dev/null &
  pid=$!
  write_state "$port.pid" "$pid"
  limit="$(wait_seconds)"; waited=0
  while [ "$waited" -lt "$limit" ]; do
    url="$(ngrok_url_for_port "$port")"
    if [ -n "$url" ]; then write_state "$port.url" "$url"; printf '%s\n' "$url"; return 0; fi
    kill -0 "$pid" 2>/dev/null || { say "ngrok stopped. Its output:"; tail -n 8 "$log" >&2; return 1; }
    sleep 1; waited=$((waited + 1))
  done
  say "ngrok did not show an address within ${limit}s. Its output so far:"; tail -n 8 "$log" >&2
  return 1
}

start_tunnel() {
  decide
  local port url
  port="$(env_get SERVER_PORT)"; port="${port:-8084}"
  case "$ACTION" in
    no-credentials) say "$REASON"; guidance; return 0 ;;
    start) ;;
    *) say "$REASON"; return 0 ;;
  esac
  say "$REASON"
  if [ "$PROVIDER" = zrok ]; then url="$(start_zrok "$port")"; else url="$(start_ngrok "$port")"; fi
  if [ -z "$url" ]; then
    say "No tunnel address, starting without one. STK answers will be fetched from Safaricom about every minute instead."
    return 0
  fi
  say "Tunnel ready: $url"
  say "STK Push answers will go to $url/daraja/business/... Check it with $url/daraja/ping once the service is up."
  printf '%s\n' "$url"
}

stop_tunnel() {
  local port pid
  port="$(env_get SERVER_PORT)"; port="${port:-8084}"
  if [ -f "${STATE_DIR:?}/${port:?}.pid" ]; then
    pid="$(cat "${STATE_DIR:?}/${port:?}.pid")"
    kill "$pid" 2>/dev/null && say "Tunnel stopped"
    rm -f "${STATE_DIR:?}/${port:?}.pid" "${STATE_DIR:?}/${port:?}.url"
  fi
}

case "${1:-plan}" in
  plan) decide; printf 'ACTION=%s\nPROVIDER=%s\nREASON=%s\n' "$ACTION" "$PROVIDER" "$REASON" ;;
  start) start_tunnel ;;
  stop) stop_tunnel ;;
  *) echo "Usage: $0 plan|start|stop" >&2; exit 2 ;;
esac
