package com.weiver.global.security.jwt;

import com.weiver.global.common.UserRole;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.handler.SecurityErrorResponseWriter;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final BlacklistTokenRepository blacklistTokenRepository = mock(BlacklistTokenRepository.class);
    private final TokenVersionRepository tokenVersionRepository = mock(TokenVersionRepository.class);
    private final BearerTokenResolver bearerTokenResolver = mock(BearerTokenResolver.class);
    private final SecurityErrorResponseWriter securityErrorResponseWriter = mock(SecurityErrorResponseWriter.class);
    private final JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, blacklistTokenRepository, tokenVersionRepository, bearerTokenResolver, securityErrorResponseWriter);

    @Test
    @DisplayName("블랙리스트에 등록된 토큰이면 401 BLACKLISTED_TOKEN 응답 반환")
    public void blacklistTokenBlocked() throws Exception {
        // given
        String accessToken = "blacklistAccessToken";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        request.addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(bearerTokenResolver.resolve(request)).thenReturn(accessToken);
        when(blacklistTokenRepository.exists(accessToken)).thenReturn(true);

        // when
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // then
        verify(securityErrorResponseWriter).write(response, request, ErrorCode.BLACKLISTED_TOKEN);
        verify(blacklistTokenRepository).exists(accessToken);
        verify(jwtTokenProvider, never()).getPublicId(anyString());
    }

    /**
     * Issue #171 재현: 실제 JwtTokenProvider·BearerTokenResolver로 만든 토큰을 필터에 통과시킨다.
     * 저장소와 오류 응답 작성기만 mock으로 둔다.
     */
    @Nested
    @DisplayName("실제 토큰 종류별 인증 (Issue #171)")
    class RealTokenTypeTest {

        private static final String SECRET = "test-secret-key-for-jwt-token-provider-unit-test-1234567890";
        private static final long ACCESS_TOKEN_EXPIRATION = 1000L * 60 * 10;            // 10분
        private static final long REFRESH_TOKEN_EXPIRATION = 1000L * 60 * 60 * 24 * 14; // 14일
        private static final String PUBLIC_ID = "uuid-applicant-171";
        private static final UserRole ROLE = UserRole.APPLICANT;
        private static final long TOKEN_VERSION = 0L;

        private final JwtTokenProvider realJwtTokenProvider = new JwtTokenProvider(
                new JwtProperties(SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION));
        private final BearerTokenResolver realBearerTokenResolver = new BearerTokenResolver();
        private final BlacklistTokenRepository blacklistRepository = mock(BlacklistTokenRepository.class);
        private final TokenVersionRepository versionRepository = mock(TokenVersionRepository.class);
        private final SecurityErrorResponseWriter errorResponseWriter = mock(SecurityErrorResponseWriter.class);
        private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                realJwtTokenProvider, blacklistRepository, versionRepository, realBearerTokenResolver, errorResponseWriter);

        @AfterEach
        void tearDown() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("대조군: Access Token을 Bearer로 보내면 인증이 설정되고 다음 필터로 넘어간다")
        void accessToken_Authenticated() throws Exception {
            // given
            String accessToken = realJwtTokenProvider.createAccessToken(PUBLIC_ID, ROLE, TOKEN_VERSION);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/test");
            request.addHeader("Authorization", "Bearer " + accessToken);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            when(blacklistRepository.exists(accessToken)).thenReturn(false);
            when(versionRepository.getCurrentVersion(PUBLIC_ID, ROLE)).thenReturn(TOKEN_VERSION);

            // when
            filter.doFilter(request, response, filterChain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(filterChain.getRequest()).isNotNull();
            verify(errorResponseWriter, never()).write(any(), any(), any());
        }

        @Test
        @DisplayName("Refresh Token을 Bearer로 보내면 인증되지 않고 오류 응답을 쓴다")
        void refreshToken_Rejected() throws Exception {
            // given
            String refreshToken = realJwtTokenProvider.createRefreshToken(PUBLIC_ID, ROLE, TOKEN_VERSION);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/test");
            request.addHeader("Authorization", "Bearer " + refreshToken);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            when(blacklistRepository.exists(refreshToken)).thenReturn(false);
            when(versionRepository.getCurrentVersion(PUBLIC_ID, ROLE)).thenReturn(TOKEN_VERSION);

            // when
            filter.doFilter(request, response, filterChain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(filterChain.getRequest()).isNull();
            verify(errorResponseWriter).write(eq(response), eq(request), eq(ErrorCode.INVALID_TOKEN));
        }

        @Test
        @DisplayName("token_type 클레임이 없는 기존 토큰을 Bearer로 보내면 INVALID_TOKEN 오류 응답을 쓴다")
        void legacyTokenWithoutTokenType_Rejected() throws Exception {
            // given
            String legacyToken = buildLegacyToken();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/test");
            request.addHeader("Authorization", "Bearer " + legacyToken);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            when(blacklistRepository.exists(legacyToken)).thenReturn(false);
            when(versionRepository.getCurrentVersion(PUBLIC_ID, ROLE)).thenReturn(TOKEN_VERSION);

            // when
            filter.doFilter(request, response, filterChain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(filterChain.getRequest()).isNull();
            verify(errorResponseWriter).write(eq(response), eq(request), eq(ErrorCode.INVALID_TOKEN));
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
    }
}
