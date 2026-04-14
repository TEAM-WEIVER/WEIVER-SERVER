package com.weaver.applicant.domain;

import com.weaver.analysis.domain.CultureReport;
import com.weaver.analysis.domain.TechnicalSkillReport;
import com.weaver.essay.domain.EssayAnswer;
import com.weaver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "applicants")
public class Applicant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "applicant_id")
    private Long applicantId;

    @Column(name = "email", unique = true)
    private String email;   // 이메일

    @Column(name = "password")
    private String password;    // 비밀번호

    @Column(name = "name")
    private String name;    // 사용자 이름

    @Column(name = "phone_number")
    private String phoneNumber; // 연락처

    @Column(name = "birthday", columnDefinition = "DATE")
    private LocalDate birthday; // 생년월일

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;    // s3 프로필 이미지 경로

    @Column(name = "last_screening_at")
    private LocalDateTime lastScreeningAt;  // 마지막 분석

    @Column(name = "next_available_screening_at")
    private LocalDateTime nextAvailableScreeningAt; // 다음 분석 가능 시점

    @OneToOne(mappedBy = "applicant", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    private TechnicalSkillReport technicalSkillReports;

    @OneToOne(mappedBy = "applicant", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    private EssayAnswer essayAnswers;

    @OneToOne(mappedBy = "applicant", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    private CultureReport cultureReports;

}
