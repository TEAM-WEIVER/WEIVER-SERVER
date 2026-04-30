package com.weiver.essay.dto.response;

import com.weiver.essay.domain.EssayAnswer;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자기소개서 응답 DTO")
public record EssayAnswerResponseDTO(

        @Schema(description = "자기소개서 ID", example = "1")
        Long answerId,

        @Schema(description = "자기소개서 답변 내용", example = "저는 B2B AI 면접 플랫폼 'Weiver'와 영화 추천 서비스 'Moov' 등 다양한 프로젝트를 통해 대용량 데이터 처리와 서버 안정성을 다지는 경험을 했습니다...")
        String answer
){
    public static EssayAnswerResponseDTO from(EssayAnswer essayAnswer){
        return new EssayAnswerResponseDTO(
                essayAnswer.getAnswerId(),
                essayAnswer.getAnswer()
        );
    }
}