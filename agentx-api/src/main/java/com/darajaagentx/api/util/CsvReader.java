package com.darajaagentx.api.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads CSV text the way Excel and Google Sheets write it: commas (or semicolons, or tabs), values in double quotes
 * when they hold the separator, a doubled quote for a quote, and any line ending. A leading byte order mark is ignored
 * and completely empty lines are skipped.
 */
public final class CsvReader {

    private CsvReader() {
    }

    public static List<List<String>> parse(String text) {
        if (text == null) {
            return List.of();
        }
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        char separator = detectSeparator(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean cellWasQuoted = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.length() == 0 && !cellWasQuoted) {
                quoted = true;
                cellWasQuoted = true;
            } else if (c == separator) {
                row.add(cell.toString());
                cell.setLength(0);
                cellWasQuoted = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                cellWasQuoted = false;
                addIfNotEmpty(rows, row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
            i++;
        }
        if (cell.length() > 0 || !row.isEmpty() || cellWasQuoted) {
            row.add(cell.toString());
            addIfNotEmpty(rows, row);
        }
        return rows;
    }

    private static void addIfNotEmpty(List<List<String>> rows, List<String> row) {
        boolean empty = row.stream().allMatch(s -> s.trim().isEmpty());
        if (!empty) {
            rows.add(row);
        }
    }

    /** Looks at the first line, outside quotes, and picks the separator that appears most. Comma wins a tie. */
    static char detectSeparator(String text) {
        int commas = 0;
        int semicolons = 0;
        int tabs = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                if (c == '\n' || c == '\r') {
                    if (commas + semicolons + tabs > 0) {
                        break;
                    }
                } else if (c == ',') {
                    commas++;
                } else if (c == ';') {
                    semicolons++;
                } else if (c == '\t') {
                    tabs++;
                }
            }
        }
        if (commas >= semicolons && commas >= tabs) {
            return ',';
        }
        return semicolons >= tabs ? ';' : '\t';
    }
}
