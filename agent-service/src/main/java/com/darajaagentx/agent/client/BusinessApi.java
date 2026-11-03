package com.darajaagentx.agent.client;

import java.util.List;
import java.util.Map;

/**
 * What the assistant may ask agentx-api to do with a customer's business accounts. Answers are plain maps, the same fields as
 * agentx-api's JSON, so a new field never breaks the assistant. Secrets are never part of any answer.
 */
public interface BusinessApi {

    /** Names and abilities of the customer's business accounts. */
    List<Map<String, Object>> accounts(String phone);

    /** Saves a request. Reads and collections go straight to Safaricom, money out waits for approval in the app. */
    Map<String, Object> prepare(Map<String, Object> body);

    List<Map<String, Object>> operations(String phone, int limit);

    Map<String, Object> operation(String id, String phone);

    /** The customer changed their mind before approving. */
    Map<String, Object> cancel(String id, String phone);

    /** agentx-api answered with an error, or could not be reached (status 0). The message is safe to show. */
    @SuppressWarnings("serial")
	class BusinessApiException extends RuntimeException {
        private final int status;

        public BusinessApiException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
