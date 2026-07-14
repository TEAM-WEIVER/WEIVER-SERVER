package com.weiver.applicant.event;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.event.dto.ApplicantProfileChangedData;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.repository.CertificateRepository;
import com.weiver.applicant.repository.EducationRepository;
import com.weiver.applicant.repository.WorkExperienceRepository;
import com.weiver.applicant.type.ProfileSyncStatus;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.global.event.util.EventIds;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.YearMonth;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ApplicantProfileEventService {

    private static final int REQUIRED_ESSAY_ANSWER_COUNT = 3;

    private final ApplicantRepository applicantRepository;
    private final EducationRepository educationRepository;
    private final WorkExperienceRepository workExperienceRepository;
    private final CertificateRepository certificateRepository;
    private final EssayAnswerRepository essayAnswerRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 지원자 프로필 전체 스냅샷을 AI 서버로 보내고 동기화 요청 상태로 바꾼다.
     */
    public void publishProfileChanged(Long applicantId) {
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        ApplicantProfileChangedData data = toProfileChangedData(applicant);
        if (applicant.getProfileSyncStatus() == ProfileSyncStatus.PENDING
                && !isInitialProfileComplete(applicant, data)) {
            return;
        }

        EventEnvelope<ApplicantProfileChangedData> envelope = EventEnvelope.request(
                EventType.APPLICANT_PROFILE_CHANGED,
                data,
                EventIds.newEventId()
        );

        applicant.markProfileSyncRequested();
        publishAfterCommit(envelope);
    }

    /**
     * 최초 동기화는 AI 분석에 필요한 프로필이 갖춰진 뒤 시작한다.
     * 경력과 자격증은 선택 항목이므로 학력/경력/자격증 중 하나 이상을 이력서 상세 완료로 본다.
     */
    private boolean isInitialProfileComplete(Applicant applicant, ApplicantProfileChangedData data) {
        boolean basicInfoCompleted = StringUtils.hasText(applicant.getName())
                && StringUtils.hasText(applicant.getEmail())
                && StringUtils.hasText(applicant.getPhoneNumber())
                && applicant.getBirthday() != null;

        boolean resumeDetailCompleted = !data.educations().isEmpty()
                || !data.experiences().isEmpty()
                || !data.certifications().isEmpty();

        return basicInfoCompleted
                && resumeDetailCompleted
                && data.essay().size() == REQUIRED_ESSAY_ANSWER_COUNT;
    }

    /**
     * 지원자 기본 정보, 학력, 경력, 자격증, 자기소개서를 이벤트 payload로 조합한다.
     */
    private ApplicantProfileChangedData toProfileChangedData(Applicant applicant) {
        return new ApplicantProfileChangedData(
                applicant.getApplicantId(),
                applicant.getName(),
                educationRepository.findAllByApplicant(applicant).stream()
                        .map(this::toEducationData)
                        .toList(),
                workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant).stream()
                        .map(this::toExperienceData)
                        .toList(),
                certificateRepository.findAllByApplicant(applicant).stream()
                        .map(certificate -> certificate.getCertificateName())
                        .toList(),
                essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant).stream()
                        .map(answer -> new ApplicantProfileChangedData.EssayData(
                                answer.getAnswerId(),
                                answer.getEssayQuestion().getQuestion(),
                                answer.getAnswer()
                        ))
                        .toList()
        );
    }

    private ApplicantProfileChangedData.EducationData toEducationData(Education education) {
        return new ApplicantProfileChangedData.EducationData(
                education.getEducationId(),
                education.getDegree() != null ? education.getDegree().name() : null,
                education.getSchoolName(),
                education.getMajor(),
                education.getGpa(),
                format(education.getStartDate()),
                format(education.getEndDate()),
                education.getStatus() != null ? education.getStatus().name() : null
        );
    }

    private ApplicantProfileChangedData.ExperienceData toExperienceData(WorkExperience experience) {
        return new ApplicantProfileChangedData.ExperienceData(
                experience.getExperienceId(),
                experience.getCompanyName(),
                experience.getStartDate(),
                experience.getEndDate(),
                experience.getEmploymentType() != null ? experience.getEmploymentType().name() : null,
                experience.getPosition(),
                experience.getDuties(),
                experience.isRecognized()
        );
    }

    private String format(YearMonth yearMonth) {
        return yearMonth != null ? yearMonth.toString() : null;
    }

    private void publishAfterCommit(EventEnvelope<?> envelope) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            domainEventPublisher.publish(envelope);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                domainEventPublisher.publish(envelope);
            }
        });
    }
}
