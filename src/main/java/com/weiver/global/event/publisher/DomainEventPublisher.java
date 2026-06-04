package com.weiver.global.event.publisher;

import com.weiver.global.event.config.RabbitMqProperties;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.util.EventRoutingKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties properties;

    public void publish(EventEnvelope<?> envelope) {
        String routingKey = EventRoutingKeys.from(envelope.eventType());
        CorrelationData correlationData = new CorrelationData(envelope.eventId());

        rabbitTemplate.convertAndSend(
                properties.domainExchange(),
                routingKey,
                envelope,
                correlationData
        );

        correlationData.getFuture().whenComplete((confirm, ex) -> {
            if (ex != null) {
                log.error("RabbitMQ publish confirm failed. eventId={}, eventType={}, routingKey={}",
                        envelope.eventId(),
                        envelope.eventType(),
                        routingKey
                );
                return;
            }
            if (confirm == null || !confirm.isAck()) {
                log.error(
                        "RabbitMQ publish not acknowledged. eventId={}, eventType={}, routingKey={}, reason={}",
                        envelope.eventId(),
                        envelope.eventType(),
                        routingKey,
                        confirm != null ? confirm.getReason() : null
                );
                return;
            }

            log.info(
                    "RabbitMQ event published. eventId={}, eventType={}, correlationId={}, routingKey={}",
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.correlationId(),
                    routingKey
            );
        });
    }
}
