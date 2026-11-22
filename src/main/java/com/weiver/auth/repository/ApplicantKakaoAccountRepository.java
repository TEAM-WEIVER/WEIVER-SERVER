package com.weiver.auth.repository;

import com.weiver.auth.domain.ApplicantKakaoAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicantKakaoAccountRepository extends JpaRepository<ApplicantKakaoAccount, Long> {

    Optional<ApplicantKakaoAccount> findByKakaoUserId(Long kakaoUserId);

    Optional<ApplicantKakaoAccount> findByApplicant_ApplicantId(Long applicantId);

    boolean existsByApplicant_ApplicantId(Long applicantId);
}
