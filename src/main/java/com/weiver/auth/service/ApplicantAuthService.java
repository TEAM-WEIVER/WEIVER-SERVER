package com.weiver.auth.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAgreement;
import com.weiver.applicant.repository.ApplicantAgreementRepository;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ApplicantStatus;
import com.weiver.auth.dto.request.*;
import com.weiver.auth.dto.response.ApplicantEmailVerifyResponseDTO;
import com.weiver.auth.dto.response.ApplicantPasswordVerifyResponseDTO;
import com.weiver.auth.dto.response.ApplicantSignupInitResponseDTO;
import com.weiver.auth.repository.ApplicantEmailSendLimitRepository;
import com.weiver.auth.repository.ApplicantEmailVerificationRepository;
import com.weiver.auth.repository.ApplicantPasswordReauthTokenRepository;
import com.weiver.auth.repository.ApplicantSignupTokenRepository;
import com.weiver.auth.repository.LoginAttemptRepository;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.global.common.UserRole;
import com.weiver.applicant.service.ApplicantProvider;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicantAuthService {

    private static final Duration EMAIL_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration SIGNUP_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration REAUTH_TOKEN_TTL = Duration.ofMinutes(10);
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    // 시도 카운터는 재발송으로 초기화되지 않도록 코드 TTL(5분)과 분리해 30분 동안 유지한다.
    private static final Duration VERIFICATION_ATTEMPT_TTL = Duration.ofMinutes(30);

    // 이메일 기준 인증번호 발송 제한: 1분에 1회, 1시간에 5회 (회원가입 인증·비밀번호 재설정 공통)
    private static final Duration EMAIL_SEND_MINUTE_WINDOW = Duration.ofMinutes(1);
    private static final int MAX_EMAIL_SENDS_PER_MINUTE = 1;
    private static final Duration EMAIL_SEND_HOUR_WINDOW = Duration.ofHours(1);
    private static final int MAX_EMAIL_SENDS_PER_HOUR = 5;

    // 로그인 시도 제한: 15분 안에 5회까지 비밀번호를 검사하고, 6번째 시도부터 15분 동안 차단(성공하면 카운터 삭제)
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOGIN_BLOCK_DURATION = Duration.ofMinutes(15);

    // 배포 이후 테스트 우회가 필요 없어지면 TEST_EMAIL_* 상수, isTestEmail(), send/verify 분기, 관련 테스트를 함께 삭제하세요.
    private static final String TEST_EMAIL_DOMAIN = "weiver.test";
    private static final String TEST_EMAIL_VERIFICATION_CODE = "000000";

    private final ApplicantRepository applicantRepository;
    private final ApplicantAgreementRepository applicantAgreementRepository;
    private final ApplicantEmailVerificationRepository emailVerificationRepository;
    private final ApplicantSignupTokenRepository signupTokenRepository;
    private final ApplicantPasswordReauthTokenRepository passwordReauthTokenRepository;
    private final TokenVersionRepository tokenVersionRepository;
    private final ApplicantVerificationCodeGenerator codeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicantProvider applicantProvider;
    private final EmailVerificationService emailVerificationService;
    private final ApplicantEmailSendLimitRepository emailSendLimitRepository;
    private final LoginAttemptRepository loginAttemptRepository;

    // 비-prod 테스트 편의용 이메일 인증 우회 스위치. prod에서는 반드시 false여야 한다(기본 false).
    @Value("${weiver.auth.test-email-bypass.enabled:false}")
    private boolean testEmailBypassEnabled;

    public void sendEmailCode(ApplicantEmailSendRequestDTO request) {
        String email = request.email();

        checkEmailSendLimit(email);

        applicantRepository.findByEmailAndDeletedFalse(email)
                .filter(existing -> existing.getStatus() == ApplicantStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                });

        issueAndSendVerificationCode(email, "[EmailVerification]");
    }

    public void sendPasswordResetCode(ApplicantEmailSendRequestDTO request) {
        String email = request.email();

        // 가입 여부 확인 전에 검사해, 제한 응답도 가입 여부와 무관하게 똑같이 나가게 한다(이메일 열거 방지).
        checkEmailSendLimit(email);

        boolean activeApplicantExists = applicantRepository.findByEmailAndDeletedFalse(email)
                .filter(existing -> existing.getStatus() == ApplicantStatus.ACTIVE)
                .isPresent();

        // 이메일 열거(enumeration) 방지: 가입되지 않은 이메일도 성공 응답을 반환하되 실제 코드는 발송하지 않는다.
        if (!activeApplicantExists) {
            return;
        }

        issueAndSendVerificationCode(email, "[PasswordReset]");
    }

    /**
     * 이메일 기준 발송 횟수 제한. 가입 여부를 확인하기 전에 호출한다.
     * 1분 카운터를 먼저 검사하고, 통과한 요청만 1시간 카운터에 센다. 테스트 도메인 우회 이메일은 제한하지 않는다.
     */
    private void checkEmailSendLimit(String email) {
        if (testEmailBypassEnabled && isTestEmail(email)) {
            return;
        }

        if (emailSendLimitRepository.incrementMinuteCount(email, EMAIL_SEND_MINUTE_WINDOW) > MAX_EMAIL_SENDS_PER_MINUTE) {
            throw new BusinessException(ErrorCode.TOO_MANY_EMAIL_REQUESTS);
        }

        if (emailSendLimitRepository.incrementHourCount(email, EMAIL_SEND_HOUR_WINDOW) > MAX_EMAIL_SENDS_PER_HOUR) {
            throw new BusinessException(ErrorCode.TOO_MANY_EMAIL_REQUESTS);
        }
    }

    /**
     * 인증번호를 발급하고 이메일로 발송하는 공통 로직.
     * 사전조건(가입 여부 검사 등)은 호출부에서 처리한 뒤 진입한다.
     */
    private void issueAndSendVerificationCode(String email, String logPrefix) {
        if (testEmailBypassEnabled && isTestEmail(email)) {
            emailVerificationRepository.deleteCode(email);
            emailVerificationRepository.deleteAttemptCount(email);
            return;
        }

        String code = codeGenerator.generateCode();

        // 시도 카운터는 지우지 않는다. 재발송으로 확인 시도 한도를 초기화할 수 없게 하기 위함이다.
        emailVerificationRepository.saveCode(email, code, EMAIL_CODE_TTL);

        try {
            emailVerificationService.sendVerificationCode(email, code);
        } catch (Exception e) {
            log.warn("{} 인증번호 메일 발송 실패 email={} cause={}", logPrefix, email, e.toString());
            emailVerificationRepository.deleteCode(email);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public ApplicantEmailVerifyResponseDTO verifyEmailCode(ApplicantEmailVerifyRequestDTO request) {
        String email = request.email();

        if (testEmailBypassEnabled && isTestEmail(email)) {
            if (!TEST_EMAIL_VERIFICATION_CODE.equals(request.code())) {
                throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
            }

            emailVerificationRepository.deleteCode(email);
            emailVerificationRepository.deleteAttemptCount(email);
            return issueVerificationToken(email);
        }

        // 1) 코드 존재 여부만 확인 (소비하지 않음 - 시도 횟수 한도 내에선 재시도 허용)
        String savedCode = emailVerificationRepository.findCodeByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED));

        // 2) 시도 횟수 증가 (TTL 30분은 첫 증가 때만 걸리고 재발송·재시도로 연장되지 않는다)
        long attemptCount = emailVerificationRepository.incrementAttemptCount(email, VERIFICATION_ATTEMPT_TTL);

        // 3) 한도 초과 시 코드만 소각하고 카운터는 남긴다 (재발송으로 우회 불가)
        if (attemptCount > MAX_VERIFICATION_ATTEMPTS) {
            emailVerificationRepository.deleteCode(email);
            throw new BusinessException(ErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS);
        }

        // 4) 코드 불일치 - 카운터는 살린 채 거절 (사용자가 한도 내에서 재시도 가능)
        if (!savedCode.equals(request.code())) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // 5) 일치 - 코드/카운터 정리 + verificationToken 발급
        emailVerificationRepository.deleteCode(email);
        emailVerificationRepository.deleteAttemptCount(email);

        return issueVerificationToken(email);
    }

    private boolean isTestEmail(String email) {
        int atIndex = email.lastIndexOf("@");
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return false;
        }

        String domain = email.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        return TEST_EMAIL_DOMAIN.equals(domain);
    }

    private ApplicantEmailVerifyResponseDTO issueVerificationToken(String email) {
        String verificationToken = UUID.randomUUID().toString();
        emailVerificationRepository.saveVerifiedToken(verificationToken, email, VERIFICATION_TOKEN_TTL);
        return new ApplicantEmailVerifyResponseDTO(verificationToken);
    }

    @Transactional
    public void changePassword(ApplicantPasswordChangeRequestDTO request) {
        validatePasswordConfirm(request.newPassword(), request.newPasswordConfirm());

        // verification 토큰을 atomic하게 소비 (한 번 시도하면 재사용 불가)
        String verifiedEmail = emailVerificationRepository.findAndDeleteVerifiedToken(request.verificationToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));

        if (!verifiedEmail.equals(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        Applicant applicant = applicantRepository.findByEmailAndDeletedFalse(request.email())
                .filter(existing -> existing.getStatus() == ApplicantStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        String encodedPassword = passwordEncoder.encode(request.newPassword());
        applicant.updatePassword(encodedPassword);

        // 비밀번호 변경 후 기존 세션/토큰 무효화 (액세스 토큰 버전 증가 + 리프레시 토큰 폐기)
        tokenVersionRepository.increaseVersion(applicant.getPublicId(), applicant.getRole());
        refreshTokenRepository.deleteByPublicId(applicant.getPublicId(), applicant.getRole());
    }

    /**
     * 로그인 상태 비밀번호 변경 1단계 - 현재 비밀번호 재인증(마이페이지 계정 설정).
     * 현재 세션의 principal로 대상을 특정하고, 입력한 현재 비밀번호가 저장된 비밀번호와 일치하는지 확인한다.
     * 일치하면 2단계(changeMyPassword)에서 소비할 단기 재인증 토큰을 Redis에 발급한다.
     * 실제 비밀번호 변경은 2단계에서 수행한다.
     */
    @Transactional(readOnly = true)
    public ApplicantPasswordVerifyResponseDTO verifyCurrentPassword(String applicantPublicId, ApplicantPasswordVerifyRequestDTO request) {
        Applicant applicant = applicantProvider.findByPublicId(applicantPublicId);

        if (!passwordEncoder.matches(request.currentPassword(), applicant.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        String reauthToken = UUID.randomUUID().toString();
        passwordReauthTokenRepository.save(reauthToken, applicant.getPublicId(), REAUTH_TOKEN_TTL);

        return new ApplicantPasswordVerifyResponseDTO(reauthToken);
    }

    /**
     * 로그인 상태 비밀번호 변경 2단계 - 새 비밀번호 설정(마이페이지 계정 설정).
     * 비로그인 재설정(changePassword)과 달리 이메일 인증 없이 현재 세션의 principal로 대상을 특정한다.
     * 1단계(verifyCurrentPassword)에서 발급한 재인증 토큰을 atomic하게 소비하고, 토큰에 묶인 publicId가
     * 현재 세션과 일치하는 경우에만 새 비밀번호로 변경한다.
     * 변경 성공 시 비로그인 재설정(changePassword)과 동일하게 기존 세션/토큰을 무효화하여 전 세션을 강제 로그아웃한다.
     */
    @Transactional
    public void changeMyPassword(String applicantPublicId, ApplicantPasswordUpdateRequestDTO request) {
        validatePasswordConfirm(request.newPassword(), request.newPasswordConfirm());

        // 재인증 토큰을 atomic하게 소비 (한 번 시도하면 재사용 불가)
        String reauthPublicId = passwordReauthTokenRepository.findAndDelete(request.reauthToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_REAUTH_TOKEN));

        // 토큰에 묶인 대상과 현재 세션이 다르면 거절 (다른 계정의 토큰 재사용 방지)
        if (!reauthPublicId.equals(applicantPublicId)) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_REAUTH_TOKEN);
        }

        Applicant applicant = applicantProvider.findByPublicId(applicantPublicId);

        String encoded = passwordEncoder.encode(request.newPassword());
        applicant.updatePassword(encoded);

        // 비밀번호 변경 후 기존 세션/토큰 무효화 (액세스 토큰 버전 증가 + 리프레시 토큰 폐기)
        tokenVersionRepository.increaseVersion(applicant.getPublicId(), applicant.getRole());
        refreshTokenRepository.deleteByPublicId(applicant.getPublicId(), applicant.getRole());
    }

    @Transactional
    public ApplicantSignupInitResponseDTO initSignup(ApplicantSignupInitRequestDTO request) {
        validatePasswordConfirm(request.password(), request.passwordConfirm());

        // verification 토큰을 atomic하게 소비 (한 번 시도하면 재사용 불가)
        String verifiedEmail = emailVerificationRepository.findAndDeleteVerifiedToken(request.verificationToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));

        if(!verifiedEmail.equals(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        Applicant applicant = applicantRepository.findByEmailAndDeletedFalse(request.email())
                .map(existing -> {
                    if (existing.getStatus() == ApplicantStatus.ACTIVE) {
                        throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                    }
                    existing.resetPendingPassword(encodedPassword);
                    return existing;
                })
                .orElseGet(() -> {
                    Applicant newApplicant = Applicant.builder()
                            .email(request.email())
                            .password(encodedPassword)
                            .role(UserRole.APPLICANT)
                            .build();
                    try {
                        Applicant saved = applicantRepository.save(newApplicant);
                        applicantRepository.flush();
                        return saved;
                    } catch (DataIntegrityViolationException e) {
                        throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
                    }
                });

        String signupToken = UUID.randomUUID().toString();
        signupTokenRepository.save(signupToken, applicant.getPublicId(), SIGNUP_TOKEN_TTL);

        return new ApplicantSignupInitResponseDTO(signupToken);
    }

    @Transactional
    public ApplicantLoginResult completeSignup(ApplicantSignupCompleteRequestDTO request) {
        validateRequiredAgreements(request.agreements());

        String publicId = signupTokenRepository.findAndDelete(request.signupToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN));

        Applicant applicant = applicantRepository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        if (applicant.getStatus() != ApplicantStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN);
        }

        ApplicantAgreement agreement = ApplicantAgreement.builder()
                .applicant(applicant)
                .termsOfService(request.agreements().termsOfService())
                .privacyPolicy(request.agreements().privacyPolicy())
                .individualMemberTerms(request.agreements().individualMemberTerms())
                .aiAnalysisConsent(request.agreements().aiAnalysisConsent())
                .sensitiveDataConsent(request.agreements().sensitiveDataConsent())
                .marketingConsent(request.agreements().marketingConsent())
                .build();

        applicantAgreementRepository.save(agreement);
        applicant.activate();

        UserRole userRole = applicant.getRole();

        long tokenVersion = tokenVersionRepository.getCurrentVersion(publicId, userRole);

        String accessToken = jwtTokenProvider.createAccessToken(publicId, userRole, tokenVersion);
        String refreshToken = jwtTokenProvider.createRefreshToken(publicId, userRole, tokenVersion);
        long refreshTokenTtlMillis = jwtTokenProvider.getRefreshTokenExpirationMillis();

        refreshTokenRepository.save(
                publicId,
                userRole,
                refreshToken,
                refreshTokenTtlMillis
        );

        return new ApplicantLoginResult(
                accessToken,
                refreshToken,
                userRole
        );

    }

    @Transactional
    public ApplicantLoginResult login(ApplicantLoginRequestDTO request) {
        String email = request.email();

        // 계정 조회·비밀번호 비교 전에 시도를 원자적으로 기록하고 허용 여부를 확인한다.
        // 한도를 넘었거나 차단 중이면 비밀번호가 맞아도 거절한다.
        if (!loginAttemptRepository.tryAcquire(email, UserRole.APPLICANT, MAX_LOGIN_ATTEMPTS, LOGIN_ATTEMPT_WINDOW, LOGIN_BLOCK_DURATION)) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }

        // 로그인 경로에서는 계정 미존재와 비밀번호 불일치를 동일한 오류(INVALID_PASSWORD)로 통일해 이메일 열거를 차단한다.
        // 계정이 없는 경우도 시도로 센다.
        Applicant applicant = applicantRepository.findByEmailAndDeletedFalse(email).orElse(null);

        // 시도는 tryAcquire에서 이미 셌으므로 실패 시 추가 Redis 호출은 없다.
        if (applicant == null || !passwordEncoder.matches(request.password(), applicant.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        loginAttemptRepository.clearAttempts(email, UserRole.APPLICANT);

        if (applicant.getStatus() != ApplicantStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SIGNUP_NOT_COMPLETED);
        }

        String publicId = applicant.getPublicId();
        UserRole userRole = applicant.getRole();

        long tokenVersion = tokenVersionRepository.getCurrentVersion(publicId, userRole);

        String accessToken = jwtTokenProvider.createAccessToken(publicId, userRole, tokenVersion);
        String refreshToken = jwtTokenProvider.createRefreshToken(publicId, userRole, tokenVersion);
        long refreshTokenTtlMillis = jwtTokenProvider.getRefreshTokenExpirationMillis();

        refreshTokenRepository.save(
                publicId,
                userRole,
                refreshToken,
                refreshTokenTtlMillis
        );

        return new ApplicantLoginResult(
                accessToken,
                refreshToken,
                userRole
        );
    }

    @Transactional
    public void withdraw(String publicId) {
        Applicant applicant = applicantProvider.findByPublicId(publicId);

        refreshTokenRepository.deleteByPublicId(
                applicant.getPublicId(),
                applicant.getRole()
        );

        applicant.withdraw();
    }

    private void validatePasswordConfirm(String password, String passwordConfirm) {
        if(!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }
    }

    private void validateRequiredAgreements(ApplicantAgreementRequestDTO agreements) {
        if(!Boolean.TRUE.equals(agreements.termsOfService())
        || !Boolean.TRUE.equals(agreements.privacyPolicy())
        || !Boolean.TRUE.equals(agreements.individualMemberTerms())
        || !Boolean.TRUE.equals(agreements.aiAnalysisConsent())
        || !Boolean.TRUE.equals(agreements.sensitiveDataConsent())) {
            throw new BusinessException(ErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED);
        }
    }
}
