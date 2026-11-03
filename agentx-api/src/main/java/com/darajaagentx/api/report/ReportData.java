package com.darajaagentx.api.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A table ready to become a file: a title, named columns and rows of text. Numeric columns are written as numbers in
 * Excel and aligned to the right in a PDF. Anything that is not a number stays text, so phone numbers and codes
 * keep their leading zeros.
 */
public record ReportData(String title, String brand, Instant generatedAt, List<Column> columns, List<List<String>> rows) {

    public record Column(String name, boolean numeric) {
        public static Column text(String name) {
            return new Column(name, false);
        }

        public static Column number(String name) {
            return new Column(name, true);
        }
    }

    public ReportData {
        title = title == null || title.isBlank() ? "Report" : title.trim();
        brand = brand == null ? "" : brand;
        columns = List.copyOf(columns);
        List<List<String>> fixed = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> r = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                String cell = i < row.size() ? row.get(i) : "";
                r.add(cell == null ? "" : cell);
            }
            fixed.add(List.copyOf(r));
        }
        rows = List.copyOf(fixed);
    }

    public int rowCount() {
        return rows.size();
    }
}
