# Safaricom sandbox public certificate

Put the **sandbox** certificate here, for example `SandboxCertificate.cer`.

Get it from the Daraja portal (developer.safaricom.co.ke), in the documentation for the Security Credential.
It is a public certificate, so it is safe to commit to Git. It must be the certificate Safaricom gives out,
never a private key.

agentx-api uses it to turn a customer's initiator password into the SecurityCredential for sandbox calls
(B2C, B2B, account balance, transaction status, reversal). Any file ending in `.cer`, `.crt`, `.pem` or `.der`
in this folder is accepted. If there are several, the first by name is used.

To keep the certificates outside the application instead, set `DARAJA_CERTS_DIR` to a folder that has
`sandbox/` and `production/` sub folders.
