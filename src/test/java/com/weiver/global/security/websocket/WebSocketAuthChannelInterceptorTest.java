package com.weiver.global.security.websocket;

import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.BearerTokenResolver;
import com.weiver.global.security.jwt.JwtProperties;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthChannelInterceptorTest {

    private static final String SECRET = "test-secret-key-for-jwt-token-provider-unit-test-1234567890";
    private static final long ACCESS_TOKEN_EXPIRATION = 1000L * 60 * 10;            // 10분
    private static final long REFRESH_TOKEN_EXPIRATION = 1000L * 60 * 60 * 24 * 14; // 14일
    private static final String PUBLIC_ID = "uuid-applicant-171";
    private static final UserRole ROLE = UserRole.APPLICANT;
    private static final long TOKEN_VERSION = 0L;

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            new JwtProperties(SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION));
    private final BearerTokenResolver bearerTokenResolver = new BearerTokenResolver();
    private final BlacklistTokenRepository blacklistTokenRepository = mock(BlacklistTokenRepository.class);
    private final TokenVersionRepository tokenVersionRepository = mock(TokenVersionRepository.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(
            jwtTokenProvider, blacklistTokenRepository, tokenVersionRepository, bearerTokenResolver);

    @Test
    @DisplayName("대조군: CONNECT의 Authorization Bearer에 Access Token을 넣으면 사용자가 설정된다")
    void connect_WithAccessTokenInAuthorization_SetsUser() {
        // given
        String accessToken = jwtTokenProvider.createAccessToken(PUBLIC_ID, ROLE, TOKEN_VERSION);
        StompHeaderAccessor accessor = connectAccessor("Authorization", "Bearer " + accessToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        stubRepositories(accessToken);

        // when
        interceptor.preSend(message, channel);

        // then
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isNotBlank();
    }

    @Test
    @DisplayName("CONNECT의 Authorization Bearer에 Refresh Token을 넣으면 AccessDeniedException이 발생한다")
    void connect_WithRefreshTokenInAuthorization_Denied() {
        // given
        String refreshToken = jwtTokenProvider.createRefreshToken(PUBLIC_ID, ROLE, TOKEN_VERSION);
        StompHeaderAccessor accessor = connectAccessor("Authorization", "Bearer " + refreshToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        stubRepositories(refreshToken);

        // when & then
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("CONNECT의 access_token 헤더에 Refresh Token을 넣으면 AccessDeniedException이 발생한다")
    void connect_WithRefreshTokenInAccessTokenHeader_Denied() {
        // given
        String refreshToken = jwtTokenProvider.createRefreshToken(PUBLIC_ID, ROLE, TOKEN_VERSION);
        StompHeaderAccessor accessor = connectAccessor("access_token", refreshToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        stubRepositories(refreshToken);

        // when & then
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("CONNECT에 token_type 클레임이 없는 기존 토큰을 넣으면 AccessDeniedException이 발생한다")
    void connect_WithLegacyTokenWithoutTokenType_Denied() {
        // given
        String legacyToken = buildLegacyToken();
        StompHeaderAccessor accessor = connectAccessor("Authorization", "Bearer " + legacyToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        stubRepositories(legacyToken);

        // when & then
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    @DisplayName("CONNECT에 만료된 Access Token을 넣으면 기존처럼 TOKEN_EXPIRED BusinessException이 전파된다")
    void connect_WithExpiredAccessToken_ThrowsTokenExpired() {
        // given
        JwtTokenProvider expiredAccessTokenProvider = new JwtTokenProvider(
                new JwtProperties(SECRET, -60_000L, REFRESH_TOKEN_EXPIRATION));
        String expiredAccessToken = expiredAccessTokenProvider.createAccessToken(PUBLIC_ID, ROLE, TOKEN_VERSION);
        StompHeaderAccessor accessor = connectAccessor("Authorization", "Bearer " + expiredAccessToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        when(blacklistTokenRepository.exists(expiredAccessToken)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
        assertThat(accessor.getUser()).isNull();
    }

    private String buildLegacyToken() {
        SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(PUBLIC_ID)
                .claim("role", ROLE.name())
                .claim("tokenVersion", TOKEN_VERSION)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TOKEN_EXPIRATION))
                .signWith(secretKey)
                .compact();
    }

    private StompHeaderAccessor connectAccessor(String headerName, String headerValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(headerName, headerValue);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private void stubRepositories(String token) {
        when(blacklistTokenRepository.exists(token)).thenReturn(false);
        when(tokenVersionRepository.getCurrentVersion(PUBLIC_ID, ROLE)).thenReturn(TOKEN_VERSION);
    }
}
