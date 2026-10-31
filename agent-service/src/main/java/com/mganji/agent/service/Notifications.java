package com.mganji.agent.service;

import com.mganji.agent.dto.ApprovalRequestedEvent;
import com.mganji.agent.dto.C2bReceivedEvent;
import com.mganji.agent.dto.OperationDoneEvent;
import com.mganji.agent.dto.PaymentDoneEvent;
import com.mganji.agent.dto.WalletActionEvent;
import com.mganji.agent.util.Money;
import com.mganji.agent.util.Phones;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * The wording of every message the system sends on its own. Pure functions so they are easy to read and test.
 * Customers get plain language; staff also get the reason M-Pesa gave, because they can act on it.
 */
public final class Notifications {

    private Notifications() {
    }

    /** Null means this status needs no message. */
    public static String payment(PaymentDoneEvent e) {
        String money = Money.kes(e.amount());
        String ref = e.accountReference() == null ? "" : " (reference " + e.accountReference() + ")";
        return switch (e.status() == null ? "" : e.status()) {
            case "SUCCESS" -> "*Payment received*\n\nThank you. We have received " + money + ref + "."
                    + (e.receipt() == null ? "" : "\n\n*M-Pesa receipt:* " + e.receipt());
            case "FAILED" -> "Your payment of " + money + ref + " was not completed, and you have not been charged."
                    + (e.reason() == null || e.reason().isBlank() ? "" : "\n\n*Reason:* " + e.reason())
                    + "\n\nReply here if you would like to try again.";
            case "CANCELLED" -> "You cancelled the M-Pesa prompt for " + money + ref + ", so nothing was charged. "
                    + "Reply here whenever you are ready to pay.";
            case "TIMEOUT" -> "The M-Pesa prompt for " + money + ref + " expired before it was answered, so nothing was charged. "
                    + "Reply here and I will send a new one.";
            default -> null;
        };
    }

    /** Null means no message. */
    public static String operation(OperationDoneEvent e, boolean requesterIsStaff) {
        String type = e.type() == null ? "" : e.type();
        String status = e.status() == null ? "" : e.status();
        String money = e.amount() == null ? "" : Money.kes(e.amount());
        String why = e.reason() == null || e.reason().isBlank() ? "" : e.reason();

        return switch (type) {
            case "REFUND" -> refund(status, money, e.receipt(), requesterIsStaff, why);
            case "WITHDRAWAL", "DISBURSEMENT" -> payout(status, money, e, why);
            case "REVERSAL" -> switch (status) {
                case "SUCCESS" -> "*Reversal completed*\n\n" + money + " reversed" + receiptLine(e.receipt());
                case "REJECTED" -> "Your reversal request for " + money + " was rejected" + (why.isEmpty() ? "." : ": " + why);
                case "TIMEOUT" -> "M-Pesa did not answer the reversal of " + money + " in time. Check the statement before trying again.";
                case "FAILED" -> "The reversal of " + money + " failed" + (why.isEmpty() ? "." : ": " + why);
                default -> null;
            };
            case "ACCOUNT_BALANCE" -> switch (status) {
                case "SUCCESS" -> "*Account balance*\n\n" + lines(e.details());
                case "TIMEOUT" -> "M-Pesa did not answer the balance request in time. Please ask again.";
                case "FAILED" -> "The balance request failed" + (why.isEmpty() ? "." : ": " + why);
                default -> null;
            };
            case "TRANSACTION_STATUS" -> switch (status) {
                case "SUCCESS" -> "*Transaction lookup*\n\n" + lines(e.details());
                case "TIMEOUT" -> "M-Pesa did not answer the lookup in time. Please ask again.";
                case "FAILED" -> "The lookup failed" + (why.isEmpty() ? "." : ": " + why);
                default -> null;
            };
            default -> null;
        };
    }

    private static String refund(String status, String money, String receipt, boolean staff, String why) {
        return switch (status) {
            case "SUCCESS" -> "*Refund sent*\n\nYour refund of " + money + " has been sent to your M-Pesa." + receiptLine(receipt)
                    + "\n\nThank you for your patience.";
            case "REJECTED" -> "Your refund request for " + money + " was not approved."
                    + "\n\nIf you have questions, reply here and I will connect you with our team.";
            case "FAILED", "TIMEOUT" -> staff
                    ? "The refund of " + money + " could not be completed" + (why.isEmpty() ? "." : ": " + why)
                    : "We could not complete your refund of " + money + " automatically. Our team has been alerted and will follow up with you here.";
            default -> null;
        };
    }

    private static String payout(String status, String money, OperationDoneEvent e, String why) {
        String to = e.recipientPhone() == null ? "" : " to " + Phones.mask(e.recipientPhone());
        return switch (status) {
            case "SUCCESS" -> "*Payout completed*\n\n" + money + to + " was sent." + receiptLine(e.receipt());
            case "REJECTED" -> "Your payout request of " + money + to + " was rejected" + (why.isEmpty() ? "." : ": " + why);
            case "FAILED" -> "The payout of " + money + to + " failed" + (why.isEmpty() ? "." : ": " + why);
            case "TIMEOUT" -> "M-Pesa did not answer the payout of " + money + to
                    + " in time. It may still go through, so check the M-Pesa statement before trying again.";
            default -> null;
        };
    }

    public static String approval(ApprovalRequestedEvent e) {
        StringBuilder sb = new StringBuilder("*Approval needed*\n\n");
        sb.append(e.type() == null ? "Request" : e.type()).append(' ')
                .append(e.amount() == null ? "" : Money.kes(e.amount()));
        if (e.recipientPhone() != null) {
            sb.append(" to ").append(Phones.mask(e.recipientPhone()));
        }
        sb.append("\n*Requested by:* ").append(Phones.mask(e.requestedBy()));
        if (e.reason() != null && !e.reason().isBlank()) {
            sb.append("\n*Reason:* ").append(e.reason());
        }
        return sb.toString();
    }

    /** Null means no message. The status is what the customer told the app, we cannot verify it with Safaricom. */
    public static String walletAction(WalletActionEvent e) {
        String what = e.summary() == null ? "your request" : e.summary();
        return switch (e.status() == null ? "" : e.status()) {
            case "DONE_REPORTED" -> "SMS_MATCHED".equals(e.verification()) || "SMS_AMOUNT".equals(e.verification())
                    ? smsBacked(e, what)
                    : e.receipt() == null || e.receipt().isBlank()
                    ? "Thanks. I've noted that you completed this:\n\n" + what
                    + "\n\nWhen M-Pesa sends you the confirmation message, forward it or send a screenshot here and I'll keep the code with this request."
                    : "Thanks. I've recorded this as done:\n\n" + what + "\n\n*M-Pesa code:* " + e.receipt();
            case "NOT_DONE" -> "Okay, I've marked this as not done:\n\n" + what
                    + "\n\nIf any money left your M-Pesa and you did not expect it, tell me and I'll connect you to a person. Otherwise, would you like to try again?";
            case "DECLINED" -> "You declined this in the app, so nothing was sent:\n\n" + what;
            default -> null;
        };
    }

    /** The app found an M-Pesa message on the customer's own phone that fits the request. Not a Safaricom confirmation. */
    private static String smsBacked(WalletActionEvent e, String what) {
        String code = e.receipt() == null || e.receipt().isBlank() ? "" : "\n\n*M-Pesa code:* " + e.receipt();
        String detail = "SMS_MATCHED".equals(e.verification())
                ? "The M-Pesa message on your phone matches this request, including who it went to."
                : "The M-Pesa message on your phone has the same amount and kind of transaction. It does not show enough to tell who received it, so please check the name in your message.";
        return "Thanks. " + detail + "\n\n" + what + code
                + "\n\nThis comes from the message on your own phone. Safaricom has not confirmed it to us.";
    }

    public static String c2b(C2bReceivedEvent e) {
        return "*Payment received*\n\nThank you. We have received " + Money.kes(e.amount())
                + (e.billRefNumber() == null || e.billRefNumber().isBlank() ? "" : " (account " + e.billRefNumber() + ")")
                + (e.transId() == null ? "" : ".\n\n*M-Pesa transaction:* " + e.transId());
    }

    private static String receiptLine(String receipt) {
        return receipt == null || receipt.isBlank() ? "" : "\n\n*M-Pesa receipt:* " + receipt;
    }

    private static String lines(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return "No details were returned.";
        }
        return details.entrySet().stream().limit(15)
                .map(en -> "- " + en.getKey() + ": " + en.getValue())
                .collect(Collectors.joining("\n"));
    }
}
