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

    private final ApplicantRepository applicantRepository;
    private final ApplicantAgreementRepository applicantAgreementRepository;
    private final ApplicantEmailVerificationRepository emailVerificationRepository;
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

        // 인증 코드를 atomic하게 조회 + 삭제 (한 번만 사용 가능)
        String savedCode = emailVerificationRepository.findAndDeleteCode(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED));

        if(!savedCode.equals(request.code())) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        String verificationToken = UUID.randomUUID().toString();

        emailVerificationRepository.saveVerifiedToken(verificationToken, email, VERIFICATION_TOKEN_TTL);

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

        String accessToken = jwtTokenProvider.createAccessToken(applicant.getApplicantId(), applicant.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(applicant.getApplicantId(), applicant.getRole());
        long refreshTokenTtlMillis = jwtTokenProvider.getRemainingExpiration(refreshToken);

        refreshTokenRepository.save(
                applicant.getApplicantId(),
                applicant.getRole(),
                refreshToken,
                refreshTokenTtlMillis
        );

        return new ApplicantLoginResult(
                accessToken,
                refreshToken,
                applicant.getRole()
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
