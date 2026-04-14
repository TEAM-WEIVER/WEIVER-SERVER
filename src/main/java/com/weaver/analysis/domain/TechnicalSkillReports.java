package com.weaver.analysis.domain;


import com.weaver.applicant.domain.Applicants;
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
@Table(name = "technical_skill_reports")
public class TechnicalSkillReports extends BaseTimeEntity {

    /**
     * /worker/extract_skills/essay || /worker/evaluate_interview response 저장
     * */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skill_report_id")
    private Long skillReportId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_tags", columnDefinition = "jsonb")
    private List<String> skillTags; // 스킬 태그

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_provided_tags", columnDefinition = "jsonb")
    private List<String> userProvidedTags; // 유저 제공 스킬 태그

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "applicant_id", nullable = false)
    @ToString.Exclude
    private Applicants applicants;

    public void assignApplicant(Applicants applicants) {
        this.applicants = applicants;
    }

}
