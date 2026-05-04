package com.weiver.auth.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAgreement;
import com.weiver.applicant.repository.ApplicantAgreementRepository;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.auth.dto.request.*;
import com.weiver.auth.dto.response.ApplicantEmailVerifyResponseDTO;
import com.weiver.auth.dto.response.ApplicantSignupResponseDTO;
import com.weiver.auth.repository.ApplicantEmailVerificationRepository;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.mail.MailMessage;
import com.weiver.global.mail.MailSender;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicantAuthService {

    private static final Duration EMAIL_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofMinutes(30);
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;

    private final ApplicantRepository applicantRepository;
    private final ApplicantAgreementRepository applicantAgreementRepository;
    private final ApplicantEmailVerificationRepository emailVerificationRepository;
    private final TokenVersionRepository tokenVersionRepository;
    private final ApplicantVerificationCodeGenerator codeGenerator;
    private final MailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public void sendEmailCode(ApplicantEmailSendRequestDTO request) {
        String email = request.email();

        if(applicantRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String code = codeGenerator.generateCode();

        emailVerificationRepository.deleteAttemptCount(email);
        emailVerificationRepository.saveCode(email, code, EMAIL_CODE_TTL);

        try {
            mailSender.send(new MailMessage(
                    email,
                    "WEIVER 이메일 인증번호",
                    "인증번호는 [" + code + "] 입니다."
            ));
        } catch (Exception e) {
            emailVerificationRepository.deleteCode(email);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public ApplicantEmailVerifyResponseDTO verifyEmailCode(ApplicantEmailVerifyRequestDTO request) {
        String email = request.email();

        // 1) 코드 존재 여부만 확인 (소비하지 않음 - 시도 횟수 한도 내에선 재시도 허용)
        String savedCode = emailVerificationRepository.findCodeByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED));

        // 2) 시도 횟수 증가 (TTL은 코드와 동일하게 부여)
        long attemptCount = emailVerificationRepository.incrementAttemptCount(email, EMAIL_CODE_TTL);

        // 3) 한도 초과 시 코드/카운터 모두 소각하고 차단
        if (attemptCount > MAX_VERIFICATION_ATTEMPTS) {
            emailVerificationRepository.deleteCode(email);
            emailVerificationRepository.deleteAttemptCount(email);
            throw new BusinessException(ErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS);
        }

        // 4) 코드 불일치 - 카운터는 살린 채 거절 (사용자가 한도 내에서 재시도 가능)
        if (!savedCode.equals(request.code())) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // 5) 일치 - 코드/카운터 정리 + verificationToken 발급
        String verificationToken = UUID.randomUUID().toString();
        emailVerificationRepository.saveVerifiedToken(verificationToken, email, VERIFICATION_TOKEN_TTL);
        emailVerificationRepository.deleteCode(email);
        emailVerificationRepository.deleteAttemptCount(email);

        return new ApplicantEmailVerifyResponseDTO(verificationToken);
    }

    @Transactional
    public ApplicantSignupResponseDTO signup(ApplicantSignupRequestDTO request) {
        validatePasswordConfirm(request.password(), request.passwordConfirm());
        validateRequiredAgreements(request.agreements());

        // verification 토큰을 atomic하게 소비 (한 번 시도하면 재사용 불가)
        String verifiedEmail = emailVerificationRepository.findAndDeleteVerifiedToken(request.verificationToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));

        if(!verifiedEmail.equals(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        Applicant applicant = Applicant.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.APPLICANT)
                .build();

        Applicant savedApplicant;
        try {
            savedApplicant = applicantRepository.save(applicant);
            applicantRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        ApplicantAgreement agreement = ApplicantAgreement.builder()
                .applicant(savedApplicant)
                .termsOfService(request.agreements().termsOfService())
                .privacyPolicy(request.agreements().privacyPolicy())
                .individualMemberTerms(request.agreements().individualMemberTerms())
                .aiAnalysisConsent(request.agreements().aiAnalysisConsent())
                .sensitiveDataConsent(request.agreements().sensitiveDataConsent())
                .marketingConsent(request.agreements().marketingConsent())
                .build();

        applicantAgreementRepository.save(agreement);

        return ApplicantSignupResponseDTO.from(savedApplicant);
    }

    @Transactional
    public ApplicantLoginResult login(ApplicantLoginRequestDTO request) {
        Applicant applicant = applicantRepository.findByEmailAndDeletedFalse(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        if(!passwordEncoder.matches(request.password(), applicant.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        Long applicantId = applicant.getApplicantId();
        UserRole userRole = applicant.getRole();

        long tokenVersion = tokenVersionRepository.getCurrentVersion(applicantId, userRole);

        String accessToken = jwtTokenProvider.createAccessToken(applicantId, userRole, tokenVersion);
        String refreshToken = jwtTokenProvider.createRefreshToken(applicantId, userRole, tokenVersion);
        long refreshTokenTtlMillis = jwtTokenProvider.getRefreshTokenExpirationMillis();

        refreshTokenRepository.save(
                applicantId,
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
    public void withdraw(Long applicantId) {
        Applicant applicant = applicantRepository.findByApplicantIdAndDeletedFalse(applicantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        refreshTokenRepository.deleteByUserId(
                applicant.getApplicantId(),
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
