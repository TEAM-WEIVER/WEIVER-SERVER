package com.weiver.domain.auth.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAgreement;
import com.weiver.applicant.repository.ApplicantAgreementRepository;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.auth.dto.request.ApplicantAgreementRequestDTO;
import com.weiver.auth.dto.request.ApplicantEmailSendRequestDTO;
import com.weiver.auth.dto.request.ApplicantEmailVerifyRequestDTO;
import com.weiver.auth.dto.request.ApplicantLoginRequestDTO;
import com.weiver.auth.dto.request.ApplicantSignupRequestDTO;
import com.weiver.auth.dto.response.ApplicantEmailVerifyResponseDTO;
import com.weiver.auth.dto.response.ApplicantSignupResponseDTO;
import com.weiver.auth.repository.ApplicantEmailVerificationRepository;
import com.weiver.auth.service.ApplicantAuthService;
import com.weiver.auth.service.ApplicantVerificationCodeGenerator;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.auth.service.EmailVerificationService;
import com.weiver.global.auth.ApplicantProvider;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApplicantAuthServiceTest {

    @InjectMocks
    private ApplicantAuthService applicantAuthService;

    @Mock private ApplicantRepository applicantRepository;
    @Mock private ApplicantAgreementRepository applicantAgreementRepository;
    @Mock private ApplicantEmailVerificationRepository emailVerificationRepository;
    @Mock private ApplicantVerificationCodeGenerator codeGenerator;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenVersionRepository tokenVersionRepository;
    @Mock private ApplicantProvider applicantProvider;

    @Test
    @DisplayName("이메일 인증번호 전송 성공 시 시도 카운터 초기화 후 코드 저장 + 메일 발송")
    public void sendEmailCode_success() {
        // given
        String email = "user@test.com";
        String code = "123456";
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO(email);

        when(applicantRepository.existsByEmail(email)).thenReturn(false);
        when(codeGenerator.generateCode()).thenReturn(code);

        // when
        applicantAuthService.sendEmailCode(request);

        // then
        verify(emailVerificationRepository).deleteAttemptCount(email);
        verify(emailVerificationRepository).saveCode(eq(email), eq(code), any(Duration.class));
        verify(emailVerificationService).sendVerificationCode(email, code);
        verify(emailVerificationRepository, never()).deleteCode(anyString());
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 EMAIL_ALREADY_EXISTS 예외 발생")
    public void sendEmailCode_emailAlreadyExists() {
        // given
        String email = "exists@test.com";
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO(email);

        when(applicantRepository.existsByEmail(email)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> applicantAuthService.sendEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_ALREADY_EXISTS.defaultMessage);

        verify(codeGenerator, never()).generateCode();
        verify(emailVerificationRepository, never()).saveCode(anyString(), anyString(), any(Duration.class));
        verify(emailVerificationService, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    @DisplayName("메일 발송 실패 시 저장한 코드를 삭제하고 EMAIL_SEND_FAILED 예외 발생")
    public void sendEmailCode_mailSendFails() {
        // given
        String email = "user@test.com";
        String code = "123456";
        ApplicantEmailSendRequestDTO request = new ApplicantEmailSendRequestDTO(email);

        when(applicantRepository.existsByEmail(email)).thenReturn(false);
        when(codeGenerator.generateCode()).thenReturn(code);
        doThrow(new RuntimeException("smtp down")).when(emailVerificationService).sendVerificationCode(anyString(), anyString());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.sendEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_SEND_FAILED.defaultMessage);

        verify(emailVerificationRepository).deleteAttemptCount(email);
        verify(emailVerificationRepository).saveCode(eq(email), eq(code), any(Duration.class));
        verify(emailVerificationRepository).deleteCode(email);
    }

    @Test
    @DisplayName("이메일 인증번호 검증 성공 시 코드/카운터를 정리하고 verificationToken을 발급한다.")
    public void verifyEmailCode_success() {
        // given
        String email = "user@test.com";
        String code = "123456";
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO(email, code);

        when(emailVerificationRepository.findCodeByEmail(email)).thenReturn(Optional.of(code));
        when(emailVerificationRepository.incrementAttemptCount(eq(email), any(Duration.class))).thenReturn(1L);

        // when
        ApplicantEmailVerifyResponseDTO response = applicantAuthService.verifyEmailCode(request);

        // then
        assertThat(response.verificationToken()).isNotBlank();
        verify(emailVerificationRepository).findCodeByEmail(email);
        verify(emailVerificationRepository).incrementAttemptCount(eq(email), any(Duration.class));
        verify(emailVerificationRepository).saveVerifiedToken(eq(response.verificationToken()), eq(email), any(Duration.class));
        verify(emailVerificationRepository).deleteCode(email);
        verify(emailVerificationRepository).deleteAttemptCount(email);
    }

    @Test
    @DisplayName("저장된 코드가 없으면 VERIFICATION_CODE_EXPIRED 예외 (카운터 증가 없음)")
    public void verifyEmailCode_expired() {
        // given
        String email = "user@test.com";
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO(email, "123456");

        when(emailVerificationRepository.findCodeByEmail(email)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.verifyEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.VERIFICATION_CODE_EXPIRED.defaultMessage);

        verify(emailVerificationRepository, never()).incrementAttemptCount(anyString(), any(Duration.class));
        verify(emailVerificationRepository, never()).saveVerifiedToken(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("코드 불일치 시 INVALID_VERIFICATION_CODE 예외 (코드/카운터는 살아있음)")
    public void verifyEmailCode_invalidCode() {
        // given
        String email = "user@test.com";
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO(email, "111111");

        when(emailVerificationRepository.findCodeByEmail(email)).thenReturn(Optional.of("999999"));
        when(emailVerificationRepository.incrementAttemptCount(eq(email), any(Duration.class))).thenReturn(2L);

        // when & then
        assertThatThrownBy(() -> applicantAuthService.verifyEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_VERIFICATION_CODE.defaultMessage);

        verify(emailVerificationRepository).findCodeByEmail(email);
        verify(emailVerificationRepository).incrementAttemptCount(eq(email), any(Duration.class));
        verify(emailVerificationRepository, never()).deleteCode(anyString());
        verify(emailVerificationRepository, never()).deleteAttemptCount(anyString());
        verify(emailVerificationRepository, never()).saveVerifiedToken(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("시도 횟수 한도 초과 시 코드/카운터 소각 후 TOO_MANY_VERIFICATION_ATTEMPTS 예외")
    public void verifyEmailCode_tooManyAttempts() {
        // given
        String email = "user@test.com";
        ApplicantEmailVerifyRequestDTO request = new ApplicantEmailVerifyRequestDTO(email, "111111");

        when(emailVerificationRepository.findCodeByEmail(email)).thenReturn(Optional.of("999999"));
        // 최대 5회 -> 6번째 시도부터 차단
        when(emailVerificationRepository.incrementAttemptCount(eq(email), any(Duration.class))).thenReturn(6L);

        // when & then
        assertThatThrownBy(() -> applicantAuthService.verifyEmailCode(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS.defaultMessage);

        verify(emailVerificationRepository).deleteCode(email);
        verify(emailVerificationRepository).deleteAttemptCount(email);
        verify(emailVerificationRepository, never()).saveVerifiedToken(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("회원가입 성공 시 토큰 소비 -> Applicant 저장 -> Agreement 저장 순으로 수행한다.")
    public void signup_success() {
        // given
        String email = "new@test.com";
        String password = "Pass1234!";
        String token = "verification-token";
        ApplicantSignupRequestDTO request = new ApplicantSignupRequestDTO(
                email, password, password, token, allTrueAgreements()
        );

        when(emailVerificationRepository.findAndDeleteVerifiedToken(token))
                .thenReturn(Optional.of(email));
        when(passwordEncoder.encode(password)).thenReturn("encoded");

        String expectedPublicId = "test-public-id";
        Applicant saved = Applicant.builder()
                .email(email)
                .password("encoded")
                .role(UserRole.APPLICANT)
                .publicId(expectedPublicId)
                .build();
        ReflectionTestUtils.setField(saved, "applicantId", 1L);
        when(applicantRepository.save(any(Applicant.class))).thenReturn(saved);

        // when
        ApplicantSignupResponseDTO response = applicantAuthService.signup(request);

        // then
        assertThat(response.publicId()).isEqualTo(expectedPublicId);
        assertThat(response.role()).isEqualTo(UserRole.APPLICANT);

        verify(emailVerificationRepository).findAndDeleteVerifiedToken(token);
        verify(applicantRepository).save(any(Applicant.class));
        verify(applicantRepository).flush();
        verify(applicantAgreementRepository).save(any(ApplicantAgreement.class));
    }

    @Test
    @DisplayName("비밀번호와 비밀번호 확인이 다르면 PASSWORD_CONFIRM_NOT_MATCH 예외 (토큰 소비 안 함)")
    public void signup_passwordConfirmMismatch() {
        // given
        ApplicantSignupRequestDTO request = new ApplicantSignupRequestDTO(
                "user@test.com", "Pass1234!", "Different1!", "token", allTrueAgreements()
        );

        // when & then
        assertThatThrownBy(() -> applicantAuthService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH.defaultMessage);

        verify(emailVerificationRepository, never()).findAndDeleteVerifiedToken(anyString());
        verify(applicantRepository, never()).save(any(Applicant.class));
    }

    @Test
    @DisplayName("필수 약관 미동의 시 REQUIRED_AGREEMENT_NOT_ACCEPTED 예외 (토큰 소비 안 함)")
    public void signup_requiredAgreementNotAccepted() {
        // given
        ApplicantAgreementRequestDTO partial = new ApplicantAgreementRequestDTO(
                true, true, true, true, false, true
        );
        ApplicantSignupRequestDTO request = new ApplicantSignupRequestDTO(
                "user@test.com", "Pass1234!", "Pass1234!", "token", partial
        );

        // when & then
        assertThatThrownBy(() -> applicantAuthService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REQUIRED_AGREEMENT_NOT_ACCEPTED.defaultMessage);

        verify(emailVerificationRepository, never()).findAndDeleteVerifiedToken(anyString());
        verify(applicantRepository, never()).save(any(Applicant.class));
    }

    @Test
    @DisplayName("verification 토큰이 Redis에 없으면 EMAIL_NOT_VERIFIED 예외")
    public void signup_verificationTokenNotFound() {
        // given
        String token = "missing-token";
        ApplicantSignupRequestDTO request = new ApplicantSignupRequestDTO(
                "user@test.com", "Pass1234!", "Pass1234!", token, allTrueAgreements()
        );

        when(emailVerificationRepository.findAndDeleteVerifiedToken(token))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_NOT_VERIFIED.defaultMessage);

        verify(applicantRepository, never()).save(any(Applicant.class));
    }

    @Test
    @DisplayName("verification 토큰의 이메일과 요청 이메일이 다르면 EMAIL_NOT_VERIFIED 예외")
    public void signup_emailMismatch() {
        // given
        String token = "token";
        ApplicantSignupRequestDTO request = new ApplicantSignupRequestDTO(
                "request@test.com", "Pass1234!", "Pass1234!", token, allTrueAgreements()
        );

        when(emailVerificationRepository.findAndDeleteVerifiedToken(token))
                .thenReturn(Optional.of("other@test.com"));

        // when & then
        assertThatThrownBy(() -> applicantAuthService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_NOT_VERIFIED.defaultMessage);

        verify(applicantRepository, never()).save(any(Applicant.class));
    }

    @Test
    @DisplayName("DB unique 제약 위반(race condition)으로 가입 실패 시 EMAIL_ALREADY_EXISTS 예외")
    public void signup_uniqueViolationOnRace() {
        // given
        String email = "race@test.com";
        String token = "token";
        ApplicantSignupRequestDTO request = new ApplicantSignupRequestDTO(
                email, "Pass1234!", "Pass1234!", token, allTrueAgreements()
        );

        when(emailVerificationRepository.findAndDeleteVerifiedToken(token))
                .thenReturn(Optional.of(email));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(applicantRepository.save(any(Applicant.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        // when & then
        assertThatThrownBy(() -> applicantAuthService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_ALREADY_EXISTS.defaultMessage);

        verify(applicantAgreementRepository, never()).save(any(ApplicantAgreement.class));
    }

    @Test
    @DisplayName("로그인 성공 시 현재 tokenVersion으로 access/refresh 토큰을 발급하고 RefreshToken을 저장한다.")
    public void login_success() {
        // given
        String email = "user@test.com";
        String rawPassword = "Pass1234!";
        String publicId = "uuid-applicant-7";
        ApplicantLoginRequestDTO request = new ApplicantLoginRequestDTO(email, rawPassword);

        Applicant applicant = Applicant.builder()
                .email(email)
                .password("encoded")
                .role(UserRole.APPLICANT)
                .publicId(publicId)
                .build();
        ReflectionTestUtils.setField(applicant, "applicantId", 7L);

        long tokenVersion = 0L;
        long ttlMillis = 1000L * 60 * 60 * 24 * 14;

        when(applicantRepository.findByEmailAndDeletedFalse(email)).thenReturn(Optional.of(applicant));
        when(passwordEncoder.matches(rawPassword, "encoded")).thenReturn(true);
        when(tokenVersionRepository.getCurrentVersion(publicId, UserRole.APPLICANT)).thenReturn(tokenVersion);
        when(jwtTokenProvider.createAccessToken(publicId, UserRole.APPLICANT, tokenVersion)).thenReturn("access");
        when(jwtTokenProvider.createRefreshToken(publicId, UserRole.APPLICANT, tokenVersion)).thenReturn("refresh");
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);

        // when
        ApplicantLoginResult result = applicantAuthService.login(request);

        // then
        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.role()).isEqualTo(UserRole.APPLICANT);
        verify(refreshTokenRepository).save(publicId, UserRole.APPLICANT, "refresh", ttlMillis);
        verify(tokenVersionRepository, never()).increaseVersion(anyString(), any(UserRole.class));
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 APPLICANT_NOT_FOUND 예외")
    public void login_applicantNotFound() {
        // given
        ApplicantLoginRequestDTO request = new ApplicantLoginRequestDTO("none@test.com", "Pass1234!");
        when(applicantRepository.findByEmailAndDeletedFalse("none@test.com")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicantAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.APPLICANT_NOT_FOUND.defaultMessage);

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), any(UserRole.class), anyLong());
        verify(refreshTokenRepository, never()).save(anyString(), any(UserRole.class), anyString(), anyLong());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 INVALID_PASSWORD 예외")
    public void login_invalidPassword() {
        // given
        ApplicantLoginRequestDTO request = new ApplicantLoginRequestDTO("user@test.com", "wrong");
        Applicant applicant = Applicant.builder()
                .email("user@test.com")
                .password("encoded")
                .role(UserRole.APPLICANT)
                .build();
        ReflectionTestUtils.setField(applicant, "applicantId", 1L);

        when(applicantRepository.findByEmailAndDeletedFalse("user@test.com")).thenReturn(Optional.of(applicant));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> applicantAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_PASSWORD.defaultMessage);

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), any(UserRole.class), anyLong());
        verify(refreshTokenRepository, never()).save(anyString(), any(UserRole.class), anyString(), anyLong());
    }

    @Test
    @DisplayName("회원탈퇴 성공 시 RefreshToken 삭제 후 Applicant를 soft-delete 처리한다.")
    public void withdraw_success() {
        // given
        String publicId = "uuid-applicant-5";
        Applicant applicant = Applicant.builder()
                .email("user@test.com")
                .password("encoded")
                .role(UserRole.APPLICANT)
                .publicId(publicId)
                .build();
        ReflectionTestUtils.setField(applicant, "applicantId", 5L);

        when(applicantProvider.findByPublicId(publicId)).thenReturn(applicant);

        // when
        applicantAuthService.withdraw(publicId);

        // then
        verify(refreshTokenRepository).deleteByPublicId(publicId, UserRole.APPLICANT);
        assertThat(applicant.isDeleted()).isTrue();
        assertThat(applicant.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴 대상 사용자가 없으면 APPLICANT_NOT_FOUND 예외")
    public void withdraw_applicantNotFound() {
        // given
        String publicId = "uuid-missing";
        when(applicantProvider.findByPublicId(publicId))
                .thenThrow(new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> applicantAuthService.withdraw(publicId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.APPLICANT_NOT_FOUND.defaultMessage);

        verify(refreshTokenRepository, never()).deleteByPublicId(anyString(), any(UserRole.class));
    }

    private static ApplicantAgreementRequestDTO allTrueAgreements() {
        return new ApplicantAgreementRequestDTO(true, true, true, true, true, true);
    }
}
