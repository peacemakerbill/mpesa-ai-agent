# STK Push callbacks

Safaricom tells agentx-api how an STK Push ended (paid, cancelled, wrong PIN, no answer) by calling an address on this service.

- **Set the address.** `AGENTX_STK_CALLBACK_URL` in `agentx-api/.env`, a public https address such as `https://your-tunnel.ngrok-free.app`. Address only: the path and a secret token for each request are added for you. Blank uses `AGENTX_PUBLIC_BASE_URL`. Only STK Push uses it. Balance, B2C and the other requests, and the links in the chat, keep using `AGENTX_PUBLIC_BASE_URL`.
- **Check it.** Open `ADDRESS/daraja/ping` in a browser. It shows `{"status":"ok"}` when the address leads to agentx-api. At startup the log also says where STK answers go, and warns if the address is localhost, a private network address or plain http.
- **Safe by design.** Each request has its own secret token in the path. A call with a wrong token is ignored, and a repeated answer is ignored.
- **If the address cannot be reached.** The prompt still arrives on the phone. About every minute, once a push is two minutes old, the service asks Safaricom how it ended, so a payment is not lost. It just shows in the chat later.
- **What you see in the chat.** Paid, with the M-Pesa code. Or not paid, with the reason: cancelled, wrong PIN, not enough balance, no answer, expired.
