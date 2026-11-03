package com.darajaagentx.api.report;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes a table as a PDF with no library. The table is set in Courier, whose letters are all the same width, so the
 * columns line up exactly and long text can be wrapped by counting characters. The page turns sideways and the type gets
 * smaller when many columns need the room; the header repeats on every page and each page says "Page 2 of 5".
 *
 * <p>Only the letters in the Windows Latin-1 set are drawn (English, Swahili, French, German and so on). Any other
 * character shows as "?".
 */
public final class PdfWriter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.of("Africa/Nairobi"));
    private static final double MARGIN = 36;
    private static final int GAP = 2;               // characters between columns
    private static final int MAX_CELL_LINES = 6;
    private static final int MIN_COLUMN = 4;

    private PdfWriter() {
    }

    private record Layout(boolean landscape, double fontSize) {
        double width() { return landscape ? 842 : 595; }
        double height() { return landscape ? 595 : 842; }
        double charWidth() { return 0.6 * fontSize; }
        double lineHeight() { return fontSize * 1.35; }
        int usableChars() { return (int) Math.floor((width() - 2 * MARGIN) / charWidth()); }
    }

    public static byte[] write(ReportData data) {
        List<ReportData.Column> cols = data.columns();
        int n = cols.size();
        int[] widths = new int[n];
        for (int c = 0; c < n; c++) {
            int longest = cols.get(c).name().length();
            for (List<String> row : data.rows()) {
                longest = Math.max(longest, row.get(c).length());
            }
            widths[c] = Math.max(MIN_COLUMN, Math.min(longest, 36));
        }
        Layout layout = chooseLayout(widths);
        int budget = layout.usableChars();
        while (total(widths) > budget) {
            int widest = 0;
            for (int c = 1; c < n; c++) {
                if (widths[c] > widths[widest]) {
                    widest = c;
                }
            }
            if (widths[widest] <= MIN_COLUMN) {
                break;
            }
            widths[widest]--;
        }

        List<StringBuilder> pages = new ArrayList<>();
        double bodyTop = layout.height() - MARGIN - 44;
        double bodyBottom = MARGIN + 22;
        StringBuilder page = null;
        double y = 0;
        int rowNo = 0;
        List<List<String>> all = new ArrayList<>(data.rows());
        int next = 0;
        do {
            if (page == null || y - rowHeight(all, next, widths, layout) < bodyBottom && next < all.size()) {
                page = new StringBuilder();
                pages.add(page);
                y = bodyTop;
                if (pages.size() == 1) {
                    text(page, "F4", 15, MARGIN, layout.height() - MARGIN - 12, data.title());
                    text(page, "F3", 8.5, MARGIN, layout.height() - MARGIN - 26,
                            all.size() + (all.size() == 1 ? " row" : " rows") + ", made " + STAMP.format(data.generatedAt()) + " (Nairobi time)");
                } else {
                    text(page, "F4", 10, MARGIN, layout.height() - MARGIN - 8, data.title() + " (continued)");
                }
                y = header(page, cols, widths, layout, y);
            }
            if (next < all.size()) {
                List<String> row = all.get(next);
                y = row(page, cols, widths, layout, y, row, rowNo % 2 == 1);
                rowNo++;
                next++;
            }
        } while (next < all.size());
        if (all.isEmpty()) {
            text(page, "F3", 10, MARGIN, y - 14, "No rows.");
        }

        for (int i = 0; i < pages.size(); i++) {
            StringBuilder p = pages.get(i);
            text(p, "F3", 8, MARGIN, MARGIN - 6, data.brand());
            String label = "Page " + (i + 1) + " of " + pages.size();
            text(p, "F3", 8, layout.width() - MARGIN - label.length() * 0.5 * 8, MARGIN - 6, label);
        }
        return assemble(pages, layout);
    }

    private static Layout chooseLayout(int[] widths) {
        int need = total(widths);
        Layout[] order = {
                new Layout(false, 9), new Layout(false, 8), new Layout(true, 9), new Layout(true, 8), new Layout(true, 7), new Layout(true, 6)};
        for (Layout l : order) {
            if (need <= l.usableChars()) {
                return l;
            }
        }
        return order[order.length - 1];
    }

    private static int total(int[] widths) {
        int sum = 0;
        for (int w : widths) {
            sum += w;
        }
        return sum + GAP * Math.max(0, widths.length - 1);
    }

    private static double header(StringBuilder page, List<ReportData.Column> cols, int[] widths, Layout l, double y) {
        double h = l.lineHeight() + 4;
        page.append("0.90 g ").append(num(MARGIN - 3)).append(' ').append(num(y - h + 3)).append(' ').append(num(l.width() - 2 * MARGIN + 6)).append(' ').append(num(h)).append(" re f 0 g\n");
        double x = MARGIN;
        for (int c = 0; c < cols.size(); c++) {
            String name = fit(cols.get(c).name(), widths[c]);
            text(page, "F2", l.fontSize(), x, y - l.fontSize() - 1, cols.get(c).numeric() ? padLeft(name, widths[c]) : name);
            x += (widths[c] + GAP) * l.charWidth();
        }
        return y - h;
    }

    private static double row(StringBuilder page, List<ReportData.Column> cols, int[] widths, Layout l, double y, List<String> row, boolean shade) {
        List<List<String>> lines = new ArrayList<>();
        int height = 1;
        for (int c = 0; c < cols.size(); c++) {
            List<String> wrapped = wrap(row.get(c), widths[c]);
            lines.add(wrapped);
            height = Math.max(height, wrapped.size());
        }
        double h = height * l.lineHeight() + 2;
        if (shade) {
            page.append("0.96 g ").append(num(MARGIN - 3)).append(' ').append(num(y - h + 1)).append(' ').append(num(l.width() - 2 * MARGIN + 6)).append(' ').append(num(h)).append(" re f 0 g\n");
        }
        double x = MARGIN;
        for (int c = 0; c < cols.size(); c++) {
            List<String> cell = lines.get(c);
            for (int i = 0; i < cell.size(); i++) {
                String s = cols.get(c).numeric() ? padLeft(cell.get(i), widths[c]) : cell.get(i);
                text(page, "F1", l.fontSize(), x, y - l.fontSize() - i * l.lineHeight(), s);
            }
            x += (widths[c] + GAP) * l.charWidth();
        }
        return y - h;
    }

    private static double rowHeight(List<List<String>> rows, int index, int[] widths, Layout l) {
        if (index >= rows.size()) {
            return 0;
        }
        int height = 1;
        for (int c = 0; c < widths.length; c++) {
            height = Math.max(height, wrap(rows.get(index).get(c), widths[c]).size());
        }
        return height * l.lineHeight() + 2;
    }

    /** Breaks text into lines of at most [width] characters at spaces, and inside a word that is too long. At most six lines. */
    static List<String> wrap(String raw, int width) {
        String s = raw == null ? "" : raw.replace('\t', ' ').replaceAll("[\\r\\n]+", " ").trim();
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            while (word.length() > width) {
                if (cur.length() > 0) {
                    lines.add(cur.toString());
                    cur.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (cur.length() == 0) {
                cur.append(word);
            } else if (cur.length() + 1 + word.length() <= width) {
                cur.append(' ').append(word);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (cur.length() > 0 || lines.isEmpty()) {
            lines.add(cur.toString());
        }
        if (lines.size() > MAX_CELL_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_CELL_LINES));
            String last = lines.get(MAX_CELL_LINES - 1);
            lines.set(MAX_CELL_LINES - 1, (last.length() > width - 3 ? last.substring(0, Math.max(0, width - 3)) : last) + "...");
        }
        return lines;
    }

    private static String fit(String s, int width) {
        return s.length() <= width ? s : s.substring(0, Math.max(1, width));
    }

    private static String padLeft(String s, int width) {
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < width; i++) {
            b.append(' ');
        }
        return b.append(s).toString();
    }

    private static void text(StringBuilder page, String font, double size, double x, double y, String s) {
        page.append("BT /").append(font).append(' ').append(num(size)).append(" Tf 1 0 0 1 ").append(num(x)).append(' ').append(num(y)).append(" Tm (")
                .append(pdfString(s)).append(") Tj ET\n");
    }

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    /** The text as a PDF string body: Windows Latin-1 characters as octal escapes where needed, brackets and slashes escaped. */
    static String pdfString(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int b = winAnsi(ch);
            if (b == '(' || b == ')' || b == '\\') {
                out.append('\\').append((char) b);
            } else if (b >= 32 && b < 127) {
                out.append((char) b);
            } else {
                out.append('\\').append(String.format("%03o", b));
            }
        }
        return out.toString();
    }

    private static int winAnsi(char c) {
        if (c >= 32 && c < 127) {
            return c;
        }
        if (c >= 160 && c <= 255) {
            return c;
        }
        return switch (c) {
            case '€' -> 0x80;
            case '‘' -> 0x91;
            case '’' -> 0x92;
            case '“' -> 0x93;
            case '”' -> 0x94;
            case '•' -> 0x95;
            case '–' -> 0x96;
            case '—' -> 0x97;
            case '…' -> 0x85;
            case '\t', '\n', '\r' -> ' ';
            default -> '?';
        };
    }

    private static byte[] assemble(List<StringBuilder> pages, Layout layout) {
        // objects: 1 catalog, 2 pages, 3..6 fonts, then a page and its content for each page
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            kids.append(7 + 2 * i).append(" 0 R ");
        }
        objects.add("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pages.size() + " >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier-Bold /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int i = 0; i < pages.size(); i++) {
            int content = 8 + 2 * i;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + num(layout.width()) + " " + num(layout.height()) + "] /Contents " + content
                    + " 0 R /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R /F4 6 0 R >> >> >>");
            String stream = pages.get(i).toString();
            objects.add("<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + stream + "endstream");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n%âãÏÓ\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int off : offsets) {
            x.append(String.format(Locale.ROOT, "%010d 00000 n \n", off));
        }
        x.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(out, x.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(b, 0, b.length);
    }
}
