package com.mpesaagent.agent.util;

public final class Phones {

    private Phones() {
    }

    /** Digits only, and a leading 0 or 7/1 on a 9 digit number becomes the 254 prefix. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("254")) {
            return digits;
        }
        if (digits.startsWith("0") && digits.length() == 10) {
            return "254" + digits.substring(1);
        }
        if (digits.length() == 9) {
            return "254" + digits;
        }
        return digits;
    }

    /** True for a normalised Kenyan mobile number such as 254712345678. */
    public static boolean isKenyanMobile(String normalized) {
        return normalized != null && normalized.matches("^254[17]\\d{8}$");
    }

    /** 254712345678 becomes 254712***678. */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 9) {
            return "***";
        }
        return phone.substring(0, 6) + "***" + phone.substring(phone.length() - 3);
    }
}
