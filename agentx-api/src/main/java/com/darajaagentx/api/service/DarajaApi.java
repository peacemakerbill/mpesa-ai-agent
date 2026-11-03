package com.darajaagentx.api.service;

import com.darajaagentx.api.model.DarajaEnvironment;

import java.util.Map;

/** Posts one JSON request to Daraja with the customer's own consumer key and secret. */
public interface DarajaApi {

    /**
     * @throws com.darajaagentx.api.exception.DarajaRefusedException        Safaricom refused it, or it never left. Safe to try again.
     * @throws com.darajaagentx.api.exception.DarajaOutcomeUnknownException it may have gone through
     */
    Map<String, Object> post(DarajaEnvironment environment, String consumerKey, String consumerSecret, String path, Map<String, Object> body);
}
