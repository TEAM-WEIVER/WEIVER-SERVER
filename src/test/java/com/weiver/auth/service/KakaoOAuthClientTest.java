package com.weiver.auth.service;

import com.weiver.auth.dto.KakaoProfile;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoOAuthClientTest {

    private static final URI CALLBACK = URI.create("https://api.weiverai.com/api/auth/applicants/kakao/callback");
    private static final URI FRONTEND_RETURN = URI.create("https://weiverai.com/auth/kakao/return");

    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoOAuthClient(new KakaoOAuthProperties("mock-client", "mock-secret", CALLBACK, FRONTEND_RETURN),
                builder.build());
    }

    @Test
    void reauthAsksKakaoToPromptForLogin() {
        URI loginUri = client.authorizationUri("mock-state", false);
        URI reauthUri = client.authorizationUri("mock-state", true);

        assertThat(loginUri.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(loginUri.getQuery()).contains("state=mock-state").doesNotContain("prompt=", "mock-secret");
        assertThat(reauthUri.getQuery()).contains("prompt=login");
    }

    @Test
    void fetchUserExchangesCodeAndOnlyReturnsVerifiedEmail() {
        mockTokenExchange();
        mockProfile("""
                {"id":123456789,"connected_at":"unused","kakao_account":{
                  "email":"verified@example.com","is_email_valid":true,"is_email_verified":true}}
                """);

        assertThat(client.fetchUser("mock-code")).isEqualTo(new KakaoProfile(123456789L, "verified@example.com"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"id\":123456789}",
            "{\"id\":123456789,\"kakao_account\":{\"email\":\"mock@example.com\",\"is_email_valid\":true,\"is_email_verified\":false}}",
            "{\"id\":123456789,\"kakao_account\":{\"email\":\"mock@example.com\",\"is_email_valid\":false,\"is_email_verified\":true}}",
            "{\"id\":123456789,\"kakao_account\":{\"email\":\"mock@example.com\",\"is_email_valid\":true}}"
    })
    void missingOrUnverifiedEmailIsOmitted(String response) {
        mockTokenExchange();
        mockProfile(response);

        assertThat(client.fetchUser("mock-code")).isEqualTo(new KakaoProfile(123456789L, null));
        server.verify();
    }

    @Test
    void providerErrorBodyAndCredentialsAreNotPropagated() {
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"error_description\":\"mock-code mock-secret\"}"));

        assertThatThrownBy(() -> client.fetchUser("mock-code"))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.KAKAO_PROVIDER_ERROR);
                    assertThat(error.getMessage()).doesNotContain("mock-code", "mock-secret");
                    assertThat(error.getCause()).isNull();
                });
        server.verify();
    }

    private void mockTokenExchange() {
        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", "mock-client");
        form.add("client_secret", "mock-secret");
        form.add("redirect_uri", CALLBACK.toString());
        form.add("code", "mock-code");

        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(form))
                .andRespond(withSuccess("{\"access_token\":\"mock-provider-token\",\"refresh_token\":\"ignored\"}",
                        MediaType.APPLICATION_JSON));
    }

    private void mockProfile(String response) {
        server.expect(requestToUriTemplate("https://kapi.kakao.com/v2/user/me?property_keys={keys}",
                        "[\"kakao_account.email\"]"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("property_keys", "%5B%22kakao_account.email%22%5D"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-provider-token"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }
}
