package com.darajaagentx.api.report;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes a table as CSV that Excel opens correctly: UTF-8 with a byte order mark, quoted where needed, lines ending in CRLF.
 * A cell that starts with = + - @ or a tab could run as a formula when the file is opened in Excel, so it gets a leading
 * apostrophe, except when it is simply a number such as -1,250.00.
 */
public final class CsvWriter {

    private static final Pattern PLAIN_NUMBER = Pattern.compile("^[+-]?[0-9][0-9,]*(\\.[0-9]+)?$");

    private CsvWriter() {
    }

    public static byte[] write(ReportData data) {
        StringBuilder out = new StringBuilder("﻿");
        List<ReportData.Column> cols = data.columns();
        for (int i = 0; i < cols.size(); i++) {
            out.append(i == 0 ? "" : ",").append(cell(cols.get(i).name()));
        }
        out.append("\r\n");
        for (List<String> row : data.rows()) {
            for (int i = 0; i < row.size(); i++) {
                out.append(i == 0 ? "" : ",").append(cell(row.get(i)));
            }
            out.append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(String raw) {
        String s = raw == null ? "" : raw;
        if (!s.isEmpty()) {
            char c = s.charAt(0);
            if ((c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') && !PLAIN_NUMBER.matcher(s).matches()) {
                s = "'" + s;
            }
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
