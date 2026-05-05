package com.weiver.jobposting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.jobposting.dto.request.JobPostingRequestDTO;
import com.weiver.jobposting.dto.request.JobPostingUpdateDTO;
import com.weiver.jobposting.service.JobPostingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
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


    private final Principal mockPrincipal = () -> "1";

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
                "requestDTO", "", MediaType.APPLICATION_JSON_VALUE, jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        // 2. 가짜 이미지 파일 생성
        MockMultipartFile imagePart = new MockMultipartFile(
                "emailBannerImage", "banner.png", MediaType.IMAGE_PNG_VALUE, "dummy image content".getBytes()
        );

        // when & then
        mockMvc.perform(multipart("/api/job-postings")
                        .file(requestDtoPart)
                        .file(imagePart)
                        .param("isTemp", "false")
                        .principal(mockPrincipal))
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
                "requestDTO", "", MediaType.APPLICATION_JSON_VALUE, jsonPayload.getBytes(StandardCharsets.UTF_8)
        );


        mockMvc.perform(multipart("/api/job-postings/{jdId}", jdId)
                        .file(requestDtoPart)
                        .with(request -> {
                            request.setMethod(HttpMethod.PUT.name());
                            return request;
                        })
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채용 공고가 성공적으로 수정되었습니다."))
                .andDo(print());
    }

    @Test
    @DisplayName("공고 리스트 조회 : Principal(로그인 정보)이 없을 경우 401 UNAUTHORIZED 발생")
    void getJobPostingsList_Unauthorized() throws Exception {
        // given

        // when & then
        mockMvc.perform(get("/api/job-postings")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isUnauthorized()) // (참고: GlobalExceptionHandler에 따라 400이나 다른 값일 수 있음. 프로젝트 설정에 맞추세요)
                .andDo(print());
    }
}