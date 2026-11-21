package com.weiver.analysis.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.event.dto.ApplicantAnalysisRequestedData;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicantAnalysisEventServiceTest {

    @Mock private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private ApplicantAnalysisEventService applicantAnalysisEventService;

    @Test
    @DisplayName("분석 요청은 제출 건에 저장한 event_id로 봉투를 만들어 커밋 뒤에 발행한다")
    void publishApplicantAnalysisRequested_PublishesAfterCommitWithGivenEventId() {
        // given
        ApplicantAnalysisRequestedData data = data();

        // when
        applicantAnalysisEventService.publishApplicantAnalysisRequested("event-1", data);

        // then
        EventEnvelope<?> envelope = captureAfterCommit();
        assertThat(envelope.eventId()).isEqualTo("event-1");
        assertThat(envelope.eventType()).isEqualTo(EventType.APPLICANT_ANALYSIS_REQUESTED);
        assertThat(envelope.correlationId()).isNull();
        assertThat(envelope.version()).isEqualTo(EventEnvelope.CURRENT_VERSION);
        assertThat(envelope.data()).isSameAs(data);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Spring 기본 설정 ObjectMapper로 직렬화하면 occurred_at에 시간대 오프셋이 들어간다")
    void envelope_OccurredAtIncludesOffset() throws Exception {
        // given: Spring Boot JacksonAutoConfiguration과 같은 기본값(날짜를 문자열로)
        ObjectMapper springLikeMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        applicantAnalysisEventService.publishApplicantAnalysisRequested("event-1", data());
        EventEnvelope<?> envelope = captureAfterCommit();

        // when
        JsonNode json = springLikeMapper.readTree(springLikeMapper.writeValueAsString(envelope));

        // then
        assertThat(json.get("occurred_at").isTextual()).isTrue();
        assertThat(json.get("occurred_at").asText()).matches(".*T.*(Z|[+-]\\d{2}:\\d{2})$");
        assertThat(json.get("data").get("submission_version").asInt()).isEqualTo(2);
    }

    private EventEnvelope<?> captureAfterCommit() {
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publishAfterCommit(captor.capture());
        return captor.getValue();
    }

    private ApplicantAnalysisRequestedData data() {
        return new ApplicantAnalysisRequestedData(
                1L, 2, "홍길동", List.of(), List.of(), List.of(), List.of(), List.of(), null);
    }
}
