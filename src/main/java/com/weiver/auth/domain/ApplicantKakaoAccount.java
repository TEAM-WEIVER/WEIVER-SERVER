package com.weiver.auth.domain;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "applicant_kakao_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicantKakaoAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "applicant_kakao_account_id")
    private Long id;

    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false, unique = true)
    private Applicant applicant;

    @Column(name = "kakao_user_id", nullable = false, unique = true)
    private Long kakaoUserId;

    @Builder
    private ApplicantKakaoAccount(Applicant applicant, Long kakaoUserId) {
        this.applicant = applicant;
        this.kakaoUserId = kakaoUserId;
    }
}
