package com.weiver.global.event.publisher;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.weiver.global.event.config.RabbitMqProperties;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {

    private static final String TRACE_ID_HEADER = "trace_id";

    private final RabbitMqProperties properties = new RabbitMqProperties(
            "domain.events", "domain.events.dlx",
            "spring.domain.events.queue", "spring.domain.events.dlq",
            "ai.domain.events.queue", "ai.domain.events.dlq"
    );

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Captor
    private ArgumentCaptor<MessagePostProcessor> postProcessorCaptor;

    @Captor
    private ArgumentCaptor<CorrelationData> correlationDataCaptor;

    private DomainEventPublisher publisher;
    private Logger publisherLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        publisher = new DomainEventPublisher(rabbitTemplate, properties);
        publisherLogger = (Logger) LoggerFactory.getLogger(DomainEventPublisher.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        publisherLogger.addAppender(logAppender);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        publisherLogger.detachAppender(logAppender);
        MDC.clear();
    }

    @Test
    @DisplayName("MDC에 traceId가 있으면 그 값을 trace_id 메시지 헤더에 싣는다")
    void publish_WithMdcTraceId_PutsItInHeader() throws Exception {
        // given
        MDC.put("traceId", "http-123");

        // when
        publisher.publish(requestEnvelope());

        // then
        assertThat(publishedTraceIdHeader()).isEqualTo("http-123");
    }

    @Test
    @DisplayName("MDC에 traceId가 없으면 새 traceId를 만들어 trace_id 메시지 헤더에 싣는다")
    void publish_WithoutMdcTraceId_GeneratesNewOne() throws Exception {
        // given
        // MDC 비어 있음 (스케줄러·전용 스레드풀 스레드)

        // when
        publisher.publish(requestEnvelope());

        // then
        assertThat(publishedTraceIdHeader()).isNotBlank();
    }

    @Test
    @DisplayName("발행 성공 로그는 콜백 스레드에 MDC가 없어도 헤더와 같은 traceId를 남긴다")
    void publish_SuccessLog_UsesSameTraceIdWithoutCallbackMdc() throws Exception {
        // given
        MDC.put("traceId", "http-123");
        publisher.publish(requestEnvelope());
        String headerTraceId = publishedTraceIdHeader();

        // when
        completeOnOtherThread(capturedFuture(), new CorrelationData.Confirm(true, null), null);

        // then
        ILoggingEvent event = singleLog("RabbitMQ event published");
        assertThat(event.getFormattedMessage()).contains("traceId=" + headerTraceId);
        assertThat(event.getMDCPropertyMap()).doesNotContainKey("traceId");
    }

    @Test
    @DisplayName("MDC가 없는 스레드에서 발행해도 발행 성공 로그에 헤더에 실은 새 traceId를 남긴다")
    void publish_SuccessLog_WithGeneratedTraceId() throws Exception {
        // given
        publisher.publish(requestEnvelope());
        String headerTraceId = publishedTraceIdHeader();

        // when
        completeOnOtherThread(capturedFuture(), new CorrelationData.Confirm(true, null), null);

        // then
        assertThat(singleLog("RabbitMQ event published").getFormattedMessage())
                .contains("traceId=" + headerTraceId);
    }

    @Test
    @DisplayName("브로커가 nack하면 발행 실패 로그에 같은 traceId를 남긴다")
    void publish_NackLog_UsesSameTraceId() throws Exception {
        // given
        MDC.put("traceId", "http-123");
        publisher.publish(requestEnvelope());

        // when
        completeOnOtherThread(capturedFuture(), new CorrelationData.Confirm(false, "nack-reason"), null);

        // then
        assertThat(singleLog("RabbitMQ publish not acknowledged").getFormattedMessage())
                .contains("traceId=http-123");
    }

    @Test
    @DisplayName("publisher confirm이 실패하면 confirm 실패 로그에 같은 traceId를 남긴다")
    void publish_ConfirmFailureLog_UsesSameTraceId() throws Exception {
        // given
        MDC.put("traceId", "http-123");
        publisher.publish(requestEnvelope());

        // when
        completeOnOtherThread(capturedFuture(), null, new IllegalStateException("channel closed"));

        // then
        assertThat(singleLog("RabbitMQ publish confirm failed").getFormattedMessage())
                .contains("traceId=http-123");
    }

    private EventEnvelope<Map<String, Object>> requestEnvelope() {
        return EventEnvelope.request(
                EventType.APPLICANT_ANALYSIS_REQUESTED,
                Map.of("applicant_id", 1L),
                "event-1"
        );
    }

    private String publishedTraceIdHeader() throws Exception {
        then(rabbitTemplate).should().convertAndSend(
                eq("domain.events"),
                eq("applicant.analysis.requested"),
                any(Object.class),
                postProcessorCaptor.capture(),
                correlationDataCaptor.capture()
        );
        Message message = postProcessorCaptor.getValue()
                .postProcessMessage(new Message(new byte[0], new MessageProperties()));
        return message.getMessageProperties().getHeader(TRACE_ID_HEADER);
    }

    private CompletableFuture<CorrelationData.Confirm> capturedFuture() {
        then(rabbitTemplate).should().convertAndSend(
                any(String.class),
                any(String.class),
                any(Object.class),
                any(MessagePostProcessor.class),
                correlationDataCaptor.capture()
        );
        return correlationDataCaptor.getValue().getFuture();
    }

    private void completeOnOtherThread(
            CompletableFuture<CorrelationData.Confirm> future,
            CorrelationData.Confirm confirm,
            Throwable failure
    ) throws InterruptedException {
        Thread callbackThread = new Thread(() -> {
            MDC.clear();
            if (failure != null) {
                future.completeExceptionally(failure);
            } else {
                future.complete(confirm);
            }
        });
        callbackThread.start();
        callbackThread.join();
    }

    private ILoggingEvent singleLog(String messagePrefix) {
        return logAppender.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith(messagePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("log not found: " + messagePrefix));
    }
}
