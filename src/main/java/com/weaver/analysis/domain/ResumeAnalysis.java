package com.weaver.analysis.domain;

import com.weaver.analysis.type.CULTUREFITSTYLE;
import com.weaver.applicant.domain.Applicant;
import com.weaver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResumeAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long analysisId;

    private String role; // AI가 판단한 주요 직무

    private Integer skillfitScore; // 스킬핏 총 점수

    @Enumerated(EnumType.STRING)
    private CULTUREFITSTYLE cultureFitStyle; // AI가 판단한 조직문화 적합 스타일

    @JdbcTypeCode(SqlTypes.JSON)
    private String experiences; // 추출된 경력 상세 리스트 (회사, 기간 등)

    @JdbcTypeCode(SqlTypes.JSON)
    private String prosList; // 장점 리스트

    @JdbcTypeCode(SqlTypes.JSON)
    private String consList; // 단점 리스트

    @JdbcTypeCode(SqlTypes.JSON)
    private String aiAnalysisSkillFit; // AI 분석 스킬핏

    private boolean isQualityScore; // AI 검증 통과 여부

    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    private Applicant applicant;

    /**
     * 편의 메소드
     * */
    public void assignApplicant(Applicant applicant) {
        this.applicant = applicant;
    }
}
