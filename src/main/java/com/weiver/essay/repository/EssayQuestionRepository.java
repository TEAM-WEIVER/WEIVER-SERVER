package com.weiver.essay.repository;

import com.weiver.essay.domain.EssayQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EssayQuestionRepository extends JpaRepository<EssayQuestion, Long> {
}
