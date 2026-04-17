package com.weiver.applicant.domain;

import com.weiver.applicant.type.Degree;
import com.weiver.applicant.type.Status;
import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    private LocalDate startDate;

    @Column(name = "end_date", columnDefinition = "DATE")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    public void assignApplicant(Applicant applicant) {
        this.applicant = applicant;
    }
}
