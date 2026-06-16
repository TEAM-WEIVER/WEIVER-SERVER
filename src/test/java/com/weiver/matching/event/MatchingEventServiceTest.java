package com.weiver.matching.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.company.domain.Company;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JobPostingStatus;
import com.weiver.matching.event.dto.MatchingRequestedData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingEventServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MatchingEventService matchingEventService;

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private DomainEventPublisher domainEventPublisher;

    @Test
    @DisplayName("매칭 요청 payload에는 공고 기술 스택과 요구사항만 포함하고 우선순위는 포함하지 않는다")
    void publishMatchingRequested_PayloadWithoutPriorities() {
        Company company = Company.builder()
                .companyId(1L)
                .build();
        JobPosting jobPosting = JobPosting.builder()
                .jdId(10L)
                .company(company)
                .status(JobPostingStatus.ACTIVE)
                .requiredTech(List.of("Java", "Spring"))
                .requirements("Spring Boot 경험")
                .competencyPriorities(List.of("문제해결력"))
                .traitPriorities(List.of("자율"))
                .build();

        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(jobPosting));

        matchingEventService.publishMatchingRequested(10L);

        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publish(captor.capture());

        EventEnvelope<?> envelope = captor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.MATCHING_REQUESTED);
        assertThat(envelope.data()).isInstanceOf(MatchingRequestedData.class);

        JsonNode json = objectMapper.valueToTree(envelope.data());
        assertThat(json.get("required_skills").get(0).asText()).isEqualTo("Java");
        assertThat(json.get("requirements").asText()).isEqualTo("Spring Boot 경험");
        assertThat(json.has("competency_priorities")).isFalse();
        assertThat(json.has("trait_priorities")).isFalse();
    }
}
