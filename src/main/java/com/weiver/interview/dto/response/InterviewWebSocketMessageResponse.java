package com.weiver.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.interview.type.AudioStatus;
import com.weiver.interview.type.InterviewSessionStatus;

import java.util.UUID;

public record InterviewWebSocketMessageResponse(
        String type,

        @JsonProperty("interview_session_id")
        UUID interviewSessionId,

        String status,

        @JsonProperty("question_code")
        String questionCode,

        Integer sequence,
        String question,
        String message,

        @JsonProperty("audio_url")
        String audioUrl,

        @JsonProperty("audio_status")
        AudioStatus audioStatus
) {
    public static InterviewWebSocketMessageResponse sessionStarted(InterviewStartResponse response) {
        return new InterviewWebSocketMessageResponse(
                "SESSION_STARTED",
                response.interviewSessionId(),
                response.status(),
                null,
                null,
                null,
                "면접 세션이 시작되었습니다.",
                null,
                null
        );
    }

    public static InterviewWebSocketMessageResponse answerAccepted(UUID interviewSessionId) {
        return new InterviewWebSocketMessageResponse(
                "ANSWER_ACCEPTED",
                interviewSessionId,
                InterviewSessionStatus.WAITING_FOR_QUESTION.name(),
                null,
                null,
                null,
                "답변이 제출되었습니다.",
                null,
                null
        );
    }

    /**
     * 질문 텍스트와 음성 정보를 한 메시지로 보낸다. 음성을 만들지 못하면 audioUrl은 null, audioStatus는 UNAVAILABLE이다.
     */
    public static InterviewWebSocketMessageResponse questionReady(
            UUID interviewSessionId,
            String questionCode,
            Integer sequence,
            String question,
            String audioUrl,
            AudioStatus audioStatus
    ) {
        return new InterviewWebSocketMessageResponse(
                "QUESTION_READY",
                interviewSessionId,
                InterviewSessionStatus.QUESTION_READY.name(),
                questionCode,
                sequence,
                question,
                null,
                audioUrl,
                audioStatus
        );
    }

    public static InterviewWebSocketMessageResponse interviewFinished(UUID interviewSessionId) {
        return new InterviewWebSocketMessageResponse(
                "INTERVIEW_FINISHED",
                interviewSessionId,
                InterviewSessionStatus.FINISHED.name(),
                null,
                null,
                null,
                "면접이 종료되었습니다.",
                null,
                null
        );
    }
}
