package com.mganji.agent.service;

import com.mganji.agent.TestSupport;
import com.mganji.agent.config.AgentProperties;
import com.mganji.agent.exception.UnsupportedFileException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor(TestSupport.props());

    @Test
    void readsCsvAndPlainText() {
        String csv = "name,amount\nTea,50\nCoffee,120\n";

        assertThat(extractor.extract(csv.getBytes(StandardCharsets.UTF_8), "text/csv", "menu.csv"))
                .contains("Tea,50").contains("Coffee,120");
        assertThat(extractor.extract("hello".getBytes(StandardCharsets.UTF_8), "application/octet-stream", "note.txt"))
                .isEqualTo("hello");
    }

    @Test
    void readsWordParagraphsAndTables() throws Exception {
        byte[] docx;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Invoice 2026-044");
            XWPFTable table = doc.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Total");
            table.getRow(0).getCell(1).setText("KES 12,000");
            doc.write(out);
            docx = out.toByteArray();
        }

        String text = extractor.extract(docx, "application/octet-stream", "invoice.docx");

        assertThat(text).contains("Invoice 2026-044").contains("Total | KES 12,000");
    }

    @Test
    void readsExcelSheets() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Payments");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Phone");
            header.createCell(1).setCellValue("Amount");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("254712345678");
            row.createCell(1).setCellValue(500);
            workbook.write(out);
            xlsx = out.toByteArray();
        }

        String text = extractor.extract(xlsx, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "payments.xlsx");

        assertThat(text).contains("Sheet: Payments").contains("Phone | Amount").contains("254712345678 | 500");
    }

    @Test
    void longTextIsCutWithANote() {
        AgentProperties small = new AgentProperties("memory", 20, Duration.ofHours(1), 5, 6, Duration.ofMinutes(1), Duration.ofHours(1),
                List.of(), 1, 1, 1, 1, 50, false);
        String text = new DocumentTextExtractor(small).extract("x".repeat(500).getBytes(StandardCharsets.UTF_8), "text/plain", "big.txt");

        assertThat(text).startsWith("x".repeat(50)).contains("left out");
    }

    @Test
    void unsupportedEmptyAndBrokenFilesGiveCustomerSafeErrors() {
        assertThatThrownBy(() -> extractor.extract(new byte[]{1, 2, 3}, "application/zip", "archive.zip"))
                .isInstanceOf(UnsupportedFileException.class).hasMessageContaining("PDF");
        assertThatThrownBy(() -> extractor.extract("   ".getBytes(StandardCharsets.UTF_8), "text/plain", "empty.txt"))
                .isInstanceOf(UnsupportedFileException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> extractor.extract("not really a docx".getBytes(StandardCharsets.UTF_8), "application/octet-stream", "fake.docx"))
                .isInstanceOf(UnsupportedFileException.class).hasMessageContaining("could not open");
    }

    @Test
    void decidesTheReaderFromNameBeforeMime() {
        assertThat(DocumentTextExtractor.kindOf("application/octet-stream", "Report.DOCX")).isEqualTo("docx");
        assertThat(DocumentTextExtractor.kindOf("text/plain", "x")).isEqualTo("text");
        assertThat(DocumentTextExtractor.kindOf("application/msword", "old.doc")).isEqualTo("unsupported");
    }
}
