package com.weiver.matching.event;

import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.global.event.util.EventIds;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JobPostingStatus;
import com.weiver.matching.event.dto.MatchingRequestedData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchingEventService {

    private static final Map<String, String> PRIORITY_CODES = Map.ofEntries(
            Map.entry("성장가능성", "GROWTH_POTENTIAL"),
            Map.entry("대처능력", "ADAPTABILITY"),
            Map.entry("일관성", "CONSISTENCY"),
            Map.entry("협업 및 커뮤니케이션", "COLLABORATION"),
            Map.entry("협업 및 팀워크", "COLLABORATION"),
            Map.entry("커뮤니케이션", "COLLABORATION"),
            Map.entry("문제해결력", "PROBLEM_SOLVING"),
            Map.entry("논리력", "LOGICAL_THINKING"),
            Map.entry("논리성", "LOGICAL_THINKING"),
            Map.entry("자율·혁신", "AUTONOMY_INNOVATION"),
            Map.entry("성과·영향", "PERFORMANCE_IMPACT"),
            Map.entry("성취·결과", "PERFORMANCE_IMPACT"),
            Map.entry("안정·질서", "STABILITY_ORDER"),
            Map.entry("관계·공동체", "RELATIONSHIP_COMMUNITY")
    );

    private final JobPostingRepository jobPostingRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 스케줄러 등 내부 흐름에서 공고 ID로 AI 매칭 요청 이벤트를 발행한다.
     */
    public void publishMatchingRequested(Long jdId) {
        JobPosting jobPosting = jobPostingRepository.findById(jdId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        publishMatchingRequested(jobPosting);
    }

    /**
     * ACTIVE 공고만 AI 매칭 요청 이벤트로 발행한다.
     */
    public void publishMatchingRequested(JobPosting jobPosting) {
        if (jobPosting.getStatus() != JobPostingStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "활성화된 공고만 매칭을 실행할 수 있습니다.");
        }

        EventEnvelope<MatchingRequestedData> envelope = EventEnvelope.request(
                EventType.MATCHING_REQUESTED,
                toMatchingRequestedData(jobPosting),
                EventIds.newEventId()
        );

        domainEventPublisher.publish(envelope);
    }

    /**
     * 공고의 기술 스택과 요구사항을 매칭 요청 payload로 변환한다.
     */
    private MatchingRequestedData toMatchingRequestedData(JobPosting jobPosting) {
        return new MatchingRequestedData(
                jobPosting.getCompany().getCompanyId(),
                jobPosting.getJdId(),
                safeList(jobPosting.getRequiredTech()),
                jobPosting.getRequirements(),
                toPriorityData(jobPosting.getCompetencyPriorities()),
                toPriorityData(jobPosting.getTraitPriorities())
        );
    }

    private List<MatchingRequestedData.PriorityData> toPriorityData(List<String> priorities) {
        List<String> values = safeList(priorities).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        return IntStream.range(0, values.size())
                .mapToObj(index -> new MatchingRequestedData.PriorityData(
                        index + 1,
                        PRIORITY_CODES.getOrDefault(values.get(index), values.get(index)),
                        values.get(index)
                ))
                .toList();
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }
}
