package com.weiver.global.security.jwt;

import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.handler.SecurityErrorResponseWriter;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
        verify(jwtTokenProvider, never()).getUserId(anyString());
    }
}
