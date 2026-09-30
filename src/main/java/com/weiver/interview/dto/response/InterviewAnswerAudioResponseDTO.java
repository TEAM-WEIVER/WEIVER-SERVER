package com.weiver.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "답변 녹음 제출 응답. 인식된 텍스트는 내려주지 않으며, 다음 질문은 STOMP QUESTION_READY로 전달된다.")
public record InterviewAnswerAudioResponseDTO(

        @Schema(description = "면접 세션 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @JsonProperty("interview_session_id")
        UUID interviewSessionId,

        @Schema(description = "답변 반영 후 면접 세션 상태", example = "WAITING_FOR_QUESTION")
        String status,

        @Schema(description = "답변한 질문 코드", example = "S_01_00")
        @JsonProperty("question_code")
        String questionCode,

        @Schema(description = "답변한 질문 순서", example = "1")
        Integer sequence
) {
}
