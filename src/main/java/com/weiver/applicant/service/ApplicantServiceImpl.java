package com.weiver.applicant.service;

import com.weiver.applicant.domain.*;
import com.weiver.applicant.dto.request.post.AwardRequestDTO;
import com.weiver.applicant.dto.request.post.CertificateRequestDTO;
import com.weiver.applicant.dto.request.post.EducationRequestDTO;
import com.weiver.applicant.dto.request.post.WorkExperienceRequestDTO;
import com.weiver.applicant.dto.request.put.ApplicantInfoRequestDTO;
import com.weiver.applicant.repository.*;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class ApplicantServiceImpl implements  ApplicantService {

    private final ApplicantRepository applicantRepository;
    private final EducationRepository educationRepository;
    private final AwardRepository awardRepository;
    private final CertificateRepository certificateRepository;
    private final WorkExperienceRepository workExperienceRepository;

    @Override
    @Transactional
    public void updateApplicantInfo(long applicantId, ApplicantInfoRequestDTO requestDTO) {

        Applicant applicant = getApplicant(applicantId);

        applicant.updateInfo(requestDTO);
    }

    @Override
    @Transactional
    public void saveEducationInfo(long applicantId, EducationRequestDTO requestDTO) {

        Applicant applicant = getApplicant(applicantId);

        List<Education> educationList = requestDTO.toEntityList(applicant);

        educationRepository.saveAll(educationList);
    }

    @Override
    @Transactional
    public void saveAwardInfo(long applicantId, AwardRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);

        List<Award> awardList = requestDTO.toEntityList(applicant);

        awardRepository.saveAll(awardList);
    }

    @Override
    @Transactional
    public void saveCertificateInfo(long applicantId, CertificateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);
        List<Certificate> certificateList = requestDTO.toEntityList(applicant);

        certificateRepository.saveAll(certificateList);

    }

    @Override
    @Transactional
    public void saveWorkExperienceInfo(long applicantId, WorkExperienceRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);

        List<WorkExperience> experienceList = requestDTO.toEntityList(applicant);

        workExperienceRepository.saveAll(experienceList);
    }

    private Applicant getApplicant(long applicantId) {
        Applicant applicant = applicantRepository.findByApplicantId(applicantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }
}
