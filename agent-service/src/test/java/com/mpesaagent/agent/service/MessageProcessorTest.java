package com.mpesaagent.agent.service;

import com.mpesaagent.agent.TestSupport;
import com.mpesaagent.agent.chat.ChatChannel;
import com.mpesaagent.agent.client.TextToSpeechClient;
import com.mpesaagent.agent.dto.InboundMessage;
import com.mpesaagent.agent.dto.InboundMessage.Kind;
import com.mpesaagent.agent.model.Identity;
import com.mpesaagent.agent.model.Prepared;
import com.mpesaagent.agent.model.Role;
import com.mpesaagent.agent.store.ConversationStore;
import com.mpesaagent.agent.store.InMemoryStateStore;
import com.mpesaagent.agent.store.PhoneLock;
import com.mpesaagent.agent.store.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageProcessorTest {

    private static final String PHONE = "254700000001";

    @Mock PhoneLock lock;
    @Mock RateLimiter limiter;
    @Mock RoleService roles;
    @Mock MediaProcessor media;
    @Mock AgentOrchestrator orchestrator;
    @Mock ButtonHandler buttons;
    @Mock ReplySender sender;
    @Mock ChatChannel channel;
    @Mock TextToSpeechClient tts;

    private ConversationStore conversations;
    private MessageProcessor processor;

    @BeforeEach
    void setUp() {
        conversations = new ConversationStore(new InMemoryStateStore(), TestSupport.mapper(), TestSupport.props());
        processor = new MessageProcessor(lock, limiter, conversations, roles, media, orchestrator, buttons,
                new MenuService(TestSupport.business(), TestSupport.props()), sender, channel, tts);
        lenient().when(lock.acquire(anyString(), any())).thenReturn(Optional.of("token"));
        lenient().when(limiter.allow(anyString())).thenReturn(true);
        lenient().when(roles.identify(PHONE)).thenReturn(new Identity(PHONE, Role.CUSTOMER, null));
    }

    private static InboundMessage text(String body) {
        return InboundMessage.text("wamid.1", PHONE, body);
    }

    private static InboundMessage tap(Kind kind, String id, String title) {
        return new InboundMessage("wamid.2", PHONE, null, 0L, kind, null, null, null, null, null, null,
                null, null, null, id, title, null, null, null);
    }

    private static Prepared prepared(String text) {
        return Prepared.of(List.of(Map.of("type", "text", "text", text)), text, false);
    }

    @Test
    void aGreetingShowsTheMenuWithoutCallingTheModel() {
        processor.process(text("Hi!"));

        verify(sender).list(eq(PHONE), anyString(), anyString(), anyString(), any());
        verifyNoInteractions(orchestrator, media);
    }

    @Test
    void anOrdinaryMessageIsAnsweredAndRemembered() {
        when(media.prepare(any())).thenReturn(prepared("I want to pay 500"));
        when(orchestrator.respond(any(), any())).thenReturn("Sure, how much?");

        processor.process(text("I want to pay 500"));

        verify(sender).text(PHONE, "Sure, how much?");
        assertThat(conversations.history(PHONE)).extracting("role").containsExactly("user", "assistant");
        verify(lock).release(PHONE, "token");
    }

    @Test
    void buttonsQueuedByToolsGoOutAfterTheAssistantsSentence() {
        when(media.prepare(any())).thenReturn(prepared("pay 500"));
        when(orchestrator.respond(any(), any())).thenAnswer(inv -> {
            ToolContext ctx = inv.getArgument(0);
            ctx.defer(s -> s.buttons(PHONE, "Confirm?", null, List.of(new ChatChannel.Button("PAY:abc", "Pay"))));
            return "Please confirm";
        });

        processor.process(text("pay 500"));

        InOrder order = inOrder(sender);
        order.verify(sender).text(PHONE, "Please confirm");
        order.verify(sender).buttons(eq(PHONE), eq("Confirm?"), any(), any());
    }

    @Test
    void aPayButtonGoesStraightToTheButtonHandler() {
        processor.process(tap(Kind.BUTTON_REPLY, "PAY:abc", "Pay"));

        verify(buttons).handle(PHONE, "PAY:abc");
        verifyNoInteractions(orchestrator, media);
    }

    @Test
    void aMenuChoiceIsTreatedAsIfThePersonHadTypedIt() {
        when(media.prepare(any())).thenReturn(prepared("x"));
        when(orchestrator.respond(any(), any())).thenReturn("ok");

        processor.process(tap(Kind.LIST_REPLY, "MENU:pay", "Make a payment"));

        ArgumentCaptor<InboundMessage> seen = ArgumentCaptor.forClass(InboundMessage.class);
        verify(media).prepare(seen.capture());
        assertThat(seen.getValue().kind()).isEqualTo(Kind.TEXT);
        assertThat(seen.getValue().text()).isEqualTo("I want to make a payment.");
        verify(buttons, never()).handle(anyString(), anyString());
    }

    @Test
    void reactionsAreIgnored() {
        processor.process(tap(Kind.REACTION, null, null));

        verifyNoInteractions(orchestrator, media, buttons);
        verify(sender, never()).text(anyString(), anyString());
    }

    @Test
    void aMessageFloodGetsASlowDownNotice() {
        when(limiter.allow(PHONE)).thenReturn(false);

        processor.process(text("pay 500"));

        verify(sender).text(PHONE, MessageProcessor.SLOW_DOWN);
        verifyNoInteractions(orchestrator, media);
    }

    @Test
    void whenTheBusinessCannotUnderstandTheFileTheDirectReplyIsSent() {
        when(media.prepare(any())).thenReturn(Prepared.reply("I cannot read that kind of file."));

        processor.process(text("see attached"));

        verify(sender).text(PHONE, "I cannot read that kind of file.");
        verifyNoInteractions(orchestrator);
    }

    @Test
    void nothingIsSentForAMessageThatNeedsNoAnswer() {
        when(media.prepare(any())).thenReturn(Prepared.skip());

        processor.process(text("hello there friend"));

        verifyNoInteractions(orchestrator);
        verify(sender, never()).text(anyString(), anyString());
    }

    @Test
    void anUnexpectedErrorGivesTheSafeReplyAndReleasesTheLock() {
        when(media.prepare(any())).thenReturn(prepared("pay 500"));
        when(orchestrator.respond(any(), any())).thenThrow(new RuntimeException("boom"));

        processor.process(text("pay 500"));

        verify(sender).text(PHONE, MessageProcessor.ERROR_REPLY);
        verify(lock).release(PHONE, "token");
    }

    @Test
    void ifThePreviousMessageIsStillBeingHandledThePersonIsToldToWait() {
        when(lock.acquire(anyString(), any())).thenReturn(Optional.empty());

        processor.process(text("pay 500"));

        verify(sender).text(PHONE, MessageProcessor.BUSY);
        verifyNoInteractions(orchestrator, media);
        verify(lock, never()).release(anyString(), anyString());
    }

    @Test
    void aSpokenQuestionGetsASpokenAnswerWhenSpeechIsEnabled() {
        when(media.prepare(any())).thenReturn(Prepared.of(List.of(Map.of("type", "text", "text", "t")), "[voice] pay", true));
        when(orchestrator.respond(any(), any())).thenReturn("Your payment is complete.");
        when(tts.enabled()).thenReturn(true);
        when(tts.speak(anyString())).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        processor.process(new InboundMessage("wamid.3", PHONE, null, 0L, Kind.AUDIO, null, "m1", "audio/ogg", null, null, true,
                null, null, null, null, null, null, null, null));

        verify(sender).text(PHONE, "Your payment is complete.");
        verify(sender).audio(eq(PHONE), any());
    }

    @Test
    void noSpokenAnswerWhenButtonsAreWaitingForATap() {
        when(media.prepare(any())).thenReturn(Prepared.of(List.of(Map.of("type", "text", "text", "t")), "[voice] pay", true));
        when(orchestrator.respond(any(), any())).thenAnswer(inv -> {
            ((ToolContext) inv.getArgument(0)).defer(s -> s.text(PHONE, "buttons"));
            return "Please confirm";
        });

        processor.process(new InboundMessage("wamid.4", PHONE, null, 0L, Kind.AUDIO, null, "m1", "audio/ogg", null, null, true,
                null, null, null, null, null, null, null, null));

        verifyNoInteractions(tts);
        verify(sender, never()).audio(anyString(), any());
    }

    @Test
    void theTypingLineIsClearedWhenTheWorkIsDone() {
        processor.process(tap(Kind.REACTION, null, null));

        verify(channel, never()).typing(PHONE, true);
        verify(channel, atLeastOnce()).typing(PHONE, false);
    }
}
