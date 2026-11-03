package com.darajaagentx.api.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads one text value out of a JSON body without a JSON library, for error messages only. */
public final class Json {

    private Json() {
    }

    /** The first "name":"value" in the text, or null. Escapes of quotes are handled, other escapes are left as they are. */
    public static String stringField(String json, String name) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"") : null;
    }
}
