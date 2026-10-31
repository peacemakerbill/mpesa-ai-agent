package com.mganji.agent.service;

import com.mganji.agent.client.ClaudeClient;
import com.mganji.agent.config.AgentProperties;
import com.mganji.agent.config.AnthropicProperties;
import com.mganji.agent.model.ChatTurn;
import com.mganji.agent.model.Prepared;
import com.mganji.agent.store.ConversationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Runs one turn of the conversation with the model: send the history plus the new message, let the model use
 * tools (up to a limit), and return its final words. Tool use blocks are not stored in the history, only text,
 * so a long chat stays cheap and old file contents are never replayed.
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    static final String FALLBACK = "I'm sorry, I had trouble finishing that. Please try again, or ask me to connect you to a person.";

    private final ClaudeClient claude;
    private final ToolCatalog catalog;
    private final ToolExecutor executor;
    private final ConversationStore conversations;
    private final SystemPrompt prompt;
    private final AnthropicProperties anthropic;
    private final AgentProperties props;
    private final ObjectMapper mapper;

    public AgentOrchestrator(ClaudeClient claude, ToolCatalog catalog, ToolExecutor executor,
                             ConversationStore conversations, SystemPrompt prompt,
                             AnthropicProperties anthropic, AgentProperties props, ObjectMapper mapper) {
        this.claude = claude;
        this.catalog = catalog;
        this.executor = executor;
        this.conversations = conversations;
        this.prompt = prompt;
        this.anthropic = anthropic;
        this.props = props;
        this.mapper = mapper;
    }

    public String respond(ToolContext ctx, Prepared input) {
        ArrayNode messages = mapper.createArrayNode();
        for (ChatTurn turn : conversations.history(ctx.phone())) {
            ObjectNode m = messages.addObject();
            m.put("role", turn.role());
            m.put("content", turn.text());
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.set("content", mapper.valueToTree(input.blocks()));

        ArrayNode system = mapper.createArrayNode();
        ObjectNode rules = system.addObject();
        rules.put("type", "text");
        rules.put("text", prompt.rules());
        rules.putObject("cache_control").put("type", "ephemeral");
        ObjectNode context = system.addObject();
        context.put("type", "text");
        context.put("text", prompt.context(ctx.identity(), Instant.now()));

        ArrayNode tools = catalog.toolsFor(ctx.role());

        for (int round = 0; round <= props.maxToolRounds(); round++) {
            ObjectNode request = mapper.createObjectNode();
            request.put("model", anthropic.model());
            request.put("max_tokens", anthropic.maxTokens());
            request.set("system", system);
            request.set("tools", tools);
            request.set("messages", messages);

            JsonNode response = claude.createMessage(request);
            JsonNode content = response.path("content");
            String stop = response.path("stop_reason").asString("");

            if (!"tool_use".equals(stop)) {
                return finalText(content, stop);
            }
            if (round == props.maxToolRounds()) {
                log.warn("Model kept asking for tools after {} rounds, giving up", round);
                break;
            }

            ObjectNode assistant = messages.addObject();
            assistant.put("role", "assistant");
            assistant.set("content", content.deepCopy());

            ArrayNode results = mapper.createArrayNode();
            for (JsonNode block : content) {
                if (!"tool_use".equals(block.path("type").asString())) {
                    continue;
                }
                String name = block.path("name").asString();
                ToolExecutor.Result result = executor.execute(ctx, name, block.path("input"));
                ObjectNode r = results.addObject();
                r.put("type", "tool_result");
                r.put("tool_use_id", block.path("id").asString());
                r.put("content", result.content());
                if (result.error()) {
                    r.put("is_error", true);
                }
            }
            ObjectNode toolTurn = messages.addObject();
            toolTurn.put("role", "user");
            toolTurn.set("content", results);
        }
        return FALLBACK;
    }

    private String finalText(JsonNode content, String stop) {
        StringBuilder text = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asString())) {
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                text.append(block.path("text").asString());
            }
        }
        String answer = text.toString().strip();
        if ("refusal".equals(stop) && answer.isEmpty()) {
            return "I'm sorry, I can't help with that. Is there something about your M-Pesa payments I can help with?";
        }
        if (answer.isEmpty()) {
            return FALLBACK;
        }
        if ("max_tokens".equals(stop)) {
            answer += "\n\n(My answer was cut short. Ask me to continue if you need the rest.)";
        }
        return answer;
    }
}