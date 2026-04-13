package com.weaver.analysis.domain;

import com.weaver.applicant.domain.Applicant;
import com.weaver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
public class AnalysisReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;


    /**
     * /worker/evaluate_interview response 저장
     * */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> skillAnalysis; // 기술핏 분석 결과

    /**
     * /worker/insert/interview_result response 저장
     * */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> cultureAnalysis; // 컬처핏 분석 결과

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    private Applicant applicant;

    public void assignApplicant(Applicant applicant) {
        this.applicant = applicant;
    }
}
