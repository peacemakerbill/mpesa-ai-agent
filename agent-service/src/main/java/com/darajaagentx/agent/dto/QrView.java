package com.darajaagentx.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QrView(String qrCodeBase64) {
}
