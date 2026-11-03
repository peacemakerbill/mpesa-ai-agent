package com.darajaagentx.api.report;

import java.text.Normalizer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Turns a table into a file in the format asked for, with a safe file name. */
public final class ReportRenderer {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("Africa/Nairobi"));

    private ReportRenderer() {
    }

    public static RenderedReport render(ReportData data, ReportFormat format) {
        byte[] bytes = switch (format) {
            case PDF -> PdfWriter.write(data);
            case CSV -> CsvWriter.write(data);
            case XLSX -> XlsxWriter.write(data);
        };
        return new RenderedReport(fileName(data, format), format.contentType(), bytes, data.rowCount());
    }

    /** lower-case-words-20260131.pdf: letters, digits and dashes only, so it is safe in a mail header and on any disk. */
    public static String fileName(ReportData data, ReportFormat format) {
        String base = Normalizer.normalize(data.title(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "report";
        }
        if (base.length() > 50) {
            base = base.substring(0, 50).replaceAll("-+$", "");
        }
        return base + "-" + DAY.format(data.generatedAt()) + "." + format.extension();
    }
}
