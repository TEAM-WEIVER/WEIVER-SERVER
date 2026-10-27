package com.weiver.global.config;

import com.weiver.global.security.websocket.WebSocketAuthChannelInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebSocketConfig의 두 STOMP 엔드포인트(/ws, /ws-sockjs) 허용 출처를 검증한다.
 * 실제 WebSocketConfig 빈으로 만든 핸들러 매핑에 MockMvc로 핸드셰이크 요청을 보낸다.
 */
@SpringJUnitWebConfig(WebSocketConfigOriginTest.TestConfig.class)
class WebSocketConfigOriginTest {

    @Configuration
    @Import(WebSocketConfig.class)
    static class TestConfig {
    }

    @MockitoBean
    private WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://weiverai.com", "https://www.weiverai.com", "https://weiver.local.weiverai.com:3000"})
    @DisplayName("SockJS 엔드포인트는 허용한 출처의 요청을 받는다")
    void sockJsEndpoint_AllowedOrigin_Accepts(String origin) throws Exception {
        // given
        String path = "/ws-sockjs/info";

        // when
        ResultActions result = mockMvc.perform(get(path).header(HttpHeaders.ORIGIN, origin));

        // then
        result.andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000", "https://evil.example"})
    @DisplayName("SockJS 엔드포인트는 제거했거나 등록되지 않은 출처의 요청을 거부한다")
    void sockJsEndpoint_DisallowedOrigin_Rejects(String origin) throws Exception {
        // given
        String path = "/ws-sockjs/info";

        // when
        ResultActions result = mockMvc.perform(get(path).header(HttpHeaders.ORIGIN, origin));

        // then
        result.andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://weiverai.com", "https://www.weiverai.com", "https://weiver.local.weiverai.com:3000"})
    @DisplayName("WS 엔드포인트는 허용한 출처의 핸드셰이크를 출처 검사에서 막지 않는다")
    void wsEndpoint_AllowedOrigin_PassesOriginCheck(String origin) throws Exception {
        // given
        // MockMvc에는 실제 WebSocket 컨테이너가 없어 업그레이드까지는 못 간다.
        // 출처 검사(OriginHandshakeInterceptor)를 통과하면 업그레이드 헤더가 없는 요청이라
        // 핸드셰이크 핸들러가 400을 돌려준다. 출처에서 막히면 403이다.
        String path = "/ws";

        // when
        ResultActions result = mockMvc.perform(get(path).header(HttpHeaders.ORIGIN, origin));

        // then
        result.andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000", "https://evil.example"})
    @DisplayName("WS 엔드포인트는 제거했거나 등록되지 않은 출처의 핸드셰이크를 거부한다")
    void wsEndpoint_DisallowedOrigin_Rejects(String origin) throws Exception {
        // given
        String path = "/ws";

        // when
        ResultActions result = mockMvc.perform(get(path).header(HttpHeaders.ORIGIN, origin));

        // then
        result.andExpect(status().isForbidden());
    }
}
