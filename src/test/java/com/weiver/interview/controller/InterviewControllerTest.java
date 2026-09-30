package com.weiver.interview.controller;

import com.weiver.global.common.UserRole;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.interview.dto.response.InterviewAnalysisSubmitResponse;
import com.weiver.interview.dto.response.InterviewAnswerAudioResponseDTO;
import com.weiver.interview.dto.response.InterviewRemainingResponse;
import com.weiver.interview.service.InterviewAnswerVoiceService;
import com.weiver.interview.service.InterviewFlowService;
import com.weiver.interview.service.InterviewSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewSessionService interviewSessionService;
    @MockitoBean
    private InterviewFlowService interviewFlowService;
    @MockitoBean
    private InterviewAnswerVoiceService interviewAnswerVoiceService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private CookieProvider cookieProvider;

    private RequestPostProcessor customAuth(String publicId) {
        return request -> {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(publicId, UserRole.APPLICANT);
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));

            SecurityContextHolder.getContext().setAuthentication(auth);
            return request;
        };
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("면접 잔여 횟수 조회 성공 시 200과 응답 필드를 반환한다")
    void getRemainingInterview_Success() throws Exception {
        // given
        String publicId = "applicant-public-id";
        LocalDate reapplyDate = LocalDate.of(2026, 10, 8);
        given(interviewSessionService.getRemainingInterview(eq(publicId)))
                .willReturn(new InterviewRemainingResponse(1, 0, 21, reapplyDate, null));

        // when, then
        mockMvc.perform(get("/api/interviews/remaining")
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.remainingCount").value(0))
                .andExpect(jsonPath("$.data.reapplyDDay").value(21))
                .andExpect(jsonPath("$.data.reapplyAvailableDate").value("2026-10-08"));

        verify(interviewSessionService).getRemainingInterview(eq(publicId));
    }

    @Test
    @DisplayName("면접 분석 요청 성공 시 200과 세션 상태·다음 응시 가능 시점을 반환한다")
    void requestInterviewAnalysis_Success() throws Exception {
        // given
        String publicId = "applicant-public-id";
        UUID sessionId = UUID.randomUUID();
        LocalDateTime nextAvailableAt = LocalDateTime.of(2026, 10, 13, 14, 30);
        given(interviewFlowService.requestAnalysis(eq(sessionId), eq(publicId)))
                .willReturn(new InterviewAnalysisSubmitResponse(
                        sessionId, "TRANSCRIPT_SAVE_REQUESTED", nextAvailableAt));

        // when, then
        mockMvc.perform(post("/api/interviews/{interviewSessionId}/analysis", sessionId)
                        .with(customAuth(publicId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.interview_session_id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.status").value("TRANSCRIPT_SAVE_REQUESTED"))
                .andExpect(jsonPath("$.data.next_available_interview_at").exists());

        verify(interviewFlowService).requestAnalysis(eq(sessionId), eq(publicId));
    }

    @Test
    @DisplayName("엣지 케이스: Principal이 없으면 면접 분석 요청 시 UNAUTHORIZED 에러가 발생한다")
    void requestInterviewAnalysis_WithoutPrincipal_ThrowsUnauthorized() throws Exception {
        // when, then
        mockMvc.perform(post("/api/interviews/{interviewSessionId}/analysis", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("엣지 케이스: Principal이 없으면 면접 잔여 횟수 조회 시 UNAUTHORIZED 에러가 발생한다")
    void getRemainingInterview_WithoutPrincipal_ThrowsUnauthorized() throws Exception {
        // when, then
        mockMvc.perform(get("/api/interviews/remaining")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // ===================== 답변 녹음 제출 =====================

    private static final String AUDIO_URL = "/api/interviews/{id}/answers/audio";

    private MockMultipartFile audioFile() {
        return new MockMultipartFile("file", "answer.webm", "audio/webm;codecs=opus", "fake-audio".getBytes());
    }

    @Test
    @DisplayName("답변 녹음 제출 성공 시 200과 세션 정보만 반환하고 인식 결과는 내려주지 않는다")
    void submitAudioAnswer_Success() throws Exception {
        // given
        UUID sessionId = UUID.randomUUID();
        given(interviewAnswerVoiceService.submitAudioAnswer(
                eq(sessionId), eq("applicant-public-id"), eq("S_01_00"), eq(1), any()))
                .willReturn(new InterviewAnswerAudioResponseDTO(sessionId, "WAITING_FOR_QUESTION", "S_01_00", 1));

        // when & then
        mockMvc.perform(multipart(AUDIO_URL, sessionId)
                        .file(audioFile())
                        .param("question_code", "S_01_00")
                        .param("sequence", "1")
                        .with(customAuth("applicant-public-id")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interview_session_id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.status").value("WAITING_FOR_QUESTION"))
                .andExpect(jsonPath("$.data.question_code").value("S_01_00"))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.answer").doesNotExist())
                .andExpect(jsonPath("$.data.transcript").doesNotExist());
    }

    @Test
    @DisplayName("답변 녹음 제출 실패 경로는 HTTP 상태와 errorCode를 함께 반환한다")
    void submitAudioAnswer_FailurePaths() throws Exception {
        UUID sessionId = UUID.randomUUID();
        assertAudioFailure(sessionId, ErrorCode.INTERVIEW_ANSWER_AUDIO_INVALID, 400);
        assertAudioFailure(sessionId, ErrorCode.INTERVIEW_ANSWER_TOO_LONG, 400);
        assertAudioFailure(sessionId, ErrorCode.INTERVIEW_ANSWER_NOT_RECOGNIZED, 422);
        assertAudioFailure(sessionId, ErrorCode.INTERVIEW_QUESTION_NOT_READY, 409);
        assertAudioFailure(sessionId, ErrorCode.INTERVIEW_ALREADY_COMPLETED, 409);
        assertAudioFailure(sessionId, ErrorCode.SPEECH_PROVIDER_RATE_LIMITED, 429);
        assertAudioFailure(sessionId, ErrorCode.SPEECH_TRANSCRIPTION_FAILED, 502);
    }

    private void assertAudioFailure(UUID sessionId, ErrorCode errorCode, int httpStatus) throws Exception {
        // 같은 mock에 예외를 반복 설정하므로 호출부가 예외를 던지지 않는 willThrow().given() 형태를 쓴다.
        willThrow(new BusinessException(errorCode))
                .given(interviewAnswerVoiceService).submitAudioAnswer(any(), any(), any(), any(), any());

        mockMvc.perform(multipart(AUDIO_URL, sessionId)
                        .file(audioFile())
                        .param("question_code", "S_01_00")
                        .param("sequence", "1")
                        .with(customAuth("applicant-public-id")))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.errorCode").value(errorCode.name()));
    }

    @Test
    @DisplayName("답변 녹음 제출 시 file 파트나 question_code·sequence가 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void submitAudioAnswer_MissingParts() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(multipart(AUDIO_URL, sessionId)
                        .param("question_code", "S_01_00")
                        .param("sequence", "1")
                        .with(customAuth("applicant-public-id")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart(AUDIO_URL, sessionId)
                        .file(audioFile())
                        .param("sequence", "1")
                        .with(customAuth("applicant-public-id")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.BIND_FAILED.name()));

        verifyNoInteractions(interviewAnswerVoiceService);
    }
}
