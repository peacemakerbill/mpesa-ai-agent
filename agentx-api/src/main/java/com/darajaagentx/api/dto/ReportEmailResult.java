package com.darajaagentx.api.dto;

import java.util.List;

/**
 * What sending a report did. [delivered] is false when the server only logs email (agentx.mail.transport: log), in which
 * case nothing reached anyone and the answer says so.
 */
public record ReportEmailResult(boolean delivered, List<String> sentTo, String fileName, String format, int rows, int bytes, String title) {
}
