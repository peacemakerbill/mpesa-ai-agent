package com.darajaagentx.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Lets agent-service start without an Anthropic API key and says so in the log instead of refusing to boot.
 *
 * <p>Without ANTHROPIC_API_KEY the assistant cannot answer: Anthropic refuses every request, so customers get no replies.
 * Everything else in the service (the chat log, file storage, the internal endpoints) still runs. Set the key in
 * agent-service/.env and restart.
 */
@Component
public class AnthropicKeyCheck {

    private static final Logger log = LoggerFactory.getLogger(AnthropicKeyCheck.class);

    private final AnthropicProperties props;

    public AnthropicKeyCheck(AnthropicProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnAtStartup() {
        if (missing(props)) {
            log.warn("ANTHROPIC_API_KEY is not set. agent-service is running, but the assistant cannot reply to customers "
                    + "until you set it in agent-service/.env and restart.");
        }
    }

    static boolean missing(AnthropicProperties p) {
        return p.apiKey() == null || p.apiKey().isBlank();
    }
}