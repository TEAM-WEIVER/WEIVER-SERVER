package com.weiver.applicant.event;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.event.dto.ApplicantProfileChangedData;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.repository.CertificateRepository;
import com.weiver.applicant.repository.EducationRepository;
import com.weiver.applicant.repository.WorkExperienceRepository;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.global.event.util.EventIds;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.portfolio.domain.Portfolio;
import com.weiver.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.YearMonth;

@Service
@Transactional
@RequiredArgsConstructor
public class ApplicantProfileEventService {

    private static final String PDF_EXTENSION = "pdf";

    private final ApplicantRepository applicantRepository;
    private final EducationRepository educationRepository;
    private final WorkExperienceRepository workExperienceRepository;
    private final CertificateRepository certificateRepository;
    private final EssayAnswerRepository essayAnswerRepository;
    private final PortfolioRepository portfolioRepository;
    private final S3Service s3Service;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 지원자 프로필 전체 스냅샷을 AI 서버로 보내고 동기화 요청 상태로 바꾼다.
     *
     * <p>제출 가능 여부(서류 작성 완료, 중복 제출) 검증은 호출부인
     * {@code ApplicantService.submitProfile}이 담당한다. 이 메서드는 스냅샷 구성과 발행만 책임진다.
     */
    public void publishProfileChanged(Long applicantId) {
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        EventEnvelope<ApplicantProfileChangedData> envelope = EventEnvelope.request(
                EventType.APPLICANT_PROFILE_CHANGED,
                toProfileChangedData(applicant),
                EventIds.newEventId()
        );

        applicant.markProfileSyncRequested();
        publishAfterCommit(envelope);
    }

    /**
     * 지원자 기본 정보, 학력, 경력, 자격증, 자기소개서, 포트폴리오 PDF 위치를 이벤트 payload로 조합한다.
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
                        .toList(),
                portfolioRepository.findByApplicant(applicant)
                        .map(this::toPortfolioData)
                        .orElse(null)
        );
    }

    /**
     * 포트폴리오 PDF는 AI 서버가 S3에서 직접 내려받도록 버킷·키만 담는다(Presigned URL은 만료되므로 쓰지 않는다).
     * 파일이 없거나 PDF가 아니거나 URL을 해석할 수 없으면 file은 null이다.
     */
    private ApplicantProfileChangedData.PortfolioData toPortfolioData(Portfolio portfolio) {
        return new ApplicantProfileChangedData.PortfolioData(
                portfolio.getPortfolioId(),
                toPortfolioFileData(portfolio)
        );
    }

    private ApplicantProfileChangedData.PortfolioFileData toPortfolioFileData(Portfolio portfolio) {
        if (!StringUtils.hasText(portfolio.getFileKey()) || !isPdf(portfolio)) {
            return null;
        }
        return s3Service.resolveObjectLocation(portfolio.getFileKey())
                .map(location -> new ApplicantProfileChangedData.PortfolioFileData(
                        location.bucket(),
                        location.key(),
                        portfolio.getFileName(),
                        portfolio.getFileSize()
                ))
                .orElse(null);
    }

    /** 저장된 파일 유형으로 판별하고, 유형이 비어 있으면 원본 파일명의 확장자로 판별한다. */
    private boolean isPdf(Portfolio portfolio) {
        String extension = StringUtils.hasText(portfolio.getFileType())
                ? portfolio.getFileType()
                : StringUtils.getFilenameExtension(portfolio.getFileName());
        return PDF_EXTENSION.equalsIgnoreCase(extension);
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
