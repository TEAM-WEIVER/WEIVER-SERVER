package com.weiver.domain.auth.service;

import com.weiver.auth.service.AuthServiceImpl;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("정상 로그아웃 시 AccessToken을 블랙리스트에 등록하고 RefreshToken을 삭제")
    public void logoutSuccess() {
        // given
        String accessToken = "validAccessToken";
        Long userId = 1L;
        long ttlMillis = 1000L * 60 * 10;

        when(jwtTokenProvider.getUserId(accessToken)).thenReturn(userId);
        when(jwtTokenProvider.getRemainingExpiration(accessToken)).thenReturn(ttlMillis);

        // when
        authService.logout(accessToken);

        // then
        verify(jwtTokenProvider).getUserId(accessToken);
        verify(jwtTokenProvider).getRemainingExpiration(accessToken);
        verify(blacklistTokenRepository).save(accessToken, ttlMillis);
        verify(refreshTokenRepository).deleteByUserId(userId);
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
        verify(jwtTokenProvider, never()).getRemainingExpiration(anyString());
        verify(blacklistTokenRepository, never()).save(anyString(), anyLong());
        verify(refreshTokenRepository, never()).deleteByUserId(anyLong());
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
        verify(jwtTokenProvider, never()).getRemainingExpiration(anyString());
        verify(blacklistTokenRepository, never()).save(anyString(), anyLong());
        verify(refreshTokenRepository, never()).deleteByUserId(anyLong());
    }
}
