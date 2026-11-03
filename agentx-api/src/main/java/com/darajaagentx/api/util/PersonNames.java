package com.darajaagentx.api.util;

import com.darajaagentx.api.exception.InvalidActionRequestException;

import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Checking and showing the parts of a person's name. */
public final class PersonNames {

    public static final int MAX = 60;

    /** Letters of any language, then spaces, dots, apostrophes and hyphens inside the name. No digits, no symbols. */
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M} .'’-]*$");

    private PersonNames() {
    }

    /** Trimmed with single spaces. Null for blank. Throws a plain message when the name has something a name should not. */
    public static String clean(String raw, String what) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() > MAX) {
            throw new InvalidActionRequestException("The " + what + " is too long. Use " + MAX + " characters or fewer.");
        }
        if (!NAME.matcher(s).matches()) {
            throw new InvalidActionRequestException("The " + what + " can have letters, spaces, hyphens and apostrophes only.");
        }
        return s;
    }

    public static String required(String raw, String what) {
        String s = clean(raw, what);
        if (s == null) {
            throw new InvalidActionRequestException("Enter your " + what + ".");
        }
        return s;
    }

    /** "Amina Wanjiru Otieno", leaving out the parts that are empty. */
    public static String full(String first, String middle, String last) {
        return Stream.of(first, middle, last).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" "));
    }

    public static String initials(String first, String last) {
        String f = first == null || first.isBlank() ? "" : first.substring(0, first.offsetByCodePoints(0, 1));
        String l = last == null || last.isBlank() ? "" : last.substring(0, last.offsetByCodePoints(0, 1));
        return (f + l).toUpperCase();
    }
}
