# Automatic tunnel for STK Push answers (development)

Safaricom answers an STK Push by calling a public https address. On your own machine there is none, so agentx-api opens one for you.
It works the same on Linux, macOS and Windows, because it is part of the service itself: no script, no sudo.

Start the service the way you always do (`mvn spring-boot:run`, or Run in your IDE). Once it is up, it looks at your settings and does the first thing that fits:

| Situation | What happens |
|---|---|
| `APP_ENV` is not `development` | Nothing. A tunnel is never opened for staging or production |
| `DEV_TUNNEL=off` | Nothing |
| `AGENTX_STK_CALLBACK_URL` is set | Nothing, your address is used |
| `AGENTX_PUBLIC_BASE_URL` is already a public https address | Nothing, STK answers use it |
| A zrok2 or ngrok account token is set | A tunnel to this service is opened in the background, its address is used for STK answers, and it is closed when the service stops |
| No token | A warning in the log saying what to add. The prompt still reaches the phone and the result is fetched from Safaricom about every minute |

The log says what happened: `Dev tunnel ready: https://...`, or why not.

## Set it up once

Add one of these to `agentx-api/.env`:

```
ZROK_ENABLE_TOKEN=your zrok account token
NGROK_AUTHTOKEN=your ngrok authtoken
```

Both services need a free account first. The token lets the service enable zrok2 on this machine, or start ngrok, without asking you.
If you already ran `zrok2 enable` or `ngrok config add-authtoken` yourself, no token is needed. With both set up, zrok2 is tried first, or the one already installed. `DEV_TUNNEL_PROVIDER=ngrok` (or `zrok`) forces one.

## What it installs

If zrok2 or ngrok is not on your PATH and you gave a token for it, the program is downloaded into `.agentx/bin` in your home folder (`C:\Users\you\.agentx\bin` on Windows). Nothing is installed anywhere else, and no administrator rights are needed.
Set `DEV_TUNNEL_INSTALL=false` to stop any download. If a download fails, the log says so and shows how to install the program yourself.

| Setting | Meaning |
|---|---|
| `DEV_TUNNEL` | `auto` (default) or `off` |
| `DEV_TUNNEL_PROVIDER` | `auto` (default), `zrok` or `ngrok` |
| `DEV_TUNNEL_INSTALL` | `true` (default) or `false` |
| `DEV_TUNNEL_WAIT` | Seconds to wait for the tunnel address, default 40 |
| `ZROK_ENABLE_TOKEN` | zrok account token, used once to enable this machine |
| `ZROK2_VERSION` | Pin the zrok2 version to download, for example `v2.0.0`. Blank means the latest version 2 release |
| `ZROK_SHARE_ARGS` | Extra arguments for `zrok2 share public` |
| `NGROK_AUTHTOKEN` | ngrok authtoken |

## How it behaves

- The tunnel points at the port this service runs on (`SERVER_PORT`, default 8084). Only STK Push answers use it.
- The service never waits for the tunnel. Until its address is known, STK answers fall back to the slower fetch from Safaricom.
- If ngrok is already tunnelling that port, it is reused. ngrok's free plan allows only one running agent.
- If the tunnel cannot start, the service still runs and the log says why.
- Open `ADDRESS/daraja/ping` in a browser to confirm the address reaches agentx-api.
- Integration tests never open a tunnel.

## What is checked and what is not

The decisions, the download and unpacking (both `.tar.gz` and `.zip`), the Windows `.exe` names, and the tunnel start with stand-in programs are covered by tests in `src/test/java/com/darajaagentx/api/devtunnel`.
What those tests cannot know is what the real zrok2 and ngrok print and where their downloads live today, so the first run against the real ones is the real test. If the address is not found, the log shows the program's own last lines.
The macOS and Windows download names are written from the pattern of the Linux ones and have not been tried.
