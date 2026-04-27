package com.weiver.applicant.service;

import com.weiver.applicant.domain.*;
import com.weiver.applicant.dto.request.post.AwardRequestDTO;
import com.weiver.applicant.dto.request.post.CertificateRequestDTO;
import com.weiver.applicant.dto.request.post.EducationRequestDTO;
import com.weiver.applicant.dto.request.post.WorkExperienceRequestDTO;
import com.weiver.applicant.dto.request.put.*;
import com.weiver.applicant.dto.response.*;
import com.weiver.applicant.repository.*;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@Transactional
@RequiredArgsConstructor
public class ApplicantServiceImpl implements  ApplicantService {

    private final ApplicantRepository applicantRepository;
    private final EducationRepository educationRepository;
    private final AwardRepository awardRepository;
    private final CertificateRepository certificateRepository;
    private final WorkExperienceRepository workExperienceRepository;
    private final S3Service s3Service;

    @Override
    public void updateApplicantInfo(long applicantId, ApplicantInfoRequestDTO requestDTO, MultipartFile profileImage) {

        Applicant applicant = getApplicant(applicantId);

        String photoUrl = applicant.getPhotoUrl();

        // 만약 새로운 이미지라면
        if(profileImage != null && !profileImage.isEmpty()) {
            if(StringUtils.hasText(photoUrl)){
                s3Service.deleteFile(photoUrl);
            }

            photoUrl = s3Service.publicUpload(profileImage, "profiles");
        }

        applicant.updateInfo(requestDTO, photoUrl);
    }

    @Override
    public void saveEducationInfo(long applicantId, EducationRequestDTO requestDTO) {

        Applicant applicant = getApplicant(applicantId);

        List<Education> educationList = requestDTO.toEntityList(applicant);

        educationRepository.saveAll(educationList);
    }

    @Override
    public void saveAwardInfo(long applicantId, AwardRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);

        List<Award> awardList = requestDTO.toEntityList(applicant);

        awardRepository.saveAll(awardList);
    }

    @Override
    public void saveCertificateInfo(long applicantId, CertificateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);
        List<Certificate> certificateList = requestDTO.toEntityList(applicant);

        certificateRepository.saveAll(certificateList);

    }

    @Override
    public void saveWorkExperienceInfo(long applicantId, WorkExperienceRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);

        List<WorkExperience> experienceList = requestDTO.toEntityList(applicant);

        workExperienceRepository.saveAll(experienceList);
    }

    @Override
    public void updateEducationInfo(long applicantId, EducationUpdateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);

        List<Education> existingEducations = educationRepository.findAllByApplicant(applicant);

        // DTO 에서 수정 대상만 추출
        Set<Long> requestEducationIds = requestDTO.educationList().stream()
                .map(EducationUpdateDetailDTO::educationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        // 삭제 할 데이터
        List<Education> toDelete = existingEducations.stream()
                .filter(education -> !requestEducationIds.contains(education.getEducationId()))
                .toList();

        educationRepository.deleteAll(toDelete);

        List<Education> toSave = new ArrayList<>();

        for (EducationUpdateDetailDTO detailDTO : requestDTO.educationList()) {
            // educationId 가 없으면 새로운 값 추가
            if (detailDTO.educationId() == null) {
                toSave.add(detailDTO.toEntity(applicant));
            } else {
                Education existingEducation = existingEducations.stream()
                        .filter(education -> education.getEducationId().equals(detailDTO.educationId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.EDUCATION_NOT_FOUND));

                if (!existingEducation.getApplicant().getApplicantId().equals(applicantId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }

                existingEducation.updateEducation(detailDTO);
            }
        }

        if (!toSave.isEmpty()) {
            educationRepository.saveAll(toSave);
        }
    }

    @Override
    public void updateAwardInfo(long applicantId, AwardUpdateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);

        List<Award> existingAwards = awardRepository.findAllByApplicant(applicant);

        // DTO 에서 수정 대상만 추출
        Set<Long> requestAwardIds = requestDTO.awardList().stream()
                .map(AwardUpdateDetailDTO::awardId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 삭제 할 데이터
        List<Award> toDelete = existingAwards.stream()
                .filter(award -> !requestAwardIds.contains(award.getAwardId()))
                .toList();
        awardRepository.deleteAll(toDelete);

        List<Award> toSave = new ArrayList<>();
        for(AwardUpdateDetailDTO detailDTO : requestDTO.awardList()) {
            if(detailDTO.awardId() == null) {
                toSave.add(detailDTO.toEntity(applicant));
            } else {
                Award existingAward = existingAwards.stream()
                        .filter(award -> award.getAwardId().equals(detailDTO.awardId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.AWARD_NOT_FOUND));

                if(!existingAward.getApplicant().getApplicantId().equals(applicantId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }

                existingAward.updateAward(detailDTO);
            }
        }

        if (!toSave.isEmpty()) {
            awardRepository.saveAll(toSave);
        }
    }

    @Override
    public void updateCertificateInfo(long applicantId, CertificateUpdateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);

        List<Certificate> existingCertificates = certificateRepository.findAllByApplicant(applicant);

        Set<Long> requestCertificateIds = requestDTO.certificateList().stream()
                .map(CertificateUpdateDetailDTO::certificateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Certificate> toDelete = existingCertificates.stream()
                .filter(certificate -> !requestCertificateIds.contains(certificate.getCertificateId()))
                .toList();
        certificateRepository.deleteAll(toDelete);

        List<Certificate> toSave = new ArrayList<>();
        for(CertificateUpdateDetailDTO detailDTO : requestDTO.certificateList()) {
            if(detailDTO.certificateId() == null) {
                toSave.add(detailDTO.toEntity(applicant));
            } else {
                Certificate existingCertificate = existingCertificates.stream()
                        .filter(certificate -> certificate.getCertificateId().equals(detailDTO.certificateId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATION_NOT_FOUND));

                if(!existingCertificate.getApplicant().getApplicantId().equals(applicantId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }

                existingCertificate.updateCertificate(detailDTO);
            }
        }

        if (!toSave.isEmpty()) {
            certificateRepository.saveAll(toSave);
        }
    }

    @Override
    public void updateWorkExperienceInfo(long applicantId, WorkExperienceUpdateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);

        List<WorkExperience> existingExperiences = workExperienceRepository.findAllByApplicant(applicant);

        Set<Long> requestExperienceIds = requestDTO.workExperienceList().stream()
                .map(WorkExperienceUpdateDetailDTO::workExperienceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<WorkExperience> toDelete = existingExperiences.stream()
                .filter(we -> !requestExperienceIds.contains(we.getExperienceId()))
                .toList();
        workExperienceRepository.deleteAll(toDelete);

        List<WorkExperience> toSave = new ArrayList<>();
        for(WorkExperienceUpdateDetailDTO detailDTO : requestDTO.workExperienceList()) {
            if(detailDTO.workExperienceId() == null) {
                toSave.add(detailDTO.toEntity(applicant));
            } else {
                WorkExperience existingExperience = existingExperiences.stream()
                        .filter(we -> we.getExperienceId().equals(detailDTO.workExperienceId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND));

                if(!existingExperience.getApplicant().getApplicantId().equals(applicantId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }

                existingExperience.updateWorkExperience(detailDTO);
            }
        }

        if (!toSave.isEmpty()) {
            workExperienceRepository.saveAll(toSave);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicantInfoResponseDTO searchApplicant(long applicantId) {
        Applicant applicant = getApplicant(applicantId);

        List<Education> educations = educationRepository.findAllByApplicant(applicant);
        List<Award> awards = awardRepository.findAllByApplicant(applicant);
        List<WorkExperience> workExperiences = workExperienceRepository.findAllByApplicant(applicant);
        List<Certificate> certificates = certificateRepository.findAllByApplicant(applicant);

        ApplicantDetailResponseDTO applicantDTO = ApplicantDetailResponseDTO.from(applicant);

        List<EducationDetailResponseDTO> educationDTOs = educations.stream()
                .map(EducationDetailResponseDTO::from)
                .toList();

        List<AwardDetailResponseDTO> awardDTOs = awards.stream()
                .map(AwardDetailResponseDTO::from)
                .toList();

        List<WorkExperienceDetailResponseDTO> workExperienceDTOs = workExperiences.stream()
                .map(WorkExperienceDetailResponseDTO::from)
                .toList();

        List<CertificateDetailResponseDTO> certificateDTOs = certificates.stream()
                .map(CertificateDetailResponseDTO::from)
                .toList();

        return new ApplicantInfoResponseDTO(
                applicantDTO,
                educationDTOs,
                awardDTOs,
                workExperienceDTOs,
                certificateDTOs
        );
    }

    private Applicant getApplicant(long applicantId) {
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }
}
