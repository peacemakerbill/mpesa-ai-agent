package com.darajaagentx.api.service;

import com.darajaagentx.api.dto.BusinessAccountRequest;
import com.darajaagentx.api.dto.ImportResult;
import com.darajaagentx.api.util.CsvReader;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The CSV file people upload to add many business accounts at once.
 *
 * <p>Row 1 is the header. Column order does not matter, and columns may be left out. The names below, or the short
 * names in {@link #ALIASES}, are understood in any case.
 */
public final class BusinessAccountCsv {

    public static final int MAX_BYTES = 1_048_576;
    public static final int MAX_ROWS = 500;

    /** The columns, in the order the template shows them. */
    public static final List<String> COLUMNS = List.of(
            "label", "environment", "shortcode_type", "shortcode", "till_number", "account_reference",
            "parent_shortcode", "consumer_key", "consumer_secret", "initiator_name", "initiator_password", "passkey");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("name", "label"),
            Map.entry("env", "environment"),
            Map.entry("type", "shortcode_type"),
            Map.entry("shortcodetype", "shortcode_type"),
            Map.entry("business_shortcode", "shortcode"),
            Map.entry("paybill", "shortcode"),
            Map.entry("till", "till_number"),
            Map.entry("tillnumber", "till_number"),
            Map.entry("account_ref", "account_reference"),
            Map.entry("accountreference", "account_reference"),
            Map.entry("parent", "parent_shortcode"),
            Map.entry("parentshortcode", "parent_shortcode"),
            Map.entry("key", "consumer_key"),
            Map.entry("consumerkey", "consumer_key"),
            Map.entry("secret", "consumer_secret"),
            Map.entry("consumersecret", "consumer_secret"),
            Map.entry("initiator", "initiator_name"),
            Map.entry("initiatorname", "initiator_name"),
            Map.entry("initiator_pass", "initiator_password"),
            Map.entry("initiatorpassword", "initiator_password"),
            Map.entry("pass_key", "passkey"));

    /** What the "Download template" button gives. The values to replace start with PASTE_. */
    public static final String TEMPLATE = String.join("\r\n",
            String.join(",", COLUMNS),
            "Main shop,sandbox,buy_goods,174379,,,,PASTE_CONSUMER_KEY,PASTE_CONSUMER_SECRET,testapi,PASTE_INITIATOR_PASSWORD,PASTE_PASSKEY",
            "Till 1,sandbox,buy_goods,,123456,,174379,,,,,",
            "Till 2,sandbox,buy_goods,,654321,,174379,,,,,",
            "Mombasa store,,,600111,543210,,174379,PASTE_CONSUMER_KEY,PASTE_CONSUMER_SECRET,,,PASTE_PASSKEY",
            "School fees,production,paybill,400200,,,,PASTE_CONSUMER_KEY,PASTE_CONSUMER_SECRET,,,",
            "Nairobi branch,production,paybill,,,NAIROBI,400200,,,,,",
            "Kisumu campus,,,400300,,,400200,PASTE_CONSUMER_KEY,PASTE_CONSUMER_SECRET,,,PASTE_PASSKEY",
            "");

    private BusinessAccountCsv() {
    }

    /** One data row with its position in the file, where the header is row 1. */
    public record Row(int row, BusinessAccountRequest request) {
    }

    public record Parsed(List<Row> rows, List<ImportResult.Issue> issues, List<String> ignoredColumns) {
    }

    public static Parsed parse(byte[] data) {
        List<ImportResult.Issue> issues = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        if (data == null || data.length == 0) {
            issues.add(new ImportResult.Issue(0, "file", "The file is empty."));
            return new Parsed(List.of(), issues, ignored);
        }
        if (data.length > MAX_BYTES) {
            issues.add(new ImportResult.Issue(0, "file", "The file is bigger than 1 MB. Split it into smaller files."));
            return new Parsed(List.of(), issues, ignored);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            issues.add(new ImportResult.Issue(0, "file", "The file is not saved as UTF-8. In Excel choose Save as, CSV UTF-8."));
            return new Parsed(List.of(), issues, ignored);
        }
        List<List<String>> table = CsvReader.parse(text);
        if (table.isEmpty()) {
            issues.add(new ImportResult.Issue(0, "file", "The file has no rows."));
            return new Parsed(List.of(), issues, ignored);
        }

        List<String> header = table.get(0);
        Map<String, Integer> columnAt = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String raw = header.get(i);
            String name = normalizeHeader(raw);
            if (name.isEmpty()) {
                continue;
            }
            String canonical = COLUMNS.contains(name) ? name : ALIASES.get(name);
            if (canonical == null) {
                ignored.add(raw.trim());
            } else if (columnAt.containsKey(canonical)) {
                issues.add(new ImportResult.Issue(1, canonical, "The column \"" + canonical + "\" appears twice in the header."));
            } else {
                columnAt.put(canonical, i);
            }
        }
        if (!columnAt.containsKey("shortcode")) {
            issues.add(new ImportResult.Issue(1, "shortcode", "The header has no \"shortcode\" column. Download the template to see the columns."));
        }
        if (!columnAt.containsKey("shortcode_type") && !columnAt.containsKey("parent_shortcode")) {
            issues.add(new ImportResult.Issue(1, "shortcode_type", "The header has no \"shortcode_type\" column. Use paybill or buy_goods."));
        }
        if (!issues.isEmpty()) {
            return new Parsed(List.of(), issues, ignored);
        }
        if (table.size() - 1 > MAX_ROWS) {
            issues.add(new ImportResult.Issue(0, "file", "The file has more than " + MAX_ROWS + " accounts. Split it into smaller files."));
            return new Parsed(List.of(), issues, ignored);
        }
        if (table.size() == 1) {
            issues.add(new ImportResult.Issue(0, "file", "The file has a header but no accounts."));
            return new Parsed(List.of(), issues, ignored);
        }

        List<Row> rows = new ArrayList<>();
        for (int r = 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            rows.add(new Row(r + 1, new BusinessAccountRequest(
                    cell(cells, columnAt, "label"),
                    cell(cells, columnAt, "environment"),
                    cell(cells, columnAt, "shortcode_type"),
                    cell(cells, columnAt, "shortcode"),
                    cell(cells, columnAt, "till_number"),
                    cell(cells, columnAt, "account_reference"),
                    null,
                    cell(cells, columnAt, "parent_shortcode"),
                    cell(cells, columnAt, "consumer_key"),
                    cell(cells, columnAt, "consumer_secret"),
                    cell(cells, columnAt, "initiator_name"),
                    cell(cells, columnAt, "initiator_password"),
                    cell(cells, columnAt, "passkey"),
                    null)));
        }
        return new Parsed(rows, issues, ignored);
    }

    private static String cell(List<String> cells, Map<String, Integer> columnAt, String column) {
        Integer at = columnAt.get(column);
        if (at == null || at >= cells.size()) {
            return null;
        }
        String v = cells.get(at).trim();
        return v.isEmpty() ? null : v;
    }

    private static String normalizeHeader(String raw) {
        return raw == null ? "" : raw.replace("﻿", "").trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
    }
}
