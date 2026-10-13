package com.weiver.global.security.csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.util.Locale;

/** Selects a cookie domain from configured API hosts, never from the Origin header. */
public class HostAwareCsrfTokenRepository implements CsrfTokenRepository {

    private final CsrfCookieProperties properties;

    public HostAwareCsrfTokenRepository(CsrfCookieProperties properties) {
        this.properties = properties;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return repository(request).generateToken(request);
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return repository(request).loadToken(request);
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        repository(request).saveToken(token, request, response);
    }

    private CookieCsrfTokenRepository repository(HttpServletRequest request) {
        String host = request.getServerName().toLowerCase(Locale.ROOT);
        String domain = properties.domains() == null
                ? properties.domain()
                : properties.domains().getOrDefault(host, properties.domain());

        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookieName(properties.cookieName());
        repository.setHeaderName(properties.headerName());
        repository.setCookieCustomizer(cookie -> {
            cookie.httpOnly(false)
                    .secure(properties.secure())
                    .sameSite(properties.sameSite())
                    .path(properties.path());
            if (domain != null && !domain.isBlank()) {
                cookie.domain(domain);
            }
        });
        return repository;
    }
}
