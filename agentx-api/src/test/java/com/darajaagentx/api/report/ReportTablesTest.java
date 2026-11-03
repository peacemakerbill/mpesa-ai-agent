package com.darajaagentx.api.report;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTablesTest {

    static final Instant NOW = Instant.parse("2026-10-29T08:30:00Z");

    private static List<ReportTables.OperationRow> ops() {
        Instant t = Instant.parse("2026-10-20T09:15:00Z");
        return List.of(
                new ReportTables.OperationRow(t.plusSeconds(86400 * 8), "B2C", "Pay out to a phone (B2C)", "Main shop", 500, "FAILED", "KES 500", null, "No funds"),
                new ReportTables.OperationRow(t.plusSeconds(86400 * 3), "STK_PUSH", "STK Push", "Main shop", 1200, "SUCCEEDED", "KES 1200", "QKA1", "Paid"),
                new ReportTables.OperationRow(t, "STK_PUSH", "STK Push", "Till 1", 300, "PENDING_APPROVAL", "KES 300", null, null));
    }

    @Test
    void statusWordsMeanWhatPeopleMean() {
        assertThat(ReportTables.businessRequests("b", NOW, null, ops(), new ReportTables.Filters("done", null, null, null, null), 100).rowCount()).isEqualTo(1);
        assertThat(ReportTables.businessRequests("b", NOW, null, ops(), new ReportTables.Filters("Successful", null, null, null, null), 100).rowCount()).isEqualTo(1);
        assertThat(ReportTables.businessRequests("b", NOW, null, ops(), new ReportTables.Filters("failed", null, null, null, null), 100).rowCount()).isEqualTo(1);
        assertThat(ReportTables.businessRequests("b", NOW, null, ops(), new ReportTables.Filters("waiting", null, null, null, null), 100).rowCount()).isEqualTo(1);
        assertThat(ReportTables.businessRequests("b", NOW, null, ops(), ReportTables.Filters.none(), 100).rowCount()).isEqualTo(3);
    }

    @Test
    void typeAccountAndDatesNarrowTheRows() {
        assertThat(ReportTables.businessRequests("b", NOW, null, ops(), new ReportTables.Filters(null, "stk push", null, null, null), 100).rowCount()).isEqualTo(2);
        assertThat(ReportTables.businessRequests("b", NOW, null, ops(), new ReportTables.Filters(null, "b2c", null, null, null), 100).rowCount()).isEqualTo(1);
        assertThat(ReportTables.businessRequests("b", NOW, null, ops(), new ReportTables.Filters(null, "payout", null, null, null), 100).rowCount()).isEqualTo(0);
        assertThat(ReportTables.businessRequests("b", NOW, null, ops(), new ReportTables.Filters(null, null, "till", null, null), 100).rowCount()).isEqualTo(1);
        ReportData oct = ReportTables.businessRequests("b", NOW, null, ops(), new ReportTables.Filters(null, null, null, "2026-10-21", "2026-10-25"), 100);
        assertThat(oct.rowCount()).isEqualTo(1);
        assertThat(oct.title()).isEqualTo("Business requests, 21 Oct 2026 to 25 Oct 2026");
        assertThatThrownBy(() -> ReportTables.businessRequests("b", NOW, null, ops(), new ReportTables.Filters(null, null, null, "2026-10-25", "2026-10-21"), 100))
                .hasMessageContaining("before the start");
    }

    @Test
    void theDayIsTheNairobiDay() {
        // 22:30 UTC on the 20th is 01:30 on the 21st in Nairobi
        List<ReportTables.OperationRow> late = List.of(new ReportTables.OperationRow(Instant.parse("2026-10-20T22:30:00Z"), "STK_PUSH", "STK Push", "A", 1, "SUCCEEDED", "s", null, null));
        assertThat(ReportTables.businessRequests("b", NOW, null, late, new ReportTables.Filters(null, null, null, "2026-10-21", "2026-10-21"), 10).rowCount()).isEqualTo(1);
        assertThat(ReportTables.businessRequests("b", NOW, null, late, new ReportTables.Filters(null, null, null, "2026-10-20", "2026-10-20"), 10).rowCount()).isEqualTo(0);
    }

    @Test
    void limitAndExplicitTitleAreRespected() {
        ReportData d = ReportTables.businessRequests("b", NOW, "Weekly", ops(), ReportTables.Filters.none(), 2);
        assertThat(d.rowCount()).isEqualTo(2);
        assertThat(d.title()).isEqualTo("Weekly");
        assertThat(d.columns().get(3).numeric()).isTrue();
    }

    @Test
    void customTablesAreCheckedAndTrimmed() {
        ReportData d = ReportTables.custom("b", NOW, " Takings ", List.of("Day", "Amount (KES)"), List.of("amount (kes)"),
                List.of(List.of("Mon", "12,500"), List.of("Tue")), 100);
        assertThat(d.title()).isEqualTo("Takings");
        assertThat(d.columns().get(1).numeric()).isTrue();
        assertThat(d.columns().get(0).numeric()).isFalse();
        assertThat(d.rows().get(1).get(1)).isEqualTo("");
        assertThatThrownBy(() -> ReportTables.custom("b", NOW, null, List.of(), null, List.of(), 100)).hasMessageContaining("at least one column");
        assertThatThrownBy(() -> ReportTables.custom("b", NOW, null, List.of("a"), null, List.of(List.of("1"), List.of("2")), 1)).hasMessageContaining("2 rows");
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            tooMany.add("c" + i);
        }
        assertThatThrownBy(() -> ReportTables.custom("b", NOW, null, tooMany, null, List.of(), 100)).hasMessageContaining("30 columns");
        ReportData long1 = ReportTables.custom("b", NOW, null, List.of("a"), null, List.of(List.of("x".repeat(5000))), 100);
        assertThat(long1.rows().get(0).get(0).length()).isEqualTo(2000);
    }
}
