package com.weiver.applicant.service;

import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.repository.TechnicalSkillReportRepository;
import com.weiver.applicant.domain.*;
import com.weiver.applicant.dto.request.put.*;
import com.weiver.applicant.dto.response.*;
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
import java.util.Optional;


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
    private final TechnicalSkillReportRepository technicalSkillReportRepository;
    private final WorkExperienceService workExperienceService;
    private final ApplicantAnalysisSubmissionService applicantAnalysisSubmissionService;
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
        Optional<ApplicantAnalysisSubmission> currentSubmission = applicantAnalysisSubmissionService.findCurrent(applicant);

        return new ApplicantSubmissionStatusResponseDTO(
                currentSubmission.isPresent(),
                toSyncStatus(currentSubmission),
                isResubmittable(currentSubmission) && isAllDocumentsCompleted(documentStatus),
                documentStatus.resumeCompleted(),
                documentStatus.essayCompleted(),
                documentStatus.portfolioCompleted(),
                isAnalysisCompleted(applicant)
        );
    }

    /**
     * 면접 시작 버튼 활성화 판단용: 기술 리포트가 있고 직무·역할 분석이 끝난 상태. 리포트가 없으면 false.
     */
    private boolean isAnalysisCompleted(Applicant applicant) {
        return technicalSkillReportRepository.findByApplicant(applicant)
                .map(TechnicalSkillReport::isAnalysisCompleted)
                .orElse(false);
    }

    /**
     * 구직자가 화면에서 "프로필 제출"을 눌렀을 때 제출 건을 만들고 AI 서버에 분석을 요청한다.
     *
     * <p>지원자 행을 비관적 잠금으로 조회한 뒤 상태 확인, 버전 발급, 제출 건 저장, 현재 제출 갱신을 한 트랜잭션에서 하고,
     * 분석 요청 이벤트는 커밋 뒤에 발행한다. 동시 제출은 잠금에서 줄을 서므로 두 번째 요청은 상태 확인에서 거절된다.
     *
     * <p>재제출은 막는다. 현재 제출 건이 분석 중(REQUESTED)이거나 완료(COMPLETED)면
     * {@link ErrorCode#PROFILE_ALREADY_SUBMITTED}로 거절하고, 제출한 적이 없거나 실패(FAILED)한 경우에만 제출할 수 있다.
     */
    @Transactional
    public void submitProfile(String publicId) {
        Applicant applicant = applicantRepository.findWithLockByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        if (!isResubmittable(applicantAnalysisSubmissionService.findCurrent(applicant))) {
            throw new BusinessException(ErrorCode.PROFILE_ALREADY_SUBMITTED);
        }
        if (!isAllDocumentsCompleted(buildDocumentStatus(applicant))) {
            throw new BusinessException(ErrorCode.PROFILE_DOCUMENT_INCOMPLETE);
        }

        applicantAnalysisSubmissionService.submit(applicant);
    }

    /** 제출 가능 상태: 제출한 적이 없거나 현재 제출 건이 실패(FAILED)했다. */
    private boolean isResubmittable(Optional<ApplicantAnalysisSubmission> currentSubmission) {
        return currentSubmission
                .map(ApplicantAnalysisSubmission::allowsResubmission)
                .orElse(true);
    }

    /**
     * 응답의 syncStatus 값은 기존 계약(PENDING·REQUESTED·COMPLETED·FAILED)을 유지하고, 현재 제출 건 상태에서 만든다.
     * 제출한 적이 없으면 PENDING이다. 의미 설명(Swagger) 변경은 2단계에서 한다.
     */
    private ProfileSyncStatus toSyncStatus(Optional<ApplicantAnalysisSubmission> currentSubmission) {
        return currentSubmission
                .map(submission -> switch (submission.getStatus()) {
                    case REQUESTED -> ProfileSyncStatus.REQUESTED;
                    case COMPLETED -> ProfileSyncStatus.COMPLETED;
                    case FAILED -> ProfileSyncStatus.FAILED;
                })
                .orElse(ProfileSyncStatus.PENDING);
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
}
