package com.weiver.global.config;

import com.weiver.global.security.websocket.WebSocketAuthChannelInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
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
    void SockJS_엔드포인트는_허용_출처의_요청을_받는다(String origin) throws Exception {
        mockMvc.perform(get("/ws-sockjs/info").header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000", "https://evil.example"})
    void SockJS_엔드포인트는_제거했거나_등록되지_않은_출처를_거부한다(String origin) throws Exception {
        mockMvc.perform(get("/ws-sockjs/info").header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://weiverai.com", "https://www.weiverai.com", "https://weiver.local.weiverai.com:3000"})
    void WS_엔드포인트는_허용_출처의_핸드셰이크를_출처_검사에서_막지_않는다(String origin) throws Exception {
        // MockMvc에는 실제 WebSocket 컨테이너가 없어 업그레이드까지는 못 간다.
        // 출처 검사(OriginHandshakeInterceptor)를 통과하면 업그레이드 헤더가 없는 요청이라
        // 핸드셰이크 핸들러가 400을 돌려준다. 출처에서 막히면 403이다.
        mockMvc.perform(get("/ws").header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000", "https://evil.example"})
    void WS_엔드포인트는_제거했거나_등록되지_않은_출처의_핸드셰이크를_거부한다(String origin) throws Exception {
        mockMvc.perform(get("/ws").header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isForbidden());
    }
}
