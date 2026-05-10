package com.weiver.applicant.service;

import com.weiver.applicant.domain.*;
import com.weiver.applicant.dto.request.put.*;
import com.weiver.applicant.dto.response.*;
import com.weiver.applicant.repository.*;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.matching.dto.response.ProfileDetailDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@Service
@Transactional
@RequiredArgsConstructor
public class ApplicantService {

    private final ApplicantRepository applicantRepository;
    private final EducationRepository educationRepository;
    private final AwardRepository awardRepository;
    private final CertificateRepository certificateRepository;
    private final WorkExperienceRepository workExperienceRepository;
    private final WorkExperienceService workExperienceService;
    private final S3Service s3Service;

    public void updateApplicantInfo(String publicId, ApplicantInfoRequestDTO requestDTO, MultipartFile profileImage) {

        Applicant applicant = getApplicant(publicId);

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

    @Transactional(readOnly = true)
    public ApplicantInfoResponseDTO searchApplicant(String publicId) {
        Applicant applicant = getApplicant(publicId);

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

    /**
     * 지원자 리포트 카드 조회 - 순수 도메인 데이터만 반환
     * */
    public ApplicantProfileDto getApplicantProfile(String publicId){
        Applicant applicant = getApplicant(publicId);
        String position = workExperienceService.getPositionName(publicId);
        return new ApplicantProfileDto(applicant, position);
    }

    private Applicant getApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }
}
