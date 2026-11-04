package com.weiver.global.metrics.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 서버 결과를 기다리는 흐름. 값({@link #tag})은 지표의 {@code flow} 태그로만 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum AiFlow {

    JD_ANALYSIS("jd_analysis"),                             // JobPosting.jdAnalysisStatus = REQUESTED
    PROFILE_SYNC("profile_sync"),                           // Applicant.profileSyncStatus = REQUESTED
    INTERVIEW_QUESTION("interview_question"),               // InterviewSession = WAITING_FOR_QUESTION
    INTERVIEW_TRANSCRIPT_SAVE("interview_transcript_save"), // InterviewSession = TRANSCRIPT_SAVE_REQUESTED
    INTERVIEW_REPORT("interview_report");                   // InterviewSession = REPORT_REQUESTED

    private final String tag;
}
