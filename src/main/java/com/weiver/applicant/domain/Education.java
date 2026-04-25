package com.weiver.applicant.domain;

import com.weiver.applicant.dto.request.put.EducationUpdateDetailDTO;
import com.weiver.applicant.type.Degree;
import com.weiver.applicant.type.Status;
import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.YearMonth;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "educations")
public class Education extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "education_id")
    private Long educationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "degree", nullable = false)
    private Degree degree;

    @Column(name = "school_name", nullable = false)
    private String schoolName;

    @Column(name = "major", nullable = false)
    private String major;

    @Column(name = "gpa", columnDefinition = "DECIMAL(3,2)")
    private BigDecimal gpa;

    @Column(name = "start_date", columnDefinition = "DATE", nullable = false)
    private YearMonth startDate;

    @Column(name = "end_date", columnDefinition = "DATE")
    private YearMonth endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    /**
     * 편의 메소드
     * */
    public void updateEducation(EducationUpdateDetailDTO updateDTO){
        if(updateDTO.degreeType() != null) {
            this.degree = Degree.valueOf(updateDTO.degreeType());
        }
        if(updateDTO.schoolName() != null) {
            this.schoolName = updateDTO.schoolName();
        }
        if(updateDTO.major() != null) {
            this.major = updateDTO.major();
        }
        if(updateDTO.gpa() != null) {
            this.gpa = new BigDecimal(updateDTO.gpa());
        }
        if(updateDTO.startDate() != null) {
            this.startDate = YearMonth.parse(updateDTO.startDate());
        }
        if(updateDTO.endDate() != null) {
            this.endDate = YearMonth.parse(updateDTO.endDate());
        }
        if(updateDTO.status() != null) {
            this.status = Status.valueOf(updateDTO.status());
        }
    }
}
