package com.weiver.analysis.domain;


import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.applicant.domain.Applicant;
import com.weiver.global.common.BaseTimeEntity;
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
@Table(
        name = "culture_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_culture_reports_applicant_id",
                columnNames = "applicant_id"
        )
)
public class CultureReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "culture_report_id")
    private Long cultureReportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "culturefit_style")
    private CulturefitStyle culturefitStyles; // 컬처핏 스타일

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "culturefit_tag", columnDefinition = "jsonb")
    private List<String> culturefitTag; // 컬처핏 리스트

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "applicant_id", nullable = false, unique = true)
    @ToString.Exclude
    private Applicant applicant;

    public void assignApplicant(Applicant applicant) {
        this.applicant = applicant;
    }

    public void updateAnalysis(CulturefitStyle culturefitStyle, List<String> culturefitTag) {
        this.culturefitStyles = culturefitStyle;
        this.culturefitTag = culturefitTag;
    }

    /**
     * 면접 최종 평가의 컬처핏 좌표로 판정한 스타일만 갱신한다. 컬처핏 태그는 기존 값을 유지한다.
     */
    public void updateCulturefitStyle(CulturefitStyle culturefitStyle) {
        this.culturefitStyles = culturefitStyle;
    }

}
