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

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchingEventService {

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
                jobPosting.getRequirements()
        );
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }
}
