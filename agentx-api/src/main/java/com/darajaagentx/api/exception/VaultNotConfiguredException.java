package com.darajaagentx.api.exception;

/** The server has no AGENTX_VAULT_KEY, so it cannot lock or unlock business credentials. */
@SuppressWarnings("serial")
public class VaultNotConfiguredException extends RuntimeException {

    public VaultNotConfiguredException() {
        super("Saving business credentials is not switched on yet. The administrator needs to set AGENTX_VAULT_KEY on the server.");
    }
}
