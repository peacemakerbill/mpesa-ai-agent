package com.mganji.ganji.service;

import com.mganji.ganji.model.ActionSource;
import com.mganji.ganji.model.ActionType;
import com.mganji.ganji.model.WalletAction;
import com.mganji.ganji.util.Phones;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One line descriptions and the cautions shown with a request. Pure functions, easy to test. */
public final class ActionSummary {

    private ActionSummary() {
    }

    public static String kes(long amount) {
        return String.format(Locale.ENGLISH, "KES %,d", amount);
    }

    /** Phone numbers are masked, because this text also travels through chat and logs. */
    public static String summary(WalletAction a) {
        Map<String, String> d = a.getDetails();
        String money = kes(a.getAmount());
        return switch (a.getType()) {
            case SEND_MONEY -> "Send " + money + " to " + person(d);
            case PAY_BILL -> "Pay " + money + " to Paybill " + d.get("paybill") + ", account " + d.get("account")
                    + business(d);
            case PAY_TILL -> "Pay " + money + " to Till " + d.get("till") + business(d);
            case PAY_POCHI -> "Pay " + money + " to Pochi la Biashara " + Phones.mask(d.get("pochiPhone")) + business(d);
            case BUY_AIRTIME -> "Buy " + money + " airtime for "
                    + (d.get("airtimePhone") == null ? "your own number" : Phones.mask(d.get("airtimePhone")));
            case SCHEDULE_RATIBA -> "Set up a " + d.getOrDefault("frequency", "").toLowerCase(Locale.ENGLISH) + " Ratiba of " + money
                    + " from " + d.get("startDate") + (d.get("endDate") == null ? "" : " to " + d.get("endDate"))
                    + ": " + inner(d);
        };
    }

    private static String inner(Map<String, String> d) {
        return switch (ActionType.valueOf(d.get("target"))) {
            case SEND_MONEY -> "send to " + person(d);
            case PAY_BILL -> "Paybill " + d.get("paybill") + ", account " + d.get("account");
            case PAY_TILL -> "Till " + d.get("till");
            default -> "payment";
        };
    }

    private static String person(Map<String, String> d) {
        if (d.containsKey("contactName")) {
            return d.get("contactName") + " (a contact on your phone)";
        }
        String name = d.get("recipientName");
        String number = Phones.mask(d.get("recipientPhone"));
        return name == null ? number : name + " " + number;
    }

    private static String business(Map<String, String> d) {
        String name = d.get("businessName");
        return name == null ? "" : " (" + name + ")";
    }

    public static List<String> warnings(WalletAction a) {
        List<String> w = new ArrayList<>();
        if (a.getSource() == ActionSource.IMAGE || a.getSource() == ActionSource.DOCUMENT) {
            w.add("These details were read from an image or a file. Check every number and name before you continue.");
        } else if (a.getSource() == ActionSource.VOICE) {
            w.add("These details came from a voice note and may have been misheard. Check every number and name.");
        }
        if (a.getDetails().containsKey("contactName")) {
            w.add("Choose the contact on this phone, then check the number is the one you mean.");
        }
        switch (a.getType()) {
            case SEND_MONEY, PAY_POCHI ->
                    w.add("M-Pesa shows the registered name before you enter your PIN. Continue only if it is who you mean.");
            case PAY_BILL, PAY_TILL ->
                    w.add("M-Pesa shows the business name before you enter your PIN. Continue only if it is the right business.");
            default -> {
            }
        }
        w.add("Never share your M-Pesa PIN with anyone, including this chat. You enter it only in M-Pesa.");
        return w;
    }
}
