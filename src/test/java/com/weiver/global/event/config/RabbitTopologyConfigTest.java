package com.weiver.global.event.config;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class RabbitTopologyConfigTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.2.6-management");

    private final RabbitMqProperties properties = new RabbitMqProperties(
            "domain.events", "domain.events.dlx", "spring.domain.events.queue",
            "spring.domain.events.dlq", "ai.domain.events.queue", "ai.domain.events.dlq");

    private Connection connection;
    private Channel channel;

    @BeforeEach
    void setUp() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(RABBIT.getAmqpUrl());
        connection = factory.newConnection();
        channel = connection.createChannel();

        // 실제 설정의 모든 빈을 선언해 새 바인딩도 빠짐없이 검증한다.
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RabbitMqProperties.class, () -> properties);
            context.register(RabbitTopologyConfig.class);
            context.refresh();
            for (TopicExchange exchange : context.getBeansOfType(TopicExchange.class).values()) {
                channel.exchangeDeclare(exchange.getName(), exchange.getType(), exchange.isDurable());
            }
            for (Queue queue : context.getBeansOfType(Queue.class).values()) {
                channel.queueDeclare(queue.getName(), queue.isDurable(), queue.isExclusive(),
                        queue.isAutoDelete(), queue.getArguments());
                channel.queuePurge(queue.getName());
            }
            for (Binding binding : context.getBeansOfType(Binding.class).values()) {
                channel.queueBind(binding.getDestination(), binding.getExchange(),
                        binding.getRoutingKey(), binding.getArguments());
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
            channel.close();
        }
        if (connection != null) {
            connection.close();
        }
    }

    @DisplayName("처리 실패 메시지는 실패한 큐의 DLQ에만 한 건 쌓인다")
    @ParameterizedTest(name = "{0} → {1} DLQ")
    @CsvSource({
            "jd.analysis.completed, spring",
            "interview.question.generated, spring",
            "interview.transcript.saved, spring",
            "jd.analysis.requested, ai",
            "applicant.profile.changed, ai"
    })
    void rejectedMessageGoesOnlyToItsOwnDlq(String routingKey, String side) throws Exception {
        // given
        boolean spring = side.equals("spring");
        String sourceQueue = spring ? properties.springQueue() : properties.aiQueue();
        String targetDlq = spring ? properties.springDlq() : properties.aiDlq();
        String otherDlq = spring ? properties.aiDlq() : properties.springDlq();
        byte[] body = (side + "-side-failure:" + routingKey).getBytes(StandardCharsets.UTF_8);
        channel.basicPublish(properties.domainExchange(), routingKey, null, body);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(channel.messageCount(sourceQueue)).isEqualTo(1));

        // when: 리스너와 같은 basicNack(requeue=false) 경로로 실패시킨다.
        GetResponse received = channel.basicGet(sourceQueue, false);
        assertThat(received).isNotNull();
        channel.basicNack(received.getEnvelope().getDeliveryTag(), false, false);

        // then: 양쪽 DLQ 개수와 실제 본문을 함께 확인한다.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(channel.messageCount(targetDlq)).as(targetDlq).isEqualTo(1);
            assertThat(channel.messageCount(otherDlq)).as(otherDlq).isZero();
        });
        GetResponse deadLetter = channel.basicGet(targetDlq, true);
        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getBody()).isEqualTo(body);
        assertThat(deadLetter.getEnvelope().getRoutingKey()).isEqualTo(routingKey);
        assertThat(channel.messageCount(targetDlq)).isZero();
        assertThat(channel.messageCount(otherDlq)).isZero();
    }
}
