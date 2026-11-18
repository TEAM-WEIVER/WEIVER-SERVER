package com.weiver.matching.repository;

import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.repository.projection.MatchingNotificationGroupDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long>, MatchResultRepositoryCustom {
    @Query("""
            SELECT new com.weiver.matching.repository.projection.MatchingNotificationGroupDTO(
                j.jdId, j.title, j.company.companyId, COUNT(m), MAX(m.createTime),
                (SELECT MAX(r.matchId) FROM MatchResult r
                 WHERE r.jobPosting.jdId = j.jdId AND r.isNotified = false
                   AND r.createTime = (
                       SELECT MAX(t.createTime) FROM MatchResult t
                       WHERE t.jobPosting.jdId = j.jdId AND t.isNotified = false
                   ))
            )
            FROM MatchResult m JOIN m.jobPosting j
            WHERE m.isNotified = false
            GROUP BY j.jdId, j.title, j.company.companyId
            """)
    List<MatchingNotificationGroupDTO> findUnnotifiedGroups();

    // 호출 서비스는 MatchingProcessingLockRepository의 공통 잠금을 먼저 획득해야 한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MatchResult m SET m.isNotified = true WHERE m.isNotified = false")
    int markAllUnnotifiedAsNotified();

    Optional<MatchResult> findByJobPosting_JdIdAndApplicant_PublicId(Long jdId, String applicantPublicId);

    Optional<MatchResult> findByJobPosting_JdIdAndApplicant_ApplicantId(Long jdId, Long applicantId);

    // 호출 서비스는 MatchingProcessingLockRepository의 공통 잠금을 먼저 획득해야 한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MatchResult m WHERE m.jobPosting.jdId = :jdId")
    void bulkDeleteByJdId(@Param("jdId") Long jdId);

    boolean existsByJobPosting_JdIdAndApplicant_PublicIdAndJobPosting_Company_PublicId(
            Long jdId, String applicantPublicId, String companyPublicId
    );
}
