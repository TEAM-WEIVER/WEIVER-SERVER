package com.weiver.global.event.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Binding;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitTopologyBindingConsistencyTest {

    @DisplayName("각 큐의 DLQ는 원래 큐와 같은 라우팅 패턴으로 바인딩된다")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"application-local.yaml", "application-prod.yaml"})
    void dlqBindingsMatchSourceQueueBindings(String profileFile) throws IOException {
        // given
        RabbitMqProperties properties = loadProperties(profileFile);

        // when
        Collection<Binding> bindings;
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RabbitMqProperties.class, () -> properties);
            context.register(RabbitTopologyConfig.class);
            context.refresh();
            bindings = context.getBeansOfType(Binding.class).values();
        }

        // then
        assertSameRoutingKeys(bindings, properties, properties.springQueue(), properties.springDlq());
        assertSameRoutingKeys(bindings, properties, properties.aiQueue(), properties.aiDlq());
    }

    private void assertSameRoutingKeys(Collection<Binding> bindings, RabbitMqProperties properties,
                                       String queue, String dlq) {
        Set<String> queueKeys = routingKeys(bindings, properties.domainExchange(), queue);
        Set<String> dlqKeys = routingKeys(bindings, properties.deadLetterExchange(), dlq);

        assertThat(queueKeys).as("%s 바인딩", queue).isNotEmpty();
        assertThat(dlqKeys)
                .as("%s 바인딩은 %s 바인딩 %s 와 같아야 한다", dlq, queue, queueKeys)
                .containsExactlyInAnyOrderElementsOf(queueKeys);
    }

    private Set<String> routingKeys(Collection<Binding> bindings, String exchange, String queue) {
        return bindings.stream()
                .filter(binding -> binding.getExchange().equals(exchange))
                .filter(binding -> binding.getDestination().equals(queue))
                .map(Binding::getRoutingKey)
                .collect(Collectors.toSet());
    }

    private RabbitMqProperties loadProperties(String profileFile) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load(profileFile, new ClassPathResource(profileFile))) {
            sources.addLast(source);
        }
        return new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources))
                .bind("weiver.rabbitmq", RabbitMqProperties.class)
                .orElseThrow(() -> new IllegalStateException(profileFile + "에 weiver.rabbitmq 설정이 없다"));
    }
}
