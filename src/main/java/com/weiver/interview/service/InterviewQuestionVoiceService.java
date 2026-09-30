package com.weiver.interview.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.s3.service.S3Service;
import com.weiver.global.speech.config.SpeechProperties;
import com.weiver.global.speech.config.SpeechTtsExecutor;
import com.weiver.global.speech.dto.SynthesizedAudio;
import com.weiver.global.speech.service.SpeechSynthesizer;
import com.weiver.interview.dto.response.InterviewWebSocketMessageResponse;
import com.weiver.interview.type.AudioStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * 질문을 음성(TTS)으로 만들어 S3에 저장하고, 텍스트와 음성 URL을 한 메시지(QUESTION_READY)로 푸시한다.
 *
 * <p>트랜잭션을 쓰지 않는다. RabbitMQ 리스너 스레드가 TTS를 기다리지 않도록 {@link SpeechTtsExecutor}에서 실행하며,
 * 어떤 이유로든 음성을 만들지 못하면(TTS·S3 오류, 스레드풀 포화, 대기 시간 초과) 텍스트만 푸시해 면접이 멈추지 않게 한다.
 * 브라우저는 그때 기본 TTS로 질문을 읽는다.
 */
@Slf4j
@Service
public class InterviewQuestionVoiceService {

    private static final String QUEUE_DESTINATION = "/queue/interviews";

    private final SpeechSynthesizer speechSynthesizer;
    private final S3Service s3Service;
    private final SimpMessagingTemplate messagingTemplate;
    private final SpeechTtsExecutor ttsExecutor;
    private final String s3Dir;
    private final Duration maxQueueWait;

    public InterviewQuestionVoiceService(
            SpeechSynthesizer speechSynthesizer,
            S3Service s3Service,
            SimpMessagingTemplate messagingTemplate,
            SpeechTtsExecutor ttsExecutor,
            SpeechProperties speechProperties
    ) {
        this.speechSynthesizer = speechSynthesizer;
        this.s3Service = s3Service;
        this.messagingTemplate = messagingTemplate;
        this.ttsExecutor = ttsExecutor;
        this.s3Dir = speechProperties.tts().s3Dir();
        this.maxQueueWait = speechProperties.tts().executor().maxQueueWait();
    }

    /**
     * TTS 작업을 전용 스레드풀에 넘기고 바로 돌아온다. 풀이 가득 차 있으면 호출한 스레드에서 텍스트만 푸시한다.
     */
    public void dispatch(QuestionVoiceCommand command) {
        long enqueuedAtNanos = System.nanoTime();
        try {
            ttsExecutor.execute(() -> process(command, enqueuedAtNanos));
        } catch (RejectedExecutionException e) {
            log.warn("[Speech] TTS 스레드풀 포화로 텍스트만 푸시합니다. sessionId={}, sequence={}",
                    command.interviewSessionId(), command.sequence());
            push(command, null, AudioStatus.UNAVAILABLE);
        }
    }

    private void process(QuestionVoiceCommand command, long enqueuedAtNanos) {
        Duration queueWait = Duration.ofNanos(System.nanoTime() - enqueuedAtNanos);
        if (queueWait.compareTo(maxQueueWait) > 0) {
            log.warn("[Speech] TTS 대기 시간 초과로 텍스트만 푸시합니다. sessionId={}, sequence={}, queueWaitMs={}",
                    command.interviewSessionId(), command.sequence(), queueWait.toMillis());
            push(command, null, AudioStatus.UNAVAILABLE);
            return;
        }

        String audioUrl = createAudioUrl(command);
        push(command, audioUrl, audioUrl != null ? AudioStatus.READY : AudioStatus.UNAVAILABLE);
    }

    /**
     * 음성을 만들어 S3에 저장하고 Presigned URL을 돌려준다. 실패하면 null.
     * 공급자·S3 오류는 각 컴포넌트가 이미 로그를 남겼으므로 여기서는 폴백 사실만 warn으로 남기고,
     * 예상하지 못한 예외만 log.error(Sentry)로 남긴다.
     */
    private String createAudioUrl(QuestionVoiceCommand command) {
        try {
            SynthesizedAudio audio = speechSynthesizer.synthesize(command.question());
            String uploadedUrl = s3Service.privateUploadBytes(audio.data(), objectKey(command), audio.mimeType());
            return s3Service.getPresignedUrl(uploadedUrl);
        } catch (BusinessException e) {
            log.warn("[Speech] 질문 음성을 만들지 못해 텍스트만 푸시합니다. sessionId={}, sequence={}, errorCode={}",
                    command.interviewSessionId(), command.sequence(), e.getCode());
            return null;
        } catch (RuntimeException e) {
            log.error("[Speech] 질문 음성 처리 중 예상하지 못한 오류로 텍스트만 푸시합니다. sessionId={}, sequence={}",
                    command.interviewSessionId(), command.sequence(), e);
            return null;
        }
    }

    private void push(QuestionVoiceCommand command, String audioUrl, AudioStatus audioStatus) {
        try {
            messagingTemplate.convertAndSendToUser(
                    command.applicantPublicId(),
                    QUEUE_DESTINATION,
                    InterviewWebSocketMessageResponse.questionReady(
                            command.interviewSessionId(),
                            command.questionCode(),
                            command.sequence(),
                            command.question(),
                            audioUrl,
                            audioStatus
                    )
            );
            log.info("[Speech] QUESTION_READY 푸시. sessionId={}, sequence={}, audioStatus={}, prepareMs={}, fallback={}",
                    command.interviewSessionId(), command.sequence(), audioStatus,
                    Duration.ofNanos(System.nanoTime() - command.receivedAtNanos()).toMillis(),
                    audioStatus == AudioStatus.UNAVAILABLE);
        } catch (RuntimeException e) {
            log.error("[Speech] QUESTION_READY 푸시 실패. sessionId={}, sequence={}",
                    command.interviewSessionId(), command.sequence(), e);
        }
    }

    private String objectKey(QuestionVoiceCommand command) {
        return s3Dir + "/" + command.interviewSessionId() + "/" + command.sequence() + ".wav";
    }

    /**
     * 스레드풀로 넘기는 값. 엔티티를 넘기지 않아 풀 스레드에서 lazy 로딩이 필요 없다.
     *
     * @param receivedAtNanos 질문 생성 이벤트를 받은 시각({@link System#nanoTime()}). 질문 준비 시간 계측용
     */
    public record QuestionVoiceCommand(
            UUID interviewSessionId,
            String applicantPublicId,
            String questionCode,
            Integer sequence,
            String question,
            long receivedAtNanos
    ) {
    }
}
