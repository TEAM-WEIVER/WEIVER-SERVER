package com.weiver.interview.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.speech.SpeechTestProperties;
import com.weiver.global.speech.dto.Transcription;
import com.weiver.global.speech.service.SpeechTranscriber;
import com.weiver.interview.dto.request.InterviewAnswerSubmitRequest;
import com.weiver.interview.dto.response.InterviewAnswerAudioResponseDTO;
import com.weiver.interview.dto.response.InterviewWebSocketMessageResponse;
import com.weiver.interview.type.InterviewSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class InterviewAnswerVoiceServiceTest {

    private static final String APPLICANT_PUBLIC_ID = "applicant-public-id";
    private static final String QUESTION_CODE = "S_01_00";
    private static final int SEQUENCE = 1;
    private static final byte[] AUDIO = "fake-opus-audio".getBytes();

    private final UUID sessionId = UUID.randomUUID();

    @Mock private InterviewFlowService interviewFlowService;
    @Mock private SpeechTranscriber speechTranscriber;

    private InterviewAnswerVoiceService voiceService;

    @BeforeEach
    void setUp() {
        voiceService = new InterviewAnswerVoiceService(
                interviewFlowService, speechTranscriber,
                SpeechTestProperties.of("http://localhost", "key", "", Duration.ofSeconds(30)));
    }

    private MockMultipartFile file(String contentType) {
        return new MockMultipartFile("file", "recording", contentType, AUDIO);
    }

    private void givenTranscription(String text, Double seconds) {
        given(speechTranscriber.transcribe(any(), anyString(), anyString())).willReturn(new Transcription(text, seconds));
    }

    private void givenSubmitAccepted() {
        given(interviewFlowService.submitAnswer(eq(sessionId), eq(APPLICANT_PUBLIC_ID), any(InterviewAnswerSubmitRequest.class)))
                .willReturn(InterviewWebSocketMessageResponse.answerAccepted(sessionId));
    }

    private InterviewAnswerAudioResponseDTO submit(MockMultipartFile file) {
        return voiceService.submitAudioAnswer(sessionId, APPLICANT_PUBLIC_ID, QUESTION_CODE, SEQUENCE, file);
    }

    private void assertRejected(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(expected);
    }

    @Test
    @DisplayName("녹음을 STT로 변환해 기존 submitAnswer에 위임하고, 응답에는 인식 결과 없이 세션 정보만 담는다")
    void submitAudioAnswer_success() {
        givenTranscription("  Kafka와 RabbitMQ를 사용했습니다.  ", 42.0);
        givenSubmitAccepted();

        InterviewAnswerAudioResponseDTO response = submit(file("audio/webm"));

        assertThat(response.interviewSessionId()).isEqualTo(sessionId);
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION.name());
        assertThat(response.questionCode()).isEqualTo(QUESTION_CODE);
        assertThat(response.sequence()).isEqualTo(SEQUENCE);

        // 사전 검증이 STT보다 먼저, 트림된 인식 텍스트가 submitAnswer로 전달된다.
        var order = inOrder(interviewFlowService, speechTranscriber);
        order.verify(interviewFlowService).validateAnswerTarget(sessionId, APPLICANT_PUBLIC_ID, QUESTION_CODE, SEQUENCE);
        order.verify(speechTranscriber).transcribe(eq(AUDIO), eq("answer.webm"), eq("audio/webm"));
        ArgumentCaptor<InterviewAnswerSubmitRequest> captor = ArgumentCaptor.forClass(InterviewAnswerSubmitRequest.class);
        order.verify(interviewFlowService).submitAnswer(eq(sessionId), eq(APPLICANT_PUBLIC_ID), captor.capture());
        assertThat(captor.getValue().answer()).isEqualTo("Kafka와 RabbitMQ를 사용했습니다.");
        assertThat(captor.getValue().questionCode()).isEqualTo(QUESTION_CODE);
        assertThat(captor.getValue().sequence()).isEqualTo(SEQUENCE);
    }

    @Test
    @DisplayName("브라우저가 붙이는 codecs 파라미터(audio/webm;codecs=opus)는 무시하고 형식만 비교한다")
    void submitAudioAnswer_acceptsContentTypeWithParameters() {
        givenTranscription("답변", 5.0);
        givenSubmitAccepted();

        submit(file("audio/webm;codecs=opus"));
        submit(file("audio/ogg; codecs=opus"));
        submit(file("audio/mp4"));

        then(speechTranscriber).should().transcribe(eq(AUDIO), eq("answer.webm"), eq("audio/webm"));
        then(speechTranscriber).should().transcribe(eq(AUDIO), eq("answer.ogg"), eq("audio/ogg"));
        then(speechTranscriber).should().transcribe(eq(AUDIO), eq("answer.mp4"), eq("audio/mp4"));
    }

    @Test
    @DisplayName("빈 파일·허용 외 형식·필수 값 누락이면 사전 검증과 STT 호출 없이 거절한다")
    void submitAudioAnswer_rejectsInvalidRequestWithoutExternalCall() {
        assertRejected(() -> submit(new MockMultipartFile("file", "a.webm", "audio/webm", new byte[0])),
                ErrorCode.INTERVIEW_ANSWER_AUDIO_INVALID);
        assertRejected(() -> submit(file("audio/mpeg")), ErrorCode.INTERVIEW_ANSWER_AUDIO_INVALID);
        assertRejected(() -> submit(file("video/webm")), ErrorCode.INTERVIEW_ANSWER_AUDIO_INVALID);
        assertRejected(() -> submit(file(null)), ErrorCode.INTERVIEW_ANSWER_AUDIO_INVALID);
        assertRejected(() -> voiceService.submitAudioAnswer(sessionId, APPLICANT_PUBLIC_ID, QUESTION_CODE, SEQUENCE, null),
                ErrorCode.INTERVIEW_ANSWER_AUDIO_INVALID);
        assertRejected(() -> voiceService.submitAudioAnswer(sessionId, APPLICANT_PUBLIC_ID, " ", SEQUENCE, file("audio/webm")),
                ErrorCode.BAD_REQUEST);
        assertRejected(() -> voiceService.submitAudioAnswer(sessionId, APPLICANT_PUBLIC_ID, QUESTION_CODE, null, file("audio/webm")),
                ErrorCode.BAD_REQUEST);

        then(interviewFlowService).shouldHaveNoInteractions();
        then(speechTranscriber).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("사전 검증(타인 세션·종료된 세션·답변 불가 상태)에서 거절되면 STT를 호출하지 않고 답변도 저장하지 않는다")
    void submitAudioAnswer_rejectedByPreCheck() {
        willThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .given(interviewFlowService).validateAnswerTarget(sessionId, APPLICANT_PUBLIC_ID, QUESTION_CODE, SEQUENCE);

        assertRejected(() -> submit(file("audio/webm")), ErrorCode.FORBIDDEN);

        then(speechTranscriber).shouldHaveNoInteractions();
        then(interviewFlowService).should(never()).submitAnswer(any(), anyString(), any());
    }

    @Test
    @DisplayName("인식 결과가 공백이면 INTERVIEW_ANSWER_NOT_RECOGNIZED이고 답변을 저장하지 않는다")
    void submitAudioAnswer_blankTranscription() {
        givenTranscription("   ", 3.0);

        assertRejected(() -> submit(file("audio/webm")), ErrorCode.INTERVIEW_ANSWER_NOT_RECOGNIZED);

        then(interviewFlowService).should(never()).submitAnswer(any(), anyString(), any());
    }

    @Test
    @DisplayName("인식된 길이가 120초+여유 5초(125초)를 넘으면 INTERVIEW_ANSWER_TOO_LONG이고 답변을 저장하지 않는다")
    void submitAudioAnswer_tooLong() {
        givenTranscription("긴 답변", 125.6);

        assertRejected(() -> submit(file("audio/webm")), ErrorCode.INTERVIEW_ANSWER_TOO_LONG);

        then(interviewFlowService).should(never()).submitAnswer(any(), anyString(), any());
    }

    @Test
    @DisplayName("정확히 125초는 허용한다(경계값)")
    void submitAudioAnswer_boundaryAllowed() {
        givenTranscription("경계 답변", 125.0);
        givenSubmitAccepted();

        assertThat(submit(file("audio/webm")).sequence()).isEqualTo(SEQUENCE);
    }

    @Test
    @DisplayName("공급자가 길이를 주지 않으면(null) 길이 검사를 건너뛰고 답변을 저장한다")
    void submitAudioAnswer_nullDurationSkipsLengthCheck() {
        givenTranscription("길이 정보 없는 답변", null);
        givenSubmitAccepted();

        assertThat(submit(file("audio/webm")).status()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION.name());
    }

    @Test
    @DisplayName("STT가 한도 초과·실패로 예외를 던지면 그대로 전달되고 답변을 저장하지 않는다")
    void submitAudioAnswer_transcriberFailurePropagates() {
        given(speechTranscriber.transcribe(any(), anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.SPEECH_PROVIDER_RATE_LIMITED))
                .willThrow(new BusinessException(ErrorCode.SPEECH_TRANSCRIPTION_FAILED));

        assertRejected(() -> submit(file("audio/webm")), ErrorCode.SPEECH_PROVIDER_RATE_LIMITED);
        assertRejected(() -> submit(file("audio/webm")), ErrorCode.SPEECH_TRANSCRIPTION_FAILED);

        then(interviewFlowService).should(never()).submitAnswer(any(), anyString(), any());
    }
}
