package com.weiver.matching.domain;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.common.BaseTimeEntity;
import com.weiver.jobposting.domain.JobPosting;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "match_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_match_result_jd_applicant",
                columnNames = {"jd_id", "applicant_id"}
        )
)
public class MatchResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "skill_score")
    private Float skillScore;

    @Column(name = "culturefit_score")
    private Float culturefitScore;

    @Column(name = "final_score")
    private Float finalScore;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "matching_rate")
    private Float matchingRate;

    @Builder.Default
    @Column(name = "is_notified", nullable = false)
    private Boolean isNotified = false; // 스케줄러가 알림을 생성했는지 여부를 추적

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jd_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    public void markAsNotified() {
        this.isNotified = true;
    }
}
