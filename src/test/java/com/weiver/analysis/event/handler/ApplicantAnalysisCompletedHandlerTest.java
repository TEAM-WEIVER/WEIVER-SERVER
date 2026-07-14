package com.weiver.analysis.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.event.dto.ApplicantAnalysisCompletedData;
import com.weiver.analysis.repository.CultureReportRepository;
import com.weiver.analysis.repository.TechnicalSkillReportRepository;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicantAnalysisCompletedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private ApplicantRepository applicantRepository;
    @Mock private TechnicalSkillReportRepository technicalSkillReportRepository;
    @Mock private CultureReportRepository cultureReportRepository;

    @Test
    @DisplayName("지원자 분석 완료 이벤트를 수신하면 기술 리포트와 컬처 리포트를 upsert한다")
    void handle_UpsertsReports() {
        ApplicantAnalysisCompletedHandler handler = new ApplicantAnalysisCompletedHandler(
                objectMapper,
                applicantRepository,
                technicalSkillReportRepository,
                cultureReportRepository
        );
        Applicant applicant = Applicant.builder().applicantId(1L).build();
        ApplicantAnalysisCompletedData data = new ApplicantAnalysisCompletedData(
                1L,
                List.of("Java", "Spring"),
                "Backend",
                "Server Engineer",
                CulturefitStyle.INCLUSIVE_INNOVATOR.name(),
                List.of("협업", "포용")
        );

        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(technicalSkillReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.empty());
        given(cultureReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.empty());

        handler.handle(envelope(data));

        ArgumentCaptor<TechnicalSkillReport> technicalCaptor = ArgumentCaptor.forClass(TechnicalSkillReport.class);
        ArgumentCaptor<CultureReport> cultureCaptor = ArgumentCaptor.forClass(CultureReport.class);
        verify(technicalSkillReportRepository).save(technicalCaptor.capture());
        verify(cultureReportRepository).save(cultureCaptor.capture());

        TechnicalSkillReport technicalSkillReport = technicalCaptor.getValue();
        assertThat(technicalSkillReport.getSkillTags()).containsExactly("Java", "Spring");
        assertThat(technicalSkillReport.getJob()).isEqualTo("Backend");
        assertThat(technicalSkillReport.getRole()).isEqualTo("Server Engineer");

        CultureReport cultureReport = cultureCaptor.getValue();
        assertThat(cultureReport.getCulturefitStyles()).isEqualTo(CulturefitStyle.INCLUSIVE_INNOVATOR);
        assertThat(cultureReport.getCulturefitTag()).containsExactly("협업", "포용");
    }

    @Test
    @DisplayName("지원자 분석 완료 payload에 applicant_id가 없으면 non-retryable 예외가 발생한다")
    void handle_ThrowsNonRetryable_WhenApplicantIdIsMissing() {
        ApplicantAnalysisCompletedHandler handler = new ApplicantAnalysisCompletedHandler(
                objectMapper,
                applicantRepository,
                technicalSkillReportRepository,
                cultureReportRepository
        );
        ApplicantAnalysisCompletedData data = new ApplicantAnalysisCompletedData(
                null,
                List.of("Java"),
                "Backend",
                "Server Engineer",
                null,
                null
        );

        assertThatThrownBy(() -> handler.handle(envelope(data)))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage("applicant_id is required");
    }

    @Test
    @DisplayName("지원자 분석 완료 payload에 job이 없으면 non-retryable 예외가 발생한다")
    void handle_ThrowsNonRetryable_WhenJobIsMissing() {
        ApplicantAnalysisCompletedHandler handler = new ApplicantAnalysisCompletedHandler(
                objectMapper,
                applicantRepository,
                technicalSkillReportRepository,
                cultureReportRepository
        );
        ApplicantAnalysisCompletedData data = new ApplicantAnalysisCompletedData(
                1L,
                List.of("Java"),
                null,
                "BACKEND",
                null,
                null
        );

        assertThatThrownBy(() -> handler.handle(envelope(data)))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage("job is required");
    }

    @Test
    @DisplayName("지원자 분석 완료 payload에 role이 없으면 non-retryable 예외가 발생한다")
    void handle_ThrowsNonRetryable_WhenRoleIsMissing() {
        ApplicantAnalysisCompletedHandler handler = new ApplicantAnalysisCompletedHandler(
                objectMapper,
                applicantRepository,
                technicalSkillReportRepository,
                cultureReportRepository
        );
        ApplicantAnalysisCompletedData data = new ApplicantAnalysisCompletedData(
                1L,
                List.of("Java"),
                "DEVELOPER",
                " ",
                null,
                null
        );

        assertThatThrownBy(() -> handler.handle(envelope(data)))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage("role is required");
    }

    private EventEnvelope<JsonNode> envelope(ApplicantAnalysisCompletedData data) {
        return new EventEnvelope<>(
                "event-1",
                EventType.APPLICANT_ANALYSIS_COMPLETED,
                null,
                OffsetDateTime.now(),
                EventEnvelope.CURRENT_VERSION,
                objectMapper.valueToTree(data)
        );
    }
}
