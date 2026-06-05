package com.weiver.interview.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewSessionStatus {

    STARTED("시작"), // session row 생성
    WAITING_FOR_QUESTION("질문 생성 대기"), // AI 질문 생성 요청 발행 완료
    QUESTION_READY("질문 수신"), // AI 질문 생성 결과 수신
    FINISHED("종료"), // 면접 종료
    TRANSCRIPT_SAVE_REQUESTED("스크립트 저장 요청"), // 면접 스크립트 저장 요청 발행
    TRANSCRIPT_SAVED("스크립트 저장 완료"), // AI 서버에 면접 스크립트 저장 완료
    REPORT_REQUESTED("리포트 생성 요청"), // 최종 평가(보고서) 요청 발행
    REPORT_COMPLETED("리포트 생성 완료"), // 최종 평가(보고서) 저장 완료
    FAILED("실패"); // 재처리 초과 또는 복구 불가

    private final String status;
}
