package com.darajaagentx.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AgentWakeTest {

    private static final String HEALTH = "http://agent.test" + AgentWake.HEALTH_PATH;

    /** A clock the test moves by hand. */
    private static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-11-14T12:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MockRestServiceServer server;
    private MovingClock clock;
    private AgentWake wake;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://agent.test");
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MovingClock();
        wake = new AgentWake(builder.build(), clock);
    }

    @Test
    void aHealthyServiceIsAwake() {
        server.expect(once(), requestTo(HEALTH)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"status\":\"UP\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(wake.awake()).isTrue();
        server.verify();
    }

    @Test
    void aServiceThatIsStillStartingIsNotAwakeAndIsAskedAgainAfterAFewSeconds() {
        server.expect(once(), requestTo(HEALTH)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(wake.awake()).isFalse();
        // Phones asking again at once do not send another ping
        assertThat(wake.awake()).isFalse();
        server.verify();

        clock.advance(Duration.ofSeconds(4));
        server.reset();
        server.expect(once(), requestTo(HEALTH)).andRespond(withSuccess());

        assertThat(wake.awake()).isTrue();
        server.verify();
    }

    @Test
    void aServiceThatCannotBeReachedIsNotAwake() {
        server.expect(once(), requestTo(HEALTH)).andRespond(withException(new IOException("connection refused")));

        assertThat(wake.awake()).isFalse();
        server.verify();
    }

    @Test
    void aHealthyAnswerIsRememberedForAWhileThenChecked() {
        server.expect(once(), requestTo(HEALTH)).andRespond(withSuccess());
        assertThat(wake.awake()).isTrue();

        clock.advance(Duration.ofSeconds(10));
        assertThat(wake.awake()).isTrue();   // no second request, the mock would fail on one
        server.verify();

        clock.advance(Duration.ofSeconds(10));
        server.reset();
        server.expect(once(), requestTo(HEALTH)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThat(wake.awake()).isFalse();   // it went to sleep again
        server.verify();
    }

    @Test
    void theProbeNeverCarriesTheInternalKey() {
        server.expect(once(), requestTo(HEALTH))
                .andExpect(request -> assertThat(request.getHeaders().getFirst("X-Internal-Key")).isNull())
                .andRespond(withSuccess());

        wake.awake();
        server.verify();
    }

    @Test
    void aServiceThatDoesNotAnswerIsAlsoWokenByOneLongRequestInTheBackground() {
        RestClient.Builder quickBuilder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer quick = MockRestServiceServer.bindTo(quickBuilder).build();
        RestClient.Builder longBuilder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer longRun = MockRestServiceServer.bindTo(longBuilder).build();
        List<Runnable> queued = new ArrayList<>();
        AgentWake sleeping = new AgentWake(quickBuilder.build(), longBuilder.build(), clock, queued::add);

        quick.expect(once(), requestTo(HEALTH)).andRespond(withException(new IOException("timed out")));
        assertThat(sleeping.awake()).isFalse();
        assertThat(queued).hasSize(1);

        clock.advance(Duration.ofSeconds(4));
        quick.reset();
        quick.expect(once(), requestTo(HEALTH)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThat(sleeping.awake()).isFalse();
        assertThat(queued).hasSize(1);   // only one long request is open at a time
        quick.verify();

        // The long request ends because the service answered
        longRun.expect(once(), requestTo(HEALTH)).andRespond(withSuccess());
        queued.get(0).run();
        longRun.verify();

        assertThat(sleeping.awake()).isTrue();   // remembered, so no new ping
    }

    @Test
    void aLongRequestThatEndsWithoutAnAnswerIsStartedAgainOnTheNextCheck() {
        RestClient.Builder quickBuilder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer quick = MockRestServiceServer.bindTo(quickBuilder).build();
        RestClient.Builder longBuilder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer longRun = MockRestServiceServer.bindTo(longBuilder).build();
        List<Runnable> queued = new ArrayList<>();
        AgentWake sleeping = new AgentWake(quickBuilder.build(), longBuilder.build(), clock, queued::add);

        quick.expect(once(), requestTo(HEALTH)).andRespond(withException(new IOException("timed out")));
        assertThat(sleeping.awake()).isFalse();

        longRun.expect(once(), requestTo(HEALTH)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        queued.get(0).run();

        clock.advance(Duration.ofSeconds(4));
        quick.reset();
        quick.expect(once(), requestTo(HEALTH)).andRespond(withException(new IOException("timed out")));
        assertThat(sleeping.awake()).isFalse();
        assertThat(queued).hasSize(2);
    }

    @Test
    void aServiceThatIsAwakeNeverStartsALongRequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer awakeServer = MockRestServiceServer.bindTo(builder).build();
        List<Runnable> queued = new ArrayList<>();
        AgentWake awake = new AgentWake(builder.build(), builder.build(), clock, queued::add);

        awakeServer.expect(once(), requestTo(HEALTH)).andRespond(withSuccess());
        assertThat(awake.awake()).isTrue();
        assertThat(queued).isEmpty();
    }
}
