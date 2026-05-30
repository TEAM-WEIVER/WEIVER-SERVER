package com.weiver.global.event.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    TopicExchange domainExchange(RabbitMqProperties properties) {
        return ExchangeBuilder
                .topicExchange(properties.domainExchange())
                .durable(true)
                .build();
    }

    @Bean
    TopicExchange deadLetterExchange(RabbitMqProperties properties) {
        return ExchangeBuilder
                .topicExchange(properties.deadLetterExchange())
                .durable(true)
                .build();
    }

    /*
        AI -> Spring
        applicant.profile.sync.completed
        jd.analysis.completed
        applicant.analysis.completed
        matching.completed
        interview.report.completed
        interview.question.generated
        interview.transcript.saved
     */
    @Bean
    Queue springQueue(RabbitMqProperties properties) {
        return QueueBuilder
                .durable(properties.springQueue())
                .deadLetterExchange(properties.deadLetterExchange())
                .build();
    }

    @Bean
    Queue springDlq(RabbitMqProperties properties) {
        return QueueBuilder
                .durable(properties.springDlq())
                .build();
    }

    /*
        Spring -> AI
        jd.analysis.requested
        applicant.analysis.requested
        matching.requested
        interview.question.requested
        interview.transcript.save.requested
        interview.report.requested
        applicant.profile.changed
     */
    @Bean
    Queue aiQueue(RabbitMqProperties properties) {
        return QueueBuilder
                .durable(properties.aiQueue())
                .deadLetterExchange(properties.deadLetterExchange())
                .build();
    }

    @Bean
    Queue aiDlq(RabbitMqProperties properties) {
        return QueueBuilder
                .durable(properties.aiDlq())
                .build();
    }

    @Bean
    Binding springCompletedBinding(Queue springQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(springQueue).to(domainExchange).with("#.completed");
    }

    @Bean
    Binding springGeneratedBinding(Queue springQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(springQueue).to(domainExchange).with("#.generated");
    }

    @Bean
    Binding springTranscriptSavedBinding(Queue springQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(springQueue).to(domainExchange).with("interview.transcript.saved");
    }

    @Bean
    Binding aiRequestedBinding(Queue aiQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(aiQueue).to(domainExchange).with("#.requested");
    }

    @Bean
    Binding aiApplicantProfileChangedBinding(Queue aiQueue, TopicExchange domainExchange) {
        return BindingBuilder.bind(aiQueue).to(domainExchange).with("applicant.profile.changed");
    }

    @Bean
    Binding springDlqBinding(Queue springDlq, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(springDlq).to(deadLetterExchange).with("#");
    }

    @Bean
    Binding aiDlqBinding(Queue aiDlq, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(aiDlq).to(deadLetterExchange).with("#");
    }
}
