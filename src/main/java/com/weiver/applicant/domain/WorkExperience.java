package com.weiver.applicant.domain;


import com.weiver.applicant.dto.request.put.WorkExperienceUpdateDetailDTO;
import com.weiver.applicant.type.EmploymentType;
import com.weiver.global.common.BaseTimeEntity;
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

    @Column(name = "company_name", nullable = false)
    private String companyName; // 회사명

    @Column(name = "start_date", columnDefinition = "DATE", nullable = false)
    private LocalDate startDate;    // 입사일

    @Column(name = "end_date", columnDefinition = "DATE")
    private LocalDate endDate;  // 퇴사일

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false)
    private EmploymentType employmentType;  // 경력 형태

    @Column(name = "position", nullable = false)
    private String position;    // 직급

    @Column(name = "duties", columnDefinition = "TEXT")
    private String duties;  // 담당 업무

    @Column(name = "is_recognized", nullable = false)
    private boolean isRecognized;   // 경력 여부

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    /**
     * 편의 메소드
     * */
    public void updateWorkExperience(WorkExperienceUpdateDetailDTO updateDTO){
        if(updateDTO.companyName() != null) {
            this.companyName = updateDTO.companyName();
        }
        if(updateDTO.startDate() != null) {
            this.startDate = updateDTO.startDate();
        }
        if(updateDTO.endDate() != null) {
            this.endDate = updateDTO.endDate();
        }
        if(updateDTO.employmentType() != null) {
            this.employmentType = EmploymentType.valueOf(updateDTO.employmentType());
        }
        if(updateDTO.position() != null) {
            this.position = updateDTO.position();
        }
        if(updateDTO.duties() != null) {
            this.duties = updateDTO.duties();
        }
        if(updateDTO.isRecognized() != null) {
            this.isRecognized = updateDTO.isRecognized();
        }
    }

}
