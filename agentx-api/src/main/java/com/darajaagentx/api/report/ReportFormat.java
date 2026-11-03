package com.darajaagentx.api.report;

import com.darajaagentx.api.exception.InvalidActionRequestException;

import java.util.Locale;

/** The file types a report can be sent as. People say "excel", "spreadsheet" or "xls" and mean XLSX. */
public enum ReportFormat {
    PDF("pdf", "application/pdf"),
    CSV("csv", "text/csv; charset=utf-8"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String contentType;

    ReportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /** Reads a word a person or the assistant used. Blank means PDF, the format that opens everywhere. */
    public static ReportFormat parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PDF;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(".", "");
        return switch (s) {
            case "pdf" -> PDF;
            case "csv", "comma separated", "text" -> CSV;
            case "xlsx", "xls", "excel", "spreadsheet", "sheet", "workbook", "microsoft excel" -> XLSX;
            default -> throw new InvalidActionRequestException("\"" + raw.trim() + "\" is not a report format I can make. Choose pdf, csv or excel.");
        };
    }
}
