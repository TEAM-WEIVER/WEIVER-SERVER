package com.weiver.applicant.domain;

import com.weiver.applicant.dto.request.put.AwardUpdateDetailDTO;
import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "awards")
public class Award extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "award_id")
    private Long awardId;

    @Column(name = "award_name", nullable = false)
    private String awardName; // 대회, 상훈명

    @Column(name = "issuer")
    private String issuer; // 발행 기관

    @Column(name = "award_date", columnDefinition = "DATE", nullable = false)
    private LocalDate awardDate; // 수상 날짜


    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    public void assignApplicant(Applicant applicant) {
        this.applicant = applicant;
    }

    public void updateAward(AwardUpdateDetailDTO updateDTO){
        if(updateDTO.awardDate() != null) {
            this.awardDate = LocalDate.parse(updateDTO.awardDate());
        }
        if(updateDTO.awardName() != null) {
            this.awardName = updateDTO.awardName();
        }
        if(updateDTO.issuer() != null) {
            this.issuer = updateDTO.issuer();
        }
    }
}
