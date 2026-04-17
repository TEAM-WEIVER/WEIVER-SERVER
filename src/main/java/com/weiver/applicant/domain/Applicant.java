package com.weiver.applicant.domain;

import com.weiver.global.common.BaseTimeEntity;
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

    @Column(name = "email", unique = true, nullable = false)
    private String email;   // 이메일

    @Column(name = "password", nullable = false)
    private String password;    // 비밀번호

    @Column(name = "name", nullable = false)
    private String name;    // 사용자 이름

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber; // 연락처

    @Column(name = "birthday", columnDefinition = "DATE", nullable = false)
    private LocalDate birthday; // 생년월일

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;    // s3 프로필 이미지 경로

    @Column(name = "last_screening_at")
    private LocalDateTime lastScreeningAt;  // 마지막 분석

    @Column(name = "next_available_screening_at")
    private LocalDateTime nextAvailableScreeningAt; // 다음 분석 가능 시점

}
