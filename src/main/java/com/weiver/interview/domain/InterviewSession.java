package com.weiver.interview.domain;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.common.BaseTimeEntity;
import com.weiver.interview.type.InterviewSessionStatus;
import com.weiver.interview.dto.response.InterviewTurnDTO;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "interview_sessions")
public class InterviewSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interview_id")
    private Long interviewId;

    @Builder.Default
    @Column(name = "interview_session_id", unique = true, updatable = false)
    private UUID interviewSessionId = UUID.randomUUID();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 50)
    private InterviewSessionStatus sessionStatus = InterviewSessionStatus.STARTED;

    @Column(name = "quarter", nullable = false, length = 10)
    private String quarter; // "2026Q1" 형식

    @Column(name = "current_sequence")
    private Integer currentSequence;

    @Column(name = "current_question_code", length = 30)
    private String currentQuestionCode;

    @Column(name = "current_question", columnDefinition = "TEXT")
    private String currentQuestion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript", nullable = false, columnDefinition = "jsonb")
    private List<InterviewTurnDTO> transcript;

    @Column(name = "video_url")
    private String videoUrl;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    /**
     * 정보 업데이트 편의 메소드
     * */
    public void updateCurrentQuestion(Integer sequence, String questionCode, String question) {
        this.currentSequence = sequence;
        this.currentQuestionCode = questionCode;
        this.currentQuestion = question;
    }

    public void updateStatus(InterviewSessionStatus sessionStatus) {
        this.sessionStatus = sessionStatus;
    }
}
