package com.weiver.auth.service;

import com.weiver.global.common.UserRole;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final BlacklistTokenRepository blacklistTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public void logout(String accessToken) {

        Long userId = jwtTokenProvider.getUserId(accessToken);
        UserRole userRole = jwtTokenProvider.getRole(accessToken);
        long ttlMillis = jwtTokenProvider.getRemainingExpiration(accessToken);

        blacklistTokenRepository.save(accessToken, ttlMillis);
        refreshTokenRepository.deleteByUserId(userRole, userId);
    }
}
