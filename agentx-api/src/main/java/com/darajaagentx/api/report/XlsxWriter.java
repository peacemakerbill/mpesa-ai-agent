package com.darajaagentx.api.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes an Excel workbook (.xlsx) with no library: an xlsx file is a zip of a few XML files. One sheet, a bold header
 * row that stays in view, filters on, column widths that fit the text, numbers as numbers and everything else as text
 * (text cells are never read as formulas).
 */
public final class XlsxWriter {

    private static final Pattern NUMBER = Pattern.compile("^-?(0|[1-9][0-9]*)(\\.[0-9]+)?$");

    private XlsxWriter() {
    }

    public static byte[] write(ReportData data) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                put(zip, "[Content_Types].xml", CONTENT_TYPES);
                put(zip, "_rels/.rels", ROOT_RELS);
                put(zip, "xl/workbook.xml", workbook(sheetName(data.title())));
                put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
                put(zip, "xl/styles.xml", STYLES);
                put(zip, "xl/worksheets/sheet1.xml", sheet(data));
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String sheet(ReportData data) {
        List<ReportData.Column> cols = data.columns();
        int lastCol = Math.max(1, cols.size());
        int lastRow = data.rows().size() + 1;
        StringBuilder x = new StringBuilder(8192);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<dimension ref=\"A1:").append(ref(lastCol - 1, lastRow)).append("\"/>")
                .append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
                .append("<sheetFormatPr defaultRowHeight=\"15\"/>");
        x.append("<cols>");
        for (int c = 0; c < cols.size(); c++) {
            int longest = cols.get(c).name().length();
            for (List<String> row : data.rows()) {
                longest = Math.max(longest, Math.min(row.get(c).length(), 60));
            }
            int width = Math.max(8, Math.min(60, longest + 2));
            x.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        x.append("</cols><sheetData>");
        x.append("<row r=\"1\">");
        for (int c = 0; c < cols.size(); c++) {
            text(x, ref(c, 1), cols.get(c).name(), 1);
        }
        x.append("</row>");
        int r = 2;
        for (List<String> row : data.rows()) {
            x.append("<row r=\"").append(r).append("\">");
            for (int c = 0; c < cols.size(); c++) {
                String v = row.get(c);
                if (cols.get(c).numeric() && NUMBER.matcher(v.replace(",", "")).matches() && !v.isEmpty()) {
                    x.append("<c r=\"").append(ref(c, r)).append("\" s=\"2\"><v>").append(v.replace(",", "")).append("</v></c>");
                } else if (!v.isEmpty()) {
                    text(x, ref(c, r), v, 0);
                }
            }
            x.append("</row>");
            r++;
        }
        x.append("</sheetData>");
        if (!cols.isEmpty()) {
            x.append("<autoFilter ref=\"A1:").append(ref(lastCol - 1, lastRow)).append("\"/>");
        }
        x.append("</worksheet>");
        return x.toString();
    }

    private static void text(StringBuilder x, String ref, String value, int style) {
        x.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(style == 0 ? "" : " s=\"" + style + "\"")
                .append("><is><t xml:space=\"preserve\">").append(escape(value)).append("</t></is></c>");
    }

    /** A1 style reference. Column 0 is A. */
    static String ref(int col, int row) {
        StringBuilder letters = new StringBuilder();
        int n = col;
        do {
            letters.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return letters.toString() + row;
    }

    /** Excel allows 31 characters and none of : \ / ? * [ ] in a sheet name. */
    static String sheetName(String title) {
        String s = title == null ? "" : title.replaceAll("[:\\\\/?*\\[\\]]", " ").replaceAll("\\s+", " ").trim();
        if (s.startsWith("'")) {
            s = s.substring(1);
        }
        if (s.endsWith("'")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) {
            s = "Report";
        }
        return s.length() > 31 ? s.substring(0, 31).trim() : s;
    }

    /** XML text without the characters XML 1.0 does not allow at all. */
    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            boolean ok = cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (!ok) {
                continue;
            }
            switch (cp) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }

    private static String workbook(String sheetName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    private static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    /** Style 0 plain text, 1 bold header on grey, 2 number with a thousands separator and two decimals. */
    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE5E7EB\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"3\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
            + "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
}
