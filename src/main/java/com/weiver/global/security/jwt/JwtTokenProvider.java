package com.weiver.global.security.jwt;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.common.UserRole;
import com.weiver.global.security.jwt.type.TokenType;
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

    private static final String TOKEN_TYPE_CLAIM = "token_type";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String createAccessToken(String publicId, UserRole role, long tokenVersion) {
        return createToken(publicId, role, tokenVersion, jwtProperties.accessTokenExpiration(), TokenType.ACCESS);
    }

    public String createRefreshToken(String publicId, UserRole role, long tokenVersion) {
        return createToken(publicId, role, tokenVersion, jwtProperties.refreshTokenExpiration(), TokenType.REFRESH);
    }

    /**
     * token_type 클레임이 access인지 확인한다. 클레임이 없거나 다른 값이면 false.
     * 만료·위조 토큰은 다른 조회 메서드와 같이 BusinessException을 던진다.
     */
    public boolean isAccessToken(String token) {
        return TokenType.ACCESS.getClaimValue().equals(getClaims(token).get(TOKEN_TYPE_CLAIM));
    }

    /**
     * Access Token이 아니면 INVALID_TOKEN 예외를 던진다.
     */
    public void validateAccessToken(String token) {
        if(!isAccessToken(token)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
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

    public String getPublicId(String token) {
        String subject = getClaims(token).getSubject();

        if(subject == null || subject.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        return subject;
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

    private String createToken(String publicId, UserRole role, long tokenVersion, long expirationMillis, TokenType tokenType) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(publicId)
                .claim("role", role.name())
                .claim("tokenVersion", tokenVersion)
                .claim(TOKEN_TYPE_CLAIM, tokenType.getClaimValue())
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
