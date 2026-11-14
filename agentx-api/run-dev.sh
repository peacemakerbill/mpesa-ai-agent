#!/usr/bin/env bash
# Starts agentx-api for development. If nothing public is configured for Safaricom's STK Push answers, it first opens a
# zrok2 or ngrok tunnel (see tools/dev-tunnel.sh and docs/DEV_TUNNEL.md), and closes it again when the service stops.
#   ./run-dev.sh            start with a tunnel when one is needed
#   ./run-dev.sh -X         extra arguments go to Maven
cd "$(dirname "$0")" || exit 1

url="$(bash tools/dev-tunnel.sh start | tail -n1)"
trap 'bash tools/dev-tunnel.sh stop' EXIT INT TERM
if [ -n "$url" ]; then
  export AGENTX_STK_CALLBACK_URL="$url"
fi

if [ -x ./mvnw ]; then ./mvnw spring-boot:run "$@"; else mvn spring-boot:run "$@"; fi
