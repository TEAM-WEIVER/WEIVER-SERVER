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

import java.util.Arrays;
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
    @DisplayName("매칭 요청 payload에 공고 기술 스택과 순위가 포함된 역량·성향 우선순위를 담는다")
    void publishMatchingRequested_PayloadWithPriorities() {
        Company company = Company.builder()
                .companyId(1L)
                .build();
        JobPosting jobPosting = JobPosting.builder()
                .jdId(10L)
                .company(company)
                .status(JobPostingStatus.ACTIVE)
                .requiredTech(List.of("Java", "Spring"))
                .requirements("Spring Boot 경험")
                .competencyPriorities(List.of("문제해결력", "성장가능성"))
                .traitPriorities(List.of("자율·혁신"))
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
        assertThat(json.get("competency_priorities").get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(json.get("competency_priorities").get(0).get("code").asText()).isEqualTo("PROBLEM_SOLVING");
        assertThat(json.get("competency_priorities").get(0).get("name").asText()).isEqualTo("문제해결력");
        assertThat(json.get("competency_priorities").get(1).get("rank").asInt()).isEqualTo(2);
        assertThat(json.get("trait_priorities").get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(json.get("trait_priorities").get(0).get("code").asText()).isEqualTo("AUTONOMY_INNOVATION");
    }

    @Test
    @DisplayName("비어 있는 매칭 우선순위는 제외하고 유효한 항목의 순위를 다시 매긴다")
    void publishMatchingRequested_IgnoresBlankPriorities() {
        Company company = Company.builder().companyId(1L).build();
        JobPosting jobPosting = JobPosting.builder()
                .jdId(10L)
                .company(company)
                .status(JobPostingStatus.ACTIVE)
                .competencyPriorities(Arrays.asList(null, " ", "문제해결력"))
                .traitPriorities(null)
                .build();

        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(jobPosting));

        matchingEventService.publishMatchingRequested(10L);

        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publish(captor.capture());
        MatchingRequestedData data = (MatchingRequestedData) captor.getValue().data();

        assertThat(data.competencyPriorities())
                .containsExactly(new MatchingRequestedData.PriorityData(1, "PROBLEM_SOLVING", "문제해결력"));
        assertThat(data.traitPriorities()).isEmpty();
    }
}
