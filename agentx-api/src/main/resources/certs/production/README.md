<div align="center">

<img src="../../../../../../docs/assets/banner-certs.svg" alt="" width="100%"/>

<br/>

<img src="../../../../../../docs/assets/badges-certs.svg" alt="Sandbox and production folders, public certificate only, never a private key, other location with DARAJA_CERTS_DIR." width="880"/>

<br/><br/>

Put the production certificate in this folder.

<sub><a href="../../../../../README.md">Back to agentx-api</a></sub>

<img src="../../../../../../docs/assets/divider.svg" alt="" width="100%"/>

</div>

# Safaricom production public certificate

Put the **production** certificate here, for example `ProductionCertificate.cer`.

Get it from the Daraja portal (developer.safaricom.co.ke), in the documentation for the Security Credential.
The production certificate is different from the sandbox one: a SecurityCredential made with the wrong one is
rejected by Safaricom. It is a public certificate, so it is safe to commit to Git. It must be the certificate
Safaricom gives out, never a private key.

Any file ending in `.cer`, `.crt`, `.pem` or `.der` in this folder is accepted. If there are several, the first by
name is used.
