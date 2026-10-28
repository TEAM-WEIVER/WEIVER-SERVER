package com.weiver.global.config;

import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.csrf.CsrfCookieFilter;
import com.weiver.global.security.csrf.CsrfCookieProperties;
import com.weiver.global.security.handler.SecurityErrorResponseWriter;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CsrfCookieProperties.class)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;
    private final CsrfCookieFilter csrfCookieFilter;
    private final CsrfCookieProperties csrfCookieProperties;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            @Value("${management.server.port:" + ManagementPortRequestMatcher.NOT_CONFIGURED + "}") int managementPort
    ) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));

        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();

        http.csrf(csrf -> csrf
                .csrfTokenRepository(cookieCsrfTokenRepository())
                .csrfTokenRequestHandler(requestHandler)
                .ignoringRequestMatchers(
                        Stream.concat(
                                Stream.concat(
                                        WhiteListConfig.applicantAuthWhitelist().stream(),
                                        WhiteListConfig.companyAuthWhitelist().stream()
                                ),
                                Stream.of(
                                        "/api/auth/applicants/password",
                                        "/ws", "/ws/**", "/ws-sockjs", "/ws-sockjs/**"
                                )
                        ).toArray(String[]::new)
                )
        );
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.logout(AbstractHttpConfigurer::disable);

        http.sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Spring Security가 직접 막는 인증/인가 실패 상황에서도 ErrorResponse 형식으로 응답을 통일
        http.exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            securityErrorResponseWriter.write(response, request, ErrorCode.UNAUTHORIZED);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            logAccessDenied(request, accessDeniedException);
                            securityErrorResponseWriter.write(response, request, ErrorCode.FORBIDDEN);
                        })
                );

        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(WhiteListConfig.swaggerWhitelist().toArray(String[]::new)).permitAll()
                        .requestMatchers(WhiteListConfig.serverWhitelist().toArray(String[]::new)).permitAll()
                        .requestMatchers(managementPortWhitelistMatcher(managementPort)).permitAll()
                        .requestMatchers(WhiteListConfig.authWhitelist().toArray(String[]::new)).permitAll()
                        .requestMatchers("/ws").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/ws-sockjs").permitAll()
                        .requestMatchers("/ws-sockjs/**").permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                WhiteListConfig.applicantAuthWhitelist().toArray(String[]::new)
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                WhiteListConfig.companyAuthWhitelist().toArray(String[]::new)
                        ).permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/auth/applicants/password").permitAll()

                        .anyRequest().authenticated()
                );

        http.addFilterAfter(csrfCookieFilter, CsrfFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 관리 포트로 들어온 요청이면서 관리 포트 허용 경로일 때만 매칭한다.
     * 앱 포트로 들어온 같은 경로 요청은 매칭하지 않아 anyRequest().authenticated()를 따른다.
     */
    private RequestMatcher managementPortWhitelistMatcher(int managementPort) {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> pathMatchers = WhiteListConfig.managementPortWhitelist().stream()
                .map(paths::matcher)
                .map(RequestMatcher.class::cast)
                .toList();

        return new AndRequestMatcher(
                new ManagementPortRequestMatcher(managementPort),
                new OrRequestMatcher(pathMatchers)
        );
    }

    @Bean
    public CookieCsrfTokenRepository cookieCsrfTokenRepository() {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();

        repository.setCookieName(csrfCookieProperties.cookieName());
        repository.setHeaderName(csrfCookieProperties.headerName());

        repository.setCookieCustomizer(cookie -> {
            cookie.httpOnly(false)
                    .secure(csrfCookieProperties.secure())
                    .sameSite(csrfCookieProperties.sameSite())
                    .path(csrfCookieProperties.path());

            String domain = csrfCookieProperties.domain();
            if (domain != null && !domain.isBlank()) {
                cookie.domain(domain);
            }
        });

        return repository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void logAccessDenied(HttpServletRequest request, Exception exception) {
        String csrfCookie = findCookieValue(request, csrfCookieProperties.cookieName());
        String csrfHeader = request.getHeader(csrfCookieProperties.headerName());

        if (exception instanceof CsrfException) {
            log.warn(
                    "[SecurityAccessDenied][CSRF] type={}, method={}, path={}, origin={}, referer={}, csrfCookiePresent={}, csrfCookieLength={}, csrfHeaderPresent={}, csrfHeaderLength={}, cookieHeaderPresent={}",
                    exception.getClass().getSimpleName(),
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getHeader("Origin"),
                    request.getHeader("Referer"),
                    csrfCookie != null,
                    csrfCookie != null ? csrfCookie.length() : 0,
                    csrfHeader != null,
                    csrfHeader != null ? csrfHeader.length() : 0,
                    request.getHeader("Cookie") != null
            );
            return;
        }

        log.warn(
                "[SecurityAccessDenied] type={}, method={}, path={}, origin={}, referer={}",
                exception.getClass().getSimpleName(),
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader("Origin"),
                request.getHeader("Referer")
        );
    }

    private String findCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
