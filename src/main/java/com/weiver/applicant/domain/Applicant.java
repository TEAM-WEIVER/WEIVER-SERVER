package com.weiver.applicant.domain;

import com.weiver.applicant.dto.request.put.ApplicantInfoRequestDTO;
import com.weiver.global.common.BaseTimeEntity;
import com.weiver.global.common.UserRole;
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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.APPLICANT;

    @Column(name = "name", nullable = true)
    private String name;    // 사용자 이름

    @Column(name = "phone_number", nullable = true)
    private String phoneNumber; // 연락처

    @Column(name = "birthday", columnDefinition = "DATE", nullable = true)
    private LocalDate birthday; // 생년월일

    @Column(name = "photo_url", columnDefinition = "TEXT", nullable = true)
    private String photoUrl;    // s3 프로필 이미지 경로

    @Column(name = "last_screening_at", nullable = true)
    private LocalDateTime lastScreeningAt;  // 마지막 분석

    @Column(name = "next_available_screening_at", nullable = true)
    private LocalDateTime nextAvailableScreeningAt; // 다음 분석 가능 시점

    @Column(name = "address", nullable = true)
    private String address;  // 주소

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    /**
     * 정보 업데이트 편의메소드
     * */
    public void updateInfo(ApplicantInfoRequestDTO updateDTO, String photoUrl){
        this.photoUrl = photoUrl;
        this.name = updateDTO.name();
        this.email = updateDTO.email();
        this.phoneNumber = updateDTO.phoneNumber();
        this.address = updateDTO.address();
        this.birthday = updateDTO.birthday();
    }

    public void withdraw() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
