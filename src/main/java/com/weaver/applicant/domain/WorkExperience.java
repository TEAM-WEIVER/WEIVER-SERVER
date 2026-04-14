package com.weaver.applicant.domain;


import com.weaver.applicant.type.EmploymentType;
import com.weaver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "work_experiences")
public class WorkExperience extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "experience_id")
    private Long experienceId;

    @Column(name = "company_name")
    private String companyName; // 회사명

    @Column(name = "start_date", columnDefinition = "DATE")
    private LocalDate startDate;    // 입사일

    @Column(name = "end_date", columnDefinition = "DATE")
    private LocalDate endDate;  // 퇴사일

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    private EmploymentType employmentType;  // 경력 형태

    @Column(name = "position")
    private String position;    // 직급

    @Column(name = "duties", columnDefinition = "TEXT")
    private String duties;  // 담당 업무

    @Column(name = "is_recognized")
    private boolean isRecognized;   // 경력 여부

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicants;

    public void assignApplicant(Applicant applicants) {
        this.applicants = applicants;
    }
}
