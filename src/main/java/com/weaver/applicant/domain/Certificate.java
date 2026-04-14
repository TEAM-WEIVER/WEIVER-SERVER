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
@Table(name = "certificates")
public class Certificate extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "certificate_id")
    private Long certificateId;

    @Column(name = "certificate_name", nullable = false)
    private String certificateName; // 자격증명

    @Column(name = "issuer", nullable = false)
    private String issuer;  // 발행처

    @Column(name = "acquisition_date", columnDefinition = "DATE", nullable = false)
    private LocalDate acquisitionDate; // 취득 날짜

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicants;

    public void assignApplicant(Applicant applicants) {
        this.applicants = applicants;
    }
}
