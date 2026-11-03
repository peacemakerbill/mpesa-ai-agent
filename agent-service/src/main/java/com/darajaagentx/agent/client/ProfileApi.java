package com.darajaagentx.agent.client;

import java.util.List;
import java.util.Map;

/**
 * What the assistant may ask agentx-api to do with the signed in customer's profile, saved email addresses and emailed
 * reports. Answers are plain maps with the same fields as agentx-api's JSON, so a new field never breaks the assistant.
 * The phone number always belongs to the customer who is chatting, never to someone the model named.
 */
public interface ProfileApi {

    Map<String, Object> profile(String phone);

    /** Only the fields present are changed. An empty text clears an optional field. */
    Map<String, Object> updateProfile(String phone, Map<String, Object> changes);

    /** Emails a 6 digit code to the address. The code itself is never part of the answer. */
    Map<String, Object> requestEmailCode(String phone, String email);

    Map<String, Object> verifyEmail(String phone, String code);

    List<Map<String, Object>> recipients(String phone);

    Map<String, Object> addRecipient(String phone, String email, String name);

    /** Several addresses found in pasted text or in the text of a file the customer attached. */
    Map<String, Object> addRecipients(String phone, String text);

    /** Removes by address or by the name the customer gave it. */
    Map<String, Object> removeRecipient(String phone, String target);

    /** Builds a PDF, CSV or Excel report and emails it. */
    Map<String, Object> sendReport(String phone, Map<String, Object> request);

    /** agentx-api answered with an error, or could not be reached (status 0). The message is safe to show. */
    class ProfileApiException extends RuntimeException {
        private final int status;

        public ProfileApiException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
