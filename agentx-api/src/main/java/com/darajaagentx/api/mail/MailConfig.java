package com.darajaagentx.api.mail;

import com.darajaagentx.api.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Chooses the mail transport from agentx.mail.transport: smtp or log. */
@Configuration(proxyBeanMethods = false)
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    MailTransport mailTransport(MailProperties props) {
        String type = props.transport() == null ? "log" : props.transport().trim().toLowerCase(Locale.ROOT);
        MailTransport transport = switch (type) {
            case "smtp" -> new SmtpMailTransport(props);
            case "log" -> new LogMailTransport();
            default -> throw new IllegalStateException("agentx.mail.transport must be smtp or log, not \"" + props.transport() + "\".");
        };
        log.info("Email: {}", transport.describe());
        return transport;
    }

    /**
     * The company details in the footer of every email (agentx.brand.*). The email files read them through
     * {@link MailRenderer}, so installing them here is all it takes. A blank support address falls back to the reply-to
     * address, then to the sender address unless that is the placeholder no-reply@localhost.
     */
    @Bean
    MailBranding mailBranding(MailProperties props,
                              @Value("${agentx.brand.support-email:}") String supportEmail,
                              @Value("${agentx.brand.website:}") String website,
                              @Value("${agentx.brand.tagline:M-Pesa payments and reports, by chat}") String tagline,
                              @Value("${agentx.brand.company:}") String company,
                              @Value("${agentx.brand.address:}") String address,
                              @Value("${agentx.brand.mark:}") String mark,
                              @Value("${agentx.brand.templates-dir:}") String templatesDir) {
        String support = supportEmail == null ? "" : supportEmail.trim();
        if (support.isEmpty() && props.replyTo() != null && !props.replyTo().isBlank()) {
            support = props.replyTo().trim();
        }
        if (support.isEmpty() && props.from() != null && !props.from().isBlank() && !props.from().trim().endsWith("@localhost")) {
            support = props.from().trim();
        }
        MailBranding branding = new MailBranding(support, website, tagline, company, address, mark, templatesDir);
        MailRenderer.install(branding);
        if (!branding.templatesDir().isEmpty()) {
            log.info("Email templates are read from {} (files missing there come from the program)", branding.templatesDir());
        }
        return branding;
    }

    /** Sign in alerts and payment notices are sent on these threads, so a slow mail server never holds up a sign in. */
    @Bean(destroyMethod = "shutdown")
    ExecutorService mailExecutor() {
        AtomicInteger n = new AtomicInteger();
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "agentx-mail-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}