package com.weiver.domain.auth.service;

import com.weiver.auth.dto.request.CompanyLoginRequestDTO;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    @DisplayName("로그인 성공 시 현재 tokenVersion으로 access/refresh 토큰을 발급하고 RefreshToken을 저장한다.")
    void login_success() {
        // given
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
    @DisplayName("존재하지 않는 loginId면 COMPANY_NOT_FOUND 예외")
    void login_companyNotFound() {
        // given
        CompanyLoginRequestDTO request = new CompanyLoginRequestDTO("none", "Pass1234!");
        when(companyRepository.findByLoginIdAndDeletedFalse("none")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> companyAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMPANY_NOT_FOUND.defaultMessage);

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), any(UserRole.class), anyLong());
        verify(refreshTokenRepository, never()).save(anyString(), any(UserRole.class), anyString(), anyLong());
    }

    @Test
    @DisplayName("탈퇴한 기업 계정으로 로그인 시 COMPANY_NOT_FOUND 예외")
    void login_deletedCompany() {
        // given
        CompanyLoginRequestDTO request = new CompanyLoginRequestDTO("deleted-company", "Pass1234!");
        when(companyRepository.findByLoginIdAndDeletedFalse("deleted-company")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> companyAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMPANY_NOT_FOUND.defaultMessage);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 INVALID_PASSWORD 예외")
    void login_invalidPassword() {
        // given
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
}
