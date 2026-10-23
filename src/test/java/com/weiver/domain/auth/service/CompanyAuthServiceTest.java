package com.weiver.domain.auth.service;

import com.weiver.auth.dto.request.CompanyLoginRequestDTO;
import com.weiver.auth.repository.LoginAttemptRepository;
import com.weiver.auth.service.CompanyAuthService;
import com.weiver.auth.service.dto.CompanyLoginResult;
import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAuthServiceTest {

    @InjectMocks
    private CompanyAuthService companyAuthService;

    @Mock private CompanyRepository companyRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenVersionRepository tokenVersionRepository;
    @Mock private LoginAttemptRepository loginAttemptRepository;

    @Test
    @DisplayName("로그인 성공 시 현재 tokenVersion으로 access/refresh 토큰을 발급하고 RefreshToken을 저장한다.")
    void login_success() {
        // given
        allowLoginAttempt("weiver");
        String loginId = "weiver";
        String rawPassword = "Pass1234!";
        String publicId = "uuid-company-1";
        long tokenVersion = 0L;
        long ttlMillis = 1000L * 60 * 60 * 24 * 14;

        CompanyLoginRequestDTO request = new CompanyLoginRequestDTO(loginId, rawPassword);

        Company company = Company.builder()
                .loginId(loginId)
                .password("encoded")
                .publicId(publicId)
                .build();
        ReflectionTestUtils.setField(company, "companyId", 1L);

        when(companyRepository.findByLoginIdAndDeletedFalse(loginId)).thenReturn(Optional.of(company));
        when(passwordEncoder.matches(rawPassword, "encoded")).thenReturn(true);
        when(tokenVersionRepository.getCurrentVersion(publicId, UserRole.COMPANY)).thenReturn(tokenVersion);
        when(jwtTokenProvider.createAccessToken(publicId, UserRole.COMPANY, tokenVersion)).thenReturn("access");
        when(jwtTokenProvider.createRefreshToken(publicId, UserRole.COMPANY, tokenVersion)).thenReturn("refresh");
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);

        // when
        CompanyLoginResult result = companyAuthService.login(request);

        // then
        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.role()).isEqualTo(UserRole.COMPANY);
        verify(refreshTokenRepository).save(publicId, UserRole.COMPANY, "refresh", ttlMillis);
    }

    @Test
    @DisplayName("존재하지 않는 loginId면 아이디 열거 방지를 위해 INVALID_PASSWORD 예외")
    void login_companyNotFound() {
        // given
        allowLoginAttempt("none");
        CompanyLoginRequestDTO request = new CompanyLoginRequestDTO("none", "Pass1234!");
        when(companyRepository.findByLoginIdAndDeletedFalse("none")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> companyAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_PASSWORD.defaultMessage);

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), any(UserRole.class), anyLong());
        verify(refreshTokenRepository, never()).save(anyString(), any(UserRole.class), anyString(), anyLong());
    }

    @Test
    @DisplayName("탈퇴한 기업 계정으로 로그인 시 존재 여부 노출 없이 INVALID_PASSWORD 예외")
    void login_deletedCompany() {
        // given
        allowLoginAttempt("deleted-company");
        CompanyLoginRequestDTO request = new CompanyLoginRequestDTO("deleted-company", "Pass1234!");
        when(companyRepository.findByLoginIdAndDeletedFalse("deleted-company")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> companyAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_PASSWORD.defaultMessage);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 INVALID_PASSWORD 예외")
    void login_invalidPassword() {
        // given
        allowLoginAttempt("weiver");
        String loginId = "weiver";
        CompanyLoginRequestDTO request = new CompanyLoginRequestDTO(loginId, "wrong");

        Company company = Company.builder()
                .loginId(loginId)
                .password("encoded")
                .publicId("uuid-company-1")
                .build();

        when(companyRepository.findByLoginIdAndDeletedFalse(loginId)).thenReturn(Optional.of(company));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> companyAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_PASSWORD.defaultMessage);

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), any(UserRole.class), anyLong());
    }

    // ----------- #174 정책 C: 로그인 시도 제한 -----------

    @Test
    @DisplayName("정책C: 시도 허용 확인이 차단을 돌려주면 계정 조회·비밀번호 비교·카운터 삭제 없이 TOO_MANY_LOGIN_ATTEMPTS 예외")
    void login_blocked() {
        // given
        given(loginAttemptRepository.tryAcquire("weiver", UserRole.COMPANY, 5, Duration.ofMinutes(15), Duration.ofMinutes(15)))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() -> companyAuthService.login(new CompanyLoginRequestDTO("weiver", "Pass1234!")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);

        verifyNoInteractions(companyRepository, passwordEncoder, jwtTokenProvider, refreshTokenRepository);
        verify(loginAttemptRepository, never()).clearAttempts(anyString(), any(UserRole.class));
    }

    @Test
    @DisplayName("정책C: loginId가 없으면 기업 네임스페이스로 시도를 확인하고 추가 Redis 호출 없이 INVALID_PASSWORD 예외")
    void login_companyNotFound_noExtraRedisCall() {
        // given
        allowLoginAttempt("none");
        given(companyRepository.findByLoginIdAndDeletedFalse("none")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> companyAuthService.login(new CompanyLoginRequestDTO("none", "Pass1234!")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);

        verify(loginAttemptRepository).tryAcquire("none", UserRole.COMPANY, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        verifyNoMoreInteractions(loginAttemptRepository);
    }

    @Test
    @DisplayName("정책C: 비밀번호가 틀리면 추가 Redis 호출 없이 INVALID_PASSWORD 예외")
    void login_invalidPassword_noExtraRedisCall() {
        // given
        allowLoginAttempt("weiver");
        Company company = Company.builder().loginId("weiver").password("encoded").publicId("uuid-company-1").build();
        given(companyRepository.findByLoginIdAndDeletedFalse("weiver")).willReturn(Optional.of(company));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> companyAuthService.login(new CompanyLoginRequestDTO("weiver", "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);

        verify(loginAttemptRepository).tryAcquire("weiver", UserRole.COMPANY, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        verifyNoMoreInteractions(loginAttemptRepository);
    }

    @Test
    @DisplayName("정책C: 로그인에 성공하면 시도 카운터를 지운다")
    void login_success_clearsAttempts() {
        // given
        allowLoginAttempt("weiver");
        Company company = Company.builder().loginId("weiver").password("encoded").publicId("uuid-company-1").build();
        given(companyRepository.findByLoginIdAndDeletedFalse("weiver")).willReturn(Optional.of(company));
        given(passwordEncoder.matches("Pass1234!", "encoded")).willReturn(true);

        // when
        companyAuthService.login(new CompanyLoginRequestDTO("weiver", "Pass1234!"));

        // then
        verify(loginAttemptRepository).clearAttempts("weiver", UserRole.COMPANY);
    }

    @Test
    @DisplayName("정책C: 차례로 요청하면 1~5번째는 비밀번호를 검사하고 6번째는 비밀번호가 맞아도 429로 거절한다")
    void login_sequentialBoundary_sixthRejectedEvenWithCorrectPassword() {
        // given
        given(loginAttemptRepository.tryAcquire("weiver", UserRole.COMPANY, 5, Duration.ofMinutes(15), Duration.ofMinutes(15)))
                .willReturn(true, true, true, true, true, false);
        Company company = Company.builder().loginId("weiver").password("encoded").publicId("uuid-company-1").build();
        given(companyRepository.findByLoginIdAndDeletedFalse("weiver")).willReturn(Optional.of(company));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        // when & then
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> companyAuthService.login(new CompanyLoginRequestDTO("weiver", "wrong")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCode.INVALID_PASSWORD);
        }
        assertThatThrownBy(() -> companyAuthService.login(new CompanyLoginRequestDTO("weiver", "Pass1234!")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);

        verify(passwordEncoder, times(5)).matches(anyString(), anyString());
        verify(passwordEncoder, never()).matches("Pass1234!", "encoded");
        verify(loginAttemptRepository, never()).clearAttempts(anyString(), any(UserRole.class));
    }

    private void allowLoginAttempt(String loginId) {
        given(loginAttemptRepository.tryAcquire(loginId, UserRole.COMPANY, 5, Duration.ofMinutes(15), Duration.ofMinutes(15)))
                .willReturn(true);
    }
}
