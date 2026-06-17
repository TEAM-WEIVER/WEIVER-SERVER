package com.weiver.analysis.domain;

import com.weiver.global.common.BaseTimeEntity;
import com.weiver.jobposting.domain.JobPosting;
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
        name = "jd_analysis_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_jd_analysis_results_jd_id",
                columnNames = "jd_id"
        )
)
public class JdAnalysisResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "jd_analysis_result_id")
    private Long jdAnalysisResultId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jd_id", nullable = false, unique = true)
    @ToString.Exclude
    private JobPosting jobPosting;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "embedding", columnDefinition = "jsonb")
    private List<Double> embedding;

    public void updateAnalysis(Long companyId, String originalText, List<Double> embedding) {
        this.companyId = companyId;
        this.originalText = originalText;
        this.embedding = embedding;
    }
}
