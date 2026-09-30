package com.weiver.global.exception.handler;

import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GlobalExceptionHandlerUploadTest.UploadLimitController.class)
@AutoConfigureMockMvc(addFilters = false)
// 테스트 클래스의 중첩 클래스는 컴포넌트 스캔에서 제외되므로 명시적으로 등록한다.
@Import(GlobalExceptionHandlerUploadTest.UploadLimitController.class)
class GlobalExceptionHandlerUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private CookieProvider cookieProvider;

    /** MockMvc는 실제 업로드 한도를 검사하지 않으므로 한도 초과 예외를 직접 던지는 테스트용 컨트롤러를 쓴다. */
    @RestController
    static class UploadLimitController {

        @PostMapping("/api/interviews/session/answers/audio")
        void audioAnswer() {
            throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
        }

        @PostMapping("/api/applicants/photo")
        void otherUpload() {
            throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
        }
    }

    @Test
    @DisplayName("답변 녹음 업로드 한도를 초과하면 413과 INTERVIEW_ANSWER_AUDIO_TOO_LARGE를 반환한다")
    void audioUploadTooLarge() throws Exception {
        mockMvc.perform(post("/api/interviews/session/answers/audio"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INTERVIEW_ANSWER_AUDIO_TOO_LARGE.name()));
    }

    @Test
    @DisplayName("다른 업로드의 한도 초과는 녹음 전용 코드가 아닌 기존처럼 413 상태코드로만 응답한다")
    void otherUploadTooLarge_keepsGenericResponse() throws Exception {
        mockMvc.perform(post("/api/applicants/photo"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value(not(ErrorCode.INTERVIEW_ANSWER_AUDIO_TOO_LARGE.name())));
    }
}
