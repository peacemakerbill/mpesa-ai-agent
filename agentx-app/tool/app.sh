#!/usr/bin/env bash
# Runs or builds the app with the settings from env/<name>.json, so the server address is never typed again.
#
#   tool/app.sh staging run [flutter args]
#   tool/app.sh production build appbundle [flutter args]
#   tool/app.sh staging show
#   tool/app.sh staging set API_BASE_URL https://example.com
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$HERE"

stop() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
usage() {
  cat >&2 <<'USAGE'
Usage:
  tool/app.sh <env> run [flutter args]
  tool/app.sh <env> build <apk|appbundle|ipa|linux|windows|macos|web> [flutter args]
  tool/app.sh <env> show
  tool/app.sh <env> set <KEY> <value>
<env> is the name of a file in env/, for example staging or production.
USAGE
  exit 2
}

[ $# -ge 2 ] || usage
ENV_NAME="$1"; CMD="$2"; shift 2
case "$ENV_NAME" in *[!A-Za-z0-9_-]*|"") stop "the environment name may only hold letters, digits, - and _" ;; esac
FILE="env/$ENV_NAME.json"
[ -f "$FILE" ] || stop "$FILE does not exist. The files in env/ are: $(ls env/*.json 2>/dev/null | sed 's|env/||; s|\.json||' | tr '\n' ' ')"
command -v python3 >/dev/null || stop "python3 is needed to read $FILE."

# Checks the file and prints nothing when it is fine.
check() {
  python3 - "$FILE" "$ENV_NAME" <<'PY'
import json, sys
path, name = sys.argv[1:3]
try:
    d = json.load(open(path, encoding="utf-8"))
except ValueError as e:
    sys.exit("%s is not valid JSON: %s" % (path, e))
if not isinstance(d, dict):
    sys.exit("%s must hold one JSON object" % path)
url = d.get("API_BASE_URL", "")
if not isinstance(url, str) or not url:
    sys.exit("%s has no API_BASE_URL" % path)
bad = [k for k, v in d.items() if isinstance(v, str) and ("YOUR_HOST" in v or "YOURINITIALS" in v)]
if bad:
    sys.exit("%s still holds a placeholder in %s. Set it with: tool/app.sh %s set API_BASE_URL <address>" % (path, ", ".join(bad), name))
if not url.startswith(("http://", "https://")):
    sys.exit("API_BASE_URL must start with https:// (or http:// for a local test)")
if url.endswith("/"):
    sys.exit("API_BASE_URL must not end with a slash")
if name == "production" and not url.startswith("https://"):
    sys.exit("a production build needs an https:// address")
PY
}

case "$CMD" in
  show)
    cat "$FILE"
    ;;
  set)
    [ $# -eq 2 ] || usage
    python3 - "$FILE" "$1" "$2" <<'PY'
import json, sys
path, key, value = sys.argv[1:4]
d = json.load(open(path, encoding="utf-8"))
d[key] = value
open(path, "w", encoding="utf-8").write(json.dumps(d, indent=2, ensure_ascii=False) + "\n")
print("set %s in %s" % (key, path))
PY
    case "$ENV_NAME" in local) ;; *) echo "Commit the change: git add $FILE" ;; esac
    ;;
  run)
    check || stop "fix $FILE first"
    exec flutter run --dart-define-from-file="$FILE" "$@"
    ;;
  build)
    [ $# -ge 1 ] || usage
    check || stop "fix $FILE first"
    TARGET="$1"; shift
    exec flutter build "$TARGET" --dart-define-from-file="$FILE" "$@"
    ;;
  *) usage ;;
esac
