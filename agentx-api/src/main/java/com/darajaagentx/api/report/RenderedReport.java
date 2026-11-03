package com.darajaagentx.api.report;

/** A finished file. */
public record RenderedReport(String fileName, String contentType, byte[] data, int rows) {
}
