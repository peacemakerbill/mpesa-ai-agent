package com.darajaagentx.api.dto;

import java.util.Map;

/**
 * Values the app uses to open M-Pesa. They live on the server so they can be corrected without a new app.
 * [environment] is development, staging or production, so the app and the console can show which system they are on.
 */
public record AppConfigResponse(String ussdMain, String mpesaAndroidPackage, String mpesaIosUrl,
                                Map<String, String> ussdTemplates, String environment) {
}
