package com.darajaagentx.api.service;

import com.darajaagentx.api.model.BusinessOperation;
import org.springframework.stereotype.Component;

/** Connects the end of a business request to the email notice. BusinessOperationService calls it after saving the result. */
@Component
public class OperationMailHook implements OperationFinishedHook {

    private final CustomerMailer mailer;

    public OperationMailHook(CustomerMailer mailer) {
        this.mailer = mailer;
    }

    @Override
    public void finished(BusinessOperation op) {
        mailer.operationFinished(op.getOwnerPhone(), op.getStatus().name(), op.getType().label(), op.getSummary(), op.getResultText(), op.getReceipt());
    }
}
