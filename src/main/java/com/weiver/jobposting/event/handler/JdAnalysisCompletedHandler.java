package com.weiver.jobposting.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.domain.JdAnalysisResult;
import com.weiver.analysis.repository.JdAnalysisResultRepository;
import com.weiver.global.event.consumer.DomainEventHandler;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.metrics.AiRoundtripMetrics;
import com.weiver.global.metrics.type.AiFlow;
import com.weiver.global.metrics.type.AiOutcome;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.event.dto.JdAnalysisCompletedData;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JdAnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JdAnalysisCompletedHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final JobPostingRepository jobPostingRepository;
    private final JdAnalysisResultRepository jdAnalysisResultRepository;
    private final AiRoundtripMetrics aiRoundtripMetrics;

    @Override
    public EventType support() {
        return EventType.JD_ANALYSIS_COMPLETED;
    }

    @Override
    @Transactional
    public void handle(EventEnvelope<JsonNode> envelope) {
        // JD 분석 결과를 공고 기준으로 저장하고 분석 완료 상태를 기록한다.
        JdAnalysisCompletedData data = objectMapper.convertValue(
                envelope.data(),
                JdAnalysisCompletedData.class
        );
        validate(data);

        JobPosting jobPosting = jobPostingRepository.findById(data.jdId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        Long companyId = jobPosting.getCompany().getCompanyId();
        // 재수신된 결과는 지연 지표에 다시 넣지 않도록, 요청 중이던 공고만 기록한다.
        boolean wasRequested = jobPosting.getJdAnalysisStatus() == JdAnalysisStatus.REQUESTED;

        jdAnalysisResultRepository.findByJobPosting_JdId(jobPosting.getJdId())
                .ifPresentOrElse(
                        result -> result.updateAnalysis(companyId, data.originalText(), data.embedding()),
                        () -> jdAnalysisResultRepository.save(JdAnalysisResult.builder()
                                .jobPosting(jobPosting)
                                .companyId(companyId)
                                .originalText(data.originalText())
                                .embedding(data.embedding())
                                .build())
                );

        jobPosting.markJdAnalysisCompleted(envelope.occurredAt());
        if (wasRequested) {
            aiRoundtripMetrics.record(AiFlow.JD_ANALYSIS, AiOutcome.COMPLETED, jobPosting.getJdAnalysisRequestedAt());
        }
    }

    private void validate(JdAnalysisCompletedData data) {
        if (data.jdId() == null) {
            throw new NonRetryableEventException("jd_id is required");
        }
    }
}
