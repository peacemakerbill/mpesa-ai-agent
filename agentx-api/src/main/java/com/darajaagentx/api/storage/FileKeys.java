package com.darajaagentx.api.storage;

import java.util.regex.Pattern;

/**
 * The only shape a file key may have: up to four parts separated by slashes, each made of letters, digits, dot, dash
 * and underscore, never starting with a dot and never containing {@code ..}. Every store checks keys with this, so a
 * key can never point outside its folder, table or bucket.
 */
final class FileKeys {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-][A-Za-z0-9._-]{0,120}(/[A-Za-z0-9_-][A-Za-z0-9._-]{0,120}){0,3}$");

    private FileKeys() {
    }

    static boolean valid(String key) {
        return key != null && KEY.matcher(key).matches() && !key.contains("..");
    }

    static String require(String key) {
        if (!valid(key)) {
            throw new StorageException("That file name is not allowed.");
        }
        return key;
    }
}
