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
@Table(name = "match_results")
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

    @Column(name = "strengths", columnDefinition = "jsonb")
    private String strengths; // 추후 구조 확정 시 전용 DTO 클래스를 만들어서 매핑 예정

    @Column(name = "weaknesses", columnDefinition = "jsonb")
    private String weaknesses; // 추후 구조 확정 시 전용 DTO 클래스를 만들어서 매핑 예정

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jd_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

}
