package com.weiver.jobposting.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.company.domain.Company;
import com.weiver.company.type.DecisionMaking;
import com.weiver.company.type.OperationStyle;
import com.weiver.company.type.RoleDefinition;
import com.weiver.company.type.WorkPace;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.event.dto.JdAnalysisRequestedData;
import com.weiver.jobposting.type.JobPostingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobPostingEventServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private JobPostingEventService jobPostingEventService;

    @Mock private DomainEventPublisher domainEventPublisher;

    @Test
    @DisplayName("JD 분석 요청 payload의 업무 방식은 company type enum을 그대로 사용한다")
    void publishJdAnalysisRequested_UsesCompanyTypeEnums() {
        Company company = Company.builder()
                .companyId(1L)
                .cultureDescription("빠르게 실험합니다.")
                .directionDescription("안정적으로 확장합니다.")
                .workPace(WorkPace.FAST_EXECUTION)
                .decisionMaking(DecisionMaking.TEAM_CONSENSUS)
                .roleDefinition(RoleDefinition.CLEAR_RESPONSIBILITY)
                .operationStyle(OperationStyle.EXPERIMENT_ORIENTED)
                .build();
        JobPosting jobPosting = JobPosting.builder()
                .jdId(10L)
                .company(company)
                .status(JobPostingStatus.ACTIVE)
                .title("백엔드 개발자")
                .jobCategory("개발")
                .detailedJob("백엔드")
                .requiredTech(List.of("Java"))
                .build();

        jobPostingEventService.publishJdAnalysisRequested(jobPosting);

        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publishAfterCommit(captor.capture());

        EventEnvelope<?> envelope = captor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.JD_ANALYSIS_REQUESTED);
        assertThat(envelope.data()).isInstanceOf(JdAnalysisRequestedData.class);

        JdAnalysisRequestedData data = (JdAnalysisRequestedData) envelope.data();
        assertThat(data.workStyle().progressSpeed()).isEqualTo(WorkPace.FAST_EXECUTION);
        assertThat(data.workStyle().decisionMaker()).isEqualTo(DecisionMaking.TEAM_CONSENSUS);
        assertThat(data.workStyle().roleDefinition()).isEqualTo(RoleDefinition.CLEAR_RESPONSIBILITY);
        assertThat(data.workStyle().operatingSystem()).isEqualTo(OperationStyle.EXPERIMENT_ORIENTED);

        JsonNode workStyleJson = objectMapper.valueToTree(data.workStyle());
        assertThat(workStyleJson.get("progress_speed").asText()).isEqualTo("FAST_EXECUTION");
        assertThat(workStyleJson.get("decision_maker").asText()).isEqualTo("TEAM_CONSENSUS");
        assertThat(workStyleJson.get("role_definition").asText()).isEqualTo("CLEAR_RESPONSIBILITY");
        assertThat(workStyleJson.get("operating_system").asText()).isEqualTo("EXPERIMENT_ORIENTED");
    }
}
