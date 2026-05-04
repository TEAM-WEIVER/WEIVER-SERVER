package com.weiver.domain.auth.service;

import com.weiver.auth.service.AuthServiceImpl;
import com.weiver.auth.service.dto.TokenReissueResult;
import com.weiver.auth.validator.AuthUserValidator;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.common.UserRole;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.type.RefreshTokenRotationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private BlacklistTokenRepository blacklistTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuthUserValidator authUserValidator;

    // ===================== accessToken =====================

    @Test
    @DisplayName("정상 로그아웃 시 AccessToken을 블랙리스트에 등록하고 RefreshToken을 삭제")
    public void logoutSuccess() {
        // given
        String accessToken = "validAccessToken";
        Long userId = 1L;
        UserRole userRole = UserRole.APPLICANT;
        long ttlMillis = 1000L * 60 * 10;

        when(jwtTokenProvider.getUserId(accessToken)).thenReturn(userId);
        when(jwtTokenProvider.getRole(accessToken)).thenReturn(userRole);
        when(jwtTokenProvider.getRemainingExpiration(accessToken)).thenReturn(ttlMillis);

        // when
        authService.logout(accessToken);

        // then
        verify(jwtTokenProvider).getUserId(accessToken);
        verify(jwtTokenProvider).getRole(accessToken);
        verify(jwtTokenProvider).getRemainingExpiration(accessToken);
        verify(blacklistTokenRepository).save(accessToken, ttlMillis);
        verify(refreshTokenRepository).deleteByUserId(userId, userRole);
    }

    @Test
    @DisplayName("만료된 토큰이면 EXPIRED TOKEN 예외 전파되고 Redis 저장/삭제는 수행되지 않는다.")
    public void logoutExpiredToken() {
        // given
        String accessToken = "expiredAccessToken";

        when(jwtTokenProvider.getUserId(accessToken)).thenThrow(new BusinessException(ErrorCode.TOKEN_EXPIRED));

        // when & then
        assertThatThrownBy(() -> authService.logout(accessToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.TOKEN_EXPIRED.defaultMessage);

        verify(jwtTokenProvider).getUserId(accessToken);
        verify(jwtTokenProvider, never()).getRole(anyString());
        verify(jwtTokenProvider, never()).getRemainingExpiration(anyString());
        verify(blacklistTokenRepository, never()).save(anyString(), anyLong());
        verify(refreshTokenRepository, never()).deleteByUserId(anyLong(), any(UserRole.class));
    }

    @Test
    @DisplayName("위조된 토큰이면 INVALID_TOKEN 예외 전파되고 Redis 저장/삭제 수행되지 않는다.")
    public void logoutInvalidToken() {
        // given
        String accessToken = "invalidAccessToken";

        when(jwtTokenProvider.getUserId(accessToken)).thenThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

        // when & then
        assertThatThrownBy(() -> authService.logout(accessToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_TOKEN.defaultMessage);

        verify(jwtTokenProvider).getUserId(accessToken);
        verify(jwtTokenProvider, never()).getRole(anyString());
        verify(jwtTokenProvider, never()).getRemainingExpiration(anyString());
        verify(blacklistTokenRepository, never()).save(anyString(), anyLong());
        verify(refreshTokenRepository, never()).deleteByUserId(anyLong(), any(UserRole.class));
    }

    // ===================== reissueToken =====================

    @Test
    @DisplayName("refreshToken이 null이면 REFRESH_TOKEN_NOT_FOUND 예외")
    public void reissueToken_nullToken() {
        assertThatThrownBy(() -> authService.reissueToken(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REFRESH_TOKEN_NOT_FOUND.defaultMessage);
    }

    @Test
    @DisplayName("refreshToken이 blank이면 REFRESH_TOKEN_NOT_FOUND 예외")
    public void reissueToken_blankToken() {
        assertThatThrownBy(() -> authService.reissueToken("   "))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REFRESH_TOKEN_NOT_FOUND.defaultMessage);
    }

    @Test
    @DisplayName("토큰 재발급 성공 시 새 accessToken과 refreshToken 반환")
    public void reissueToken_success() {
        // given
        String oldRefreshToken = "old_refresh_token";
        Long userId = 1L;
        UserRole userRole = UserRole.APPLICANT;
        String newAccessToken = "new_access_token";
        String newRefreshToken = "new_refresh_token";
        long ttlMillis = 1000L * 60 * 60 * 24 * 7;

        when(jwtTokenProvider.getUserId(oldRefreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getRole(oldRefreshToken)).thenReturn(userRole);
        when(jwtTokenProvider.createAccessToken(userId, userRole)).thenReturn(newAccessToken);
        when(jwtTokenProvider.createRefreshToken(userId, userRole)).thenReturn(newRefreshToken);
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);
        when(refreshTokenRepository.rotateIfMatches(userId, userRole, oldRefreshToken, newRefreshToken, ttlMillis))
                .thenReturn(RefreshTokenRotationResult.ROTATED);

        // when
        TokenReissueResult result = authService.reissueToken(oldRefreshToken);

        // then
        assertThat(result.accessToken()).isEqualTo(newAccessToken);
        assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
        verify(jwtTokenProvider).getRefreshTokenExpirationMillis();
        verify(jwtTokenProvider, never()).getRemainingExpiration(oldRefreshToken);
    }

    @Test
    @DisplayName("Redis에 저장된 토큰 없으면 REFRESH_TOKEN_NOT_FOUND 예외")
    public void reissueToken_notFound() {
        // given
        String refreshToken = "valid_refresh_token";
        Long userId = 1L;
        UserRole userRole = UserRole.APPLICANT;
        long ttlMillis = 1000L * 60 * 60 * 24 * 7;

        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getRole(refreshToken)).thenReturn(userRole);
        when(jwtTokenProvider.createAccessToken(userId, userRole)).thenReturn("new_access_token");
        when(jwtTokenProvider.createRefreshToken(userId, userRole)).thenReturn("new_refresh_token");
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);
        when(refreshTokenRepository.rotateIfMatches(userId, userRole, refreshToken, "new_refresh_token", ttlMillis))
                .thenReturn(RefreshTokenRotationResult.NOT_FOUND);

        // when & then
        assertThatThrownBy(() -> authService.reissueToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REFRESH_TOKEN_NOT_FOUND.defaultMessage);
        verify(jwtTokenProvider).getRefreshTokenExpirationMillis();
    }

    @Test
    @DisplayName("토큰 재사용 감지(MISMATCH) 시 기존 토큰 삭제 후 TOKEN_REUSE_DETECTED 예외")
    public void reissueToken_mismatch() {
        // given
        String refreshToken = "reused_refresh_token";
        Long userId = 1L;
        UserRole userRole = UserRole.APPLICANT;
        long ttlMillis = 1000L * 60 * 60 * 24 * 7;

        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getRole(refreshToken)).thenReturn(userRole);
        when(jwtTokenProvider.createAccessToken(userId, userRole)).thenReturn("new_access_token");
        when(jwtTokenProvider.createRefreshToken(userId, userRole)).thenReturn("new_refresh_token");
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);
        when(refreshTokenRepository.rotateIfMatches(userId, userRole, refreshToken, "new_refresh_token", ttlMillis))
                .thenReturn(RefreshTokenRotationResult.MISMATCH);

        // when & then
        assertThatThrownBy(() -> authService.reissueToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.TOKEN_REUSE_DETECTED.defaultMessage);

        verify(refreshTokenRepository).deleteByUserId(userId, userRole);
        verify(jwtTokenProvider).getRefreshTokenExpirationMillis();
    }

    @Test
    @DisplayName("동시 요청 충돌(CONCURRENT_MODIFIED) 시 기존 토큰 삭제 후 TOKEN_REUSE_DETECTED 예외")
    public void reissueToken_concurrentModified() {
        // given
        String refreshToken = "concurrent_refresh_token";
        Long userId = 1L;
        UserRole userRole = UserRole.APPLICANT;
        long ttlMillis = 1000L * 60 * 60 * 24 * 7;

        when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(userId);
        when(jwtTokenProvider.getRole(refreshToken)).thenReturn(userRole);
        when(jwtTokenProvider.createAccessToken(userId, userRole)).thenReturn("new_access_token");
        when(jwtTokenProvider.createRefreshToken(userId, userRole)).thenReturn("new_refresh_token");
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);
        when(refreshTokenRepository.rotateIfMatches(userId, userRole, refreshToken, "new_refresh_token", ttlMillis))
                .thenReturn(RefreshTokenRotationResult.CONCURRENT_MODIFIED);

        // when & then
        assertThatThrownBy(() -> authService.reissueToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.TOKEN_REUSE_DETECTED.defaultMessage);

        verify(refreshTokenRepository).deleteByUserId(userId, userRole);
        verify(jwtTokenProvider).getRefreshTokenExpirationMillis();
    }
}
