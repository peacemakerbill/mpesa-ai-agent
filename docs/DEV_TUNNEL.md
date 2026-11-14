# Automatic tunnel for STK Push answers (development)

Safaricom answers an STK Push by calling a public https address. On your own machine there is none, so this starts one for you.

```
cd agentx-api
./run-dev.sh
```

`run-dev.sh` is `mvn spring-boot:run` with one extra step in front. It looks at your settings and does the first thing that fits:

| Situation | What happens |
|---|---|
| `APP_ENV` is not `development` | Nothing. A tunnel is never opened for staging or production |
| `DEV_TUNNEL=off` | Nothing |
| `AGENTX_STK_CALLBACK_URL` is set (environment or `.env`) | Nothing, your address is used |
| `AGENTX_PUBLIC_BASE_URL` is already a public https address | Nothing, STK answers use it |
| A zrok2 or ngrok account is set up | A tunnel to this service is started, its address is used for STK answers, and it is closed when the service stops |
| No account is set up | It tells you what to add and starts the service anyway. The prompt still reaches the phone and the result is fetched from Safaricom about every minute |

`tools/dev-tunnel.sh plan` shows which of these applies without starting anything.

## Set it up once

Add one of these to `agentx-api/.env`:

```
ZROK_ENABLE_TOKEN=your zrok account token
NGROK_AUTHTOKEN=your ngrok authtoken
```

Both services need a free account first. The token is what lets the script enable zrok2 on this machine or start ngrok without asking you.
With both set, zrok2 is tried first. `DEV_TUNNEL_PROVIDER=ngrok` (or `zrok`) forces one.

## What it installs

If zrok2 or ngrok is missing and you gave a token for it, the program is downloaded into `~/.agentx/bin`. No sudo and nothing outside your home folder.
Set `DEV_TUNNEL_INSTALL=false` to stop it from downloading anything. If a download fails it says so and shows the official install command for you to run yourself.

| Setting | Meaning |
|---|---|
| `DEV_TUNNEL` | `auto` (default) or `off` |
| `DEV_TUNNEL_PROVIDER` | `auto` (default), `zrok` or `ngrok` |
| `DEV_TUNNEL_INSTALL` | `true` (default) or `false` |
| `DEV_TUNNEL_WAIT` | Seconds to wait for the tunnel address, default 40 |
| `ZROK_ENABLE_TOKEN` | zrok account token, used once to enable this machine |
| `ZROK2_VERSION` | Pin the zrok2 version to download, for example `v2.0.0` |
| `ZROK_SHARE_ARGS` | Extra arguments for `zrok2 share public` |
| `NGROK_AUTHTOKEN` | ngrok authtoken |

## How it behaves

- The tunnel points at `SERVER_PORT` (default 8084). Only STK Push answers use it, through `AGENTX_STK_CALLBACK_URL`.
- If ngrok is already tunnelling that port, it is reused. ngrok's free plan allows only one running agent.
- If the tunnel cannot start, the service still starts, with a message saying why.
- Open `ADDRESS/daraja/ping` in a browser to confirm the address reaches agentx-api.
- Windows: not supported by the script. Start a tunnel yourself and set `AGENTX_STK_CALLBACK_URL`.

## Checking the script

`bash tools/dev-tunnel-test.sh` runs 29 checks of the decisions, the install step and both providers using stand-in programs. It needs no network and no account.
It cannot check what the real zrok2 and ngrok print, so the first run against the real ones is the real test. If zrok2 starts but no address is found, the script shows zrok2's own output.
