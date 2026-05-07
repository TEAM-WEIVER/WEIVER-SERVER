package com.weiver.matching.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.applicant.domain.QWorkExperience;
import com.weiver.matching.dto.request.ApplicantSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.weiver.matching.domain.QMatchResult.matchResult;
import static com.weiver.applicant.domain.QApplicant.applicant;
import static com.weiver.analysis.domain.QCultureReport.cultureReport;
import static com.weiver.analysis.domain.QTechnicalSkillReport.technicalSkillReport;
import static com.weiver.applicant.domain.QWorkExperience.workExperience;

@Repository
@RequiredArgsConstructor
public class MatchResultRepositoryImpl implements MatchResultRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Tuple> searchApplicantsTuple(ApplicantSearchCondition condition, Pageable pageable) {


        QWorkExperience weSub = new QWorkExperience("weSub");

        StringExpression recentPositionSubQuery = Expressions.asString(
                JPAExpressions.select(workExperience.position)
                        .from(workExperience)
                        .where(
                                workExperience.applicant.eq(applicant),
                                workExperience.experienceId.eq(
                                        JPAExpressions.select(weSub.experienceId.max())
                                                .from(weSub)
                                                .where(weSub.applicant.eq(applicant))
                                )
                        )
        );

        List<Tuple> content = queryFactory
                .select(matchResult, cultureReport, technicalSkillReport, recentPositionSubQuery)
                .from(matchResult)
                .join(matchResult.applicant, applicant)

                .leftJoin(cultureReport).on(cultureReport.applicant.eq(applicant))
                .leftJoin(technicalSkillReport).on(technicalSkillReport.applicant.eq(applicant))

                .where(
                        matchResult.jobPosting.jdId.eq(condition.jdId()),
                        keywordContains(condition.keyword()),
                        skillScoreGoe(condition.skillScoreMin()),
                        cultureStyleEq(condition.cultureStyle()),
                        techStacksContain(condition.techStacks())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(matchResult.skillScore.desc(), matchResult.createTime.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(matchResult.count())
                .from(matchResult)
                .join(matchResult.applicant, applicant)
                .leftJoin(cultureReport).on(cultureReport.applicant.eq(applicant))
                .leftJoin(technicalSkillReport).on(technicalSkillReport.applicant.eq(applicant))
                .where(
                        matchResult.jobPosting.jdId.eq(condition.jdId()),
                        keywordContains(condition.keyword()),
                        skillScoreGoe(condition.skillScoreMin()),
                        cultureStyleEq(condition.cultureStyle()),
                        techStacksContain(condition.techStacks())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

  
    /**
     * 동적 쿼리 메소드들 - 검색 조건이 없는 경우 null 반환하여 where 절에서 무시되도록 처리
     * */

    private BooleanExpression keywordContains(String keyword) {
        return StringUtils.hasText(keyword) ? applicant.name.contains(keyword) : null;
    }

    private BooleanExpression skillScoreGoe(Integer minScore) {
        return minScore != null ? matchResult.skillScore.goe(minScore) : null;
    }

    private BooleanExpression cultureStyleEq(String style) {
        if (!StringUtils.hasText(style)) return null;
        try {
            return cultureReport.culturefitStyles.eq(CulturefitStyle.valueOf(style));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BooleanExpression techStacksContain(List<String> techStacks) {
        if (techStacks == null || techStacks.isEmpty()) return null;

        BooleanExpression expression = null;
        for (String stack : techStacks) {
            // JSONB 배열 데이터 문자열 스캔 처리
            BooleanExpression likeExpression = Expressions.booleanTemplate(
                    "CAST({0} AS string) like {1}", technicalSkillReport.skillTags, "%\"" + stack + "\"%");
            expression = (expression == null) ? likeExpression : expression.and(likeExpression);
        }
        return expression;
    }
}