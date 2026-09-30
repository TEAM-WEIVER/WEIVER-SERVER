package com.weiver.interview.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.global.speech.SpeechTestProperties;
import com.weiver.global.speech.config.SpeechProperties;
import com.weiver.global.speech.config.SpeechTtsExecutor;
import com.weiver.global.speech.dto.SynthesizedAudio;
import com.weiver.global.speech.service.SpeechSynthesizer;
import com.weiver.interview.dto.response.InterviewWebSocketMessageResponse;
import com.weiver.interview.service.InterviewQuestionVoiceService.QuestionVoiceCommand;
import com.weiver.interview.type.AudioStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class InterviewQuestionVoiceServiceTest {

    private static final String APPLICANT_PUBLIC_ID = "applicant-public-id";
    private static final String QUESTION = "Kafka와 RabbitMQ를 함께 사용한 이유가 무엇인가요?";
    private static final byte[] WAV = "RIFF....WAVE".getBytes();
    private static final String UPLOADED_URL = "https://private-bucket.s3.amazonaws.com/interview-tts/x/2.wav";
    private static final String PRESIGNED_URL = "https://private-bucket.s3.amazonaws.com/interview-tts/x/2.wav?X-Amz-Signature=abc";

    private final UUID sessionId = UUID.randomUUID();

    @Mock private SpeechSynthesizer speechSynthesizer;
    @Mock private S3Service s3Service;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private SpeechTtsExecutor ttsExecutor;

    private InterviewQuestionVoiceService voiceService;

    @BeforeEach
    void setUp() {
        voiceService = serviceWithMaxQueueWait(Duration.ofSeconds(5));
    }

    private InterviewQuestionVoiceService serviceWithMaxQueueWait(Duration maxQueueWait) {
        SpeechProperties base = SpeechTestProperties.of("http://localhost", "key", "", Duration.ofSeconds(8));
        SpeechProperties.Tts tts = base.tts();
        SpeechProperties.Tts withWait = new SpeechProperties.Tts(
                tts.baseUrl(), tts.apiKey(), tts.model(), tts.voice(), tts.style(), tts.timeout(), tts.s3Dir(),
                new SpeechProperties.Executor(4, 8, 50, maxQueueWait));
        return new InterviewQuestionVoiceService(
                speechSynthesizer, s3Service, messagingTemplate, ttsExecutor,
                new SpeechProperties(base.stt(), withWait));
    }

    private QuestionVoiceCommand command() {
        return new QuestionVoiceCommand(sessionId, APPLICANT_PUBLIC_ID, "S_02_00", 2, QUESTION, System.nanoTime());
    }

    /** 스레드풀에 넘긴 작업을 그 자리에서 실행한다(delayMillis만큼 대기열에서 기다린 것처럼 만든다). */
    private void givenExecutorRunsTask(long delayMillis) {
        willAnswer(invocation -> {
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).given(ttsExecutor).execute(any(Runnable.class));
    }

    private InterviewWebSocketMessageResponse pushedMessage() {
        ArgumentCaptor<InterviewWebSocketMessageResponse> captor =
                ArgumentCaptor.forClass(InterviewWebSocketMessageResponse.class);
        then(messagingTemplate).should().convertAndSendToUser(
                eq(APPLICANT_PUBLIC_ID), eq("/queue/interviews"), captor.capture());
        return captor.getValue();
    }

    private void assertTextOnly(InterviewWebSocketMessageResponse message) {
        assertThat(message.type()).isEqualTo("QUESTION_READY");
        assertThat(message.question()).isEqualTo(QUESTION);
        assertThat(message.questionCode()).isEqualTo("S_02_00");
        assertThat(message.sequence()).isEqualTo(2);
        assertThat(message.audioUrl()).isNull();
        assertThat(message.audioStatus()).isEqualTo(AudioStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("TTS와 S3 저장에 성공하면 질문 텍스트와 audio_url을 한 메시지로 READY 상태로 푸시한다")
    void dispatch_success() {
        // given
        givenExecutorRunsTask(0);
        given(speechSynthesizer.synthesize(QUESTION)).willReturn(new SynthesizedAudio(WAV, "audio/wav"));
        given(s3Service.privateUploadBytes(any(), anyString(), anyString())).willReturn(UPLOADED_URL);
        given(s3Service.getPresignedUrl(UPLOADED_URL)).willReturn(PRESIGNED_URL);

        // when
        voiceService.dispatch(command());

        // then
        then(s3Service).should().privateUploadBytes(
                eq(WAV), eq("interview-tts/" + sessionId + "/2.wav"), eq("audio/wav"));

        InterviewWebSocketMessageResponse message = pushedMessage();
        assertThat(message.type()).isEqualTo("QUESTION_READY");
        assertThat(message.interviewSessionId()).isEqualTo(sessionId);
        assertThat(message.status()).isEqualTo("QUESTION_READY");
        assertThat(message.question()).isEqualTo(QUESTION);
        assertThat(message.audioUrl()).isEqualTo(PRESIGNED_URL);
        assertThat(message.audioStatus()).isEqualTo(AudioStatus.READY);
    }

    @Test
    @DisplayName("TTS가 실패하면 S3를 호출하지 않고 텍스트만 UNAVAILABLE로 푸시한다")
    void dispatch_synthesisFails() {
        givenExecutorRunsTask(0);
        given(speechSynthesizer.synthesize(QUESTION))
                .willThrow(new BusinessException(ErrorCode.SPEECH_SYNTHESIS_FAILED));

        voiceService.dispatch(command());

        then(s3Service).shouldHaveNoInteractions();
        assertTextOnly(pushedMessage());
    }

    @Test
    @DisplayName("S3 업로드가 실패하면 텍스트만 UNAVAILABLE로 푸시한다")
    void dispatch_s3UploadFails() {
        givenExecutorRunsTask(0);
        given(speechSynthesizer.synthesize(QUESTION)).willReturn(new SynthesizedAudio(WAV, "audio/wav"));
        given(s3Service.privateUploadBytes(any(), anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        voiceService.dispatch(command());

        then(s3Service).should(never()).getPresignedUrl(anyString());
        assertTextOnly(pushedMessage());
    }

    @Test
    @DisplayName("Presigned URL 생성이 실패하면 텍스트만 UNAVAILABLE로 푸시한다")
    void dispatch_presignFails() {
        givenExecutorRunsTask(0);
        given(speechSynthesizer.synthesize(QUESTION)).willReturn(new SynthesizedAudio(WAV, "audio/wav"));
        given(s3Service.privateUploadBytes(any(), anyString(), anyString())).willReturn(UPLOADED_URL);
        given(s3Service.getPresignedUrl(UPLOADED_URL))
                .willThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        voiceService.dispatch(command());

        assertTextOnly(pushedMessage());
    }

    @Test
    @DisplayName("예상하지 못한 예외가 나도 질문이 유실되지 않도록 텍스트만 UNAVAILABLE로 푸시한다")
    void dispatch_unexpectedException() {
        givenExecutorRunsTask(0);
        given(speechSynthesizer.synthesize(QUESTION)).willThrow(new IllegalStateException("boom"));

        voiceService.dispatch(command());

        assertTextOnly(pushedMessage());
    }

    @Test
    @DisplayName("스레드풀이 가득 차 작업이 거절되면 TTS 없이 호출한 스레드에서 텍스트만 푸시한다")
    void dispatch_rejectedByExecutor() {
        willThrow(new RejectedExecutionException("full")).given(ttsExecutor).execute(any(Runnable.class));

        voiceService.dispatch(command());

        then(speechSynthesizer).shouldHaveNoInteractions();
        then(s3Service).shouldHaveNoInteractions();
        assertTextOnly(pushedMessage());
    }

    @Test
    @DisplayName("대기열에서 한도보다 오래 기다린 작업은 TTS를 건너뛰고 텍스트만 푸시한다")
    void dispatch_skipsTtsWhenQueueWaitExceeded() {
        // given: 한도 10ms, 작업이 50ms 뒤에 시작된 것처럼 만든다
        InterviewQuestionVoiceService shortWaitService = serviceWithMaxQueueWait(Duration.ofMillis(10));
        givenExecutorRunsTask(50);

        // when
        shortWaitService.dispatch(command());

        // then
        then(speechSynthesizer).shouldHaveNoInteractions();
        then(s3Service).shouldHaveNoInteractions();
        assertTextOnly(pushedMessage());
    }

    @Test
    @DisplayName("대기 시간이 한도 안이면 정상적으로 TTS를 수행한다")
    void dispatch_runsTtsWhenQueueWaitWithinLimit() {
        givenExecutorRunsTask(20);
        given(speechSynthesizer.synthesize(QUESTION)).willReturn(new SynthesizedAudio(WAV, "audio/wav"));
        given(s3Service.privateUploadBytes(any(), anyString(), anyString())).willReturn(UPLOADED_URL);
        given(s3Service.getPresignedUrl(UPLOADED_URL)).willReturn(PRESIGNED_URL);

        voiceService.dispatch(command());

        assertThat(pushedMessage().audioStatus()).isEqualTo(AudioStatus.READY);
    }

    @Test
    @DisplayName("푸시 자체가 실패해도 예외가 풀 스레드 밖으로 전파되지 않는다")
    void dispatch_pushFailureDoesNotPropagate() {
        givenExecutorRunsTask(0);
        given(speechSynthesizer.synthesize(QUESTION)).willReturn(new SynthesizedAudio(WAV, "audio/wav"));
        given(s3Service.privateUploadBytes(any(), anyString(), anyString())).willReturn(UPLOADED_URL);
        given(s3Service.getPresignedUrl(UPLOADED_URL)).willReturn(PRESIGNED_URL);
        willThrow(new IllegalStateException("broker down"))
                .given(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any(Object.class));

        voiceService.dispatch(command());

        then(messagingTemplate).should().convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }
}
