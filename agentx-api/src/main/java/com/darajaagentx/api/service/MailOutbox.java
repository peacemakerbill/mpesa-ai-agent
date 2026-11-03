package com.darajaagentx.api.service;

import com.darajaagentx.api.config.MailProperties;
import com.darajaagentx.api.mail.MailDeliveryException;
import com.darajaagentx.api.mail.MailTransport;
import com.darajaagentx.api.mail.OutgoingMail;
import com.darajaagentx.api.model.EmailLog;
import com.darajaagentx.api.repository.EmailLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * The one door every email leaves through. It hands the email to the transport and writes one line to the email log
 * (what, to whom, sent or failed), never the body or the attachment.
 */
@Component
public class MailOutbox {

    private static final Logger log = LoggerFactory.getLogger(MailOutbox.class);

    private final MailTransport transport;
    private final EmailLogRepository logs;
    private final MailProperties props;
    private final Executor executor;

    public MailOutbox(MailTransport transport, EmailLogRepository logs, MailProperties props, @Qualifier("mailExecutor") Executor executor) {
        this.transport = transport;
        this.logs = logs;
        this.props = props;
        this.executor = executor;
    }

    public MailProperties props() {
        return props;
    }

    /** False when emails are only written to the log. */
    public boolean delivers() {
        return transport.delivers();
    }

    public String describe() {
        return transport.describe();
    }

    /** Sends now and waits. Throws MailDeliveryException when the mail server says no. */
    public void send(String ownerPhone, String kind, OutgoingMail mail) {
        String attachment = mail.attachments().isEmpty() ? null : mail.attachments().get(0).fileName();
        try {
            transport.send(mail);
        } catch (MailDeliveryException e) {
            record(ownerPhone, kind, mail, EmailLog.FAILED, e.getMessage(), attachment);
            throw e;
        } catch (RuntimeException e) {
            record(ownerPhone, kind, mail, EmailLog.FAILED, "Unexpected error while sending", attachment);
            throw new MailDeliveryException("The email could not be sent.", e);
        }
        record(ownerPhone, kind, mail, transport.delivers() ? EmailLog.SENT : EmailLog.LOGGED, null, attachment);
    }

    /** Sends in the background and never throws: a notice must not hold up or break the thing it is about. */
    public void sendQuietly(String ownerPhone, String kind, OutgoingMail mail) {
        try {
            executor.execute(() -> {
                try {
                    send(ownerPhone, kind, mail);
                } catch (RuntimeException e) {
                    log.warn("Could not send the {} email: {}", kind, e.getMessage());
                }
            });
        } catch (RuntimeException e) {
            log.warn("Could not queue the {} email: {}", kind, e.getMessage());
        }
    }

    private void record(String owner, String kind, OutgoingMail mail, String status, String error, String attachment) {
        try {
            String to = String.join(", ", mail.to());
            logs.save(new EmailLog(owner, kind, to, mail.subject(), status, error, attachment));
        } catch (RuntimeException e) {
            log.warn("Could not write the email log: {}", e.getMessage());
        }
    }
}
