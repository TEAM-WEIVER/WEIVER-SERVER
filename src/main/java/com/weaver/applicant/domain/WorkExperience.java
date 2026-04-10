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
public class WorkExperience extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long experienceId;

    private String companyName; // 회사명

    @Column(columnDefinition = "DATE")
    private LocalDate startDate;    // 입사일

    @Column(columnDefinition = "DATE")
    private LocalDate endDate;  // 퇴사일

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;  // 경력 형태

    private String position;    // 직급

    @Column(columnDefinition = "TEXT")
    private String duties;  // 담당 업무

    private boolean isRecognized;   // 경력 여부

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    private Applicant applicant;

    public void assignApplicant(Applicant applicant) {
        this.applicant = applicant;
    }
}
