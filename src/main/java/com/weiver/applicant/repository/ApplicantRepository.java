package com.weiver.applicant.repository;

import com.weiver.applicant.domain.Applicant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicantRepository extends JpaRepository<Applicant, Long> {
    Optional<Applicant> findByPublicId(String publicId);
    boolean existsByEmail(String email);
    Optional<Applicant> findByEmailAndDeletedFalse(String email);
    Optional<Applicant> findByPublicIdAndDeletedFalse(String publicId);

    /**
     * 분석 제출·완료 처리용: 지원자 행을 비관적 쓰기 잠금으로 조회한다.
     * 지원자 행을 먼저 잠그고 그다음 제출 건 행을 다룬다(순서 고정).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Applicant a WHERE a.publicId = :publicId")
    Optional<Applicant> findWithLockByPublicId(@Param("publicId") String publicId);

    /** 분석 완료 처리용: 지원자 id로 지원자 행을 비관적 쓰기 잠금으로 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Applicant a WHERE a.applicantId = :applicantId")
    Optional<Applicant> findWithLockByApplicantId(@Param("applicantId") Long applicantId);

    /**
     * 현재 제출 버전 포인터를 바꾼다. {@code current_submission_version}은 엔티티에서 updatable=false라 이 쿼리로만 갱신한다.
     * 지원자 행 잠금({@link #findWithLockByPublicId})을 쥔 제출 트랜잭션 안에서만 부른다.
     * 같은 트랜잭션의 관리 엔티티(잠근 지원자·저장한 제출 건)를 유지하려고 영속성 컨텍스트는 비우지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Applicant a SET a.currentSubmissionVersion = :submissionVersion WHERE a.applicantId = :applicantId")
    int updateCurrentSubmissionVersion(@Param("applicantId") Long applicantId,
                                       @Param("submissionVersion") Integer submissionVersion);
}
