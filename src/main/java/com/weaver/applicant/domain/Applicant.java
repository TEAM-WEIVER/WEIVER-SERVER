package com.weaver.applicant.domain;

import com.weaver.analysis.domain.TechnicalSkill;
import com.weaver.essay.domain.EssayAnswer;
import com.weaver.global.common.BaseTimeEntity;
import com.weaver.portfolio.domain.Portfolio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
public class Applicant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long applicantId;

    private String email;   // 이메일
    private String password;    // 비밀번호
    private String name;    // 사용자 이름
    private String phoneNumber; // 연락처

    @Column(columnDefinition = "DATE")
    private LocalDate birthDay; // 생년월일

    @Column(columnDefinition = "TEXT")
    private String photoUrl;    // s3 프로필 이미지 경로

    private LocalDateTime lastScreeningAt;  // 마지막 분석

    private LocalDateTime nextAvailableScreeningAt; // 다음 분석 가능 시점

    @OneToOne(mappedBy = "applicant", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    private TechnicalSkill technicalSkill;

    @OneToOne(mappedBy = "applicant", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    private EssayAnswer essayAnswer;

    @Builder.Default
    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<Education> educations = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<WorkExperience> workExperiences = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<Award> awards = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<Certificate> certificates = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<Portfolio> portfolios = new ArrayList<>();





    /**
     * 편의 메소드
     * */
    public void addEducation(Education education) {
        this.educations.add(education);
        education.assignApplicant(this);
    }

    public void addWorkExperience(WorkExperience workExperience) {
        workExperiences.add(workExperience);
        workExperience.assignApplicant(this);
    }

    public void addAward(Award award) {
        awards.add(award);
        award.assignApplicant(this);
    }

    public void addCertificate(Certificate certificate) {
        certificates.add(certificate);
        certificate.assignApplicant(this);
    }

    public void addPortfolio(Portfolio portfolio) {
        portfolios.add(portfolio);
        portfolio.assignApplicant(this);
    }

    public void assignTechnicalSkill(TechnicalSkill technicalSkill) {
        this.technicalSkill = technicalSkill;
        technicalSkill.assignApplicant(this);
    }

    public void assignEssayAnswer(EssayAnswer essayAnswer) {
        this.essayAnswer = essayAnswer;
        essayAnswer.assignApplicant(this);
    }
}
