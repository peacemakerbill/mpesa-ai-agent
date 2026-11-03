package com.darajaagentx.api.mail;

import com.darajaagentx.api.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
