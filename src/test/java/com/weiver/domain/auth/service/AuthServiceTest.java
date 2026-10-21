package com.weiver.domain.auth.service;

import com.weiver.auth.service.AuthServiceImpl;
import com.weiver.auth.service.dto.TokenReissueResult;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.common.UserRole;
import com.weiver.global.security.jwt.JwtProperties;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import com.weiver.global.security.jwt.type.RefreshTokenRotationResult;
import com.weiver.global.security.util.TokenHashUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private TokenVersionRepository tokenVersionRepository;

    // ===================== accessToken =====================

    @Test
    @DisplayName("정상 로그아웃 시 AccessToken을 블랙리스트에 등록하고 RefreshToken을 삭제")
    public void logoutSuccess() {
        // given
        String accessToken = "validAccessToken";
        String publicId = "uuid-applicant-1";
        UserRole userRole = UserRole.APPLICANT;
        long ttlMillis = 1000L * 60 * 10;

        when(jwtTokenProvider.getPublicId(accessToken)).thenReturn(publicId);
        when(jwtTokenProvider.getRole(accessToken)).thenReturn(userRole);
        when(jwtTokenProvider.getRemainingExpiration(accessToken)).thenReturn(ttlMillis);

        // when
        authService.logout(accessToken);

        // then
        verify(jwtTokenProvider).getPublicId(accessToken);
        verify(jwtTokenProvider).getRole(accessToken);
        verify(jwtTokenProvider).getRemainingExpiration(accessToken);
        verify(blacklistTokenRepository).save(accessToken, ttlMillis);
        verify(refreshTokenRepository).deleteByPublicId(publicId, userRole);
    }

    @Test
    @DisplayName("만료된 토큰이면 예외 없이 관대하게 종료하고 Redis 저장/삭제는 수행하지 않는다.")
    public void logoutExpiredToken() {
        // given
        String accessToken = "expiredAccessToken";

        when(jwtTokenProvider.getPublicId(accessToken)).thenThrow(new BusinessException(ErrorCode.TOKEN_EXPIRED));

        // when
        authService.logout(accessToken);

        // then
        verify(jwtTokenProvider).getPublicId(accessToken);
        verify(jwtTokenProvider, never()).getRole(anyString());
        verify(jwtTokenProvider, never()).getRemainingExpiration(anyString());
        verify(blacklistTokenRepository, never()).save(anyString(), anyLong());
        verify(refreshTokenRepository, never()).deleteByPublicId(anyString(), any(UserRole.class));
    }

    @Test
    @DisplayName("위조된 토큰이면 예외 없이 관대하게 종료하고 Redis 저장/삭제는 수행하지 않는다.")
    public void logoutInvalidToken() {
        // given
        String accessToken = "invalidAccessToken";

        when(jwtTokenProvider.getPublicId(accessToken)).thenThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

        // when
        authService.logout(accessToken);

        // then
        verify(jwtTokenProvider).getPublicId(accessToken);
        verify(jwtTokenProvider, never()).getRole(anyString());
        verify(jwtTokenProvider, never()).getRemainingExpiration(anyString());
        verify(blacklistTokenRepository, never()).save(anyString(), anyLong());
        verify(refreshTokenRepository, never()).deleteByPublicId(anyString(), any(UserRole.class));
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
        String publicId = "uuid-applicant-1";
        UserRole userRole = UserRole.APPLICANT;
        long tokenVersion = 1L;
        String newAccessToken = "new_access_token";
        String newRefreshToken = "new_refresh_token";
        long ttlMillis = 1000L * 60 * 60 * 24 * 7;

        when(jwtTokenProvider.getPublicId(oldRefreshToken)).thenReturn(publicId);
        when(jwtTokenProvider.getRole(oldRefreshToken)).thenReturn(userRole);
        when(tokenVersionRepository.getCurrentVersion(publicId, userRole)).thenReturn(tokenVersion);
        when(jwtTokenProvider.createAccessToken(publicId, userRole, tokenVersion)).thenReturn(newAccessToken);
        when(jwtTokenProvider.createRefreshToken(publicId, userRole, tokenVersion)).thenReturn(newRefreshToken);
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);
        when(refreshTokenRepository.rotateIfMatches(publicId, userRole, oldRefreshToken, newRefreshToken, ttlMillis))
                .thenReturn(RefreshTokenRotationResult.ROTATED);

        // when
        TokenReissueResult result = authService.reissueToken(oldRefreshToken);

        // then
        assertThat(result.accessToken()).isEqualTo(newAccessToken);
        assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
        verify(jwtTokenProvider).getRefreshTokenExpirationMillis();
        verify(jwtTokenProvider, never()).getRemainingExpiration(oldRefreshToken);
        verify(tokenVersionRepository, never()).increaseVersion(anyString(), any(UserRole.class));
    }

    @Test
    @DisplayName("Redis에 저장된 토큰 없으면 REFRESH_TOKEN_NOT_FOUND 예외")
    public void reissueToken_notFound() {
        // given
        String refreshToken = "valid_refresh_token";
        String publicId = "uuid-applicant-1";
        UserRole userRole = UserRole.APPLICANT;
        long tokenVersion = 0L;
        long ttlMillis = 1000L * 60 * 60 * 24 * 7;

        when(jwtTokenProvider.getPublicId(refreshToken)).thenReturn(publicId);
        when(jwtTokenProvider.getRole(refreshToken)).thenReturn(userRole);
        when(tokenVersionRepository.getCurrentVersion(publicId, userRole)).thenReturn(tokenVersion);
        when(jwtTokenProvider.createAccessToken(publicId, userRole, tokenVersion)).thenReturn("new_access_token");
        when(jwtTokenProvider.createRefreshToken(publicId, userRole, tokenVersion)).thenReturn("new_refresh_token");
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);
        when(refreshTokenRepository.rotateIfMatches(publicId, userRole, refreshToken, "new_refresh_token", ttlMillis))
                .thenReturn(RefreshTokenRotationResult.NOT_FOUND);

        // when & then
        assertThatThrownBy(() -> authService.reissueToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REFRESH_TOKEN_NOT_FOUND.defaultMessage);
        verify(jwtTokenProvider).getRefreshTokenExpirationMillis();
        verify(tokenVersionRepository, never()).increaseVersion(anyString(), any(UserRole.class));
    }

    @Test
    @DisplayName("토큰 재사용 감지(MISMATCH) 시 기존 토큰 삭제 + tokenVersion 증가 후 TOKEN_REUSE_DETECTED 예외")
    public void reissueToken_mismatch() {
        // given
        String refreshToken = "reused_refresh_token";
        String publicId = "uuid-applicant-1";
        UserRole userRole = UserRole.APPLICANT;
        long tokenVersion = 0L;
        long ttlMillis = 1000L * 60 * 60 * 24 * 7;

        when(jwtTokenProvider.getPublicId(refreshToken)).thenReturn(publicId);
        when(jwtTokenProvider.getRole(refreshToken)).thenReturn(userRole);
        when(tokenVersionRepository.getCurrentVersion(publicId, userRole)).thenReturn(tokenVersion);
        when(jwtTokenProvider.createAccessToken(publicId, userRole, tokenVersion)).thenReturn("new_access_token");
        when(jwtTokenProvider.createRefreshToken(publicId, userRole, tokenVersion)).thenReturn("new_refresh_token");
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);
        when(refreshTokenRepository.rotateIfMatches(publicId, userRole, refreshToken, "new_refresh_token", ttlMillis))
                .thenReturn(RefreshTokenRotationResult.MISMATCH);

        // when & then
        assertThatThrownBy(() -> authService.reissueToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.TOKEN_REUSE_DETECTED.defaultMessage);

        verify(refreshTokenRepository).deleteByPublicId(publicId, userRole);
        verify(tokenVersionRepository).increaseVersion(publicId, userRole);
        verify(jwtTokenProvider).getRefreshTokenExpirationMillis();
    }

    @Test
    @DisplayName("동시 요청 충돌(CONCURRENT_MODIFIED) 시 기존 토큰 삭제 + tokenVersion 증가 후 TOKEN_REUSE_DETECTED 예외")
    public void reissueToken_concurrentModified() {
        // given
        String refreshToken = "concurrent_refresh_token";
        String publicId = "uuid-applicant-1";
        UserRole userRole = UserRole.APPLICANT;
        long tokenVersion = 0L;
        long ttlMillis = 1000L * 60 * 60 * 24 * 7;

        when(jwtTokenProvider.getPublicId(refreshToken)).thenReturn(publicId);
        when(jwtTokenProvider.getRole(refreshToken)).thenReturn(userRole);
        when(tokenVersionRepository.getCurrentVersion(publicId, userRole)).thenReturn(tokenVersion);
        when(jwtTokenProvider.createAccessToken(publicId, userRole, tokenVersion)).thenReturn("new_access_token");
        when(jwtTokenProvider.createRefreshToken(publicId, userRole, tokenVersion)).thenReturn("new_refresh_token");
        when(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(ttlMillis);
        when(refreshTokenRepository.rotateIfMatches(publicId, userRole, refreshToken, "new_refresh_token", ttlMillis))
                .thenReturn(RefreshTokenRotationResult.CONCURRENT_MODIFIED);

        // when & then
        assertThatThrownBy(() -> authService.reissueToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.TOKEN_REUSE_DETECTED.defaultMessage);

        verify(refreshTokenRepository).deleteByPublicId(publicId, userRole);
        verify(tokenVersionRepository).increaseVersion(publicId, userRole);
        verify(jwtTokenProvider).getRefreshTokenExpirationMillis();
    }

    // ===================== reissueToken: 실제 토큰 종류 회귀 (Issue #171) =====================

    @Nested
    @DisplayName("실제 토큰으로 재발급 (Issue #171)")
    class RealTokenReissueTest {

        private static final String SECRET = "test-secret-key-for-jwt-token-provider-unit-test-1234567890";
        private static final long ACCESS_TOKEN_EXPIRATION = 1000L * 60 * 10;            // 10분
        private static final long REFRESH_TOKEN_EXPIRATION = 1000L * 60 * 60 * 24 * 14; // 14일
        private static final String PUBLIC_ID = "uuid-applicant-171";
        private static final UserRole ROLE = UserRole.APPLICANT;
        private static final long TOKEN_VERSION = 0L;

        private final JwtTokenProvider realJwtTokenProvider = new JwtTokenProvider(
                new JwtProperties(SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION));
        private final BlacklistTokenRepository blacklistRepository = mock(BlacklistTokenRepository.class);
        private final RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        private final TokenVersionRepository versionRepository = mock(TokenVersionRepository.class);
        private final AuthServiceImpl realTokenAuthService = new AuthServiceImpl(
                realJwtTokenProvider, blacklistRepository, refreshRepository, versionRepository);

        @Test
        @DisplayName("token_type 클레임이 없는 기존 Refresh Token으로도 재발급이 ROTATED 경로로 성공한다")
        void reissue_WithLegacyRefreshToken_Rotated() {
            // given
            String legacyRefreshToken = buildLegacyToken(REFRESH_TOKEN_EXPIRATION);
            when(versionRepository.getCurrentVersion(PUBLIC_ID, ROLE)).thenReturn(TOKEN_VERSION);
            stubRotationAgainstStoredRefreshToken(legacyRefreshToken);

            // when
            TokenReissueResult result = realTokenAuthService.reissueToken(legacyRefreshToken);

            // then
            assertThat(tokenTypeClaim(result.accessToken())).isEqualTo("access");
            assertThat(tokenTypeClaim(result.refreshToken())).isEqualTo("refresh");
            assertThat(realJwtTokenProvider.getPublicId(result.refreshToken())).isEqualTo(PUBLIC_ID);
            verify(refreshRepository).rotateIfMatches(eq(PUBLIC_ID), eq(ROLE), eq(legacyRefreshToken),
                    eq(result.refreshToken()), eq(REFRESH_TOKEN_EXPIRATION));
            verify(refreshRepository, never()).deleteByPublicId(anyString(), any(UserRole.class));
            verify(versionRepository, never()).increaseVersion(anyString(), any(UserRole.class));
        }

        @Test
        @DisplayName("Access Token으로 재발급을 요청하면 저장된 해시와 달라 TOKEN_REUSE_DETECTED 예외")
        void reissue_WithAccessToken_TokenReuseDetected() {
            // given
            String storedRefreshToken = realJwtTokenProvider.createRefreshToken(PUBLIC_ID, ROLE, TOKEN_VERSION);
            String accessToken = realJwtTokenProvider.createAccessToken(PUBLIC_ID, ROLE, TOKEN_VERSION);
            when(versionRepository.getCurrentVersion(PUBLIC_ID, ROLE)).thenReturn(TOKEN_VERSION);
            stubRotationAgainstStoredRefreshToken(storedRefreshToken);

            // when & then
            assertThatThrownBy(() -> realTokenAuthService.reissueToken(accessToken))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCode.TOKEN_REUSE_DETECTED);
            verify(refreshRepository).deleteByPublicId(PUBLIC_ID, ROLE);
            verify(versionRepository).increaseVersion(PUBLIC_ID, ROLE);
        }

        /** Redis에 storedRefreshToken의 해시가 저장돼 있다고 보고, 입력 토큰 해시가 같을 때만 ROTATED를 돌려준다. */
        private void stubRotationAgainstStoredRefreshToken(String storedRefreshToken) {
            String storedHash = TokenHashUtil.sha256(storedRefreshToken);
            when(refreshRepository.rotateIfMatches(eq(PUBLIC_ID), eq(ROLE), anyString(), anyString(), anyLong()))
                    .thenAnswer(invocation -> storedHash.equals(TokenHashUtil.sha256(invocation.getArgument(2)))
                            ? RefreshTokenRotationResult.ROTATED
                            : RefreshTokenRotationResult.MISMATCH);
        }

        private Object tokenTypeClaim(String token) {
            SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("token_type");
        }

        private String buildLegacyToken(long expirationMillis) {
            SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Date now = new Date();
            return Jwts.builder()
                    .subject(PUBLIC_ID)
                    .claim("role", ROLE.name())
                    .claim("tokenVersion", TOKEN_VERSION)
                    .issuedAt(now)
                    .expiration(new Date(now.getTime() + expirationMillis))
                    .signWith(secretKey)
                    .compact();
        }
    }
}
