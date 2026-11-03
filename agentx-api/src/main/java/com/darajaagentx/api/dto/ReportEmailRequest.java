package com.darajaagentx.api.dto;

import java.util.List;

/**
 * Email a report. The same request serves the app, the console and the assistant.
 *
 * <p>[source] says where the rows come from: BUSINESS_REQUESTS, BUSINESS_ACCOUNTS or PAYMENTS (the server builds the table
 * itself), or TABLE (the caller gives [columns] and [rows], for example what the assistant just showed in the chat).
 *
 * <p>[to] holds addresses and, as well, the words "me" (the customer's own verified email) and names saved as recipients.
 * With [toMe] true the customer's own email is added. With [toSaved] true every saved recipient is added.
 */
public record ReportEmailRequest(
        String source,
        String title,
        String format,
        List<String> to,
        Boolean toMe,
        Boolean toSaved,
        String message,
        String status,
        String type,
        String account,
        String from,
        String until,
        Integer limit,
        List<String> columns,
        List<String> numericColumns,
        List<List<String>> rows
) {
}
