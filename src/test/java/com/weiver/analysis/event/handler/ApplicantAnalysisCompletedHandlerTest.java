package com.weiver.analysis.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weiver.analysis.event.dto.ApplicantAnalysisCompletedData;
import com.weiver.analysis.service.ApplicantAnalysisResultService;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApplicantAnalysisCompletedHandlerTest {

    private static final String CORRELATION_ID = "request-event-1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private ApplicantAnalysisResultService applicantAnalysisResultService;

    private ApplicantAnalysisCompletedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApplicantAnalysisCompletedHandler(objectMapper, applicantAnalysisResultService);
    }

    @Test
    @DisplayName("성공 결과는 correlation_id와 data를 그대로 완료 처리 서비스에 넘긴다")
    void handle_Success_DelegatesWithCorrelationId() {
        // given
        ObjectNode data = successPayload();

        // when
        handler.handle(envelope(data));

        // then
        ArgumentCaptor<ApplicantAnalysisCompletedData> captor = ArgumentCaptor.forClass(ApplicantAnalysisCompletedData.class);
        verify(applicantAnalysisResultService).applyCompletion(eq(CORRELATION_ID), captor.capture());
        assertThat(captor.getValue().applicantId()).isEqualTo(1L);
        assertThat(captor.getValue().submissionVersion()).isEqualTo(2);
        assertThat(captor.getValue().isFailed()).isFalse();
    }

    @Test
    @DisplayName("outcome이 없으면(AI 배포 전 payload) 성공으로 보고 넘긴다")
    void handle_MissingOutcome_TreatedAsSuccess() {
        // given
        ObjectNode data = successPayload();
        data.remove("outcome");

        // when
        handler.handle(envelope(data));

        // then
        ArgumentCaptor<ApplicantAnalysisCompletedData> captor = ArgumentCaptor.forClass(ApplicantAnalysisCompletedData.class);
        verify(applicantAnalysisResultService).applyCompletion(eq(CORRELATION_ID), captor.capture());
        assertThat(captor.getValue().outcome()).isNull();
        assertThat(captor.getValue().isFailed()).isFalse();
    }

    @Test
    @DisplayName("실패 결과는 job·role이 null이어도 예외 없이 넘긴다")
    void handle_Failure_DoesNotRequireJobAndRole() {
        // given
        ObjectNode data = objectMapper.createObjectNode();
        data.put("applicant_id", 1);
        data.put("submission_version", 2);
        data.put("outcome", "FAILED");
        data.putNull("job");
        data.putNull("role");
        data.putObject("error").put("stage", "PORTFOLIO_DOWNLOAD").put("code", "S3_DOWNLOAD_FAILED");

        // when
        handler.handle(envelope(data));

        // then
        ArgumentCaptor<ApplicantAnalysisCompletedData> captor = ArgumentCaptor.forClass(ApplicantAnalysisCompletedData.class);
        verify(applicantAnalysisResultService).applyCompletion(eq(CORRELATION_ID), captor.capture());
        assertThat(captor.getValue().isFailed()).isTrue();
        assertThat(captor.getValue().error().stage()).isEqualTo("PORTFOLIO_DOWNLOAD");
    }

    @Test
    @DisplayName("applicant_id가 없으면 non-retryable 예외가 발생한다")
    void handle_MissingApplicantId_ThrowsNonRetryable() {
        ObjectNode data = successPayload();
        data.remove("applicant_id");

        assertNonRetryable(data, "applicant_id is required");
    }

    @Test
    @DisplayName("submission_version이 없으면 non-retryable 예외가 발생한다")
    void handle_MissingSubmissionVersion_ThrowsNonRetryable() {
        ObjectNode data = successPayload();
        data.remove("submission_version");

        assertNonRetryable(data, "submission_version is required");
    }

    @Test
    @DisplayName("outcome이 SUCCEEDED·FAILED 밖의 값이면 non-retryable 예외가 발생한다")
    void handle_UnknownOutcome_ThrowsNonRetryable() {
        ObjectNode data = successPayload();
        data.put("outcome", "PARTIAL");

        assertNonRetryable(data, "Unsupported outcome: PARTIAL");
    }

    @Test
    @DisplayName("성공 결과에 job이 없으면 non-retryable 예외가 발생한다")
    void handle_SuccessWithoutJob_ThrowsNonRetryable() {
        ObjectNode data = successPayload();
        data.putNull("job");

        assertNonRetryable(data, "job is required");
    }

    @Test
    @DisplayName("성공 결과에 role이 비어 있으면 non-retryable 예외가 발생한다")
    void handle_SuccessWithBlankRole_ThrowsNonRetryable() {
        ObjectNode data = successPayload();
        data.put("role", " ");

        assertNonRetryable(data, "role is required");
    }

    @Test
    @DisplayName("성공 결과의 culturefit_style이 허용 값(enum 이름·한글 설명)이 아니면 잠금·리포트 저장 전에 non-retryable 예외가 발생한다")
    void handle_SuccessWithUnknownCulturefitStyle_ThrowsNonRetryable() {
        ObjectNode data = successPayload();
        data.put("culturefit_style", "UNKNOWN_STYLE");

        assertNonRetryable(data, "Unsupported culturefit_style: UNKNOWN_STYLE");
    }

    @Test
    @DisplayName("성공 결과의 culturefit_style이 enum 이름이나 한글 설명이면 통과한다")
    void handle_SuccessWithKnownCulturefitStyle_Delegates() {
        // given
        ObjectNode byName = successPayload();
        byName.put("culturefit_style", "INCLUSIVE_INNOVATOR");
        ObjectNode byDescription = successPayload();
        byDescription.put("culturefit_style", "포용적 혁신가");

        // when
        handler.handle(envelope(byName));
        handler.handle(envelope(byDescription));

        // then
        verify(applicantAnalysisResultService, org.mockito.Mockito.times(2))
                .applyCompletion(eq(CORRELATION_ID), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("핸들러는 트랜잭션을 열지 않는다(트랜잭션은 완료 처리 서비스에 있다)")
    void handle_IsNotTransactional() {
        assertThat(Arrays.stream(ApplicantAnalysisCompletedHandler.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class)))
                .isEmpty();
        assertThat(ApplicantAnalysisCompletedHandler.class.isAnnotationPresent(Transactional.class)).isFalse();
    }

    private void assertNonRetryable(ObjectNode data, String message) {
        assertThatThrownBy(() -> handler.handle(envelope(data)))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage(message);
        verifyNoInteractions(applicantAnalysisResultService);
    }

    private ObjectNode successPayload() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("applicant_id", 1);
        data.put("submission_version", 2);
        data.put("outcome", "SUCCEEDED");
        data.putArray("skill_tags").add("Java");
        data.put("job", "DEVELOPER");
        data.put("role", "BACKEND");
        data.put("portfolio_status", "pdf");
        data.putNull("portfolio_issue");
        return data;
    }

    private EventEnvelope<JsonNode> envelope(JsonNode data) {
        return new EventEnvelope<>(
                "result-event-1",
                EventType.APPLICANT_ANALYSIS_COMPLETED,
                CORRELATION_ID,
                OffsetDateTime.now(),
                EventEnvelope.CURRENT_VERSION,
                data
        );
    }
}
