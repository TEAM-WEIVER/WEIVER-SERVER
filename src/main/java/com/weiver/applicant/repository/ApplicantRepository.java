package com.weiver.applicant.repository;

import com.weiver.applicant.domain.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query(value = """
        SELECT
            a.name AS "name",
            a.email AS "email",
            a.phone_number AS "phoneNumber",
            a.birthday AS "birthday",
            (
                EXISTS (
                    SELECT 1
                    FROM educations e
                    WHERE e.applicant_id = a.applicant_id
                )
                OR EXISTS (
                    SELECT 1
                    FROM work_experiences w
                    WHERE w.applicant_id = a.applicant_id
                )
                OR EXISTS (
                    SELECT 1
                    FROM certificates c
                    WHERE c.applicant_id = a.applicant_id
                )
                OR EXISTS (
                    SELECT 1
                    FROM awards aw
                    WHERE aw.applicant_id = a.applicant_id
                )
            ) AS "resumeDetailCompleted",
            EXISTS (
                SELECT 1
                FROM essay_answers ea
                WHERE ea.applicant_id = a.applicant_id
            ) AS "essayCompleted",
            EXISTS (
                SELECT 1
                FROM portfolios p
                WHERE p.applicant_id = a.applicant_id
            ) AS "portfolioCompleted"
        FROM applicants a
        WHERE a.public_id = :publicId
        """, nativeQuery = true)
    Optional<ApplicantDocumentStatusProjection> findDocumentStatusByPublicId(
            @Param("publicId") String publicId
    );
}
