package com.weiver.jobposting.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.jobposting.domain.EmailTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.weiver.jobposting.domain.QEmailTemplate.emailTemplate;
import static com.weiver.jobposting.domain.QJobPosting.jobPosting;

@Repository
@RequiredArgsConstructor
public class EmailTemplateRepositoryImpl implements EmailTemplateRepositoryCustom{

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<EmailTemplate> findWithJobPostingByJdId(Long jdId) {
        EmailTemplate result = queryFactory
                .selectFrom(emailTemplate)
                .join(emailTemplate.jobPosting, jobPosting).fetchJoin()
                .where(jobPosting.jdId.eq(jdId))
                .fetchOne();

        return Optional.ofNullable(result);
    }
}
