package com.weiver.interview.domain;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.common.BaseTimeEntity;
import com.weiver.interview.type.InterviewSessionStatus;
import com.weiver.interview.dto.response.InterviewTurnDTO;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    @Column(name = "interview_session_id", nullable = false, unique = true, updatable = false)
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
    @Builder.Default
    @Column(name = "transcript", nullable = false, columnDefinition = "jsonb")
    private List<InterviewTurnDTO> transcript = new ArrayList<>();

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

    public List<InterviewTurnDTO> getTranscript() {
        return transcript != null ? transcript : List.of();
    }

    public boolean hasTurn(String questionCode, Integer sequence) {
        return getTranscript().stream()
                .anyMatch(turn -> matches(turn, questionCode, sequence));
    }

    public boolean appendQuestion(String questionCode, Integer sequence, String question) {
        List<InterviewTurnDTO> turns = new ArrayList<>(getTranscript());
        boolean duplicated = turns.stream()
                .anyMatch(turn -> matches(turn, questionCode, sequence));

        if (duplicated) {
            return false;
        }

        turns.add(InterviewTurnDTO.questionOnly(questionCode, sequence, question));
        this.transcript = turns;
        updateCurrentQuestion(sequence, questionCode, question);
        return true;
    }

    public InterviewTurnDTO updateAnswer(String questionCode, Integer sequence, String answer) {
        List<InterviewTurnDTO> turns = new ArrayList<>(getTranscript());

        for (int i = 0; i < turns.size(); i++) {
            InterviewTurnDTO turn = turns.get(i);
            if (matches(turn, questionCode, sequence)) {
                InterviewTurnDTO updated = turn.withAnswer(answer);
                turns.set(i, updated);
                this.transcript = turns;
                updateCurrentQuestion(updated.sequence(), updated.questionCode(), updated.question());
                return updated;
            }
        }

        return null;
    }

    private boolean matches(InterviewTurnDTO turn, String questionCode, Integer sequence) {
        if (turn == null) {
            return false;
        }

        return Objects.equals(turn.questionCode(), questionCode)
                || Objects.equals(turn.sequence(), sequence);
    }
}
