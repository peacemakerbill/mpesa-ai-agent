#!/usr/bin/env bash
# Makes .env from .env.example and fills in the secrets that must be random.
# Run it once, from this folder:  bash init-env.sh
set -eu
cd "$(dirname "$0")"

if [ -f .env ]; then
  echo "STOP: .env already exists. Delete it first if you really want new secrets (that would sign everyone out and lose saved business credentials)." >&2
  exit 1
fi
command -v openssl >/dev/null || { echo "STOP: openssl is not installed." >&2; exit 1; }

cp .env.example .env
chmod 600 .env
set_value() { sed -i "s|^$1=.*|$1=$2|" .env; }

set_value INTERNAL_KEY "$(openssl rand -hex 32)"
set_value AGENTX_TOKEN_SECRET "$(openssl rand -hex 32)"
set_value REDIS_PASSWORD "$(openssl rand -hex 24)"
set_value RABBITMQ_PASSWORD "$(openssl rand -hex 24)"
set_value AGENTX_VAULT_KEY "$(openssl rand -base64 32)"

echo "Made .env with new secrets."
echo ""
echo "Now open it and fill in DOMAIN, MONGODB_URI and ANTHROPIC_API_KEY:"
echo "  nano .env"
echo ""
echo "Copy this line to a safe place outside this server (a password manager). Without it, saved business credentials cannot be opened after a rebuild:"
grep '^AGENTX_VAULT_KEY=' .env
