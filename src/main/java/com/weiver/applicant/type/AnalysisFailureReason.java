package com.weiver.applicant.type;

/**
 * 지원자 분석 제출 건의 실패 이유. TIMEOUT은 Spring이 마감 시각으로 판단하고, ANALYSIS_FAILED는 AI가 알린다.
 *
 * <p>값을 추가하면 운영(ddl-auto update)은 enum CHECK 제약을 고치지 않으므로 수동 DDL이 먼저 필요하다.
 */
public enum AnalysisFailureReason {
    TIMEOUT,
    ANALYSIS_FAILED
}
