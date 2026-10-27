package com.weiver.global.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rabbitmq.client.Channel;
import com.weiver.global.event.config.RabbitMqProperties;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.global.event.validation.EventEnvelopeValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DomainEventListenerTest {

    private static final String TRACE_ID_HEADER = "trace_id";
    private static final long DELIVERY_TAG = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock
    private DomainEventDispatcher dispatcher;

    @Mock
    private Channel channel;

    private DomainEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new DomainEventListener(objectMapper, new EventEnvelopeValidator(), dispatcher);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("trace_id 헤더 값과 봉투의 eventId·eventType·correlationId가 dispatch 시점 MDC에 들어간다")
    void onMessage_PutsTraceIdHeaderAndEnvelopeFieldsInMdc() throws Exception {
        // given
        Map<String, String> mdcAtDispatch = captureMdcAtDispatch();
        Message message = message(resultEnvelope(), "trace-abc");

        // when
        listener.onMessage(message, channel);

        // then
        assertThat(mdcAtDispatch)
                .containsEntry("traceId", "trace-abc")
                .containsEntry("eventId", "result-1")
                .containsEntry("eventType", "APPLICANT_ANALYSIS_COMPLETED")
                .containsEntry("correlationId", "request-1");
        then(channel).should().basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("trace_id 헤더가 없으면 새 traceId를 만들어 dispatch 시점 MDC에 넣는다")
    void onMessage_WithoutTraceIdHeader_GeneratesNewTraceId() throws Exception {
        // given
        Map<String, String> mdcAtDispatch = captureMdcAtDispatch();
        Message message = message(resultEnvelope(), null);

        // when
        listener.onMessage(message, channel);

        // then
        assertThat(mdcAtDispatch.get("traceId")).isNotBlank();
    }

    @Test
    @DisplayName("정상 처리 후 리스너가 넣은 MDC 키만 지우고 원래 있던 다른 MDC 키는 남긴다")
    void onMessage_Success_RemovesOnlyOwnMdcKeys() throws Exception {
        // given
        MDC.put("otherKey", "keep-me");
        Map<String, String> mdcAtDispatch = captureMdcAtDispatch();
        Message message = message(resultEnvelope(), "trace-abc");

        // when
        listener.onMessage(message, channel);

        // then
        assertThat(mdcAtDispatch).containsEntry("traceId", "trace-abc");
        assertListenerKeysRemovedAndOtherKeyKept();
        then(channel).should().basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("핸들러 예외로 DLQ로 보낸 뒤에도 리스너가 넣은 MDC 키만 지우고 다른 MDC 키는 남긴다")
    void onMessage_HandlerFailure_RemovesOnlyOwnMdcKeys() throws Exception {
        // given
        MDC.put("otherKey", "keep-me");
        Map<String, String> mdcAtDispatch = new HashMap<>();
        willAnswer(invocation -> {
            mdcAtDispatch.putAll(MDC.getCopyOfContextMap());
            throw new NonRetryableEventException("handler failed");
        }).given(dispatcher).dispatch(any());
        Message message = message(resultEnvelope(), "trace-abc");

        // when
        listener.onMessage(message, channel);

        // then
        assertThat(mdcAtDispatch).containsEntry("traceId", "trace-abc");
        then(channel).should().basicNack(DELIVERY_TAG, false, false);
        assertListenerKeysRemovedAndOtherKeyKept();
    }

    @Test
    @DisplayName("봉투를 파싱하지 못해도 실패 로그 시점에 traceId가 MDC에 있고 처리 후 지워진다")
    void onMessage_UnparsableBody_StillSetsTraceIdThenRemovesIt() throws Exception {
        // given
        MDC.put("otherKey", "keep-me");
        Map<String, String> mdcAtNack = new HashMap<>();
        willAnswer(invocation -> {
            mdcAtNack.putAll(MDC.getCopyOfContextMap());
            return null;
        }).given(channel).basicNack(DELIVERY_TAG, false, false);
        Message message = rawMessage("not-json".getBytes(), "trace-abc");

        // when
        listener.onMessage(message, channel);

        // then
        assertThat(mdcAtNack).containsEntry("traceId", "trace-abc").doesNotContainKey("eventId");
        then(dispatcher).shouldHaveNoInteractions();
        assertListenerKeysRemovedAndOtherKeyKept();
    }

    @Test
    @DisplayName("리스너 안에서 발행하는 후속 이벤트는 받은 trace_id를 그대로 헤더에 싣는다")
    void onMessage_FollowUpPublish_InheritsTraceId() throws Exception {
        // given
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        DomainEventPublisher publisher = new DomainEventPublisher(rabbitTemplate, new RabbitMqProperties(
                "domain.events", "domain.events.dlx",
                "spring.domain.events.queue", "spring.domain.events.dlq",
                "ai.domain.events.queue", "ai.domain.events.dlq"
        ));
        willAnswer(invocation -> {
            publisher.publishAfterCommit(EventEnvelope.request(
                    EventType.INTERVIEW_REPORT_REQUESTED, Map.of("interview_session_id", 1L), "follow-up-1"));
            return null;
        }).given(dispatcher).dispatch(any());
        Message message = message(resultEnvelope(), "trace-abc");

        // when
        listener.onMessage(message, channel);

        // then
        ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        then(rabbitTemplate).should().convertAndSend(
                any(String.class),
                any(String.class),
                any(Object.class),
                postProcessorCaptor.capture(),
                any(CorrelationData.class)
        );
        Message published = postProcessorCaptor.getValue()
                .postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat((String) published.getMessageProperties().getHeader(TRACE_ID_HEADER)).isEqualTo("trace-abc");
    }

    private Map<String, String> captureMdcAtDispatch() {
        Map<String, String> captured = new HashMap<>();
        willAnswer(invocation -> {
            captured.putAll(MDC.getCopyOfContextMap());
            return null;
        }).given(dispatcher).dispatch(any());
        return captured;
    }

    private void assertListenerKeysRemovedAndOtherKeyKept() {
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("eventId")).isNull();
        assertThat(MDC.get("eventType")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("otherKey")).isEqualTo("keep-me");
    }

    private EventEnvelope<Map<String, Object>> resultEnvelope() {
        return EventEnvelope.result(
                EventType.APPLICANT_ANALYSIS_COMPLETED,
                Map.of("applicant_id", 1L),
                "result-1",
                "request-1"
        );
    }

    private Message message(EventEnvelope<?> envelope, String traceIdHeader) throws Exception {
        return rawMessage(objectMapper.writeValueAsBytes(envelope), traceIdHeader);
    }

    private Message rawMessage(byte[] body, String traceIdHeader) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        if (traceIdHeader != null) {
            properties.setHeader(TRACE_ID_HEADER, traceIdHeader);
        }
        return new Message(body, properties);
    }
}
