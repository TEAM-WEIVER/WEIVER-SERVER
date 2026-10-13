package com.weiver.global.security.csrf;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfFilter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class HostAwareCsrfTokenRepositoryTest {

    private final HostAwareCsrfTokenRepository repository = new HostAwareCsrfTokenRepository(
            new CsrfCookieProperties("XSRF-TOKEN", "X-XSRF-TOKEN", "/", "piuda.site", true, "None",
                    Map.of("api.piuda.site", "piuda.site", "api.weiverai.com", "weiverai.com")));

    @Test
    void productionConfigurationBindsExactHostDomainMapping() {
        var yaml = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        yaml.setResources(new org.springframework.core.io.ClassPathResource("application-prod.yaml"));
        var properties = yaml.getObject();
        var values = new java.util.HashMap<String, Object>();
        properties.forEach((key, value) -> values.put(key.toString(), value));
        var binder = new org.springframework.boot.context.properties.bind.Binder(
                new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(values));
        var bound = binder.bind("cookie.csrf-token", CsrfCookieProperties.class).get();
        assertThat(bound.domains()).containsEntry("api.piuda.site", "piuda.site")
                .containsEntry("api.weiverai.com", "weiverai.com");
        assertThat(bound.domain()).isEqualTo("piuda.site");
    }

    @ParameterizedTest
    @CsvSource({"api.piuda.site,piuda.site", "api.weiverai.com,weiverai.com", "API.WEIVERAI.COM,weiverai.com"})
    void issueAndClearCookieForApiHost(String host, String domain) {
        var request = new MockHttpServletRequest();
        request.setServerName(host);
        // A foreign Origin must not influence the cookie's domain.
        request.addHeader("Origin", "https://evil.example");
        var response = new MockHttpServletResponse();
        var token = repository.generateToken(request);
        repository.saveToken(token, request, response);
        assertThat(response.getHeader("Set-Cookie"))
                .contains("XSRF-TOKEN=" + token.getToken(), "Domain=" + domain, "Path=/", "Secure")
                .doesNotContain("HttpOnly", "evil.example");
        assertThat(response.getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("None");

        var cleared = new MockHttpServletResponse();
        repository.saveToken(null, request, cleared);
        assertThat(cleared.getHeader("Set-Cookie")).contains("Domain=" + domain, "Max-Age=0");
    }

    @ParameterizedTest
    @CsvSource({"api.piuda.site,true", "api.weiverai.com,true", "api.piuda.site,false", "api.weiverai.com,false"})
    void csrfFilterAcceptsMatchingTokenAndRejectsMismatch(String host, boolean matching) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/auth/reissue");
        request.setServerName(host);
        var token = repository.generateToken(request);
        request.setCookies(new Cookie("XSRF-TOKEN", token.getToken()));
        request.addHeader("X-XSRF-TOKEN", matching ? token.getToken() : "invalid");
        var response = new MockHttpServletResponse();
        var reachedApplication = new AtomicBoolean();
        var filter = new CsrfFilter(repository);
        filter.setRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler());
        filter.doFilter(request, response, (req, res) -> reachedApplication.set(true));
        assertThat(reachedApplication.get()).isEqualTo(matching);
        assertThat(response.getStatus()).isEqualTo(matching ? 200 : 403);
    }
}
