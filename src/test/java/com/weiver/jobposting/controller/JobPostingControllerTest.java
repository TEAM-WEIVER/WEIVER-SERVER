package com.weiver.jobposting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.common.UserRole;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.jobposting.dto.request.JobPostingRequestDTO;
import com.weiver.jobposting.dto.request.JobPostingUpdateDTO;
import com.weiver.jobposting.service.JobPostingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder; // 💡 핵심 임포트
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobPostingController.class)
@AutoConfigureMockMvc(addFilters = false)
class JobPostingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobPostingService jobPostingService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private CookieProvider cookieProvider;

    private RequestPostProcessor customAuth(String publicId) {
        return request -> {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(publicId, UserRole.COMPANY);
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY")));

            SecurityContextHolder.getContext().setAuthentication(auth);
            return request;
        };
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("공고 생성 컨트롤러: JSON 데이터와 이미지 파일이 함께 전송되어 성공")
    void saveJobPosting_Success() throws Exception {
        // given
        JobPostingRequestDTO requestDTO = new JobPostingRequestDTO(
                "2026년 상반기 프론트엔드 엔지니어 채용 (수정)",
                LocalDate.of(2026, 5, 31),
                "개발",
                "프론트엔드 개발자",
                "React 기반 대규모 사용자 서비스 프론트엔드 개발",
                "관련 학과 전공자 또는 그에 준하는 지식 보유자",
                "React, TypeScript 사용 경험 3년 이상",
                "Next.js 기반 SSR/SSG 경험자 우대",
                List.of("문제해결력", "커뮤니케이션"),
                List.of("React", "TypeScript", "Next.js"),
                List.of("자율·혁신", "성취·결과"),
                "[Weaver] 서류 전형 결과 안내",
                "안녕하세요. Weaver 지원에 감사드립니다..."
        );
        String jsonPayload = objectMapper.writeValueAsString(requestDTO);

        MockMultipartFile requestDtoPart = new MockMultipartFile(
                "updateDTO", "", MediaType.APPLICATION_JSON_VALUE, jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile imagePart = new MockMultipartFile(
                "emailBannerImage", "banner.png", MediaType.IMAGE_PNG_VALUE, "dummy image content".getBytes()
        );

        // when & then
        mockMvc.perform(multipart("/api/job-postings")
                        .file(requestDtoPart)
                        .file(imagePart)
                        .param("isTemp", "false")
                        .with(customAuth("1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채용 공고가 성공적으로 등록되었습니다."))
                .andDo(print());
    }

    @Test
    @DisplayName("공고 수정 : PUT 메서드로 Multipart 요청을 보낼 때 Spring Boot 제약 우회 성공")
    void updateJobPosting_Success() throws Exception {
        // given
        Long jdId = 100L;

        JobPostingUpdateDTO updateDTO = new JobPostingUpdateDTO(
                "2026년 상반기 프론트엔드 엔지니어 채용 (수정)",
                LocalDate.of(2026, 5, 31),
                "개발",
                "프론트엔드 개발자",
                "React 기반 대규모 사용자 서비스 프론트엔드 개발",
                "관련 학과 전공자 또는 그에 준하는 지식 보유자",
                "React, TypeScript 사용 경험 3년 이상",
                "Next.js 기반 SSR/SSG 경험자 우대",
                List.of("문제해결력", "커뮤니케이션"),
                List.of("React", "TypeScript", "Next.js"),
                List.of("자율·혁신", "성취·결과"),
                "[Weaver] 서류 전형 결과 안내",
                "안녕하세요. Weaver 지원에 감사드립니다...",
                true
        );

        String jsonPayload = objectMapper.writeValueAsString(updateDTO);

        MockMultipartFile requestDtoPart = new MockMultipartFile(
                "updateDTO", "", MediaType.APPLICATION_JSON_VALUE, jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/job-postings/{jdId}", jdId)
                        .file(requestDtoPart)
                        .with(request -> {
                            request.setMethod(HttpMethod.PUT.name());
                            return request;
                        })
                        .with(customAuth("1"))) // ⬅️ 다이렉트 주입!
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채용 공고가 성공적으로 수정되었습니다."))
                .andDo(print());
    }
}