#!/usr/bin/env bash
# Prepares a fresh Ubuntu server (Oracle Cloud or any other): installs Docker and opens ports 80 and 443 in the server's own firewall.
# Run it once on the server, as your normal user:  bash setup-vm.sh
# You still have to open ports 80 and 443 in the cloud provider's network settings, see docs/DEPLOY_ORACLE.md.
set -eu

[ "$(id -u)" -ne 0 ] || { echo "STOP: run this as your normal user, not as root. It uses sudo where it needs to." >&2; exit 1; }
echo "Server type: $(uname -m)"

if ! command -v docker >/dev/null 2>&1; then
  echo "Installing Docker"
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"
sudo systemctl enable --now docker

echo "Opening ports 80 and 443 in the server firewall"
for port in 80 443; do
  if ! sudo iptables -C INPUT -m state --state NEW -p tcp --dport "$port" -j ACCEPT 2>/dev/null; then
    sudo iptables -I INPUT 1 -m state --state NEW -p tcp --dport "$port" -j ACCEPT
  fi
done
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y iptables-persistent >/dev/null
sudo netfilter-persistent save >/dev/null

echo ""
echo "Done. Log out and log in again so that the docker command works without sudo, then continue with the guide."
