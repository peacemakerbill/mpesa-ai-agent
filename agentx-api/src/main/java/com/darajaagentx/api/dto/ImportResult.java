package com.darajaagentx.api.dto;

import java.util.List;

/**
 * What a CSV upload did, or would do when dryRun is true. When there is any issue nothing is saved,
 * so the person can fix the file and upload it again.
 */
public record ImportResult(
        boolean dryRun,
        boolean saved,
        int total,
        int created,
        int updated,
        List<Row> rows,
        List<Issue> issues,
        List<String> ignoredColumns
) {

    /** What happens to one row: "create" or "update". */
    public record Row(int row, String label, String environment, String shortcodeType, String shortcode,
                      String tillNumber, String accountReference, String parent, String action) {
    }

    /** One problem. Row 1 is the header line, so the first account is row 2. Row 0 means the whole file. */
    public record Issue(int row, String field, String message) {
    }
}
