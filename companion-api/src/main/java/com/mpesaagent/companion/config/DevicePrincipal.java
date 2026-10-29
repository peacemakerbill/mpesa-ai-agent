package com.mpesaagent.companion.config;

/** Who is calling the app endpoints: a phone that signed in with a code for this number. */
public record DevicePrincipal(String phone, String deviceId) {
}
