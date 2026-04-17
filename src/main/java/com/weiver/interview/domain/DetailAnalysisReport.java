package com.weiver.interview.domain;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "detail_analysis_reports")
public class DetailAnalysisReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_report_id")
    private Long detailReportId;

    @Column(name = "skill_analysis", nullable = false, columnDefinition = "jsonb")
    private String skillAnalysis;

    @Column(name = "culture_analysis", nullable = false, columnDefinition = "jsonb")
    private String cultureAnalysis;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;
}
