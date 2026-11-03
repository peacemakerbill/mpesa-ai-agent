package com.darajaagentx.api.service;

import com.darajaagentx.api.model.DarajaEnvironment;

/** Asks Safaricom whether a consumer key and secret are accepted. A separate type so the service can be tested without the network. */
public interface TokenProbe {

    enum Kind {
        /** Safaricom gave an access token. */
        ACCEPTED,
        /** Safaricom refused the key and secret (HTTP 400 or 401). */
        REFUSED,
        /** Safaricom answered with some other error, or with something that is not a token. */
        ERROR,
        /** Safaricom could not be reached. */
        UNREACHABLE
    }

    record Outcome(Kind kind, int status) {
    }

    Outcome probe(DarajaEnvironment environment, String consumerKey, String consumerSecret);
}
