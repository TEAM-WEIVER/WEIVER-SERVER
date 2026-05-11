package com.weiver.matching.dto.response;

public record InterviewScriptDTO(
        String question,   // 면접 질문
        String answer      // 면접 답변
) {}
