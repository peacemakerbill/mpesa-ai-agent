package com.darajaagentx.api.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the email to the log instead of sending it (agentx.mail.transport: log). The text is in the log, so a developer can
 * read a verification code without a mail server. Do not use on a real system: the code is visible to anyone who reads the log.
 */
public class LogMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(LogMailTransport.class);

    @Override
    public void send(OutgoingMail mail) {
        StringBuilder files = new StringBuilder();
        for (OutgoingMail.Attachment a : mail.attachments()) {
            files.append(files.length() == 0 ? "" : ", ").append(a.fileName()).append(" (").append(a.data().length).append(" bytes)");
        }
        log.info("EMAIL (log transport, not sent) to {} subject \"{}\"{}\n{}", mail.to(), mail.subject(),
                files.length() == 0 ? "" : " attachments: " + files, mail.text());
    }

    @Override
    public String describe() {
        return "log only (nothing is sent)";
    }

    @Override
    public boolean delivers() {
        return false;
    }
}
