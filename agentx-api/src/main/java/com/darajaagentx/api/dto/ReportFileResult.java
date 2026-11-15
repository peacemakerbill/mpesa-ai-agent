package com.darajaagentx.api.dto;

/**
 * A report built as a file for the customer to download, with nothing emailed. [data] is the file as Base64.
 */
public record ReportFileResult(String fileName, String contentType, String format, String title, int rows, int bytes, String data) {
}
