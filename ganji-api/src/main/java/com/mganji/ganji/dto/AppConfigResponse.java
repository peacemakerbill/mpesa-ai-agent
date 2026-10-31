package com.mganji.ganji.dto;

import java.util.Map;

/** Values the app uses to open M-Pesa. They live on the server so they can be corrected without a new app. */
public record AppConfigResponse(String ussdMain, String mpesaAndroidPackage, String mpesaIosUrl,
                                Map<String, String> ussdTemplates) {
}
