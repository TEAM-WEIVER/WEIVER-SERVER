package com.weiver.auth.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ApplicantStatus;
import com.weiver.auth.domain.ApplicantKakaoAccount;
import com.weiver.auth.dto.KakaoProfile;
import com.weiver.auth.dto.request.ApplicantPasswordSetupRequestDTO;
import com.weiver.auth.dto.request.KakaoSignupCompleteRequestDTO;
import com.weiver.auth.dto.response.ApplicantLoginMethodsResponseDTO;
import com.weiver.auth.repository.ApplicantEmailVerificationRepository;
import com.weiver.auth.repository.ApplicantKakaoAccountRepository;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoApplicantAccountService {

    public enum SignupStatus {
        SIGNUP_REQUIRED,
        EXISTING_ACCOUNT,
        PENDING_ACCOUNT
    }

    private final ApplicantRepository applicantRepository;
    private final ApplicantKakaoAccountRepository kakaoAccountRepository;
    private final ApplicantEmailVerificationRepository emailVerificationRepository;
    private final ApplicantAuthService applicantAuthService;
    private final PasswordEncoder passwordEncoder;

    public SignupStatus classifySignup(String email) {
        if (!StringUtils.hasText(email)) {
            return SignupStatus.SIGNUP_REQUIRED;
        }
        return applicantRepository.findByEmailAndDeletedFalse(email)
                .map(applicant -> applicant.getStatus() == ApplicantStatus.ACTIVE
                        ? SignupStatus.EXISTING_ACCOUNT : SignupStatus.PENDING_ACCOUNT)
                .orElse(SignupStatus.SIGNUP_REQUIRED);
    }

    public Optional<String> findLinkedApplicantPublicId(Long kakaoUserId) {
        return kakaoAccountRepository.findByKakaoUserId(kakaoUserId)
                .map(ApplicantKakaoAccount::getApplicant)
                .filter(this::isActiveApplicant)
                .map(Applicant::getPublicId);
    }

    @Transactional
    public Optional<ApplicantLoginResult> loginIfLinked(Long kakaoUserId) {
        return findActiveLinkedApplicantForUpdate(kakaoUserId).map(applicantAuthService::issueApplicantLogin);
    }

    @Transactional
    public ApplicantLoginResult loginAfterSignup(Long kakaoUserId, String publicId) {
        return findActiveLinkedApplicantForUpdate(kakaoUserId)
                .filter(applicant -> applicant.getPublicId().equals(publicId))
                .map(applicantAuthService::issueApplicantLogin)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN));
    }

    private Optional<Applicant> findActiveLinkedApplicantForUpdate(Long kakaoUserId) {
        return kakaoAccountRepository.findByKakaoUserId(kakaoUserId)
                .map(account -> account.getApplicant().getApplicantId())
                .flatMap(applicantRepository::findByIdAndDeletedFalseForUpdate)
                .filter(this::isActiveApplicant)
                .filter(applicant -> kakaoAccountRepository.findByApplicant_ApplicantId(applicant.getApplicantId())
                        .filter(account -> account.getKakaoUserId().equals(kakaoUserId))
                        .isPresent());
    }

    @Transactional
    public String completeSignup(KakaoProfile profile, KakaoSignupCompleteRequestDTO request) {
        applicantAuthService.validateRequiredAgreements(request.agreements());
        if (kakaoAccountRepository.findByKakaoUserId(profile.kakaoUserId()).isPresent()) {
            throw new BusinessException(ErrorCode.KAKAO_ACCOUNT_ALREADY_LINKED);
        }

        String email = resolveSignupEmail(profile.verifiedEmail(), request);
        switch (classifySignup(email)) {
            case PENDING_ACCOUNT -> throw new BusinessException(ErrorCode.SIGNUP_NOT_COMPLETED);
            case EXISTING_ACCOUNT -> throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            case SIGNUP_REQUIRED -> { }
        }

        Applicant applicant;
        try {
            applicant = applicantRepository.saveAndFlush(Applicant.builder().email(email).build());
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            throw e;
        }
        applicantAuthService.activateWithAgreements(applicant, request.agreements());
        saveLink(applicant, profile.kakaoUserId());
        return applicant.getPublicId();
    }

    private String resolveSignupEmail(String kakaoEmail, KakaoSignupCompleteRequestDTO request) {
        String email = request.email();
        if (StringUtils.hasText(kakaoEmail) && (!StringUtils.hasText(email) || kakaoEmail.equals(email))) {
            return kakaoEmail;
        }
        if (!StringUtils.hasText(email) || !StringUtils.hasText(request.verificationToken())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        return emailVerificationRepository.findAndDeleteVerifiedToken(request.verificationToken())
                .filter(email::equals)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));
    }

    public void verifyLinkPassword(String publicId, String currentPassword) {
        Applicant applicant = requireActive(applicantRepository.findByPublicIdAndDeletedFalse(publicId));
        applicantAuthService.verifyLinkPassword(applicant, currentPassword);
    }

    @Transactional
    public void completeLink(String publicId, Long kakaoUserId) {
        Applicant applicant = requireActive(applicantRepository.findByPublicIdAndDeletedFalseForUpdate(publicId));
        if (!applicant.hasLocalPassword()) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (kakaoAccountRepository.existsByApplicant_ApplicantId(applicant.getApplicantId())
                || kakaoAccountRepository.findByKakaoUserId(kakaoUserId).isPresent()) {
            throw new BusinessException(ErrorCode.KAKAO_ACCOUNT_ALREADY_LINKED);
        }
        saveLink(applicant, kakaoUserId);
    }

    private void saveLink(Applicant applicant, Long kakaoUserId) {
        try {
            kakaoAccountRepository.saveAndFlush(ApplicantKakaoAccount.builder()
                    .applicant(applicant)
                    .kakaoUserId(kakaoUserId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new BusinessException(ErrorCode.KAKAO_ACCOUNT_ALREADY_LINKED);
            }
            throw e;
        }
    }

    public ApplicantLoginMethodsResponseDTO getLoginMethods(String publicId) {
        Applicant applicant = requireActive(applicantRepository.findByPublicIdAndDeletedFalse(publicId));
        return new ApplicantLoginMethodsResponseDTO(applicant.hasLocalPassword(),
                kakaoAccountRepository.existsByApplicant_ApplicantId(applicant.getApplicantId()));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void sendPasswordSetupCode(String publicId) {
        Applicant applicant = requireActive(applicantRepository.findByPublicIdAndDeletedFalse(publicId));
        requirePasswordSetupEligibility(applicant);
        applicantAuthService.sendPasswordSetupCode(applicant);
    }

    @Transactional
    public void setupPassword(String publicId, KakaoProfile reauthenticatedProfile, ApplicantPasswordSetupRequestDTO request) {
        applicantAuthService.validatePasswordConfirm(request.newPassword(), request.newPasswordConfirm());
        Applicant applicant = requireActive(applicantRepository.findByPublicIdAndDeletedFalseForUpdate(publicId));
        ApplicantKakaoAccount account = requirePasswordSetupEligibility(applicant);
        if (!account.getKakaoUserId().equals(reauthenticatedProfile.kakaoUserId())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_REAUTH_TOKEN);
        }
        applicantAuthService.verifyPasswordSetupCode(applicant, request.emailCode());
        applicant.updatePassword(passwordEncoder.encode(request.newPassword()));
        applicantAuthService.invalidateSessions(applicant);
    }

    private ApplicantKakaoAccount requirePasswordSetupEligibility(Applicant applicant) {
        if (applicant.hasLocalPassword()) {
            throw new BusinessException(ErrorCode.PASSWORD_ALREADY_CONFIGURED);
        }
        return kakaoAccountRepository.findByApplicant_ApplicantId(applicant.getApplicantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_REAUTH_TOKEN));
    }

    private Applicant requireActive(Optional<Applicant> result) {
        Applicant applicant = result.orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        if (!isActiveApplicant(applicant)) {
            throw new BusinessException(ErrorCode.SIGNUP_NOT_COMPLETED);
        }
        return applicant;
    }

    private boolean isActiveApplicant(Applicant applicant) {
        return !applicant.isDeleted() && applicant.getRole() == UserRole.APPLICANT
                && applicant.getStatus() == ApplicantStatus.ACTIVE;
    }

    private boolean isUniqueViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException cause && "23505".equals(cause.getSQLState());
    }
}
