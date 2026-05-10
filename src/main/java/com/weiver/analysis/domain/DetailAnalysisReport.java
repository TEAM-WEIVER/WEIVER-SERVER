package com.weiver.analysis.domain;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "detail_analysis_reports")
public class DetailAnalysisReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;


    /**
     * /worker/evaluate_interview response 저장
     * */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_analysis", columnDefinition = "jsonb")
    private Map<String, Object> skillAnalysis; // 기술핏 분석 결과

    /**
     * /worker/insert/interview_result response 저장
     * */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "culture_analysis", columnDefinition = "jsonb")
    private Map<String, Object> cultureAnalysis; // 컬처핏 분석 결과

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

}
