package com.darajaagentx.api.util;

import com.darajaagentx.api.exception.InvalidActionRequestException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reading and checking email addresses. Also finds the addresses in a pasted list or a file. */
public final class Emails {

    public static final int MAX_LENGTH = 254;

    private static final Pattern ADDRESS = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    /** Something that looks like an address inside other text. */
    private static final Pattern FIND = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9.-]{1,253}[A-Za-z0-9]");

    private Emails() {
    }

    /** Lower case and trimmed, or null when it is not an address. Line breaks are refused so an address can never add mail headers. */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("<") && s.endsWith(">") && s.length() > 2) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.isEmpty() || s.length() > MAX_LENGTH || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0 || s.contains("..")) {
            return null;
        }
        if (!ADDRESS.matcher(s).matches()) {
            return null;
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /** Like {@link #clean} but explains the problem. */
    public static String require(String raw) {
        String email = clean(raw);
        if (email == null) {
            throw new InvalidActionRequestException("\"" + abbreviate(raw) + "\" is not a valid email address. Use the form name@example.com.");
        }
        return email;
    }

    public static String domain(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    /** a***@example.com, for logs and for showing an address back to someone who may not be its owner. */
    public static String mask(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** What was found in a pasted list or a text file: separators may be commas, semicolons, spaces or new lines, and "Name <a@b.co>" works. */
    public record Found(List<String> emails, List<String> invalid) {
    }

    public static Found findAll(String text) {
        Set<String> good = new LinkedHashSet<>();
        List<String> bad = new ArrayList<>();
        if (text == null) {
            return new Found(List.of(), List.of());
        }
        for (String piece : text.split("[,;\\s]+")) {
            String token = piece.trim();
            if (token.isEmpty()) {
                continue;
            }
            token = token.replaceAll("^[<\"'(]+|[>\"'),.:]+$", "");
            if (token.isEmpty()) {
                continue;
            }
            if (token.indexOf('@') < 0) {
                continue;   // a name or a heading, not an address
            }
            String ok = clean(token);
            if (ok != null) {
                good.add(ok);
            } else {
                bad.add(abbreviate(token));
            }
        }
        return new Found(new ArrayList<>(good), bad);
    }

    /** Finds addresses anywhere in free text, for example a sentence typed in the chat. */
    public static List<String> extract(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text != null) {
            Matcher m = FIND.matcher(text);
            while (m.find()) {
                String ok = clean(m.group());
                if (ok != null) {
                    out.add(ok);
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("[\\r\\n]", " ").trim();
        return t.length() > 60 ? t.substring(0, 57) + "..." : t;
    }
}
