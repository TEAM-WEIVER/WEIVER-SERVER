package com.weiver.applicant.type;

/**
 * 지원자 분석 제출 건의 상태.
 *
 * <p>REQUESTED는 제출부터 분석 완료까지, COMPLETED는 현재 제출의 분석 결과 저장 완료,
 * FAILED는 분석 실패(이유는 {@link AnalysisFailureReason})다.
 *
 * <p>값을 추가하면 운영(ddl-auto update)은 enum CHECK 제약을 고치지 않으므로 수동 DDL이 먼저 필요하다.
 */
public enum AnalysisSubmissionStatus {
    REQUESTED,
    COMPLETED,
    FAILED
}
