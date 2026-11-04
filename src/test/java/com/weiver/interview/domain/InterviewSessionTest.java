package com.weiver.interview.domain;

import com.weiver.interview.type.InterviewSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewSessionTest {

    @Test
    @DisplayName("질문 생성을 요청하면 WAITING_FOR_QUESTION 상태가 되고 질문 요청 시각이 기록된다")
    void markQuestionRequested_RecordsQuestionRequestedAt() {
        // given
        InterviewSession session = session(InterviewSessionStatus.STARTED);
        LocalDateTime before = LocalDateTime.now();

        // when
        session.markQuestionRequested();

        // then
        LocalDateTime after = LocalDateTime.now();
        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION);
        assertThat(session.getQuestionRequestedAt()).isNotNull();
        assertThat(session.getQuestionRequestedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("스크립트 저장을 요청하면 TRANSCRIPT_SAVE_REQUESTED 상태가 되고 저장 요청 시각이 기록된다")
    void markTranscriptSaveRequested_RecordsTranscriptSaveRequestedAt() {
        // given
        InterviewSession session = session(InterviewSessionStatus.FINISHED);
        LocalDateTime before = LocalDateTime.now();

        // when
        session.markTranscriptSaveRequested();

        // then
        LocalDateTime after = LocalDateTime.now();
        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED);
        assertThat(session.getTranscriptSaveRequestedAt()).isNotNull();
        assertThat(session.getTranscriptSaveRequestedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("리포트 생성을 요청하면 REPORT_REQUESTED 상태가 되고 리포트 요청 시각이 기록된다")
    void markReportRequested_RecordsReportRequestedAt() {
        // given
        InterviewSession session = session(InterviewSessionStatus.TRANSCRIPT_SAVED);
        LocalDateTime before = LocalDateTime.now();

        // when
        session.markReportRequested();

        // then
        LocalDateTime after = LocalDateTime.now();
        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.REPORT_REQUESTED);
        assertThat(session.getReportRequestedAt()).isNotNull();
        assertThat(session.getReportRequestedAt()).isBetween(before, after);
    }

    private InterviewSession session(InterviewSessionStatus status) {
        return InterviewSession.builder()
                .quarter("2026Q4")
                .sessionStatus(status)
                .build();
    }
}
