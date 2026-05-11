package com.weiver.matching.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.global.common.UserRole;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.matching.dto.request.ApplicantSearchCondition;
import com.weiver.matching.dto.response.ApplicantListResponseDTO;
import com.weiver.matching.service.MatchResultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchResultController.class)
@AutoConfigureMockMvc(addFilters = false)
class MatchResultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MatchResultService matchResultService;

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
    @DisplayName("성공: 파라미터 없이 요청하면 기본 페이징 조건으로 조회된다.")
    void searchApplicants_Success_NoParams() throws Exception {
        // given
        Long jdId = 1L;
        ApplicantListResponseDTO dummyDto = new ApplicantListResponseDTO(
                "pub-1", "url", "이현우", "백엔드 개발자", 90f,
                CulturefitStyle.INCLUSIVE_INNOVATOR.getDescription(), List.of("포용성"), List.of("Java")
        );
        Page<ApplicantListResponseDTO> mockPage = new PageImpl<>(List.of(dummyDto), PageRequest.of(0, 10), 1);

        given(matchResultService.searchApplicantList(any(ApplicantSearchCondition.class), any(Pageable.class), any(String.class)))
                .willReturn(mockPage);

        // when & then
        mockMvc.perform(get("/api/job-postings/{jdId}/applicants", jdId)
                        .with(customAuth("company-1"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.content[0].applicantName").value("이현우"))
                .andExpect(jsonPath("$.data.content[0].skillScore").value(90.0))
                .andDo(print());
    }

    @Test
    @DisplayName("성공: 모든 필터 파라미터가 ApplicantSearchCondition 레코드로 정확히 매핑된다.")
    void searchApplicants_Success_WithAllParams() throws Exception {
        // given
        Long jdId = 1L;
        Page<ApplicantListResponseDTO> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 5), 0);

        given(matchResultService.searchApplicantList(any(ApplicantSearchCondition.class), any(Pageable.class), any(String.class)))
                .willReturn(emptyPage);

        // when
        mockMvc.perform(get("/api/job-postings/{jdId}/applicants", jdId)
                        .param("keyword", "홍길동")
                        .param("skillScoreMin", "80")
                        .param("cultureStyle", "INCLUSIVE_INNOVATOR")
                        .param("techStacks", "React")
                        .param("techStacks", "Java")
                        .param("page", "0")
                        .param("size", "5")
                        .with(customAuth("company-1"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());

        // then
        ArgumentCaptor<ApplicantSearchCondition> conditionCaptor = ArgumentCaptor.forClass(ApplicantSearchCondition.class);
        verify(matchResultService).searchApplicantList(conditionCaptor.capture(), any(Pageable.class), any(String.class));

        ApplicantSearchCondition capturedCondition = conditionCaptor.getValue();
        assertThat(capturedCondition.jdId()).isEqualTo(jdId);
        assertThat(capturedCondition.keyword()).isEqualTo("홍길동");
        assertThat(capturedCondition.skillScoreMin()).isEqualTo(80);
        assertThat(capturedCondition.cultureStyle()).isEqualTo("INCLUSIVE_INNOVATOR");
        assertThat(capturedCondition.techStacks()).containsExactly("React", "Java");
    }

    @Test
    @DisplayName("엣지케이스: 인증 객체가 없을 경우 401 UNAUTHORIZED 예외가 발생한다.")
    void searchApplicants_Unauthorized() throws Exception {
        Long jdId = 1L;

        // when & then
        mockMvc.perform(get("/api/job-postings/{jdId}/applicants", jdId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    @Test
    @DisplayName("엣지케이스: 파라미터 타입이 다를 경우(ex. 숫자에 문자열 입력) 400 BAD REQUEST 예외가 발생한다.")
    void searchApplicants_TypeMismatch() throws Exception {
        // given
        Long jdId = 1L;

        // when & then
        mockMvc.perform(get("/api/job-postings/{jdId}/applicants", jdId)
                        .param("skillScoreMin", "백점")
                        .with(customAuth("company-1"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }
}
