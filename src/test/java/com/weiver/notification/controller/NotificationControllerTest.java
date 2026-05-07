package com.weiver.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.common.UserRole;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.notification.dto.response.NotificationResponseDTO;
import com.weiver.notification.service.NotificationService;
import com.weiver.notification.type.NotificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService notificationService;

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
    @DisplayName("성공: 알림 목록 조회 시 명세서와 동일한 래핑 구조(NotificationDTO)로 응답한다.")
    void getNotifications_Success() throws Exception {
        // given
        String publicId = "comp-123";

        NotificationResponseDTO dto1 = new NotificationResponseDTO(
                101L,
                NotificationType.RESUME_MATCH_TALENT.getDescription(),
                "백엔드 공고에 새로운 매칭이 있습니다.",
                false,
                5L,
                LocalDateTime.now()
        );

        NotificationResponseDTO dto2 = new NotificationResponseDTO(
                98L,
                NotificationType.RESUME_MATCH_TALENT.getDescription(),
                "프론트엔드 공고에 새로운 매칭이 있습니다.",
                true,
                2L,
                LocalDateTime.now().minusDays(1)
        );
        given(notificationService.getCompanyNotifications(publicId)).willReturn(List.of(dto1, dto2));

        // when & then
        mockMvc.perform(get("/api/notifications")
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.NotificationDTO").isArray())
                .andExpect(jsonPath("$.data.NotificationDTO[0].notificationId").value(101))
                .andExpect(jsonPath("$.data.NotificationDTO[0].isRead").value(false))
                .andExpect(jsonPath("$.data.NotificationDTO[0].jdId").value(5))
                .andExpect(jsonPath("$.data.NotificationDTO[1].notificationId").value(98))
                .andExpect(jsonPath("$.data.NotificationDTO[1].isRead").value(true))
                .andDo(print());
    }

    @Test
    @DisplayName("성공 엣지케이스: 알림이 하나도 없는 경우 빈 배열을 포함하여 정상 응답한다.")
    void getNotifications_Empty_Success() throws Exception {
        // given
        String publicId = "comp-123";
        given(notificationService.getCompanyNotifications(publicId)).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/api/notifications")
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.NotificationDTO").isEmpty())
                .andDo(print());
    }


    @Test
    @DisplayName("성공: 단일 알림 읽음 처리 API가 정상적으로 호출된다.")
    void readNotification_Success() throws Exception {
        // given
        String publicId = "comp-123";
        Long notificationId = 101L;

        // when & then
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andDo(print());

        // 서비스 메서드가 정확한 파라미터로 호출되었는지 검증
        verify(notificationService).markAsRead(notificationId, publicId);
    }

    @Test
    @DisplayName("실패 엣지케이스: 알림 ID 경로 변수에 숫자가 아닌 문자열이 들어오면 400 Bad Request가 발생한다.")
    void readNotification_TypeMismatch() throws Exception {
        // given
        String publicId = "comp-123";
        String invalidId = "not-a-number";

        // when & then
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", invalidId)
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("성공: 전체 알림 읽음 처리 API가 정상적으로 호출된다.")
    void readAllNotifications_Success() throws Exception {
        // given
        String publicId = "comp-123";

        // when & then
        mockMvc.perform(patch("/api/notifications/read-all")
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andDo(print());

        // 서비스 메서드 호출 검증
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).markAllAsRead(captor.capture());
        assertThat(captor.getValue()).isEqualTo(publicId);
    }

    @Test
    @DisplayName("실패 엣지케이스: 인증 객체(Principal) 없이 접근하면 401 Unauthorized 에러가 발생한다.")
    void unauthorizedAccess_ThrowsException() throws Exception {
        // given
        Long notificationId = 101L;

        // when & then (읽음 처리 API에 인증 없이 접근)
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }
}