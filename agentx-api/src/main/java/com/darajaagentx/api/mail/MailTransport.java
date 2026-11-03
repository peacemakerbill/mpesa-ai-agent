package com.darajaagentx.api.mail;

/** Hands one email to whatever delivers it: an SMTP server, or the log. */
public interface MailTransport {

    /** @throws MailDeliveryException when the email could not be handed over. The message is safe to show. */
    void send(OutgoingMail mail);

    /** For the log and the health check, for example "SMTP smtp.gmail.com:587". */
    String describe();

    /** False for the log transport: nothing really leaves the server, so the app tells the person. */
    boolean delivers();
}
