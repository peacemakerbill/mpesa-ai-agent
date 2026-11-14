package com.darajaagentx.api.dto;

/**
 * What the phone app reads while services start. [api] is always "up" (this answer came from it). [agent] is "up" or
 * "waking". [ready] is true when the app can start working.
 */
public record WakeResponse(String api, String agent, boolean ready) {

    public static WakeResponse of(boolean agentAwake) {
        return new WakeResponse("up", agentAwake ? "up" : "waking", agentAwake);
    }
}
