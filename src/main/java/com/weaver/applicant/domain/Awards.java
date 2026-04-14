package com.weaver.applicant.domain;

import com.weaver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "awards")
public class Awards extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "award_id")
    private Long awardId;

    @Column(name = "award_name")
    private String awardName;

    @Column(name = "issuer")
    private String issuer;

    @Column(name = "award_date",columnDefinition = "DATE")
    private LocalDate awardDate;


    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicants applicants;

    public void assignApplicant(Applicants applicants) {
        this.applicants = applicants;
    }
}
