package com.weiver.auth.service;

import com.weiver.auth.repository.KakaoOAuthTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class KakaoOAuthServiceTest {

    private static final String BROWSER_ID = "12345678-1234-1234-1234-123456789abc";
    private static final String LOCAL_STATE = "aaaaaaaa-1234-1234-1234-123456789abc";
    private static final String LEGACY_STATE = "bbbbbbbb-1234-1234-1234-123456789abc";
    private static final URI DEFAULT_RETURN = URI.create("https://weiverai.com/auth/kakao/return");
    private static final URI LOCAL_RETURN = URI.create("https://weiver.local.weiverai.com:3000/auth/kakao/return");

    @Mock private KakaoOAuthClient client;
    @Mock private KakaoOAuthProperties properties;
    @Mock private KakaoApplicantAccountService accounts;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> values;

    private KakaoOAuthTicketRepository tickets;
    private KakaoOAuthService service;

    @BeforeEach
    void setUp() {
        Map<String, String> redisEntries = new HashMap<>();
        given(redisTemplate.opsForValue()).willReturn(values);
        willAnswer(invocation -> {
            redisEntries.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).given(values).set(anyString(), anyString(), any(Duration.class));
        given(values.getAndDelete(anyString())).willAnswer(invocation -> redisEntries.remove(invocation.getArgument(0)));

        tickets = new KakaoOAuthTicketRepository(redisTemplate, Jackson2ObjectMapperBuilder.json().build());
        service = new KakaoOAuthService(client, properties, tickets, accounts);
    }

    @Test
    @DisplayName("한 브라우저에서 로컬 티켓과 구형 티켓은 각각 지정된 복귀 주소로 돌아간다")
    void callback_ShouldUseReturnAddressFromEachState() {
        // given
        given(properties.frontendReturnUri()).willReturn(DEFAULT_RETURN);
        given(properties.localFrontendReturnUri()).willReturn(LOCAL_RETURN);
        tickets.save("state", BROWSER_ID, LOCAL_STATE,
                Map.of("purpose", "LOGIN", "flowId", "local-flow", "localReturn", true), Duration.ofMinutes(5));
        tickets.save("state", BROWSER_ID, LEGACY_STATE,
                Map.of("purpose", "LOGIN", "flowId", "legacy-flow"), Duration.ofMinutes(5));

        // when
        var legacyResult = service.callback(BROWSER_ID, LEGACY_STATE, null, "access_denied");
        var localResult = service.callback(BROWSER_ID, LOCAL_STATE, null, "access_denied");

        // then
        assertThat(legacyResult.returnUri()).isEqualTo(DEFAULT_RETURN);
        assertThat(legacyResult.flowId()).isEqualTo("legacy-flow");
        assertThat(localResult.returnUri()).isEqualTo(LOCAL_RETURN);
        assertThat(localResult.flowId()).isEqualTo("local-flow");
    }
}
