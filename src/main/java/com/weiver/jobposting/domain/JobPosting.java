package com.weiver.jobposting.domain;

import com.weiver.company.domain.Company;
import com.weiver.global.common.BaseTimeEntity;
import com.weiver.jobposting.dto.request.JobPostingUpdateDTO;
import com.weiver.jobposting.type.JdAnalysisStatus;
import com.weiver.jobposting.type.JobPostingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "job_postings")
public class JobPosting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "jd_id")
    private Long jdId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "job_category", length = 50, nullable = false)
    private String jobCategory;

    @Column(name = "detailed_job", length = 50, nullable = false)
    private String detailedJob;

    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    @Column(name = "jd_url", columnDefinition = "TEXT")
    private String jdUrl;

    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "qualifications", columnDefinition = "TEXT")
    private String qualifications;

    @Column(name = "preferred_qualifications", columnDefinition = "TEXT")
    private String preferredQualifications;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "competency_priorities", columnDefinition = "jsonb")
    private List<String> competencyPriorities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trait_priorities", columnDefinition = "jsonb")
    private List<String> traitPriorities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_tech", columnDefinition = "jsonb")
    private List<String> requiredTech;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobPostingStatus status;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "jd_analysis_status", nullable = false, length = 50)
    private JdAnalysisStatus jdAnalysisStatus = JdAnalysisStatus.PENDING;

    @Column(name = "jd_analysis_requested_at")
    private OffsetDateTime jdAnalysisRequestedAt;

    @Column(name = "jd_analyzed_at")
    private OffsetDateTime jdAnalyzedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * 편의 메소드
     * */
    public void updateJobPosting(JobPostingUpdateDTO updateDTO){
        this.title = updateDTO.title();
        this.jobCategory = updateDTO.jobCategory();
        this.deadline = updateDTO.deadline();
        this.detailedJob = updateDTO.detailedJob();
        this.jobDescription = updateDTO.jobDescription();
        this.qualifications = updateDTO.qualifications();
        this.requirements = updateDTO.requirements();
        this.preferredQualifications = updateDTO.preferredQualifications();
        this.competencyPriorities = updateDTO.competencyPriorities() != null
                ? updateDTO.competencyPriorities()
                : List.of();
        this.requiredTech = updateDTO.requiredTechs() != null
                ? updateDTO.requiredTechs()
                : List.of();
        this.traitPriorities = updateDTO.traitPriorities() != null
                ? updateDTO.traitPriorities()
                : List.of();
    }

    public void updateStatus(JobPostingStatus status){
        this.status = status;
    }

    public void markJdAnalysisRequested() {
        if (this.jdAnalysisStatus == JdAnalysisStatus.REQUESTED) {
            return;
        }
        if (this.jdAnalysisStatus == JdAnalysisStatus.COMPLETED) {
            throw new IllegalStateException("Completed JD analysis cannot be requested again.");
        }
        this.jdAnalysisStatus = JdAnalysisStatus.REQUESTED;
        this.jdAnalysisRequestedAt = OffsetDateTime.now();
    }

    public void markJdAnalysisCompleted() {
        if (this.jdAnalysisStatus == JdAnalysisStatus.COMPLETED) {
            return;
        }
        if (this.jdAnalysisStatus != JdAnalysisStatus.REQUESTED) {
            throw new IllegalStateException("JD analysis can be completed only after it has been requested.");
        }
        this.jdAnalysisStatus = JdAnalysisStatus.COMPLETED;
        this.jdAnalyzedAt = OffsetDateTime.now();
    }

    public void markJdAnalysisFailed() {
        if (this.jdAnalysisStatus == JdAnalysisStatus.FAILED) {
            return;
        }
        if (this.jdAnalysisStatus == JdAnalysisStatus.COMPLETED) {
            throw new IllegalStateException("Completed JD analysis cannot be marked as failed.");
        }
        this.jdAnalysisStatus = JdAnalysisStatus.FAILED;
    }
}
