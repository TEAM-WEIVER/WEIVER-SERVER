package com.weiver.applicant.domain;

import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Table(name = "applicant_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicantAgreement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "applicant_agreement_id")
    private Long applicantAgreementId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false, updatable = true)
    private Applicant applicant;

    @Column(nullable = false)
    private boolean termsOfService;

    @Column(nullable = false)
    private boolean privacyPolicy;

    @Column(nullable = false)
    private boolean individualMemberTerms;

    @Column(nullable = false)
    private boolean aiAnalysisConsent;

    @Column(nullable = false)
    private boolean sensitiveDataConsent;

    @Column(nullable = false)
    private boolean marketingConsent;

    @Builder
    private ApplicantAgreement(
            Applicant applicant,
            boolean termsOfService,
            boolean privacyPolicy,
            boolean individualMemberTerms,
            boolean aiAnalysisConsent,
            boolean sensitiveDataConsent,
            boolean marketingConsent
    ) {
        this.applicant = applicant;
        this.termsOfService = termsOfService;
        this.privacyPolicy = privacyPolicy;
        this.individualMemberTerms = individualMemberTerms;
        this.aiAnalysisConsent = aiAnalysisConsent;
        this.sensitiveDataConsent = sensitiveDataConsent;
        this.marketingConsent = marketingConsent;
    }
}
