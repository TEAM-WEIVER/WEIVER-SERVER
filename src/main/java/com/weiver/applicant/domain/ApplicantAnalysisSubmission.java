package com.weiver.applicant.domain;

import com.weiver.applicant.type.AnalysisFailureReason;
import com.weiver.applicant.type.AnalysisSubmissionStatus;
import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 지원자 프로필 제출 1회 = 분석 요청 1건. 현재 상태의 정본이며, {@link Applicant#getCurrentSubmissionVersion()}이
 * 지원자의 현재 제출 건을 가리킨다.
 *
 * <p>status·failure_reason enum에 값을 추가하면 운영(ddl-auto update)은 CHECK 제약을 고치지 않으므로
 * 수동 DDL이 먼저 필요하다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(
        name = "applicant_analysis_submissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_applicant_analysis_submissions_applicant_version",
                        columnNames = {"applicant_id", "submission_version"}
                ),
                @UniqueConstraint(
                        name = "uq_applicant_analysis_submissions_event_id",
                        columnNames = "event_id"
                )
        }
)
public class ApplicantAnalysisSubmission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_id")
    private Long submissionId;

    @Builder.Default
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private String publicId = UUID.randomUUID().toString();

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @Column(name = "submission_version", nullable = false)
    private Integer submissionVersion;

    /** 분석 요청 봉투의 event_id. 완료 이벤트의 correlation_id와 맞춰 본다. */
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    /** 분석 요청 data 전체(submission_version 포함). 추적용이며 재전송에는 쓰지 않는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshot;

    /** submission_version을 뺀 스냅샷의 SHA-256(소문자 hex). */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisSubmissionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private AnalysisFailureReason failureReason;

    /** AI가 보낸 error.stage 원문. */
    @Column(name = "error_stage", length = 50)
    private String errorStage;

    /** AI가 보낸 error.code 원문. */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    /** AI가 보낸 portfolio_status 원문. */
    @Column(name = "portfolio_status", length = 30)
    private String portfolioStatus;

    /** AI가 보낸 portfolio_issue 원문. */
    @Column(name = "portfolio_issue", length = 30)
    private String portfolioIssue;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "deadline_at", nullable = false)
    private OffsetDateTime deadlineAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    /**
     * 분석을 요청한 제출 건을 만든다. 마감 시각은 요청 시각에 분석 마감 시간을 더한 값이다.
     */
    public static ApplicantAnalysisSubmission request(
            Applicant applicant,
            int submissionVersion,
            String eventId,
            Map<String, Object> snapshot,
            String contentHash,
            OffsetDateTime requestedAt,
            Duration deadline
    ) {
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        Objects.requireNonNull(deadline, "deadline must not be null");
        return ApplicantAnalysisSubmission.builder()
                .applicant(Objects.requireNonNull(applicant, "applicant must not be null"))
                .submissionVersion(submissionVersion)
                .eventId(Objects.requireNonNull(eventId, "eventId must not be null"))
                .snapshot(Objects.requireNonNull(snapshot, "snapshot must not be null"))
                .contentHash(Objects.requireNonNull(contentHash, "contentHash must not be null"))
                .status(AnalysisSubmissionStatus.REQUESTED)
                .requestedAt(requestedAt)
                .deadlineAt(requestedAt.plus(deadline))
                .build();
    }

    /** 분석 요청 중(REQUESTED)인지. 이 동안에는 재제출과 포트폴리오 파일 교체를 막는다. */
    public boolean isRequested() {
        return this.status == AnalysisSubmissionStatus.REQUESTED;
    }

    /** 다시 제출할 수 있는 상태인지. 분석이 실패(FAILED)한 제출 건만 재제출을 허용한다. */
    public boolean allowsResubmission() {
        return this.status == AnalysisSubmissionStatus.FAILED;
    }
}
