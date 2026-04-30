package com.weiver.auth.service;

import com.weiver.auth.service.dto.TokenReissueResult;
import com.weiver.auth.validator.AuthUserValidator;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.type.RefreshTokenRotationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final BlacklistTokenRepository blacklistTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthUserValidator authUserValidator;

    @Override
    public void logout(String accessToken) {

        Long userId = jwtTokenProvider.getUserId(accessToken);
        UserRole userRole = jwtTokenProvider.getRole(accessToken);
        long ttlMillis = jwtTokenProvider.getRemainingExpiration(accessToken);

        blacklistTokenRepository.save(accessToken, ttlMillis);
        refreshTokenRepository.deleteByUserId(userId, userRole);
    }

    @Override
    public TokenReissueResult reissueToken(String refreshToken) {
        if(refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        jwtTokenProvider.validateRefreshToken(refreshToken);

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        UserRole userRole = jwtTokenProvider.getRole(refreshToken);

        authUserValidator.validateExist(userId, userRole);

        String newAccessToken = jwtTokenProvider.createAccessToken(userId, userRole);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId, userRole);
        long refreshTokenTtlMillis = jwtTokenProvider.getRemainingExpiration(refreshToken);

        RefreshTokenRotationResult rotationResult = refreshTokenRepository.rotateIfMatches(
                userId,
                userRole,
                refreshToken,
                newRefreshToken,
                refreshTokenTtlMillis
        );

        return switch (rotationResult) {
            case ROTATED -> new TokenReissueResult(newAccessToken, newRefreshToken);
            case NOT_FOUND -> throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
            case MISMATCH, CONCURRENT_MODIFIED -> {
                refreshTokenRepository.deleteByUserId(userId, userRole);
                throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
            }
        };
    }
}
