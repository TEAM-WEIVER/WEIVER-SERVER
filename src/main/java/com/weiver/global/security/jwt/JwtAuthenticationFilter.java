package com.weiver.global.security.jwt;

import com.weiver.global.config.WhiteListConfig;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.common.UserRole;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.global.security.handler.SecurityErrorResponseWriter;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> WHITELIST_PATHS = buildWhitelistPaths();

    private final JwtTokenProvider jwtTokenProvider;
    private final BlacklistTokenRepository blacklistTokenRepository;
    private final TokenVersionRepository tokenVersionRepository;
    private final BearerTokenResolver bearerTokenResolver;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    private static List<String> buildWhitelistPaths() {
        List<String> paths = new ArrayList<>();
        paths.addAll(WhiteListConfig.swaggerWhitelist());
        paths.addAll(WhiteListConfig.authWhitelist());
        paths.addAll(WhiteListConfig.applicantAuthWhitelist());
        paths.addAll(WhiteListConfig.companyAuthWhitelist());
        paths.addAll(WhiteListConfig.serverWhitelist());
        return List.copyOf(paths);
    }

    /**
     * permitAll 화이트리스트 경로는 토큰 검증을 건너뛴다.
     * 만료/무효 토큰을 실은 재발급 요청(/api/auth/reissue 등)이 401로 막히지 않도록 한다.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return WHITELIST_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String accessToken = bearerTokenResolver.resolve(request);

            if(accessToken == null) {
                filterChain.doFilter(request, response);
                return;
            }

            if(blacklistTokenRepository.exists(accessToken)) {
                securityErrorResponseWriter.write(response, request, ErrorCode.BLACKLISTED_TOKEN);
                return;
            }

            String publicId = jwtTokenProvider.getPublicId(accessToken);
            UserRole userRole = jwtTokenProvider.getRole(accessToken);
            long tokenVersion = jwtTokenProvider.getTokenVersion(accessToken);

            long currentTokenVersion = tokenVersionRepository.getCurrentVersion(publicId, userRole);

            if(tokenVersion != currentTokenVersion) {
                securityErrorResponseWriter.write(response, request, ErrorCode.INVALID_TOKEN);
                return;
            }

            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(publicId, userRole);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + userRole.name())));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (BusinessException e) {
            SecurityContextHolder.clearContext();
            securityErrorResponseWriter.write(response, request, e.getCode());
        }
    }
}
