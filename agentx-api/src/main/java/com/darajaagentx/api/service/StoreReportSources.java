package com.darajaagentx.api.service;

import com.darajaagentx.api.dto.BusinessAccountView;
import com.darajaagentx.api.model.BusinessOperation;
import com.darajaagentx.api.model.WalletAction;
import com.darajaagentx.api.repository.BusinessOperationRepository;
import com.darajaagentx.api.report.ReportTables;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/** Reads the customer's own business requests, business accounts and app payments. Never reads a secret. */
@Component
public class StoreReportSources implements ReportSources {

    private final BusinessOperationRepository operations;
    private final BusinessAccountService accounts;
    private final ActionService actions;

    public StoreReportSources(BusinessOperationRepository operations, BusinessAccountService accounts, ActionService actions) {
        this.operations = operations;
        this.accounts = accounts;
        this.actions = actions;
    }

    @Override
    public List<ReportTables.OperationRow> operations(String owner) {
        return operations.findTop500ByOwnerPhoneOrderByCreatedAtDesc(owner).stream().map(StoreReportSources::row).toList();
    }

    private static ReportTables.OperationRow row(BusinessOperation o) {
        return new ReportTables.OperationRow(o.getCreatedAt(), o.getType().name(), o.getType().label(), o.getAccountLabel(), o.getAmount(),
                o.getStatus().name(), o.getSummary(), o.getReceipt(), o.getResultText() != null ? o.getResultText() : o.getFailure());
    }

    @Override
    public List<ReportTables.AccountRow> accounts(String owner) {
        return accounts.list(owner).stream().map(StoreReportSources::row).toList();
    }

    private static ReportTables.AccountRow row(BusinessAccountView a) {
        String credentials = a.inheritsCredentials() ? "head office" : (a.hasConsumerSecret() ? "own" : "none");
        String ready = a.capabilities().stream().filter(BusinessAccountView.Capability::ready).map(BusinessAccountView.Capability::label)
                .collect(Collectors.joining(", "));
        return new ReportTables.AccountRow(a.label(), a.environment().name().toLowerCase(), a.shortcodeType().name().toLowerCase(), a.shortcode(),
                a.tillNumber(), a.accountReference(), a.headShortcode(), credentials, a.initiatorName(), ready);
    }

    @Override
    public List<ReportTables.PaymentRow> payments(String owner) {
        return actions.list(owner, 50).stream().map(StoreReportSources::row).toList();
    }

    private static ReportTables.PaymentRow row(WalletAction a) {
        return new ReportTables.PaymentRow(a.getCreatedAt(), a.getType().name().replace('_', ' ').toLowerCase(), a.getAmount(),
                a.getStatus().name(), ActionSummary.summary(a), a.getReceipt());
    }
}
