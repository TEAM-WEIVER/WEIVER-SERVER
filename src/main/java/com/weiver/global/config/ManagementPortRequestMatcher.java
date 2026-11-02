package com.weiver.global.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 요청이 앱 포트와 분리된 actuator 관리 포트(management.server.port)로 들어왔을 때만 매칭한다.
 * 관리 포트가 없거나(-1), 실제 포트를 알 수 없거나(0), 앱 포트(server.port)와 같으면 어떤 요청도 매칭하지 않는다.
 * 그래서 관리 포트 설정이 빠지거나 앱 포트로 덮어써지면 지표 경로는 공개되지 않고 기존 인증 규칙을 따른다.
 */
public class ManagementPortRequestMatcher implements RequestMatcher {

    public static final int NOT_CONFIGURED = -1;

    private final int managementPort;
    private final int serverPort;

    public ManagementPortRequestMatcher(int managementPort, int serverPort) {
        this.managementPort = managementPort;
        this.serverPort = serverPort;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return managementPort > 0
                && managementPort != serverPort
                && request.getLocalPort() == managementPort;
    }
}
