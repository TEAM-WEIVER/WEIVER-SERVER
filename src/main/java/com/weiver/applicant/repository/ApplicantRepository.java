package com.weiver.applicant.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.type.ProfileSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface ApplicantRepository extends JpaRepository<Applicant, Long> {
    Optional<Applicant> findByPublicId(String publicId);
    boolean existsByEmail(String email);
    Optional<Applicant> findByEmailAndDeletedFalse(String email);
    Optional<Applicant> findByPublicIdAndDeletedFalse(String publicId);

    /** 대기 지표용: 해당 프로필 동기화 상태인 지원자 수. */
    long countByProfileSyncStatus(ProfileSyncStatus profileSyncStatus);

    /** 대기 지표용: 해당 프로필 동기화 상태인 지원자 중 가장 이른 요청 시각. 요청 시각이 null인 행은 MIN에서 빠진다. */
    @Query("SELECT MIN(a.profileSyncRequestedAt) FROM Applicant a WHERE a.profileSyncStatus = :status")
    Optional<OffsetDateTime> findOldestProfileSyncRequestedAt(@Param("status") ProfileSyncStatus status);
}
