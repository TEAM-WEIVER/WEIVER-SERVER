package com.weiver.global.config;

import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.csrf.CsrfCookieFilter;
import com.weiver.global.security.csrf.CsrfCookieProperties;
import com.weiver.global.security.handler.SecurityErrorResponseWriter;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CsrfCookieProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;
    private final CsrfCookieFilter csrfCookieFilter;
    private final CsrfCookieProperties csrfCookieProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> {});
        http.csrf(csrf -> csrf
                .csrfTokenRepository(cookieCsrfTokenRepository())
                .ignoringRequestMatchers(
                        Stream.concat(
                                WhiteListConfig.applicantAuthWhitelist().stream(),
                                WhiteListConfig.companyAuthWhitelist().stream()
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
                            securityErrorResponseWriter.write(response, request, ErrorCode.FORBIDDEN);
                        })
                );

        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(WhiteListConfig.swaggerWhitelist().toArray(String[]::new)).permitAll()
                        .requestMatchers(WhiteListConfig.serverWhitelist().toArray(String[]::new)).permitAll()
                        .requestMatchers(WhiteListConfig.authWhitelist().toArray(String[]::new)).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                WhiteListConfig.applicantAuthWhitelist().toArray(String[]::new)
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                WhiteListConfig.companyAuthWhitelist().toArray(String[]::new)
                        ).permitAll()

                        .anyRequest().authenticated()
                );

        http.addFilterAfter(csrfCookieFilter, CsrfFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
}
