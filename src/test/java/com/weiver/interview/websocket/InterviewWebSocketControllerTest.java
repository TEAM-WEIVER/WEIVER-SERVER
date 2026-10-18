package com.weiver.interview.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.interview.dto.request.InterviewAnswerSubmitRequest;
import com.weiver.interview.dto.request.InterviewStartRequest;
import com.weiver.interview.dto.response.InterviewStartResponse;
import com.weiver.interview.dto.response.InterviewWebSocketMessageResponse;
import com.weiver.interview.service.InterviewFlowService;
import com.weiver.interview.type.InterviewSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InterviewWebSocketControllerTest {

    private static final String APPLICANT_PUBLIC_ID = "applicant-public-id";

    private final InterviewFlowService interviewFlowService = mock(InterviewFlowService.class);
    private final InterviewWebSocketController controller = new InterviewWebSocketController(interviewFlowService);

    @Test
    @DisplayName("WebSocket 면접 시작 메시지는 지원자 publicId로 세션을 시작하고 SESSION_STARTED 응답을 반환한다")
    void startInterview_ReturnsSessionStartedMessage() {
        UUID sessionId = UUID.randomUUID();
        InterviewStartRequest request = new InterviewStartRequest("TECHNICAL");

        given(interviewFlowService.startInterview(APPLICANT_PUBLIC_ID, request))
                .willReturn(new InterviewStartResponse(sessionId, InterviewSessionStatus.WAITING_FOR_QUESTION.name()));

        InterviewWebSocketMessageResponse response = controller.startInterview(request, principal());

        assertThat(response.type()).isEqualTo("SESSION_STARTED");
        assertThat(response.interviewSessionId()).isEqualTo(sessionId);
        assertThat(response.status()).isEqualTo("WAITING_FOR_QUESTION");
        verify(interviewFlowService).startInterview(APPLICANT_PUBLIC_ID, request);
    }

    @Test
    @DisplayName("WebSocket 답변 제출 메시지는 지원자 publicId와 세션 UUID를 서비스에 전달한다")
    void submitAnswer_ReturnsServiceResponse() {
        UUID sessionId = UUID.randomUUID();
        InterviewAnswerSubmitRequest request = new InterviewAnswerSubmitRequest("S_01_00", 1, "답변");
        InterviewWebSocketMessageResponse serviceResponse =
                InterviewWebSocketMessageResponse.answerAccepted(sessionId);

        given(interviewFlowService.submitAnswer(sessionId, APPLICANT_PUBLIC_ID, request))
                .willReturn(serviceResponse);

        InterviewWebSocketMessageResponse response = controller.submitAnswer(sessionId, request, principal());

        assertThat(response).isSameAs(serviceResponse);
        verify(interviewFlowService).submitAnswer(sessionId, APPLICANT_PUBLIC_ID, request);
    }

    @Test
    @DisplayName("면접 시작 중 분석 미완료 업무 예외가 나면 ERROR 메시지로 오류 코드와 기본 메시지를 반환한다")
    void handleBusinessException_AnalysisNotCompleted_ReturnsErrorMessage() {
        // given
        InterviewStartRequest request = new InterviewStartRequest("TECHNICAL");
        given(interviewFlowService.startInterview(APPLICANT_PUBLIC_ID, request))
                .willThrow(new BusinessException(ErrorCode.APPLICANT_ANALYSIS_NOT_COMPLETED));

        // when: 실제 STOMP 처리에서는 메시지 핸들러가 던진 예외를 @MessageExceptionHandler가 받는다.
        BusinessException thrown = catchBusinessException(() -> controller.startInterview(request, principal()));
        InterviewWebSocketMessageResponse response = controller.handleBusinessException(thrown);

        // then
        assertThat(response.type()).isEqualTo("ERROR");
        assertThat(response.errorCode()).isEqualTo("APPLICANT_ANALYSIS_NOT_COMPLETED");
        assertThat(response.message()).isEqualTo("지원자 분석이 완료되지 않았습니다.");
        assertThat(response.interviewSessionId()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.questionCode()).isNull();
        assertThat(response.sequence()).isNull();
        assertThat(response.question()).isNull();
    }

    @Test
    @DisplayName("업무 예외에 지정된 메시지가 있으면 ERROR 메시지에 그 메시지를 그대로 담는다")
    void handleBusinessException_CustomMessage_KeepsMessage() {
        // given
        BusinessException exception = new BusinessException(ErrorCode.BAD_REQUEST, "질문 번호가 맞지 않습니다.");

        // when
        InterviewWebSocketMessageResponse response = controller.handleBusinessException(exception);

        // then
        assertThat(response.errorCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.message()).isEqualTo("질문 번호가 맞지 않습니다.");
    }

    @Test
    @DisplayName("ERROR 메시지는 error_code(snake_case)로 직렬화되고 정상 메시지의 error_code는 null이다")
    void errorMessage_SerializesErrorCodeInSnakeCase() throws Exception {
        // given
        ObjectMapper objectMapper = new ObjectMapper();

        // when
        JsonNode error = objectMapper.valueToTree(
                InterviewWebSocketMessageResponse.error(ErrorCode.APPLICANT_ANALYSIS_NOT_COMPLETED, "지원자 분석이 완료되지 않았습니다."));
        JsonNode accepted = objectMapper.valueToTree(InterviewWebSocketMessageResponse.answerAccepted(UUID.randomUUID()));

        // then
        assertThat(error.get("type").asText()).isEqualTo("ERROR");
        assertThat(error.get("error_code").asText()).isEqualTo("APPLICANT_ANALYSIS_NOT_COMPLETED");
        assertThat(error.has("errorCode")).isFalse();
        assertThat(accepted.get("error_code").isNull()).isTrue();
    }

    @Test
    @DisplayName("업무 예외 핸들러는 요청한 연결에만 /queue/interviews로 보낸다(broadcast=false)")
    void handleBusinessException_SendsOnlyToRequestingSession() throws Exception {
        // given
        Method handler = InterviewWebSocketController.class.getMethod("handleBusinessException", BusinessException.class);

        // when
        MessageExceptionHandler exceptionHandler = handler.getAnnotation(MessageExceptionHandler.class);
        SendToUser sendToUser = handler.getAnnotation(SendToUser.class);

        // then
        assertThat(exceptionHandler.value()).containsExactly(BusinessException.class);
        assertThat(sendToUser.destinations()).containsExactly("/queue/interviews");
        assertThat(sendToUser.broadcast()).isFalse();
    }

    @Test
    @DisplayName("답변 제출 중 업무 예외가 나면 ERROR 메시지로 오류 코드를 반환한다")
    void handleBusinessException_SubmitAnswerFailure_ReturnsErrorMessage() {
        // given
        UUID sessionId = UUID.randomUUID();
        InterviewAnswerSubmitRequest request = new InterviewAnswerSubmitRequest("S_01_00", 1, "답변");
        given(interviewFlowService.submitAnswer(sessionId, APPLICANT_PUBLIC_ID, request))
                .willThrow(new BusinessException(ErrorCode.APPLICANT_ANALYSIS_NOT_COMPLETED));

        // when
        BusinessException thrown = catchBusinessException(() -> controller.submitAnswer(sessionId, request, principal()));
        InterviewWebSocketMessageResponse response = controller.handleBusinessException(thrown);

        // then
        assertThat(response.type()).isEqualTo("ERROR");
        assertThat(response.errorCode()).isEqualTo("APPLICANT_ANALYSIS_NOT_COMPLETED");
    }

    @Test
    @DisplayName("principal이 없으면 UNAUTHORIZED 업무 예외가 ERROR 메시지로 바뀐다")
    void handleBusinessException_NoPrincipal_ReturnsUnauthorizedError() {
        // given
        InterviewStartRequest request = new InterviewStartRequest("TECHNICAL");

        // when
        BusinessException thrown = catchBusinessException(() -> controller.startInterview(request, null));
        InterviewWebSocketMessageResponse response = controller.handleBusinessException(thrown);

        // then
        assertThat(response.type()).isEqualTo("ERROR");
        assertThat(response.errorCode()).isEqualTo("UNAUTHORIZED");
        verifyNoInteractions(interviewFlowService);
    }

    private BusinessException catchBusinessException(Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            return e;
        }
        throw new AssertionError("BusinessException이 발생해야 한다");
    }

    private Principal principal() {
        return () -> APPLICANT_PUBLIC_ID;
    }
}
