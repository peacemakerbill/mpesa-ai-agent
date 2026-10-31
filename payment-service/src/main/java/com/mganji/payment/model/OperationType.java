package com.mganji.payment.model;

/**
 * Everything the business can ask Daraja to do besides collecting a payment from a customer.
 * Money leaving the business account always needs a second person to approve it.
 */
public enum OperationType {
    /** Look up any M-Pesa transaction by receipt number. Staff only. */
    TRANSACTION_STATUS,
    /** Read the business account balance. Admin only. */
    ACCOUNT_BALANCE,
    /** Send a customer's money back to the phone that paid. Customers may request, staff approve. */
    REFUND,
    /** Pay out to a phone number (B2C). Staff request, a different approver approves. */
    WITHDRAWAL,
    /** Pay out to a phone number (B2C), for example supplier or salary payments. Same rules as WITHDRAWAL. */
    DISBURSEMENT,
    /** Reverse a customer to business transaction. Admin request, a different approver approves. */
    REVERSAL;

    public boolean requiresApproval() {
        return this == REFUND || this == WITHDRAWAL || this == DISBURSEMENT || this == REVERSAL;
    }

    public boolean isB2c() {
        return this == REFUND || this == WITHDRAWAL || this == DISBURSEMENT;
    }
}
