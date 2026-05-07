package com.weiver.matching.service;

import com.querydsl.core.Tuple;
import com.weiver.applicant.domain.*;
import com.weiver.applicant.dto.response.*;
import com.weiver.applicant.repository.*;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.mail.MailMessage;
import com.weiver.global.mail.MailSender;
import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.repository.EmailTemplateRepository;
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
    private final AwardRepository awardRepository;
    private final CertificateRepository certificateRepository;
    private final WorkExperienceRepository workExperienceRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final MailSender mailSender;

    /**
     * 매핑된 구직자 리스트 조회
     * */
    public Page<ApplicantListResponseDTO> searchApplicantList(ApplicantSearchCondition condition, Pageable pageable) {
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

    @Transactional
    public void sendContactEmail(Long jdId, String applicantPublicId, String companyPublicId) {

        MatchResult matchResult = matchResultRepository.findByJobPosting_JdIdAndApplicant_PublicIdAndJobPosting_Company_PublicId(
                jdId, applicantPublicId, companyPublicId
        ).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        EmailTemplate template = emailTemplateRepository.findWithJobPostingByJdId(jdId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_FOUND));

        Applicant applicant = matchResult.getApplicant();
        String toEmail = applicant.getEmail();

        String subject = template.getEmailTitle();
        String body = template.getEmailContent();

        // 템플릿 본문에 "{지원자명}" 또는 "{{name}}" 이라는 치환 변수가 존재할 경우 동작
        if (body != null && applicant.getName() != null) {
            body = body.replace("{{name}}", applicant.getName());
        }

        MailMessage mailMessage = new MailMessage(toEmail, subject, body);
        mailSender.send(mailMessage);
    }

    private Applicant getApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }


}
