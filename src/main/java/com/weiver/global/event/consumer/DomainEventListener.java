package com.weiver.global.event.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.exception.RetryableEventException;
import com.weiver.global.event.validation.EventEnvelopeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListener {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeValidator validator;
    private final DomainEventDispatcher dispatcher;

    @RabbitListener(queues = "${weiver.rabbitmq.spring-queue}")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            EventEnvelope<JsonNode> envelope = objectMapper.readValue(message.getBody(), new TypeReference<>() {});

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
        }
    }
}
