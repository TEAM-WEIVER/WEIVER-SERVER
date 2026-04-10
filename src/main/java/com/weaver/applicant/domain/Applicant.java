package com.weaver.applicant.domain;

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
public class Applicant {

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

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime lastScreeningAt;  // 마지막 분석

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime nextAvailableScreeningAt; // 다음 분석 가능 시점

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt; // 생성 시간

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime updatedAt; // 수정 시간

    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    List<Education> educations = new ArrayList<>();

    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    List<WorkExperience> workExperiences = new ArrayList<>();

    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    List<Award> awards = new ArrayList<>();

    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    List<Certificate> certificates = new ArrayList<>();


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
}
