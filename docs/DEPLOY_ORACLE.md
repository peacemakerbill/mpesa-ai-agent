<div align="center">

<img src="assets/banner-deploy-oracle.svg" alt="" width="100%"/>

<br/>

<img src="assets/badges-deploy-oracle.svg" alt="Oracle Cloud Always Free, Docker with one Compose file, Ubuntu 24.04 on Arm, Caddy with automatic https, MongoDB Atlas, RabbitMQ on the server." width="880"/>

<br/><br/>

Run the whole system on one free Oracle Cloud server that stays awake around the clock.

<sub><a href="../README.md">Back to the project overview</a> &nbsp;|&nbsp; <a href="DEPLOY_RENDER.md">Deploy on Render instead</a> &nbsp;|&nbsp; <a href="ENVIRONMENTS.md">Environments</a></sub>

<img src="assets/divider.svg" alt="" width="100%"/>

</div>

<div align="center">
<img src="assets/oracle-steps.svg" alt="The eight steps: Oracle account, MongoDB Atlas, the server, a fixed address, a domain name, prepare the server, settings, start and check." width="100%"/>
</div>

# Deploying on Oracle Cloud (Always Free)

This guide runs Daraja Agent-X on one free virtual machine in Oracle Cloud. Everything stays awake around the clock, so Safaricom callbacks and chat messages are never lost to a sleeping service.

It uses the files in `deploy/oracle/`:

| File | What it does |
|---|---|
| `docker-compose.yml` | Runs agentx-api, agent-service, Redis, RabbitMQ and Caddy |
| `Caddyfile` | Gives the site https by itself and hides the internal endpoints |
| `.env.example` | The settings. `init-env.sh` turns it into your `.env` |
| `init-env.sh` | Makes `.env` and generates every secret that must be random |
| `setup-vm.sh` | Installs Docker and opens ports 80 and 443 on the server |

MongoDB stays on MongoDB Atlas (free). RabbitMQ and Redis run on the same machine, so you do not need CloudAMQP or Render.

Allow two to three hours the first time. Most of it is creating accounts and waiting for the first build.

## What you will end up with

```
Internet, https on port 443
   Caddy (certificate and proxy)
      agentx-api (public API)
         agent-service (chat and the model), RabbitMQ, Redis, all on the private network of this machine
         MongoDB Atlas (outside)
```

Only Caddy is reachable from the internet. Ports 80 and 443 are the only ones you open, besides 22 for your own SSH. The `/internal` endpoints are answered with 404 by Caddy as well, even though they also need the internal key.

Cost: nothing for Oracle or Atlas while you stay inside the free limits. You pay Anthropic for what the chat model uses. A card is needed to open the Oracle account.

## Step 1. The Oracle account

1. Sign up at [oracle.com/cloud/free](https://www.oracle.com/cloud/free/). Oracle asks for a card and a phone number to verify you. A small temporary hold may appear and is released.
2. Choose your **home region** with care, it cannot be changed later. Johannesburg is the closest to Kenya, and Frankfurt is a common alternative. Free ARM machines are sometimes unavailable in a region ("out of capacity"), so this is a bit of luck.
3. Wait for the account to be ready. This can take a few minutes up to a day.

## Step 2. MongoDB Atlas

1. Sign in at [cloud.mongodb.com](https://cloud.mongodb.com), create a project and a free M0 cluster.
2. Under Database Access, add a user with a generated password (letters and digits are simplest).
3. Under Network Access, allow `0.0.0.0/0` for now. After the server exists you can narrow it to the server's public IP address.
4. Press Connect, then Drivers, and copy the connection string. Change it so it has the database name and a fast failure setting:

```
mongodb+srv://USER:PASSWORD@cluster0.xxxxx.mongodb.net/agentx?retryWrites=true&w=majority&serverSelectionTimeoutMS=5000
```

Keep it for step 9. M0 has 512 MB of storage and no automatic backups, see Backups below.

## Step 3. Create the server

1. On your own computer, make an SSH key if you do not have one:

```
ssh-keygen -t ed25519 -f ~/.ssh/oracle_agentx
```

Keep the private file safe. You will paste the contents of `~/.ssh/oracle_agentx.pub` into Oracle.

2. In the Oracle console open Compute, then Instances, and press Create instance.
3. Name it `agentx`.
4. Image: Canonical Ubuntu 24.04, the Arm (aarch64) build.
5. Shape: change the shape to Ampere, `VM.Standard.A1.Flex`. Use 2 OCPUs and 12 GB of memory, or the largest size the Always Free limit allows for your account. These services need about 6 GB in total.
6. Networking: leave the defaults, and make sure a public IPv4 address is assigned.
7. Add SSH keys: paste your public key.
8. Boot volume: 50 GB is plenty.
9. Press Create.

If Oracle says "Out of capacity for shape VM.Standard.A1.Flex", try another availability domain if the region has one, try fewer OCPUs, or try again later (early morning is often better). It is common and usually passes.

## Step 4. A fixed public IP address

An address that changes would break your domain name.

1. Open Networking, then IP management, then Reserved public IPs, and create one.
2. Open the instance, then Attached VNICs, then the VNIC, then IPv4 addresses, edit the address, and choose the reserved IP. If the console only offers No public IP first, choose that and save, then edit again and select your reserved IP.

Note the address. The rest of this guide calls it `SERVER_IP`.

## Step 5. Open ports 80 and 443 in Oracle's network

Oracle blocks everything except SSH until you allow it.

1. Open the instance, then the subnet, then its Default Security List.
2. Add two ingress rules: source `0.0.0.0/0`, protocol TCP, destination port `80`, and the same again for port `443`.

The server's own firewall is opened in step 7.

## Step 6. A domain name

https needs a name, not a bare IP address.

**If you own a domain:** add an A record, for example `api.yourcompany.co.ke`, pointing to `SERVER_IP`.

**If you do not:** use a free name from [duckdns.org](https://www.duckdns.org). Sign in, pick a name such as `agentx-bill`, and set its current IP to `SERVER_IP`. Your address is then `agentx-bill.duckdns.org`.

Check it from your computer. It must print `SERVER_IP`:

```
dig +short agentx-bill.duckdns.org
```

DNS can take a few minutes to spread. Do not start the system before it answers correctly, because the certificate request would fail.

## Step 7. Prepare the server

Log in:

```
ssh -i ~/.ssh/oracle_agentx ubuntu@SERVER_IP
```

Get the code. If the repository is private, make a read-only deploy key for it in GitHub, or use a personal access token in the URL.

```
sudo apt-get update && sudo apt-get install -y git
git clone https://github.com/peacemakerbill/mpesa-ai-agent.git
cd mpesa-ai-agent/deploy/oracle
bash setup-vm.sh
```

`setup-vm.sh` installs Docker and opens ports 80 and 443 in the server's firewall. When it finishes, log out and log in again, so that `docker` works without `sudo`:

```
exit
ssh -i ~/.ssh/oracle_agentx ubuntu@SERVER_IP
```

## Step 8. Make the settings

```
cd mpesa-ai-agent/deploy/oracle
bash init-env.sh
nano .env
```

`init-env.sh` writes `.env` with new random secrets. It prints the vault key at the end. Copy that line to a password manager now. It locks the business credentials people save, and if it is lost those can never be opened again.

In `.env` fill in the three things it could not guess:

| Setting | Value |
|---|---|
| `DOMAIN` | Your name from step 6, without `https://` |
| `MONGODB_URI` | The Atlas string from step 2 |
| `ANTHROPIC_API_KEY` | Your Anthropic key |

Leave `CODE_DELIVERY=log` and `MAIL_TRANSPORT=log` while you test. You change them in step 11.

## Step 9. Start it

```
docker compose up -d --build
```

The first build takes ten to fifteen minutes, because Maven downloads the dependencies and builds both services. Watch it:

```
docker compose logs -f agentx-api
```

A good start shows `Environment: production` and `Started AgentXApiApplication`. Stop watching with Ctrl+C, the services keep running. Check the other one:

```
docker compose logs agent-service | tail -20
docker compose ps
```

`docker compose ps` should show every service as running, and Redis and RabbitMQ as healthy.

If agentx-api stops with `This cannot start as production`, the log lists every setting it refuses. Fix them in `.env` and run `docker compose up -d` again.

## Step 10. Check it from outside

On your own computer:

```
curl -s https://agentx-bill.duckdns.org/actuator/health
curl -s https://agentx-bill.duckdns.org/api/config
```

The first answers `{"status":"UP"}`. The second shows JSON with `"environment":"production"`. The first request can take a few seconds while Caddy gets the certificate.

Check that the internal endpoints are closed:

```
curl -s -o /dev/null -w "%{http_code}\n" https://agentx-bill.duckdns.org/internal/files/chat-media/x
```

This must print `404`.

Do not use the browser test console against production. Production refuses `CORS_ALLOWED_ORIGINS` on purpose. Test with the phone app:

```
flutter run --dart-define=API_BASE_URL=https://agentx-bill.duckdns.org
```

With `CODE_DELIVERY=log`, the sign-in code is written to the log. Request a code in the app, then read it on the server:

```
docker compose logs --tail 50 agentx-api
```

## Step 11. Before real customers use it

Edit `.env`, then run `docker compose up -d` to apply it.

**Sign-in codes by SMS.** With `log`, customers never receive a code.

| Setting | Value |
|---|---|
| `CODE_DELIVERY` | `sms` |
| `SMS_USERNAME` | Your Africa's Talking username (not `sandbox`) |
| `SMS_API_KEY` | A new live key |
| `SMS_SENDER_ID` | Your approved sender name, or empty |
| `SMS_BASE_URL` | `https://api.africastalking.com` |

**Email.** Set `MAIL_TRANSPORT=smtp` and fill in `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` and `MAIL_FROM_NAME`. Oracle may block outbound port 25, and ports 587 and 465 are normally open. For Gmail use an app password. See `docs/PROFILE_AND_EMAIL.md` for every mail setting.

Safaricom posts its answers to `https://YOUR_DOMAIN/daraja/business/...`, which works as soon as the domain is live.

## Day to day

| Task | Command, run in `mpesa-ai-agent/deploy/oracle` |
|---|---|
| Update to the latest code | `git pull && docker compose up -d --build` |
| See what is running | `docker compose ps` |
| Read logs | `docker compose logs --tail 100 agentx-api` (or `agent-service`, `caddy`) |
| Restart one service | `docker compose restart agent-service` |
| Stop everything | `docker compose down` (data stays) |
| Free disk space after many updates | `docker image prune -f` |

All services restart by themselves after a crash or a reboot of the server.

## Backups

Three things must never exist only on this server:

1. **The vault key.** It is in your password manager from step 8.
2. **The `.env` file.** Copy it to a safe place. It holds every secret.
3. **The database.** Atlas M0 has no automatic backups. Take a copy now and then from the server, in `mpesa-ai-agent/deploy/oracle`:

```
mkdir -p ~/backups
docker run --rm -v ~/backups:/backups mongo:7 sh -c 'mongodump --uri "$0" --archive=/backups/agentx-$(date +%F).gz --gzip' "$(grep '^MONGODB_URI=' .env | cut -d= -f2-)"
```

Copy the file in `~/backups` to your own computer. To make it automatic, put the command in `crontab -e` once a week, or move to a paid Atlas tier that has backups.

## Keeping the free server

- Oracle can reclaim an Always Free machine that sits idle for a long time. Check Oracle's current policy on idle instances. Many people avoid the risk by upgrading the account to Pay As You Go, where you still pay nothing inside the Always Free limits. If you do that, set a budget alert in Oracle so a mistake cannot cost you.
- Do not create extra paid resources in the account by accident. Everything in this guide stays within the free limits.
- Keep an eye on disk space (`df -h`). Docker images from many updates can fill it. `docker image prune -f` clears the old ones.

## When something goes wrong

| What you see | Likely cause and fix |
|---|---|
| The certificate is not issued, the site does not open | The domain does not point at `SERVER_IP` yet, or ports 80 and 443 are closed in Oracle's security list or in the server firewall. Check with `dig` and with `docker compose logs caddy` |
| `This cannot start as production` in the agentx-api log | Read the list and fix `.env`. The usual one is a wrong `DOMAIN` |
| agentx-api cannot reach MongoDB | Atlas Network Access does not allow the server's IP, or the user, password or database name in `MONGODB_URI` is wrong |
| agent-service starts but replies are empty | `ANTHROPIC_API_KEY` is missing or wrong. The log says so at start |
| Build fails with an out of memory error | The server has too little memory. Use more OCPUs and memory in the shape, or build with `docker compose build agentx-api` and then `docker compose build agent-service`, one at a time |
| Links in the chat open the wrong address | `DOMAIN` in `.env` is wrong. Fix it and run `docker compose up -d` |
| Customers get no sign-in code | `CODE_DELIVERY` is still `log`, or the Africa's Talking values are wrong |
| Business accounts will not save | `AGENTX_VAULT_KEY` is empty in `.env` |
| `permission denied` on docker | You did not log out and in again after step 7 |

## Security checklist

- Only ports 22, 80 and 443 are open. Port 22 can be narrowed to your own IP address in Oracle's security list.
- Log in with the SSH key only. Do not set a password for the `ubuntu` user.
- `.env` is not in Git and has mode 600. Run `git status` before every commit.
- `APP_ENV` is `production`, and `CODE_DELIVERY` is `sms`, never `dev`, before customers use it.
- Keep the server updated now and then: `sudo apt-get update && sudo apt-get upgrade -y`, then `sudo reboot` when it asks.
- Test business accounts on the sandbox first. Production Daraja credentials belong only on the live server.

<div align="center">
<img src="assets/divider.svg" alt="" width="100%"/>
</div>
