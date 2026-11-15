# STK Push callbacks

Safaricom tells agentx-api how an STK Push ended (paid, cancelled, wrong PIN, no answer) by calling an address on this service.

- **Set the address.** `AGENTX_STK_CALLBACK_URL` in `agentx-api/.env`, a public https address such as `https://your-tunnel.ngrok-free.app`. Address only: a short path and a secret token for each request are added for you, so Safaricom is given `ADDRESS/stk-callback/ID/TOKEN`. Blank uses `AGENTX_PUBLIC_BASE_URL`. Only STK Push uses it. Results and queue timeouts have their own settings (below), and the links in the chat use `AGENTX_PUBLIC_BASE_URL`.
- **Check it.** Open `ADDRESS/daraja/ping` in a browser. It shows `{"status":"ok"}` when the address leads to agentx-api. At startup the log also says where STK answers go, and warns if the address is localhost, a private network address or plain http.
- **Safe by design.** Each request has its own secret token in the path. A call with a wrong token is ignored, and a repeated answer is ignored.
- **If the address cannot be reached.** The prompt still arrives on the phone. About every minute, once a push is two minutes old, the service asks Safaricom how it ended, so a payment is not lost. It just shows in the chat later.
- **What you see in the chat.** Paid, with the M-Pesa code. Or not paid, with the reason: cancelled, wrong PIN, not enough balance, no answer, expired.

## Results and queue timeouts (B2C, B2B, Pochi, balance, status, reversal)

These requests are accepted at once and finished later, so Safaricom posts the final answer to two addresses that the request carries.

- **Result address.** `AGENTX_RESULT_URL` in `agentx-api/.env` (or `agentx.initiator.result-url` in `application.yml`). Safaricom posts the final outcome here: done with the receipt, or failed with the reason.
- **Timeout address.** `AGENTX_TIMEOUT_URL` (or `agentx.initiator.timeout-url`). Safaricom posts here when the request waited too long in its queue. That means the outcome is not known yet, not that it failed, so the chat says it is still being checked.
- **Two ways to write each one.**
  - An address, `https://hooks.example.com`. A short path and the secret token of each request are added for you, which is how every answer is matched to its request: `ADDRESS/results/ID/TOKEN` for results and `ADDRESS/timeout/ID/TOKEN` for timeouts.
  - A template with `{id}` and `{token}`, `https://hooks.example.com/results/{id}/{token}`. It is used as written with the two values filled in, and `{kind}` becomes `results`, `timeout` or `stk-callback`. A template must contain both `{id}` and `{token}`, and its path has to be one that agentx-api listens on (`/results`, `/timeout`, `/stk-callback`, each followed by `/ID/TOKEN`) unless you run your own relay in front of it. The older `/daraja/business/ID/TOKEN/result|timeout|callback` addresses still work, so a request made earlier is still answered.
  - Do not put words like `mpesa` or `safaricom` in any path. Daraja refuses those addresses.
- **Left blank.** The public address of the service (`AGENTX_PUBLIC_BASE_URL`) is used, or the development tunnel when one is open.
- **The address has to reach agentx-api.** If an address points somewhere else, agentx-api never hears the answer and cannot tell the chat what happened. The startup log shows where results and timeouts go, and warns when an address is localhost, a private network address or plain http.
