package com.weiver.global.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 요청이 actuator 관리 포트(management.server.port)로 들어왔을 때만 매칭한다.
 * 관리 포트가 없거나(-1) 실제 포트를 알 수 없으면(0) 어떤 요청도 매칭하지 않는다.
 * 그래서 관리 포트 설정이 빠지면 지표 경로는 공개되지 않고 기존 인증 규칙을 따른다.
 */
public class ManagementPortRequestMatcher implements RequestMatcher {

    public static final int NOT_CONFIGURED = -1;

    private final int managementPort;

    public ManagementPortRequestMatcher(int managementPort) {
        this.managementPort = managementPort;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return managementPort > 0 && request.getLocalPort() == managementPort;
    }
}
