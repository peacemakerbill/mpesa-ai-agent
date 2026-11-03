package com.darajaagentx.api.report;

import com.darajaagentx.api.exception.InvalidActionRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the tables that reports are made of, from plain rows. These functions know nothing about databases, so the
 * rules (which rows, which columns, what the title says) are easy to check.
 */
public final class ReportTables {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(NAIROBI);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public static final int MAX_COLUMNS = 30;
    public static final int MAX_CELL = 2000;

    private ReportTables() {
    }

    /** One business request (STK Push, B2C and so on) as a row. */
    public record OperationRow(Instant at, String typeCode, String type, String account, int amount, String status, String summary,
                               String receipt, String result) {
    }

    /** One business account, without a secret. */
    public record AccountRow(String label, String environment, String shortcodeType, String shortcode, String till, String reference,
                             String headShortcode, String credentials, String initiator, String ready) {
    }

    /** One payment made from the app. */
    public record PaymentRow(Instant at, String type, int amount, String status, String summary, String receipt) {
    }

    /** The narrowing a customer asked for. Blank fields mean no narrowing. */
    public record Filters(String status, String type, String account, String from, String until) {
        public static Filters none() {
            return new Filters(null, null, null, null, null);
        }
    }

    // ------------------------------------------------------------------ business requests

    public static ReportData businessRequests(String brand, Instant now, String title, List<OperationRow> all, Filters f, int limit) {
        LocalDate from = date(f.from(), "from");
        LocalDate until = date(f.until(), "until");
        if (from != null && until != null && until.isBefore(from)) {
            throw new InvalidActionRequestException("The end date is before the start date.");
        }
        Set<String> statuses = statuses(f.status());
        String type = norm(f.type());
        String account = f.account() == null ? "" : f.account().trim().toLowerCase(Locale.ROOT);

        List<List<String>> rows = new ArrayList<>();
        for (OperationRow r : all) {
            LocalDate day = r.at().atZone(NAIROBI).toLocalDate();
            if (from != null && day.isBefore(from) || until != null && day.isAfter(until)) {
                continue;
            }
            if (!statuses.isEmpty() && !statuses.contains(r.status())) {
                continue;
            }
            if (!type.isEmpty() && !(norm(r.typeCode()).equals(type) || norm(r.type()).contains(type))) {
                continue;
            }
            if (!account.isEmpty() && !r.account().toLowerCase(Locale.ROOT).contains(account)) {
                continue;
            }
            rows.add(List.of(STAMP.format(r.at()), r.type(), r.account(), String.valueOf(r.amount()), words(r.status()),
                    nz(r.summary()), nz(r.receipt()), nz(r.result())));
            if (rows.size() >= limit) {
                break;
            }
        }
        String name = title != null && !title.isBlank() ? title : "Business requests" + describe(f, from, until);
        return new ReportData(name, brand, now, List.of(ReportData.Column.text("Date"), ReportData.Column.text("Request"),
                ReportData.Column.text("Account"), ReportData.Column.number("Amount (KES)"), ReportData.Column.text("Status"),
                ReportData.Column.text("Details"), ReportData.Column.text("M-Pesa code"), ReportData.Column.text("Result")), rows);
    }

    // ------------------------------------------------------------------ accounts and payments

    public static ReportData businessAccounts(String brand, Instant now, String title, List<AccountRow> all, int limit) {
        List<List<String>> rows = new ArrayList<>();
        for (AccountRow a : all) {
            rows.add(List.of(a.label(), a.environment(), a.shortcodeType(), a.shortcode(), nz(a.till()), nz(a.reference()), nz(a.headShortcode()),
                    nz(a.credentials()), nz(a.initiator()), nz(a.ready())));
            if (rows.size() >= limit) {
                break;
            }
        }
        return new ReportData(title != null && !title.isBlank() ? title : "Business accounts", brand, now,
                List.of(ReportData.Column.text("Name"), ReportData.Column.text("Environment"), ReportData.Column.text("Type"),
                        ReportData.Column.text("Shortcode"), ReportData.Column.text("Till"), ReportData.Column.text("Account reference"),
                        ReportData.Column.text("Head office"), ReportData.Column.text("Credentials"), ReportData.Column.text("Initiator"),
                        ReportData.Column.text("Can do")), rows);
    }

    public static ReportData payments(String brand, Instant now, String title, List<PaymentRow> all, Filters f, int limit) {
        LocalDate from = date(f.from(), "from");
        LocalDate until = date(f.until(), "until");
        Set<String> statuses = f.status() == null || f.status().isBlank() ? Set.of() : Set.of(f.status().trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        List<List<String>> rows = new ArrayList<>();
        for (PaymentRow p : all) {
            LocalDate day = p.at().atZone(NAIROBI).toLocalDate();
            if (from != null && day.isBefore(from) || until != null && day.isAfter(until)) {
                continue;
            }
            if (!statuses.isEmpty() && !statuses.contains(p.status())) {
                continue;
            }
            rows.add(List.of(STAMP.format(p.at()), p.type(), String.valueOf(p.amount()), words(p.status()), nz(p.summary()), nz(p.receipt())));
            if (rows.size() >= limit) {
                break;
            }
        }
        String name = title != null && !title.isBlank() ? title : "Payments" + describe(f, from, until);
        return new ReportData(name, brand, now, List.of(ReportData.Column.text("Date"), ReportData.Column.text("Type"),
                ReportData.Column.number("Amount (KES)"), ReportData.Column.text("Status"), ReportData.Column.text("Details"),
                ReportData.Column.text("M-Pesa code")), rows);
    }

    // ------------------------------------------------------------------ a table the caller made

    /** A table given as columns and rows, for example what the assistant just showed in the chat. */
    public static ReportData custom(String brand, Instant now, String title, List<String> columns, List<String> numericColumns,
                                    List<List<String>> rows, int maxRows) {
        if (columns == null || columns.isEmpty()) {
            throw new InvalidActionRequestException("A report needs at least one column.");
        }
        if (columns.size() > MAX_COLUMNS) {
            throw new InvalidActionRequestException("A report can have up to " + MAX_COLUMNS + " columns.");
        }
        if (rows == null) {
            rows = List.of();
        }
        if (rows.size() > maxRows) {
            throw new InvalidActionRequestException("That report has " + rows.size() + " rows. The most one email can carry is " + maxRows + ". Narrow it down by date.");
        }
        Set<String> numeric = new java.util.HashSet<>();
        if (numericColumns != null) {
            numericColumns.forEach(n -> numeric.add(n == null ? "" : n.trim().toLowerCase(Locale.ROOT)));
        }
        List<ReportData.Column> cols = new ArrayList<>();
        for (String c : columns) {
            String name = clip(c == null || c.isBlank() ? "Column" : c.trim(), 80);
            cols.add(new ReportData.Column(name, numeric.contains(name.toLowerCase(Locale.ROOT))));
        }
        List<List<String>> cleaned = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> r = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) {
                r.add(clip(i < row.size() && row.get(i) != null ? row.get(i) : "", MAX_CELL));
            }
            cleaned.add(r);
        }
        return new ReportData(title != null && !title.isBlank() ? clip(title.trim(), 120) : "Report", brand, now, cols, cleaned);
    }

    // ------------------------------------------------------------------ helpers

    static LocalDate date(String raw, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidActionRequestException("The " + what + " date \"" + raw.trim() + "\" is not a date. Use the form 2026-10-31.");
        }
    }

    static Set<String> statuses(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        return switch (s) {
            case "succeeded", "success", "successful", "done", "completed", "complete", "paid" -> Set.of("SUCCEEDED");
            case "failed", "failure", "error", "errors" -> Set.of("FAILED");
            case "pending", "waiting", "pending approval", "open" -> Set.of("PENDING_APPROVAL", "SUBMITTED");
            case "declined", "rejected", "cancelled", "canceled" -> Set.of("DECLINED");
            case "expired" -> Set.of("EXPIRED");
            default -> throw new InvalidActionRequestException("\"" + raw.trim() + "\" is not a status. Use done, failed, pending, declined or expired.");
        };
    }

    private static String describe(Filters f, LocalDate from, LocalDate until) {
        List<String> parts = new ArrayList<>();
        if (from != null && until != null) {
            parts.add(DAY.format(from) + " to " + DAY.format(until));
        } else if (from != null) {
            parts.add("from " + DAY.format(from));
        } else if (until != null) {
            parts.add("until " + DAY.format(until));
        }
        if (f.status() != null && !f.status().isBlank()) {
            parts.add(f.status().trim().toLowerCase(Locale.ROOT));
        }
        if (f.type() != null && !f.type().isBlank()) {
            parts.add(f.type().trim());
        }
        if (f.account() != null && !f.account().isBlank()) {
            parts.add(f.account().trim());
        }
        return parts.isEmpty() ? "" : ", " + String.join(", ", parts);
    }

    private static String words(String status) {
        return status == null ? "" : status.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
