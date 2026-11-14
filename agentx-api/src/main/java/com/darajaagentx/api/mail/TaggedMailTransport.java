package com.darajaagentx.api.mail;

/** Puts a tag such as "[STAGING] " in front of every subject, so a test email is never taken for a real one. */
public final class TaggedMailTransport implements MailTransport {

    private final MailTransport delegate;
    private final String tag;

    public TaggedMailTransport(MailTransport delegate, String tag) {
        this.delegate = delegate;
        this.tag = tag == null ? "" : tag;
    }

    @Override
    public void send(OutgoingMail mail) {
        delegate.send(new OutgoingMail(mail.to(), tag + mail.subject(), mail.text(), mail.html(), mail.attachments(), mail.replyTo()));
    }

    @Override
    public String describe() {
        return delegate.describe() + ", subjects start with " + tag.trim();
    }

    @Override
    public boolean delivers() {
        return delegate.delivers();
    }
}
