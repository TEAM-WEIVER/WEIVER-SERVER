package com.weiver.jobposting.event;

import com.weiver.company.domain.Company;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.global.event.util.EventIds;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.event.dto.JdAnalysisRequestedData;
import com.weiver.jobposting.type.JobPostingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@Transactional
@RequiredArgsConstructor
public class JobPostingEventService {

    private final DomainEventPublisher domainEventPublisher;

    /**
     * ACTIVE 공고만 JD 분석 요청 이벤트로 발행하고 요청 상태를 기록한다.
     */
    public void publishJdAnalysisRequested(JobPosting jobPosting) {
        if (jobPosting.getStatus() != JobPostingStatus.ACTIVE) {
            return;
        }

        EventEnvelope<JdAnalysisRequestedData> envelope = EventEnvelope.request(
                EventType.JD_ANALYSIS_REQUESTED,
                toJdAnalysisRequestedData(jobPosting),
                EventIds.newEventId()
        );

        domainEventPublisher.publish(envelope);
        jobPosting.markJdAnalysisRequested();
    }

    /**
     * 공고 내용과 회사 컬처/업무 방식을 AI 서버가 쓰는 JD 분석 payload로 변환한다.
     */
    private JdAnalysisRequestedData toJdAnalysisRequestedData(JobPosting jobPosting) {
        Company company = jobPosting.getCompany();

        return new JdAnalysisRequestedData(
                jobPosting.getJdId(),
                company.getCompanyId(),
                jobPosting.getTitle(),
                jobPosting.getJobCategory(),
                jobPosting.getDetailedJob(),
                jobPosting.getJobDescription(),
                jobPosting.getRequirements(),
                jobPosting.getQualifications(),
                jobPosting.getPreferredQualifications(),
                safeList(jobPosting.getRequiredTech()),
                companyCulture(company),
                new JdAnalysisRequestedData.WorkStyleData(
                        company.getWorkPace(),
                        company.getDecisionMaking(),
                        company.getRoleDefinition(),
                        company.getOperationStyle()
                )
        );
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }

    /**
     * 회사 문화 설명과 방향성 설명을 하나의 분석용 텍스트로 합친다.
     */
    private String companyCulture(Company company) {
        return Stream.of(company.getCultureDescription(), company.getDirectionDescription())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(Objects::nonNull)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }
}
