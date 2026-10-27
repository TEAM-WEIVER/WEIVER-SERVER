package com.weiver.global.event.publisher;

import com.weiver.global.event.config.RabbitMqProperties;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.util.EventHeaders;
import com.weiver.global.event.util.EventRoutingKeys;
import com.weiver.global.logging.util.TraceIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private static final long PUBLISH_CONFIRM_TIMEOUT_SECONDS = 10L;
    private static final String TRACE_ID = "traceId";

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties properties;

    public void publish(EventEnvelope<?> envelope) {
        validateEnvelope(envelope);

        String routingKey = EventRoutingKeys.from(envelope.eventType());
        // confirm 콜백은 다른 스레드에서 돌아 MDC가 없으므로, 발행 스레드에서 읽은 값을 콜백 로그에 직접 넘긴다.
        String traceId = resolveTraceId();
        CorrelationData correlationData = new CorrelationData(envelope.eventId());

        rabbitTemplate.convertAndSend(
                properties.domainExchange(),
                routingKey,
                envelope,
                message -> {
                    message.getMessageProperties().setHeader(EventHeaders.TRACE_ID, traceId);
                    return message;
                },
                correlationData
        );

        correlationData.getFuture().orTimeout(PUBLISH_CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((confirm, ex) -> {
            if (ex != null) {
                log.error("RabbitMQ publish confirm failed. traceId={}, eventId={}, eventType={}, routingKey={}",
                        traceId,
                        envelope.eventId(),
                        envelope.eventType(),
                        routingKey,
                        ex
                );
                return;
            }
            if (confirm == null || !confirm.isAck()) {
                log.error(
                        "RabbitMQ publish not acknowledged. traceId={}, eventId={}, eventType={}, routingKey={}, reason={}",
                        traceId,
                        envelope.eventId(),
                        envelope.eventType(),
                        routingKey,
                        confirm != null ? confirm.getReason() : null
                );
                return;
            }

            log.info(
                    "RabbitMQ event published. traceId={}, eventId={}, eventType={}, correlationId={}, routingKey={}",
                    traceId,
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.correlationId(),
                    routingKey
            );
        });
    }

    /**
     * DB 변경이 커밋된 뒤 이벤트를 발행해 DB 상태와 메시지 순서를 맞춘다.
     * 활성 트랜잭션 동기화가 없으면 즉시 발행한다.
     */
    public void publishAfterCommit(EventEnvelope<?> envelope) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(envelope);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(envelope);
            }
        });
    }

    /**
     * HTTP 요청·리스너 스레드는 MDC traceId를 그대로 쓰고,
     * MDC가 없는 스레드(스케줄러, 전용 스레드풀)는 새로 만든다.
     */
    private String resolveTraceId() {
        String traceId = MDC.get(TRACE_ID);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return TraceIdGenerator.generate();
    }

    private void validateEnvelope(EventEnvelope<?> envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("Event envelope must not be null");
        }
        if (!StringUtils.hasText(envelope.eventId())) {
            throw new IllegalArgumentException("event_id is required for publishing");
        }
        if (envelope.eventType() == null) {
            throw new IllegalArgumentException("event_type is required for publishing");
        }
    }
}
