package com.weiver.applicant.service;

import com.weiver.applicant.domain.*;
import com.weiver.applicant.dto.request.put.*;
import com.weiver.applicant.dto.response.*;
import com.weiver.applicant.event.ApplicantProfileEventService;
import com.weiver.applicant.repository.*;
import com.weiver.applicant.type.ProfileSyncStatus;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final EssayAnswerRepository essayAnswerRepository;
    private final PortfolioRepository portfolioRepository;
    private final WorkExperienceService workExperienceService;
    private final ApplicantProfileEventService applicantProfileEventService;
    private final S3Service s3Service;

    // 진입 메서드는 트랜잭션을 열지 않는다(NOT_SUPPORTED). S3 업로드/삭제는 트랜잭션 경계 밖에서 수행하고,
    // 각 DB 접근은 리포지토리 단위의 짧은 트랜잭션으로 처리한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateApplicantInfo(String publicId, ApplicantInfoRequestDTO requestDTO, MultipartFile profileImage) {

        // 새 프로필 이미지 업로드는 DB 접근 이전, 트랜잭션 경계 밖에서 먼저 수행해 URL을 확보한다.
        String newPhotoUrl = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            newPhotoUrl = s3Service.publicUpload(profileImage, "profiles");
        }

        Applicant applicant = getApplicant(publicId);
        String previousPhotoUrl = applicant.getPhotoUrl();

        // 이메일이 실제로 변경되는 경우에만 중복 검사한다.
        String newEmail = requestDTO.email();
        if (StringUtils.hasText(newEmail)
                && !newEmail.equals(applicant.getEmail())
                && applicantRepository.existsByEmail(newEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String photoUrl = (newPhotoUrl != null) ? newPhotoUrl : previousPhotoUrl;
        applicant.updateInfo(requestDTO, photoUrl);
        applicantRepository.save(applicant);

        // DB 반영이 확정된 뒤 기존 파일을 삭제해 롤백 시 원본 이미지가 유실되지 않게 한다.
        if (newPhotoUrl != null && StringUtils.hasText(previousPhotoUrl)) {
            s3Service.deleteFile(previousPhotoUrl);
        }
    }

    @Transactional(readOnly = true)
    public ApplicantInfoResponseDTO searchApplicant(String publicId) {
        Applicant applicant = getApplicant(publicId);

        List<Education> educations = educationRepository.findAllByApplicant(applicant);
        List<Award> awards = awardRepository.findAllByApplicant(applicant);
        List<WorkExperience> workExperiences = workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant);
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

    @Transactional(readOnly = true)
    public ApplicantSubmissionStatusResponseDTO getSubmissionStatus(String publicId) {
        Applicant applicant = getApplicant(publicId);

        ApplicantDocumentStatusResponseDTO documentStatus = buildDocumentStatus(applicant);

        return new ApplicantSubmissionStatusResponseDTO(
                isSubmitted(applicant),
                applicant.getProfileSyncStatus(),
                isSubmittable(applicant, documentStatus),
                documentStatus.resumeCompleted(),
                documentStatus.essayCompleted(),
                documentStatus.portfolioCompleted()
        );
    }

    /**
     * 구직자가 화면에서 "프로필 제출"을 눌렀을 때 AI 서버 프로필 동기화를 시작한다.
     *
     * <p>동기화 완료 이벤트를 받은 핸들러가 이어서 지원자 분석을 요청하므로, 이 한 번의 제출로
     * 동기화와 분석이 모두 수행된다.
     *
     * <p>재제출은 막는다. 제출 직후(REQUESTED)와 동기화 완료(COMPLETED) 상태에서는
     * {@link ErrorCode#PROFILE_ALREADY_SUBMITTED}로 거절하고, 동기화가 실패한(FAILED) 경우에만
     * 다시 제출할 수 있다.
     */
    @Transactional
    public void submitProfile(String publicId) {
        Applicant applicant = getApplicant(publicId);

        if (!isResubmittable(applicant)) {
            throw new BusinessException(ErrorCode.PROFILE_ALREADY_SUBMITTED);
        }
        if (!isAllDocumentsCompleted(buildDocumentStatus(applicant))) {
            throw new BusinessException(ErrorCode.PROFILE_DOCUMENT_INCOMPLETE);
        }

        applicantProfileEventService.publishProfileChanged(applicant.getApplicantId());
    }

    /**
     * 제출 버튼 활성화 판단용: 아직 제출하지 않았거나 동기화가 실패했고, 필수 서류가 모두 작성된 상태.
     */
    private boolean isSubmittable(Applicant applicant, ApplicantDocumentStatusResponseDTO documentStatus) {
        return isResubmittable(applicant) && isAllDocumentsCompleted(documentStatus);
    }

    private boolean isResubmittable(Applicant applicant) {
        ProfileSyncStatus syncStatus = applicant.getProfileSyncStatus();
        return syncStatus == ProfileSyncStatus.PENDING || syncStatus == ProfileSyncStatus.FAILED;
    }

    private boolean isAllDocumentsCompleted(ApplicantDocumentStatusResponseDTO documentStatus) {
        return documentStatus.resumeCompleted()
                && documentStatus.essayCompleted()
                && documentStatus.portfolioCompleted();
    }

    private ApplicantDocumentStatusResponseDTO buildDocumentStatus(Applicant applicant) {
        boolean resumeCompleted = isResumeCompleted(applicant);
        boolean essayCompleted = essayAnswerRepository.existsByApplicant(applicant);
        boolean portfolioCompleted = portfolioRepository.existsByApplicant(applicant);

        return new ApplicantDocumentStatusResponseDTO(
                resumeCompleted,
                essayCompleted,
                portfolioCompleted
        );
    }

    /**
     * 지원자 리포트 카드 조회 - 순수 도메인 데이터만 반환
     * */
    public ApplicantProfileDTO getApplicantProfile(String publicId){
        Applicant applicant = getApplicant(publicId);
        String position = workExperienceService.getPositionName(publicId);
        return new ApplicantProfileDTO(applicant, position);
    }

    public Applicant getApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }

    private boolean isResumeCompleted(Applicant applicant) {
        boolean basicInfoCompleted = StringUtils.hasText(applicant.getName())
                && StringUtils.hasText(applicant.getEmail())
                && StringUtils.hasText(applicant.getPhoneNumber())
                && applicant.getBirthday() != null;

        boolean resumeDetailCompleted = educationRepository.existsByApplicant(applicant)
                || workExperienceRepository.existsByApplicant(applicant)
                || certificateRepository.existsByApplicant(applicant)
                || awardRepository.existsByApplicant(applicant);

        return basicInfoCompleted && resumeDetailCompleted;
    }

    // 사용자의 '제출 행위' 기준: 제출 요청(REQUESTED) 이후는 모두 제출됨으로 본다.
    // 동기화 실패(FAILED)도 사용자 입장에서는 제출된 상태이며, 실패 메시지는 추후 폴링으로 재처리한다.
    private boolean isSubmitted(Applicant applicant) {
        return applicant.isProfileSubmitted();
    }
}
