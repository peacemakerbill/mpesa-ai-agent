package com.darajaagentx.api.service;

import com.darajaagentx.api.report.ReportTables;

import java.util.List;

/** Where the rows of a built-in report come from. The real one reads the stores; a test gives fixed rows. */
public interface ReportSources {

    /** Newest first. */
    List<ReportTables.OperationRow> operations(String owner);

    List<ReportTables.AccountRow> accounts(String owner);

    /** Newest first. */
    List<ReportTables.PaymentRow> payments(String owner);
}
