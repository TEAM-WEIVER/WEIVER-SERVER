package com.weiver.matching.service;

import com.querydsl.core.Tuple;
import com.weiver.applicant.domain.*;
import com.weiver.applicant.dto.response.*;
import com.weiver.applicant.repository.*;
import com.weiver.global.email.dto.EmailSendRequest;
import com.weiver.global.email.service.EmailSender;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.repository.EmailTemplateRepository;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.dto.request.ApplicantSearchCondition;
import com.weiver.matching.dto.response.ApplicantListResponseDTO;
import com.weiver.matching.repository.MatchResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

import static com.weiver.analysis.domain.QCultureReport.cultureReport;
import static com.weiver.analysis.domain.QTechnicalSkillReport.technicalSkillReport;
import static com.weiver.matching.domain.QMatchResult.matchResult;
import static java.util.Objects.requireNonNull;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchResultService {

    private final MatchResultRepository matchResultRepository;
    private final ApplicantRepository applicantRepository;
    private final EducationRepository educationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final AwardRepository awardRepository;
    private final CertificateRepository certificateRepository;
    private final WorkExperienceRepository workExperienceRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailSender emailSender;

    /**
     * 매핑된 구직자 리스트 조회
     * */
    public Page<ApplicantListResponseDTO> searchApplicantList(ApplicantSearchCondition condition, Pageable pageable, String publicId) {

        boolean isOwner = jobPostingRepository.existsByJdIdAndCompany_PublicId(condition.jdId(), publicId);

        if (!isOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 채용 공고에 접근할 권한이 없습니다.");
        }

        Page<Tuple> tuplePage = matchResultRepository.searchApplicantsTuple(condition, pageable);

        // Tuple 순회하면서 DTO 매핑
        return tuplePage.map(tuple -> {
            String position = tuple.get(3, String.class);

            return ApplicantListResponseDTO.of(
                    requireNonNull(tuple.get(matchResult)),
                    requireNonNull(tuple.get(cultureReport)),
                    requireNonNull(tuple.get(technicalSkillReport)),
                    position
            );
        });
    }

    public ApplicantInfoResponseDTO searchApplicantDetail(Long jdId, String applicantPublicId, String companyPublicId) {

        boolean isApplied = matchResultRepository.existsByJobPosting_JdIdAndApplicant_PublicIdAndJobPosting_Company_PublicId(
                jdId, applicantPublicId, companyPublicId
        );

        if (!isApplied) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }


        Applicant applicant = getApplicant(applicantPublicId);

        List<Education> educations = educationRepository.findAllByApplicant(applicant);
        List<Award> awards = awardRepository.findAllByApplicant(applicant);
        List<WorkExperience> workExperiences = workExperienceRepository.findAllByApplicant(applicant);
        List<Certificate> certificates = certificateRepository.findAllByApplicant(applicant);

        return new ApplicantInfoResponseDTO(
                ApplicantDetailResponseDTO.from(applicant),
                educations.stream().map(EducationDetailResponseDTO::from).toList(),
                awards.stream().map(AwardDetailResponseDTO::from).toList(),
                workExperiences.stream().map(WorkExperienceDetailResponseDTO::from).toList(),
                certificates.stream().map(CertificateDetailResponseDTO::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public void sendContactEmail(Long jdId, String applicantPublicId, String companyPublicId) {

        MatchResult matchResult = matchResultRepository.findMatchResultForContact(
                jdId, applicantPublicId, companyPublicId
        ).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        EmailTemplate template = emailTemplateRepository.findWithJobPostingByJdId(jdId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_FOUND));

        Applicant applicant = matchResult.getApplicant();
        String toEmail = applicant.getEmail();

        if (toEmail == null || toEmail.isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_FOUND, "수신자 이메일이 존재하지 않습니다.");
        }

        String subject = template.getEmailTitle();
        String body = template.getEmailContent();

        if (body != null) {
            if (applicant.getName() != null) {
                body = body.replace("{{name}}", applicant.getName());
            }

            // HTML 방어 로직 - 이메일 템플릿에 HTML 태그가 포함되어 있지 않다면 줄바꿈을 <br>로 변환
            if (!body.matches(".*<[a-zA-Z]+.*?>.*")) {
                body = body.replace("\n", "<br>");
            }
        }
        emailSender.send(EmailSendRequest.ofHtml(toEmail, subject, body));
    }

    /**
     * 매칭 결과 검증 - 해당 공고에 대한 매칭 결과가 존재하는지 검증
     * */
    public MatchResult getValidatedMatchResult(Long jdId, String applicantPublicId, String companyPublicId) {
        return matchResultRepository.findMatchResultForContact(jdId, applicantPublicId, companyPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
    }

    private Applicant getApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }


}
