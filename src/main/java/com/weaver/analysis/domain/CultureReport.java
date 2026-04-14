package com.weaver.analysis.domain;


import com.weaver.analysis.type.CulturefitStyle;
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
@Table(name = "culture_reports")
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
    @JoinColumn(name = "applicant_id", nullable = false)
    @ToString.Exclude
    private Applicant applicant;

    public void assignApplicant(Applicant applicant) {
        this.applicant = applicant;
    }

}
