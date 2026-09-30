package com.weiver.interview.websocket;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.interview.dto.request.InterviewAnswerSubmitRequest;
import com.weiver.interview.dto.request.InterviewStartRequest;
import com.weiver.interview.dto.response.InterviewStartResponse;
import com.weiver.interview.dto.response.InterviewWebSocketMessageResponse;
import com.weiver.interview.service.InterviewFlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@Validated
@RequiredArgsConstructor
public class InterviewWebSocketController {

    private final InterviewFlowService interviewFlowService;

    @MessageMapping("/interviews/start")
    @SendToUser("/queue/interviews")
    public InterviewWebSocketMessageResponse startInterview(
            InterviewStartRequest request,
            Principal principal
    ) {
        InterviewStartResponse response = interviewFlowService.startInterview(publicId(principal), request);
        return InterviewWebSocketMessageResponse.sessionStarted(response);
    }

    @MessageMapping("/interviews/{interviewSessionId}/answers")
    @SendToUser("/queue/interviews")
    public InterviewWebSocketMessageResponse submitAnswer(
            @DestinationVariable UUID interviewSessionId,
            @Valid InterviewAnswerSubmitRequest request,
            Principal principal
    ) {
        InterviewWebSocketMessageResponse response =
                interviewFlowService.submitAnswer(interviewSessionId, publicId(principal), request);
        // 텍스트·음성 답변 경로의 사용 비율을 로그로 볼 수 있도록 경로를 남긴다(답변 내용은 남기지 않는다).
        log.info("[Interview] 답변 제출. source=TEXT, sessionId={}, sequence={}", interviewSessionId, request.sequence());
        return response;
    }

    private String publicId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.getName();
    }
}
