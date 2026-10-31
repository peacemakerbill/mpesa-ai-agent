package com.mganji.agent.service;

import com.mganji.agent.config.AgentProperties;
import com.mganji.agent.exception.UnsupportedFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pulls the text out of the office and text files people send, so the model can read them. */
@Component
public class DocumentTextExtractor {

    private static final int MAX_SHEETS = 5;
    private static final int MAX_ROWS = 200;
    private static final int MAX_COLUMNS = 30;

    private final int maxChars;

    public DocumentTextExtractor(AgentProperties props) {
        this.maxChars = props.maxTextChars();
    }

    /** @throws UnsupportedFileException with a message that can be shown to the customer */
    public String extract(byte[] bytes, String mimeType, String fileName) {
        String kind = kindOf(mimeType, fileName);
        try {
            String text = switch (kind) {
                case "docx" -> readDocx(bytes);
                case "xlsx", "xls" -> readWorkbook(bytes);
                case "text" -> new String(bytes, StandardCharsets.UTF_8);
                default -> throw new UnsupportedFileException(
                        "I can read PDF, Word (.docx), Excel (.xlsx), CSV, text and image files. "
                                + "Please send this one in one of those formats.");
            };
            String cleaned = text.replace("\u0000", "").strip();
            if (cleaned.isEmpty()) {
                throw new UnsupportedFileException("That file looks empty, I could not find any text in it.");
            }
            return cleaned.length() <= maxChars ? cleaned
                    : cleaned.substring(0, maxChars) + "\n[The file is longer than this, the rest was left out.]";
        } catch (UnsupportedFileException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new UnsupportedFileException("I could not open that file. It may be damaged or password protected.", e);
        }
    }

    /** Which reader to use, looking at the file name first because phones often send a generic mime type. */
    static String kindOf(String mimeType, String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String mime = mimeType == null ? "" : mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".docx") || mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            return "docx";
        }
        if (name.endsWith(".xlsx") || mime.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            return "xlsx";
        }
        if (name.endsWith(".xls") || mime.equals("application/vnd.ms-excel")) {
            return "xls";
        }
        if (name.endsWith(".csv") || name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json")
                || name.endsWith(".xml") || name.endsWith(".tsv") || name.endsWith(".log")
                || mime.startsWith("text/") || mime.equals("application/json") || mime.equals("application/xml")) {
            return "text";
        }
        return "unsupported";
    }

    private String readDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<String> lines = new ArrayList<>();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                lines.add(paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cells.add(cell.getText().replace('\n', ' ').strip());
                    }
                    lines.add(String.join(" | ", cells));
                }
            }
            return String.join("\n", lines);
        }
    }

    private String readWorkbook(byte[] bytes) throws IOException {
        DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
        StringBuilder out = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            int sheets = Math.min(workbook.getNumberOfSheets(), MAX_SHEETS);
            for (int s = 0; s < sheets; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                out.append("Sheet: ").append(sheet.getSheetName()).append('\n');
                int rowCount = 0;
                for (Row row : sheet) {
                    if (rowCount++ >= MAX_ROWS) {
                        out.append("[more rows left out]\n");
                        break;
                    }
                    List<String> cells = new ArrayList<>();
                    int last = Math.min(row.getLastCellNum(), MAX_COLUMNS);
                    for (int c = 0; c < last; c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : formatter.formatCellValue(cell));
                    }
                    out.append(String.join(" | ", cells)).append('\n');
                }
                out.append('\n');
            }
        }
        return out.toString();
    }
}
