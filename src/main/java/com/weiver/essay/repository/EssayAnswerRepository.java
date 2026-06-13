package com.weiver.essay.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.essay.domain.EssayAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EssayAnswerRepository extends JpaRepository<EssayAnswer, Long> {
    boolean existsByApplicant(Applicant applicant);

    @Query("""
            select ea
            from EssayAnswer ea
            join fetch ea.essayQuestion eq
            where ea.applicant = :applicant
            order by eq.sequence asc
            """)
    List<EssayAnswer> findAllByApplicantWithQuestionOrderBySequence(@Param("applicant") Applicant applicant);
}
