package com.darajaagentx.api.dto;

import java.time.Instant;

/**
 * devCode is only present when the server shows codes on screen (CODE_SHOW_IN_APP, development and staging only).
 * smsSent says whether a text message with the code was handed to the SMS provider, so the app can tell the person
 * to check their messages, or to read the code on screen.
 */
public record CodeRequestResponse(Instant expiresAt, String devCode, boolean smsSent) {

    public CodeRequestResponse(Instant expiresAt, String devCode) {
        this(expiresAt, devCode, false);
    }
}
