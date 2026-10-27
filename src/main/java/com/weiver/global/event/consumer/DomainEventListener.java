package com.weiver.global.event.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.exception.RetryableEventException;
import com.weiver.global.event.util.EventHeaders;
import com.weiver.global.event.validation.EventEnvelopeValidator;
import com.weiver.global.logging.util.TraceIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListener {

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_EVENT_ID = "eventId";
    private static final String MDC_EVENT_TYPE = "eventType";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ObjectMapper objectMapper;
    private final EventEnvelopeValidator validator;
    private final DomainEventDispatcher dispatcher;

    @RabbitListener(queues = "${weiver.rabbitmq.spring-queue}")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        // 리스너 스레드는 재사용되므로 MDC.clear() 대신 여기서 넣은 키만 지운다.
        List<String> mdcKeys = new ArrayList<>(4);
        putMdc(mdcKeys, MDC_TRACE_ID, resolveTraceId(message));

        try {
            EventEnvelope<JsonNode> envelope = objectMapper.readValue(message.getBody(), new TypeReference<>() {});
            putEnvelopeMdc(mdcKeys, envelope);

            validator.validate(envelope);

            log.info(
                    "RabbitMQ event received. eventId={}, eventType={}, correlationId={}",
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.correlationId()
            );

            dispatcher.dispatch(envelope);
            channel.basicAck(deliveryTag, false);
        } catch (RetryableEventException e) {
            log.warn("Retryable event handling failed. Move to DLQ. reason={}", e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.error("Event handling failed. Move to DLQ.", e);
            channel.basicNack(deliveryTag, false, false);
        } finally {
            mdcKeys.forEach(MDC::remove);
        }
    }

    /**
     * 발행 측이 실은 trace_id 헤더를 이어받는다. 헤더가 없으면(AI 서버 결과 이벤트 등) 새로 만든다.
     */
    private String resolveTraceId(Message message) {
        Object header = message.getMessageProperties().getHeader(EventHeaders.TRACE_ID);
        if (header != null && StringUtils.hasText(header.toString())) {
            return header.toString();
        }
        return TraceIdGenerator.generate();
    }

    private void putEnvelopeMdc(List<String> mdcKeys, EventEnvelope<JsonNode> envelope) {
        if (envelope == null) {
            return;
        }
        putMdc(mdcKeys, MDC_EVENT_ID, envelope.eventId());
        putMdc(mdcKeys, MDC_EVENT_TYPE, envelope.eventType() != null ? envelope.eventType().name() : null);
        putMdc(mdcKeys, MDC_CORRELATION_ID, envelope.correlationId());
    }

    private void putMdc(List<String> mdcKeys, String key, String value) {
        if (value == null) {
            return;
        }
        MDC.put(key, value);
        mdcKeys.add(key);
    }
}
