package com.weiver.auth.service;

import com.weiver.auth.service.dto.TokenReissueResult;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import com.weiver.global.security.jwt.type.RefreshTokenRotationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final BlacklistTokenRepository blacklistTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenVersionRepository tokenVersionRepository;

    @Override
    public void logout(String accessToken) {

        if(accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String publicId;
        UserRole userRole;
        long ttlMillis;
        try {
            publicId = jwtTokenProvider.getPublicId(accessToken);
            userRole = jwtTokenProvider.getRole(accessToken);
            ttlMillis = jwtTokenProvider.getRemainingExpiration(accessToken);
        } catch (BusinessException e) {
            // 만료/서명 오류 등 무효 토큰은 클레임을 신뢰할 수 없어 블랙리스트 등록·리프레시 삭제가 무의미하다.
            // 로그아웃은 무효 토큰이어도 안전하게 종료되는 편이 낫기 때문에 관대하게 처리한다.
            log.warn("[Logout] 무효 토큰으로 로그아웃 요청되어 관대하게 종료합니다. errorCode={}", e.getCode());
            return;
        }

        blacklistTokenRepository.save(accessToken, ttlMillis);
        refreshTokenRepository.deleteByPublicId(publicId, userRole);
    }

    @Override
    public TokenReissueResult reissueToken(String refreshToken) {
        if(refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        jwtTokenProvider.validateRefreshToken(refreshToken);

        String publicId = jwtTokenProvider.getPublicId(refreshToken);
        UserRole userRole = jwtTokenProvider.getRole(refreshToken);

        long tokenVersion = tokenVersionRepository.getCurrentVersion(publicId, userRole);

        String newAccessToken = jwtTokenProvider.createAccessToken(publicId, userRole, tokenVersion);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(publicId, userRole, tokenVersion);
        long refreshTokenTtlMillis = jwtTokenProvider.getRefreshTokenExpirationMillis();

        RefreshTokenRotationResult rotationResult = refreshTokenRepository.rotateIfMatches(
                publicId,
                userRole,
                refreshToken,
                newRefreshToken,
                refreshTokenTtlMillis
        );

        return switch (rotationResult) {
            case ROTATED -> new TokenReissueResult(newAccessToken, newRefreshToken);
            case NOT_FOUND -> throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
            case MISMATCH, CONCURRENT_MODIFIED -> {
                refreshTokenRepository.deleteByPublicId(publicId, userRole);
                tokenVersionRepository.increaseVersion(publicId, userRole);
                throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
            }
        };
    }
}
