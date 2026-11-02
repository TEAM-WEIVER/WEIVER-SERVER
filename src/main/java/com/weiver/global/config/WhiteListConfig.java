package com.weiver.global.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WhiteListConfig {

    // Swagger 관련 인가 설정
    public static List<String> swaggerWhitelist() {
        return List.of(
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html"
        );
    }

    // 인증 없이 접근 가능한 공통 Auth API
    public static List<String> authWhitelist() {
        return List.of(
                "/api/auth/reissue",
                "/api/auth/csrf"
        );
    }

    // 구직자 인증/회원가입 관련 인가 설정
    public static List<String> applicantAuthWhitelist() {
        return List.of(
                "/api/auth/applicants/email/send",
                "/api/auth/applicants/email/verify",
                "/api/auth/applicants/signup/init",
                "/api/auth/applicants/signup/agreements",
                "/api/auth/applicants/login",
                "/api/auth/applicants/password/email/send",
                "/api/auth/applicants/password/email/verify"
        );
    }

    // 기업 인증 관련 인가 설정
    public static List<String> companyAuthWhitelist() {
        return List.of(
                "/api/auth/companies/login"
        );
    }

    // 서버 상태 확인, 운영 편의 API
    public static List<String> serverWhitelist() {
        return List.of(
                "/actuator/health"
        );
    }

    // 관리 포트(management.server.port)로 들어온 요청에만 허용하는 운영 지표 경로
    public static List<String> managementPortWhitelist() {
        return List.of(
                "/actuator/prometheus"
        );
    }
}
