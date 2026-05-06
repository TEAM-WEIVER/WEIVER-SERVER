package com.weiver.jobposting.repository;

import com.weiver.jobposting.domain.EmailTemplate;

import java.util.Optional;

public interface EmailTemplateRepositoryCustom {
    Optional<EmailTemplate> findWithJobPostingByJdId(Long jdId);
}
