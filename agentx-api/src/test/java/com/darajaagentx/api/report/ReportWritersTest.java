package com.darajaagentx.api.report;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportWritersTest {

    static final Instant AT = Instant.parse("2026-10-29T08:30:00Z");

    private static ReportData sample(int rows) {
        List<List<String>> data = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            data.add(List.of("2026-10-" + String.format("%02d", 1 + i % 28), "STK Push", "Main shop", String.valueOf(100 + i), "0712345678", "Paid QK" + i));
        }
        return new ReportData("Business requests, October 2026", "Daraja Agent-X", AT,
                List.of(ReportData.Column.text("Date"), ReportData.Column.text("Type"), ReportData.Column.text("Account"), ReportData.Column.number("Amount (KES)"),
                        ReportData.Column.text("Phone"), ReportData.Column.text("Result")), data);
    }

    @Test
    void formatWordsAreUnderstood() {
        assertThat(ReportFormat.parse(null)).isEqualTo(ReportFormat.PDF);
        assertThat(ReportFormat.parse(" PDF ")).isEqualTo(ReportFormat.PDF);
        assertThat(ReportFormat.parse("Excel")).isEqualTo(ReportFormat.XLSX);
        assertThat(ReportFormat.parse(".xls")).isEqualTo(ReportFormat.XLSX);
        assertThat(ReportFormat.parse("Spreadsheet")).isEqualTo(ReportFormat.XLSX);
        assertThat(ReportFormat.parse("CSV")).isEqualTo(ReportFormat.CSV);
        assertThatThrownBy(() -> ReportFormat.parse("docx")).hasMessageContaining("pdf, csv or excel");
    }

    @Test
    void fileNamesAreSafe() {
        ReportData d = new ReportData("  Çafé / Ünïcode: \"report\" ../../etc/passwd  ", "x", AT, List.of(ReportData.Column.text("a")), List.of());
        String name = ReportRenderer.fileName(d, ReportFormat.PDF);
        assertThat(name).isEqualTo("cafe-unicode-report-etc-passwd-20261029.pdf");
        assertThat(ReportRenderer.fileName(new ReportData("***", "x", AT, List.of(ReportData.Column.text("a")), List.of()), ReportFormat.CSV)).isEqualTo("report-20261029.csv");
        assertThat(ReportRenderer.fileName(new ReportData("a".repeat(200), "x", AT, List.of(ReportData.Column.text("a")), List.of()), ReportFormat.CSV).length() < 70).isTrue();
    }

    @Test
    void csvHasABomQuotesCellsAndNeutralisesFormulas() {
        ReportData d = new ReportData("t", "b", AT, List.of(ReportData.Column.text("Name"), ReportData.Column.number("Amount"), ReportData.Column.text("Note")),
                List.of(List.of("=HYPERLINK(\"http://evil\")", "-1,250.00", "plain"), List.of("+254712345678", "12", "has, comma"),
                        List.of("@SUM(A1)", "3", "line\nbreak"), List.of("say \"hi\"", "4", "-cmd|' /C calc'!A0")));
        byte[] bytes = CsvWriter.write(d);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        String text = new String(bytes, StandardCharsets.UTF_8).substring(1);
        assertThat(text).startsWith("Name,Amount,Note\r\n");
        assertThat(text).contains("\"'=HYPERLINK(\"\"http://evil\"\")\",\"-1,250.00\",plain");     // a number stays a number, quoted for its comma
        assertThat(text).contains("+254712345678,12,\"has, comma\"");       // digits only: a number, not a formula
        assertThat(CsvWriter.cell("+1+1")).isEqualTo("'+1+1");
        assertThat(text).contains("'@SUM(A1),3,\"line\nbreak\"");
        assertThat(text).contains("\"say \"\"hi\"\"\",4,'-cmd|' /C calc'!A0");
    }

    @Test
    void xlsxIsAZipOfWellFormedXmlWithTheDataInIt() throws Exception {
        ReportData d = sample(3);
        byte[] bytes = XlsxWriter.write(new ReportData("A/B: [odd] name that is far too long for a sheet", "b", AT, d.columns(),
                List.of(List.of("=1+1", "Main <shop> & co", "x", "1,500.50", "0712", "ok \u0001bad control"))));
        Map<String, String> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                parts.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(parts.keySet()).contains("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml");
        for (Map.Entry<String, String> p : parts.entrySet()) {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(p.getValue().getBytes(StandardCharsets.UTF_8)));
        }
        String sheet = parts.get("xl/worksheets/sheet1.xml");
        assertThat(sheet).contains("Main &lt;shop&gt; &amp; co");
        assertThat(sheet).contains("<v>1500.50</v>");                                    // a number cell
        assertThat(sheet).contains("t=\"inlineStr\"");
        assertThat(sheet).contains(">=1+1<");                                            // text, never a formula (no <f> element)
        assertThat(sheet).doesNotContain("<f>");
        assertThat(sheet).doesNotContain("\u0001");
        assertThat(sheet).contains("<autoFilter ref=\"A1:F2\"/>");
        assertThat(sheet).contains("state=\"frozen\"");
        assertThat(parts.get("xl/workbook.xml")).contains("name=\"A B odd name that is far too lo\"");
    }

    @Test
    void xlsxColumnLettersGoPastZ() {
        assertThat(XlsxWriter.ref(0, 1)).isEqualTo("A1");
        assertThat(XlsxWriter.ref(25, 2)).isEqualTo("Z2");
        assertThat(XlsxWriter.ref(26, 3)).isEqualTo("AA3");
        assertThat(XlsxWriter.ref(27, 4)).isEqualTo("AB4");
        assertThat(XlsxWriter.ref(701, 5)).isEqualTo("ZZ5");
        assertThat(XlsxWriter.ref(702, 6)).isEqualTo("AAA6");
    }

    @Test
    void pdfIsAWellFormedDocumentWithAllPagesAndNumbers() {
        byte[] bytes = PdfWriter.write(sample(150));
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(raw).startsWith("%PDF-1.4");
        assertThat(raw.trim()).endsWith("%%EOF");
        assertThat(raw).contains("Business requests, October 2026");
        assertThat(raw).contains("Page 1 of ");
        // the cross reference table points at every object
        int xref = Integer.parseInt(raw.substring(raw.lastIndexOf("startxref") + 10).trim().split("\\s")[0]);
        assertThat(raw.substring(xref, xref + 4)).isEqualTo("xref");
        int objects = Integer.parseInt(raw.substring(raw.indexOf("/Size ") + 6).split("[ >]")[0]) - 1;
        String[] lines = raw.substring(xref).split("\n");
        int entries = 0;
        for (String line : lines) {
            if (line.matches("\\d{10} 00000 n ")) {
                int offset = Integer.parseInt(line.substring(0, 10));
                assertThat(raw.substring(offset).matches("(?s)^" + (entries + 1) + " 0 obj.*")).isTrue();
                entries++;
            }
        }
        assertThat(entries).isEqualTo(objects);
        int pages = Integer.parseInt(raw.substring(raw.indexOf("/Count ") + 7).split("[ >]")[0]);
        assertThat(pages > 1).isTrue();
        assertThat(raw).contains("Page " + pages + " of " + pages);
    }

    @Test
    void pdfTextIsEscapedAndUnknownCharactersBecomeQuestionMarks() {
        assertThat(PdfWriter.pdfString("a(b)c\\d")).isEqualTo("a\\(b\\)c\\\\d");
        assertThat(PdfWriter.pdfString("café")).isEqualTo("caf\\351");
        assertThat(PdfWriter.pdfString("€5 “q”")).isEqualTo("\\2005 \\223q\\224");
        assertThat(PdfWriter.pdfString("中文")).isEqualTo("??");
        assertThat(PdfWriter.pdfString("tab\there")).isEqualTo("tab here");
    }

    @Test
    void pdfWrapsLongTextAndCapsTheLines() {
        assertThat(PdfWriter.wrap("a quick brown fox jumps", 10)).containsExactly("a quick", "brown fox", "jumps");
        assertThat(PdfWriter.wrap("0123456789abcdef", 10)).containsExactly("0123456789", "abcdef");
        assertThat(PdfWriter.wrap("", 10)).containsExactly("");
        List<String> capped = PdfWriter.wrap("word ".repeat(100), 10);
        assertThat(capped).hasSize(6);
        assertThat(capped.get(5)).endsWith("...");
    }

    @Test
    void emptyReportsStillMakeAValidFileInEveryFormat() {
        ReportData empty = sample(0);
        for (ReportFormat f : ReportFormat.values()) {
            RenderedReport r = ReportRenderer.render(empty, f);
            assertThat(r.data().length > 100 || f == ReportFormat.CSV).isTrue();
            assertThat(r.rows()).isEqualTo(0);
        }
        assertThat(new String(PdfWriter.write(empty), StandardCharsets.ISO_8859_1)).contains("No rows.");
    }

    @Test
    void shortRowsArePaddedAndCellsAreNeverNull() {
        ReportData d = new ReportData("t", "b", AT, List.of(ReportData.Column.text("a"), ReportData.Column.text("b")), List.of(List.of("only one")));
        assertThat(d.rows().get(0)).hasSize(2);
        assertThat(d.rows().get(0).get(1)).isEqualTo("");
        ReportRenderer.render(d, ReportFormat.XLSX);
    }
}
