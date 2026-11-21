package com.weiver.applicant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.event.ApplicantAnalysisEventService;
import com.weiver.analysis.event.dto.ApplicantAnalysisRequestedData;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAnalysisSubmission;
import com.weiver.applicant.repository.ApplicantAnalysisSubmissionRepository;
import com.weiver.global.event.util.EventIds;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 지원자 분석 제출 건(applicant_analysis_submissions)을 다룬다. 현재 상태의 정본은 제출 건 행이다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ApplicantAnalysisSubmissionService {

    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {};

    private final ApplicantAnalysisSubmissionRepository submissionRepository;
    private final ApplicantAnalysisSnapshotService snapshotService;
    private final ApplicantAnalysisEventService applicantAnalysisEventService;
    private final ObjectMapper objectMapper;

    /** 분석 요청부터 마감까지의 시간. 마감 시각을 읽어 처리하는 로직은 #207에서 만든다. */
    @Value("${weiver.applicant-analysis.deadline}")
    private final Duration analysisDeadline;

    /** 지원자의 현재 제출 건. 제출한 적이 없으면 DB를 읽지 않고 빈 값을 돌려준다. */
    @Transactional(readOnly = true)
    public Optional<ApplicantAnalysisSubmission> findCurrent(Applicant applicant) {
        Integer currentVersion = applicant.getCurrentSubmissionVersion();
        if (currentVersion == null) {
            return Optional.empty();
        }
        return submissionRepository.findByApplicantAndSubmissionVersion(applicant, currentVersion);
    }

    /** 현재 제출 건이 분석 요청 중(REQUESTED)인지. */
    @Transactional(readOnly = true)
    public boolean isCurrentRequested(Applicant applicant) {
        return findCurrent(applicant)
                .map(ApplicantAnalysisSubmission::isRequested)
                .orElse(false);
    }

    /**
     * 다음 버전의 제출 건을 REQUESTED로 저장하고 지원자의 현재 제출을 바꾼 뒤, 분석 요청을 커밋 뒤에 발행한다.
     *
     * <p>호출자가 지원자 행을 잠그고({@code ApplicantRepository.findWithLockByPublicId}) 제출 가능 여부를 확인한
     * 같은 트랜잭션에서 불러야 한다. (applicant_id, submission_version) 유니크 제약은 마지막 방어선이다.
     */
    public ApplicantAnalysisSubmission submit(Applicant applicant) {
        int submissionVersion = applicant.nextSubmissionVersion();
        String eventId = EventIds.newEventId();

        ApplicantAnalysisRequestedData data = snapshotService.assemble(applicant, submissionVersion);
        Map<String, Object> snapshot = objectMapper.convertValue(data, SNAPSHOT_TYPE);

        ApplicantAnalysisSubmission submission = submissionRepository.save(ApplicantAnalysisSubmission.request(
                applicant,
                submissionVersion,
                eventId,
                snapshot,
                snapshotService.contentHash(snapshot),
                OffsetDateTime.now(),
                analysisDeadline
        ));
        applicant.pointToSubmission(submissionVersion);

        applicantAnalysisEventService.publishApplicantAnalysisRequested(eventId, data);
        return submission;
    }
}
