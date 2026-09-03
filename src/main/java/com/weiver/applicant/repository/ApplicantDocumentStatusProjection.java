package com.weiver.applicant.repository;

import java.time.LocalDate;

public interface ApplicantDocumentStatusProjection {
    String getName();

    String getEmail();

    String getPhoneNumber();

    LocalDate getBirthday();

    Boolean getResumeDetailCompleted();

    Boolean getEssayCompleted();

    Boolean getPortfolioCompleted();
}
