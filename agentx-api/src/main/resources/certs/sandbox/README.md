<div align="center">

<img src="../../../../../../docs/assets/banner-certs.svg" alt="" width="100%"/>

<br/>

<img src="../../../../../../docs/assets/badges-certs.svg" alt="Sandbox and production folders, public certificate only, never a private key, other location with DARAJA_CERTS_DIR." width="880"/>

<br/><br/>

Put the sandbox certificate in this folder.

<sub><a href="../../../../../README.md">Back to agentx-api</a></sub>

<img src="../../../../../../docs/assets/divider.svg" alt="" width="100%"/>

</div>

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
