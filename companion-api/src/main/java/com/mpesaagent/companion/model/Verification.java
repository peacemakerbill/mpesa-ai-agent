package com.mpesaagent.companion.model;

/**
 * How well a completion is backed up. Higher is stronger, and a level can only go up.
 * <p>
 * NONE        nothing reported yet<br>
 * REPORTED    the customer said it was done (maybe with a code they typed)<br>
 * SMS_AMOUNT  an M-Pesa message on their phone matched the amount, time and kind, but nothing identified the recipient<br>
 * SMS_MATCHED as above, and the recipient's number or the Paybill account also matched
 * <p>
 * Even the highest level is read from the customer's own phone. It is not a confirmation from Safaricom.
 */
public enum Verification {
    NONE, REPORTED, SMS_AMOUNT, SMS_MATCHED;

    public boolean atLeast(Verification other) {
        return compareTo(other) >= 0;
    }
}
