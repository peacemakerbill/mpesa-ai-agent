package com.mpesaagent.companion.util;

import com.mpesaagent.companion.exception.InvalidActionRequestException;

import java.util.regex.Pattern;

public final class Phones {

    private static final Pattern KENYAN_MOBILE = Pattern.compile("^254[17]\\d{8}$");

    private Phones() {
    }

    /** Accepts 07XXXXXXXX, 01XXXXXXXX, 7XXXXXXXX, +2547XXXXXXXX and 2547XXXXXXXX (spaces and dashes allowed). */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new InvalidActionRequestException("Phone number is required");
        }
        String digits = raw.replaceAll("[\\s\\-]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (!digits.matches("\\d+")) {
            throw new InvalidActionRequestException("Phone number must contain digits only");
        }
        String normalized;
        if (digits.startsWith("254")) {
            normalized = digits;
        } else if (digits.startsWith("0") && digits.length() == 10) {
            normalized = "254" + digits.substring(1);
        } else if (digits.length() == 9) {
            normalized = "254" + digits;
        } else {
            normalized = digits;
        }
        if (!KENYAN_MOBILE.matcher(normalized).matches()) {
            throw new InvalidActionRequestException("Invalid Kenyan phone number");
        }
        return normalized;
    }

    /** 254712345678 becomes 254712***678. */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 9) {
            return "***";
        }
        return phone.substring(0, 6) + "***" + phone.substring(phone.length() - 3);
    }
}
