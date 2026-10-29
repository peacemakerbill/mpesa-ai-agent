package com.mpesaagent.payment.util;

import com.mpesaagent.payment.exception.InvalidPaymentRequestException;

import java.util.regex.Pattern;

public final class PhoneNumberUtil {

    /** Daraja wants 2547XXXXXXXX or 2541XXXXXXXX. */
    private static final Pattern DARAJA_FORMAT = Pattern.compile("^254[17]\\d{8}$");

    private PhoneNumberUtil() {
    }

    /**
     * Accepts 07XXXXXXXX, 01XXXXXXXX, 7XXXXXXXX, 1XXXXXXXX, +2547XXXXXXXX and 2547XXXXXXXX
     * (spaces and dashes allowed) and returns the Daraja format.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new InvalidPaymentRequestException("Phone number is required");
        }
        String digits = raw.replaceAll("[\\s\\-]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (!digits.matches("\\d+")) {
            throw new InvalidPaymentRequestException("Phone number must contain digits only");
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
        if (!DARAJA_FORMAT.matcher(normalized).matches()) {
            throw new InvalidPaymentRequestException("Invalid Kenyan phone number");
        }
        return normalized;
    }

    /** 254712345678 becomes 254712***678, for logs. */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 9) {
            return "***";
        }
        return phone.substring(0, 6) + "***" + phone.substring(phone.length() - 3);
    }
}
