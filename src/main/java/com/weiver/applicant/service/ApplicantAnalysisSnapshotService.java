package com.weiver.applicant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.weiver.analysis.event.dto.ApplicantAnalysisRequestedData;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import com.weiver.applicant.domain.Certificate;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.repository.AwardRepository;
import com.weiver.applicant.repository.CertificateRepository;
import com.weiver.applicant.repository.EducationRepository;
import com.weiver.applicant.repository.WorkExperienceRepository;
import com.weiver.essay.domain.EssayAnswer;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.global.s3.service.S3Service;
import com.weiver.global.security.util.TokenHashUtil;
import com.weiver.portfolio.domain.Portfolio;
import com.weiver.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 프로필 제출 시점의 분석 요청 스냅샷을 조립하고 content_hash를 계산한다.
 *
 * <p>항목은 항상 같은 순서로 담는다: 학력·경력·자격증·수상은 엔티티 id 순, 자기소개서는 문항 순서(같으면 답변 id) 순.
 * 그래서 같은 내용이면 조회 순서가 달라도 같은 스냅샷과 같은 해시가 나온다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ApplicantAnalysisSnapshotService {

    private static final String PDF_EXTENSION = "pdf";
    private static final String SUBMISSION_VERSION_FIELD = "submission_version";

    /** 해시 입력 직렬화 전용. 전역 ObjectMapper 설정에 영향받지 않게 따로 둔다. */
    private static final ObjectMapper HASH_MAPPER = JsonMapper.builder().build();

    private final EducationRepository educationRepository;
    private final WorkExperienceRepository workExperienceRepository;
    private final CertificateRepository certificateRepository;
    private final AwardRepository awardRepository;
    private final EssayAnswerRepository essayAnswerRepository;
    private final PortfolioRepository portfolioRepository;
    private final S3Service s3Service;

    /**
     * 지원자 기본 정보, 학력, 경력, 자격증, 자기소개서, 수상, 포트폴리오 PDF 위치를 분석 요청 data로 조립한다.
     */
    public ApplicantAnalysisRequestedData assemble(Applicant applicant, int submissionVersion) {
        return new ApplicantAnalysisRequestedData(
                applicant.getApplicantId(),
                submissionVersion,
                applicant.getName(),
                educationRepository.findAllByApplicant(applicant).stream()
                        .sorted(Comparator.comparing(Education::getEducationId, nullsLast()))
                        .map(this::toEducationData)
                        .toList(),
                workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant).stream()
                        .sorted(Comparator.comparing(WorkExperience::getExperienceId, nullsLast()))
                        .map(this::toExperienceData)
                        .toList(),
                certificateRepository.findAllByApplicant(applicant).stream()
                        .sorted(Comparator.comparing(Certificate::getCertificateId, nullsLast()))
                        .map(Certificate::getCertificateName)
                        .toList(),
                essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant).stream()
                        .sorted(Comparator
                                .comparing((EssayAnswer answer) -> answer.getEssayQuestion().getSequence(), nullsLast())
                                .thenComparing(EssayAnswer::getAnswerId, nullsLast()))
                        .map(answer -> new ApplicantAnalysisRequestedData.EssayData(
                                answer.getAnswerId(),
                                answer.getEssayQuestion().getQuestion(),
                                answer.getAnswer()
                        ))
                        .toList(),
                awardRepository.findAllByApplicant(applicant).stream()
                        .sorted(Comparator.comparing(Award::getAwardId, nullsLast()))
                        .map(this::toAwardData)
                        .toList(),
                portfolioRepository.findByApplicant(applicant)
                        .map(this::toPortfolioData)
                        .orElse(null)
        );
    }

    /**
     * 스냅샷에서 submission_version을 뺀 값의 SHA-256(소문자 hex).
     * 모든 깊이의 맵 키를 사전순으로 정렬해 직렬화하므로 키 순서에 영향받지 않는다.
     */
    public String contentHash(Map<String, Object> snapshot) {
        Map<String, Object> content = new HashMap<>(snapshot);
        content.remove(SUBMISSION_VERSION_FIELD);
        try {
            return TokenHashUtil.sha256(HASH_MAPPER.writeValueAsString(sortKeys(content)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("analysis snapshot is not serializable", e);
        }
    }

    /** 중첩 맵까지 키를 정렬한 복사본을 만든다. 리스트는 순서를 그대로 둔다(항목 순서는 조립할 때 고정한다). */
    private static Object sortKeys(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), sortKeys(nested)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ApplicantAnalysisSnapshotService::sortKeys).toList();
        }
        return value;
    }

    /**
     * 포트폴리오 PDF는 AI 서버가 S3에서 직접 내려받도록 버킷·키만 담는다(Presigned URL은 만료되므로 쓰지 않는다).
     */
    private ApplicantAnalysisRequestedData.PortfolioData toPortfolioData(Portfolio portfolio) {
        return new ApplicantAnalysisRequestedData.PortfolioData(
                portfolio.getPortfolioId(),
                toPortfolioFileData(portfolio)
        );
    }

    /** 파일이 없거나 PDF가 아니거나 URL을 해석할 수 없으면 null이다. */
    private ApplicantAnalysisRequestedData.PortfolioFileData toPortfolioFileData(Portfolio portfolio) {
        if (!StringUtils.hasText(portfolio.getFileKey()) || !isPdf(portfolio)) {
            return null;
        }
        return s3Service.resolveObjectLocation(portfolio.getFileKey())
                .map(location -> ApplicantAnalysisRequestedData.PortfolioFileData.of(
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

    private ApplicantAnalysisRequestedData.EducationData toEducationData(Education education) {
        return new ApplicantAnalysisRequestedData.EducationData(
                education.getDegree() != null ? education.getDegree().name() : null,
                education.getSchoolName(),
                education.getMajor(),
                education.getStatus() != null ? education.getStatus().name() : null
        );
    }

    private ApplicantAnalysisRequestedData.ExperienceData toExperienceData(WorkExperience experience) {
        return new ApplicantAnalysisRequestedData.ExperienceData(
                experience.getCompanyName(),
                experience.getPosition(),
                experience.getEmploymentType() != null ? experience.getEmploymentType().name() : null,
                experience.getStartDate(),
                experience.getEndDate(),
                experience.getDuties()
        );
    }

    private ApplicantAnalysisRequestedData.AwardData toAwardData(Award award) {
        return new ApplicantAnalysisRequestedData.AwardData(
                award.getAwardId(),
                award.getAwardName(),
                award.getIssuer(),
                award.getAwardDate()
        );
    }

    private static <T extends Comparable<? super T>> Comparator<T> nullsLast() {
        return Comparator.nullsLast(Comparator.naturalOrder());
    }
}
