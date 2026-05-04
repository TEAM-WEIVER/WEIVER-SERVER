package com.weiver.global.security.jwt;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.common.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String createAccessToken(Long userId, UserRole role, long tokenVersion) {
        return createToken(userId, role, tokenVersion, jwtProperties.accessTokenExpiration());
    }

    public String createRefreshToken(Long userId, UserRole role, long tokenVersion) {
        return createToken(userId, role, tokenVersion, jwtProperties.refreshTokenExpiration());
    }

    public void validateRefreshToken(String token) {
        try {
            getClaims(token);
        } catch (BusinessException e) {
            if(e.getCode() == ErrorCode.TOKEN_EXPIRED) {
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
            }
            throw e;
        }
    }

    public long getRemainingExpiration(String token) {
        Date expiration = getClaims(token).getExpiration();

        return expiration.getTime() - System.currentTimeMillis();
    }

    public long getRefreshTokenExpirationMillis() {
        return jwtProperties.refreshTokenExpiration();
    }

    public Long getUserId(String token) {
        String subject = getClaims(token).getSubject();

        if(subject == null || subject.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        return Long.valueOf(subject);
    }

    public UserRole getRole(String token) {
        return UserRole.valueOf(getClaims(token).get("role", String.class));
    }

    public long getTokenVersion(String token) {
        Long tokenVersion = getClaims(token).get("tokenVersion", Long.class);

        if(tokenVersion == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        return tokenVersion;
    }

    private String createToken(Long userId, UserRole role, long tokenVersion, long expirationMillis) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    private Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

    }
}
