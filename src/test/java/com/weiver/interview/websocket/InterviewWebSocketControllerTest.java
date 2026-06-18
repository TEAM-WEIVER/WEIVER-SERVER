package com.weiver.interview.websocket;

import com.weiver.interview.dto.request.InterviewAnswerSubmitRequest;
import com.weiver.interview.dto.request.InterviewStartRequest;
import com.weiver.interview.dto.response.InterviewStartResponse;
import com.weiver.interview.dto.response.InterviewWebSocketMessageResponse;
import com.weiver.interview.service.InterviewFlowService;
import com.weiver.interview.type.InterviewSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    private Principal principal() {
        return () -> APPLICANT_PUBLIC_ID;
    }
}
