package com.weiver.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.auth.dto.KakaoProfile;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
@EnableConfigurationProperties(KakaoOAuthProperties.class)
public class KakaoOAuthClient {

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final KakaoOAuthProperties properties;
    private final RestClient restClient;

    @Autowired
    public KakaoOAuthClient(KakaoOAuthProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory()).build());
    }

    KakaoOAuthClient(KakaoOAuthProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public URI authorizationUri(String state, boolean reauthenticate) {
        properties.requireLoginConfiguration();
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri().toString())
                .queryParam("state", state);
        if (reauthenticate) {
            uri.queryParam("prompt", "login");
        }
        return uri.build().encode().toUri();
    }

    public KakaoProfile fetchUser(String code) {
        properties.requireLoginConfiguration();
        var body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", properties.clientId());
        body.add("client_secret", properties.clientSecret());
        body.add("redirect_uri", properties.redirectUri().toString());
        body.add("code", code);

        try {
            TokenResponse token = restClient.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body).retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw providerError();
                    })
                    .body(TokenResponse.class);
            if (token == null || !StringUtils.hasText(token.accessToken())) {
                throw providerError();
            }

            URI userInfoUri = UriComponentsBuilder.fromUriString(USER_INFO_URL)
                    .queryParam("property_keys", "[\"kakao_account.email\"]")
                    .build().encode().toUri();
            UserResponse user = restClient.get().uri(userInfoUri)
                    .headers(headers -> headers.setBearerAuth(token.accessToken()))
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw providerError();
                    })
                    .body(UserResponse.class);
            if (user == null || user.id() == null || user.id() <= 0) {
                throw providerError();
            }
            return new KakaoProfile(user.id(), user.account() == null ? null : user.account().verifiedEmail());
        } catch (RestClientException e) {
            throw providerError();
        }
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    private static BusinessException providerError() {
        return new BusinessException(ErrorCode.KAKAO_PROVIDER_ERROR);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount account) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAccount(
            String email,
            @JsonProperty("is_email_valid") Boolean emailValid,
            @JsonProperty("is_email_verified") Boolean emailVerified
    ) {
        String verifiedEmail() {
            if (Boolean.TRUE.equals(emailValid) && Boolean.TRUE.equals(emailVerified) && StringUtils.hasText(email)) {
                return email;
            }
            return null;
        }
    }
}
