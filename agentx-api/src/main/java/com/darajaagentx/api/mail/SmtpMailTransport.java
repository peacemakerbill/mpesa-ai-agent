package com.darajaagentx.api.mail;

import com.darajaagentx.api.config.MailProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

/** Sends through an SMTP server (agentx.mail.smtp). Needs spring-boot-starter-mail in the pom. */
public class SmtpMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailTransport.class);

    private final JavaMailSenderImpl sender;
    private final MailProperties props;

    public SmtpMailTransport(MailProperties props) {
        this.props = props;
        MailProperties.Smtp smtp = props.smtp();
        if (smtp.host() == null || smtp.host().isBlank()) {
            throw new IllegalStateException("agentx.mail.transport is smtp but agentx.mail.smtp.host is empty. Set MAIL_HOST, or use transport log.");
        }
        sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host());
        sender.setPort(smtp.port());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (smtp.username() != null && !smtp.username().isBlank()) {
            sender.setUsername(smtp.username());
            sender.setPassword(smtp.password());
            String host = smtp.host().toLowerCase(Locale.ROOT);
            if ((host.endsWith("gmail.com") || host.endsWith("googlemail.com")) && smtp.password() != null
                    && !smtp.password().isEmpty() && smtp.password().length() != 16) {
                log.warn("The Gmail password in MAIL_PASSWORD has {} characters. A Gmail app password has exactly 16 letters, "
                        + "so part of it may be missing. Create a new one at myaccount.google.com/apppasswords.", smtp.password().length());
            }
        }
        Properties p = sender.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", String.valueOf(smtp.username() != null && !smtp.username().isBlank()));
        p.put("mail.smtp.starttls.enable", String.valueOf(smtp.startTls()));
        p.put("mail.smtp.ssl.enable", String.valueOf(smtp.ssl()));
        p.put("mail.smtp.connectiontimeout", String.valueOf(smtp.connectTimeout().toMillis()));
        p.put("mail.smtp.timeout", String.valueOf(smtp.timeout().toMillis()));
        p.put("mail.smtp.writetimeout", String.valueOf(smtp.timeout().toMillis()));
    }

    @Override
    public void send(OutgoingMail mail) {
        try {
            MimeMessage message = sender.createMimeMessage();
            boolean multipart = !mail.attachments().isEmpty() || mail.html() != null;
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(props.from(), props.fromName(), StandardCharsets.UTF_8.name()));
            String replyTo = mail.replyTo() != null ? mail.replyTo() : props.replyTo();
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setTo(mail.to().toArray(String[]::new));
            helper.setSubject(mail.subject());
            if (mail.html() != null) {
                // HTML only. The plain text of a message is for the log and for tests, it is not sent.
                // The pattern bands are small pictures sent inside the email, so they show without a web address to fetch them from.
                MailRenderer.Prepared prepared = MailRenderer.prepare(mail.html());
                helper.setText(prepared.html(), true);
                for (java.util.Map.Entry<String, byte[]> picture : prepared.images().entrySet()) {
                    helper.addInline(picture.getKey(), new org.springframework.core.io.ByteArrayResource(picture.getValue()), "image/png");
                }
            } else {
                helper.setText(mail.text(), false);
            }
            for (OutgoingMail.Attachment a : mail.attachments()) {
                helper.addAttachment(a.fileName(), new org.springframework.core.io.ByteArrayResource(a.data()), a.contentType());
            }
            sender.send(message);
        } catch (MailException e) {
            throw new MailDeliveryException("The mail server did not accept the email: " + brief(e), e);
        } catch (Exception e) {
            throw new MailDeliveryException("The email could not be prepared: " + brief(e), e);
        }
    }

    private static String brief(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String m = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        m = m.replaceAll("[\\r\\n]+", " ");
        return m.length() > 160 ? m.substring(0, 160) : m;
    }

    @Override
    public String describe() {
        return "SMTP " + props.smtp().host() + ":" + props.smtp().port();
    }

    @Override
    public boolean delivers() {
        return true;
    }
}