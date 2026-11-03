package com.darajaagentx.api.exception;

/** Safaricom answered with an error, or the call never left this server. Nothing was done, so it is safe to try again. */
public class DarajaRefusedException extends RuntimeException {
    public DarajaRefusedException(String message) {
        super(message);
    }
}
