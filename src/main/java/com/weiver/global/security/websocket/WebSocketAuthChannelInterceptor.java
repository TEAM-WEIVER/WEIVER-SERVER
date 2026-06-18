package com.weiver.global.security.websocket;

import com.weiver.global.common.UserRole;
import com.weiver.global.security.jwt.BearerTokenResolver;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String ACCESS_TOKEN_HEADER = "access_token";

    private final JwtTokenProvider jwtTokenProvider;
    private final BlacklistTokenRepository blacklistTokenRepository;
    private final TokenVersionRepository tokenVersionRepository;
    private final BearerTokenResolver bearerTokenResolver;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String accessToken = resolveAccessToken(accessor);
        if (accessToken == null) {
            throw new AccessDeniedException("WebSocket access token is required");
        }
        if (blacklistTokenRepository.exists(accessToken)) {
            throw new AccessDeniedException("Blacklisted WebSocket access token");
        }

        String publicId = jwtTokenProvider.getPublicId(accessToken);
        UserRole userRole = jwtTokenProvider.getRole(accessToken);
        long tokenVersion = jwtTokenProvider.getTokenVersion(accessToken);
        long currentTokenVersion = tokenVersionRepository.getCurrentVersion(publicId, userRole);

        if (tokenVersion != currentTokenVersion) {
            throw new AccessDeniedException("Invalid WebSocket access token version");
        }

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(publicId, userRole);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + userRole.name()))
        );

        accessor.setUser(authentication);
        return message;
    }

    private String resolveAccessToken(StompHeaderAccessor accessor) {
        String authorizationHeader = firstNativeHeader(accessor, HttpHeaders.AUTHORIZATION);
        String accessToken = bearerTokenResolver.resolve(authorizationHeader);
        if (accessToken != null) {
            return accessToken;
        }

        return firstNativeHeader(accessor, ACCESS_TOKEN_HEADER);
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String headerName) {
        List<String> values = accessor.getNativeHeader(headerName);
        if (values == null || values.isEmpty()) {
            return null;
        }

        String value = values.get(0);
        return value != null && !value.isBlank() ? value : null;
    }
}
