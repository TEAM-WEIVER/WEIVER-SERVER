package com.weiver.global.security.jwt;

import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-token-provider-unit-test-1234567890";
    private static final long ACCESS_TOKEN_EXPIRATION = 1000L * 60 * 10;       // 10분
    private static final long REFRESH_TOKEN_EXPIRATION = 1000L * 60 * 60 * 24 * 14; // 14일

    private JwtTokenProvider jwtTokenProvider;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    // ===================== createToken =====================

    @Test
    @DisplayName("createAccessToken: subject/role/tokenVersion claim과 ACCESS_TOKEN_EXPIRATION 만료시각으로 발급")
    public void createAccessToken_success() {
        // given
        Long userId = 1L;
        UserRole role = UserRole.APPLICANT;
        long tokenVersion = 3L;

        // when
        String token = jwtTokenProvider.createAccessToken(userId, role, tokenVersion);

        // then
        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo(role);
        assertThat(jwtTokenProvider.getTokenVersion(token)).isEqualTo(tokenVersion);

        // JJWT는 exp claim을 초 단위로 저장하므로 최대 1초 정도의 손실이 발생할 수 있음
        long remaining = jwtTokenProvider.getRemainingExpiration(token);
        assertThat(remaining)
                .isLessThanOrEqualTo(ACCESS_TOKEN_EXPIRATION)
                .isGreaterThan(ACCESS_TOKEN_EXPIRATION - 5000);
    }

    @Test
    @DisplayName("createRefreshToken: REFRESH_TOKEN_EXPIRATION 만료시각으로 발급")
    public void createRefreshToken_success() {
        // given
        Long userId = 7L;
        UserRole role = UserRole.COMPANY;
        long tokenVersion = 0L;

        // when
        String token = jwtTokenProvider.createRefreshToken(userId, role, tokenVersion);

        // then
        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo(role);
        assertThat(jwtTokenProvider.getTokenVersion(token)).isEqualTo(tokenVersion);
        assertThat(jwtTokenProvider.getRemainingExpiration(token))
                .isLessThanOrEqualTo(REFRESH_TOKEN_EXPIRATION)
                .isGreaterThan(REFRESH_TOKEN_EXPIRATION - 1000);
    }

    // ===================== validateRefreshToken =====================

    @Test
    @DisplayName("validateRefreshToken: 유효한 토큰이면 예외 없이 통과")
    public void validateRefreshToken_valid() {
        // given
        String token = jwtTokenProvider.createRefreshToken(1L, UserRole.APPLICANT, 0L);

        // when & then
        jwtTokenProvider.validateRefreshToken(token);
    }

    @Test
    @DisplayName("validateRefreshToken: 만료된 토큰이면 REFRESH_TOKEN_EXPIRED 예외")
    public void validateRefreshToken_expired() {
        // given
        String expiredToken = buildExpiredToken(1L, UserRole.APPLICANT, 0L);

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateRefreshToken(expiredToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REFRESH_TOKEN_EXPIRED.defaultMessage);
    }

    @Test
    @DisplayName("validateRefreshToken: 위조된 토큰이면 INVALID_TOKEN 예외")
    public void validateRefreshToken_invalid() {
        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateRefreshToken("not.a.jwt"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_TOKEN.defaultMessage);
    }

    // ===================== getRemainingExpiration =====================

    @Test
    @DisplayName("getRemainingExpiration: 발급 직후 access 토큰의 남은 시간은 ACCESS_TOKEN_EXPIRATION 이하")
    public void getRemainingExpiration_freshToken() {
        // given
        String token = jwtTokenProvider.createAccessToken(1L, UserRole.APPLICANT, 0L);

        // when
        long remaining = jwtTokenProvider.getRemainingExpiration(token);

        // then
        assertThat(remaining).isPositive().isLessThanOrEqualTo(ACCESS_TOKEN_EXPIRATION);
    }

    // ===================== getRefreshTokenExpirationMillis =====================

    @Test
    @DisplayName("getRefreshTokenExpirationMillis: 설정값 그대로 반환")
    public void getRefreshTokenExpirationMillis_returnsConfigValue() {
        // when
        long value = jwtTokenProvider.getRefreshTokenExpirationMillis();

        // then
        assertThat(value).isEqualTo(REFRESH_TOKEN_EXPIRATION);
    }

    // ===================== getUserId =====================

    @Test
    @DisplayName("getUserId: 토큰의 subject를 Long으로 반환")
    public void getUserId_success() {
        // given
        String token = jwtTokenProvider.createAccessToken(42L, UserRole.APPLICANT, 0L);

        // when & then
        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("getUserId: subject가 비어 있으면 INVALID_TOKEN 예외")
    public void getUserId_blankSubject() {
        // given
        String token = Jwts.builder()
                .subject("")
                .claim("role", UserRole.APPLICANT.name())
                .claim("tokenVersion", 0L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(secretKey)
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.getUserId(token))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_TOKEN.defaultMessage);
    }

    @Test
    @DisplayName("getUserId: 만료된 토큰이면 TOKEN_EXPIRED 예외")
    public void getUserId_expired() {
        // given
        String expiredToken = buildExpiredToken(1L, UserRole.APPLICANT, 0L);

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.getUserId(expiredToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.TOKEN_EXPIRED.defaultMessage);
    }

    // ===================== getRole =====================

    @Test
    @DisplayName("getRole: claim에 저장된 role을 UserRole enum으로 반환")
    public void getRole_success() {
        // given
        String token = jwtTokenProvider.createAccessToken(1L, UserRole.COMPANY, 0L);

        // when & then
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo(UserRole.COMPANY);
    }

    // ===================== getTokenVersion =====================

    @Test
    @DisplayName("getTokenVersion: claim의 tokenVersion 값을 long으로 반환")
    public void getTokenVersion_success() {
        // given
        String token = jwtTokenProvider.createAccessToken(1L, UserRole.APPLICANT, 5L);

        // when & then
        assertThat(jwtTokenProvider.getTokenVersion(token)).isEqualTo(5L);
    }

    @Test
    @DisplayName("getTokenVersion: tokenVersion claim이 없으면 INVALID_TOKEN 예외")
    public void getTokenVersion_missingClaim() {
        // given
        String tokenWithoutVersion = Jwts.builder()
                .subject("1")
                .claim("role", UserRole.APPLICANT.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(secretKey)
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.getTokenVersion(tokenWithoutVersion))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_TOKEN.defaultMessage);
    }

    // ===================== getClaims (간접 검증) =====================

    @Test
    @DisplayName("위조된 서명의 토큰은 INVALID_TOKEN 예외")
    public void invalidSignature() {
        // given - 다른 secret으로 서명된 토큰
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "other-secret-key-different-from-the-real-one-1234567890".getBytes(StandardCharsets.UTF_8)
        );
        String forgedToken = Jwts.builder()
                .subject("1")
                .claim("role", UserRole.APPLICANT.name())
                .claim("tokenVersion", 0L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(otherKey)
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.getUserId(forgedToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_TOKEN.defaultMessage);
    }

    // ===================== helpers =====================

    private String buildExpiredToken(Long userId, UserRole role, long tokenVersion) {
        Date past = new Date(System.currentTimeMillis() - 60_000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .claim("tokenVersion", tokenVersion)
                .issuedAt(new Date(past.getTime() - 1000))
                .expiration(past)
                .signWith(secretKey)
                .compact();
    }
}