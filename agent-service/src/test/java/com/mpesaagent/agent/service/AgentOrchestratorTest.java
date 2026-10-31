package com.mpesaagent.agent.service;

import com.mpesaagent.agent.TestSupport;
import com.mpesaagent.agent.client.ClaudeClient;
import com.mpesaagent.agent.config.AnthropicProperties;
import com.mpesaagent.agent.model.Identity;
import com.mpesaagent.agent.model.Prepared;
import com.mpesaagent.agent.model.Role;
import com.mpesaagent.agent.store.ConversationStore;
import com.mpesaagent.agent.store.InMemoryStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    private static final String PHONE = "254700000001";
    private final ObjectMapper mapper = TestSupport.mapper();

    @Mock ClaudeClient claude;
    @Mock ToolExecutor executor;

    private ConversationStore conversations;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        conversations = new ConversationStore(new InMemoryStateStore(), mapper, TestSupport.props());
        orchestrator = new AgentOrchestrator(claude, new ToolCatalog(mapper), executor, conversations,
                new SystemPrompt(TestSupport.business(), TestSupport.props()),
                new AnthropicProperties("key", "http://x", "claude-sonnet-5-5", 512, "2023-06-01"),
                TestSupport.props(), mapper);
    }

    private ToolContext ctx(Role role) {
        return new ToolContext(new Identity(PHONE, role, null), "wamid.1");
    }

    private static Prepared hello() {
        return Prepared.of(List.of(Map.of("type", "text", "text", "hello")), "hello", false);
    }

    private JsonNode text(String stop, String answer) {
        try {
            return mapper.readTree("{\"stop_reason\":\"" + stop + "\",\"content\":[{\"type\":\"text\",\"text\":\"" + answer + "\"}]}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode toolUse(String id, String name) {
        try {
            return mapper.readTree("{\"stop_reason\":\"tool_use\",\"content\":[{\"type\":\"text\",\"text\":\"One moment\"},"
                    + "{\"type\":\"tool_use\",\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"input\":{\"amount\":500}}]}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> toolNames(ObjectNode request) {
        List<String> names = new ArrayList<>();
        request.path("tools").forEach(t -> names.add(t.path("name").asString()));
        return names;
    }

    @Test
    void plainAnswerIsReturnedAndNoToolIsRun() {
        when(claude.createMessage(any())).thenReturn(text("end_turn", "Hello there"));

        String answer = orchestrator.respond(ctx(Role.CUSTOMER), hello());

        assertThat(answer).isEqualTo("Hello there");
        verify(claude, times(1)).createMessage(any());
    }

    @Test
    void toolUseIsExecutedAndItsResultSentBackToTheModel() {
        when(claude.createMessage(any())).thenReturn(toolUse("tu_1", ToolCatalog.PROPOSE_PAYMENT), text("end_turn", "Please confirm"));
        when(executor.execute(any(), eq(ToolCatalog.PROPOSE_PAYMENT), any())).thenReturn(new ToolExecutor.Result("queued", false));

        String answer = orchestrator.respond(ctx(Role.CUSTOMER), hello());

        assertThat(answer).isEqualTo("Please confirm");
        ArgumentCaptor<ObjectNode> requests = ArgumentCaptor.forClass(ObjectNode.class);
        verify(claude, times(2)).createMessage(requests.capture());
        // the captured object is the same instance each round, so check the final shape
        JsonNode messages = requests.getAllValues().get(1).path("messages");
        JsonNode last = messages.get(messages.size() - 1);
        assertThat(last.path("role").asString()).isEqualTo("user");
        assertThat(last.path("content").get(0).path("type").asString()).isEqualTo("tool_result");
        assertThat(last.path("content").get(0).path("tool_use_id").asString()).isEqualTo("tu_1");
        assertThat(last.path("content").get(0).path("content").asString()).isEqualTo("queued");
    }

    @Test
    void toolErrorsAreFlaggedToTheModel() {
        when(claude.createMessage(any())).thenReturn(toolUse("tu_1", ToolCatalog.PROPOSE_PAYMENT), text("end_turn", "Sorry"));
        when(executor.execute(any(), any(), any())).thenReturn(new ToolExecutor.Result("amount too high", true));

        orchestrator.respond(ctx(Role.CUSTOMER), hello());

        ArgumentCaptor<ObjectNode> requests = ArgumentCaptor.forClass(ObjectNode.class);
        verify(claude, times(2)).createMessage(requests.capture());
        JsonNode messages = requests.getValue().path("messages");
        assertThat(messages.get(messages.size() - 1).path("content").get(0).path("is_error").asBoolean()).isTrue();
    }

    @Test
    void theModelOnlySeesToolsForTheCallersRole() {
        when(claude.createMessage(any())).thenReturn(text("end_turn", "ok"));

        orchestrator.respond(ctx(Role.CUSTOMER), hello());
        ArgumentCaptor<ObjectNode> customer = ArgumentCaptor.forClass(ObjectNode.class);
        verify(claude).createMessage(customer.capture());
        List<String> customerTools = toolNames(customer.getValue());

        assertThat(customerTools).contains(ToolCatalog.PROPOSE_PAYMENT)
                .doesNotContain(ToolCatalog.REQUEST_PAYOUT, ToolCatalog.ACCOUNT_BALANCE, ToolCatalog.REQUEST_REVERSAL,
                        ToolCatalog.LOOKUP_TRANSACTION, ToolCatalog.REVIEW_APPROVAL);
    }

    @Test
    void adminsGetEveryTool() {
        when(claude.createMessage(any())).thenReturn(text("end_turn", "ok"));

        orchestrator.respond(ctx(Role.ADMIN), hello());

        ArgumentCaptor<ObjectNode> request = ArgumentCaptor.forClass(ObjectNode.class);
        verify(claude).createMessage(request.capture());
        assertThat(toolNames(request.getValue())).contains(ToolCatalog.ACCOUNT_BALANCE, ToolCatalog.REQUEST_REVERSAL,
                ToolCatalog.REQUEST_PAYOUT, ToolCatalog.REVIEW_APPROVAL, ToolCatalog.PROPOSE_PAYMENT);
    }

    @Test
    void systemPromptIsTwoBlocksWithTheStaticOneCached() {
        when(claude.createMessage(any())).thenReturn(text("end_turn", "ok"));

        orchestrator.respond(ctx(Role.CUSTOMER), hello());

        ArgumentCaptor<ObjectNode> request = ArgumentCaptor.forClass(ObjectNode.class);
        verify(claude).createMessage(request.capture());
        JsonNode system = request.getValue().path("system");
        assertThat(system.size()).isEqualTo(2);
        assertThat(system.get(0).path("cache_control").path("type").asString()).isEqualTo("ephemeral");
        assertThat(system.get(1).has("cache_control")).isFalse();
        assertThat(request.getValue().path("model").asString()).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void historyComesFirstAndStartsWithAUserTurn() {
        conversations.append(PHONE, "assistant", "stray");
        conversations.append(PHONE, "user", "earlier question");
        conversations.append(PHONE, "assistant", "earlier answer");
        when(claude.createMessage(any())).thenReturn(text("end_turn", "ok"));

        orchestrator.respond(ctx(Role.CUSTOMER), hello());

        ArgumentCaptor<ObjectNode> request = ArgumentCaptor.forClass(ObjectNode.class);
        verify(claude).createMessage(request.capture());
        JsonNode messages = request.getValue().path("messages");
        assertThat(messages.size()).isEqualTo(3);
        assertThat(messages.get(0).path("role").asString()).isEqualTo("user");
        assertThat(messages.get(0).path("content").asString()).isEqualTo("earlier question");
        assertThat(messages.get(2).path("content").get(0).path("text").asString()).isEqualTo("hello");
    }

    @Test
    void givesUpAfterTooManyToolRounds() {
        when(claude.createMessage(any())).thenReturn(toolUse("tu", ToolCatalog.CHECK_PAYMENT_STATUS));
        when(executor.execute(any(), any(), any())).thenReturn(new ToolExecutor.Result("pending", false));

        String answer = orchestrator.respond(ctx(Role.CUSTOMER), hello());

        assertThat(answer).isEqualTo(AgentOrchestrator.FALLBACK);
        verify(claude, times(TestSupport.props().maxToolRounds() + 1)).createMessage(any());
    }

    @Test
    void refusalWithoutTextGetsAPoliteMessage() {
        when(claude.createMessage(any())).thenReturn(mapperRead("{\"stop_reason\":\"refusal\",\"content\":[]}"));

        assertThat(orchestrator.respond(ctx(Role.CUSTOMER), hello())).contains("can't help with that");
    }

    @Test
    void cutOffAnswerSaysSo() {
        when(claude.createMessage(any())).thenReturn(text("max_tokens", "Part one"));

        assertThat(orchestrator.respond(ctx(Role.CUSTOMER), hello())).startsWith("Part one").contains("cut short");
    }

    @Test
    void emptyAnswerFallsBack() {
        when(claude.createMessage(any())).thenReturn(mapperRead("{\"stop_reason\":\"end_turn\",\"content\":[]}"));

        assertThat(orchestrator.respond(ctx(Role.CUSTOMER), hello())).isEqualTo(AgentOrchestrator.FALLBACK);
    }

    private JsonNode mapperRead(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}