package com.weiver.interview.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.speech.config.SpeechProperties;
import com.weiver.global.speech.dto.Transcription;
import com.weiver.global.speech.service.SpeechTranscriber;
import com.weiver.interview.dto.request.InterviewAnswerSubmitRequest;
import com.weiver.interview.dto.response.InterviewAnswerAudioResponseDTO;
import com.weiver.interview.dto.response.InterviewWebSocketMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 답변 녹음을 STT로 텍스트화해 기존 답변 제출 흐름({@link InterviewFlowService#submitAnswer})에 합류시킨다.
 *
 * <p>트랜잭션을 쓰지 않는다. 사전 검증과 답변 반영은 {@link InterviewFlowService}가 각각 자기 트랜잭션에서 처리하고,
 * 외부 STT 호출은 그 사이에서 트랜잭션 밖으로 수행한다. 녹음 원본은 서버에 보관하지 않는다.
 */
@Slf4j
@Service
public class InterviewAnswerVoiceService {

    /** 허용 녹음 형식(타입/서브타입) → Groq가 형식을 판별할 파일 확장자. */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "audio/webm", "webm",
            "audio/ogg", "ogg",
            "audio/mp4", "mp4"
    );

    private final InterviewFlowService interviewFlowService;
    private final SpeechTranscriber speechTranscriber;
    private final List<String> allowedContentTypes;
    private final Duration maxAnswerDuration;

    public InterviewAnswerVoiceService(
            InterviewFlowService interviewFlowService,
            SpeechTranscriber speechTranscriber,
            SpeechProperties speechProperties
    ) {
        this.interviewFlowService = interviewFlowService;
        this.speechTranscriber = speechTranscriber;
        SpeechProperties.Stt stt = speechProperties.stt();
        this.allowedContentTypes = stt.allowedContentTypes().stream()
                .map(InterviewAnswerVoiceService::baseType)
                .toList();
        this.maxAnswerDuration = Duration.ofSeconds(stt.maxAnswerSeconds()).plus(stt.durationTolerance());
    }

    /**
     * 순서: 요청 검증 → 답변 가능 여부 사전 검증 → STT → 인식 결과 검사(공백·길이) → 기존 답변 제출 위임.
     * 앞 단계에서 실패하면 뒤 단계(특히 외부 STT 호출)는 실행하지 않고 transcript도 바꾸지 않는다.
     */
    public InterviewAnswerAudioResponseDTO submitAudioAnswer(
            UUID interviewSessionId,
            String applicantPublicId,
            String questionCode,
            Integer sequence,
            MultipartFile file
    ) {
        String contentType = validateRequest(questionCode, sequence, file);

        interviewFlowService.validateAnswerTarget(interviewSessionId, applicantPublicId, questionCode, sequence);

        Transcription transcription = transcribe(file, contentType);
        validateTranscription(transcription);

        InterviewWebSocketMessageResponse accepted = interviewFlowService.submitAnswer(
                interviewSessionId,
                applicantPublicId,
                new InterviewAnswerSubmitRequest(questionCode, sequence, transcription.text().trim())
        );

        log.info("[Interview] 답변 제출. source=VOICE, sessionId={}, sequence={}, audioBytes={}, audioSeconds={}",
                interviewSessionId, sequence, file.getSize(), transcription.durationSeconds());

        return new InterviewAnswerAudioResponseDTO(
                accepted.interviewSessionId(),
                accepted.status(),
                questionCode,
                sequence
        );
    }

    /**
     * 빈 파일·허용 외 형식·필수 값 누락을 STT 호출 전에 거절하고, 파라미터를 뗀 정규화된 content type을 돌려준다.
     */
    private String validateRequest(String questionCode, Integer sequence, MultipartFile file) {
        if (!StringUtils.hasText(questionCode) || sequence == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "question_code와 sequence는 필수입니다.");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_ANSWER_AUDIO_INVALID);
        }

        // 브라우저는 "audio/webm;codecs=opus"처럼 파라미터를 붙여 보낸다.
        String contentType = baseType(file.getContentType());
        if (!allowedContentTypes.contains(contentType) || !EXTENSION_BY_CONTENT_TYPE.containsKey(contentType)) {
            log.warn("[Interview] 허용되지 않은 녹음 형식. contentType={}", file.getContentType());
            throw new BusinessException(ErrorCode.INTERVIEW_ANSWER_AUDIO_INVALID);
        }
        return contentType;
    }

    private Transcription transcribe(MultipartFile file, String contentType) {
        byte[] audio;
        try {
            audio = file.getBytes();
        } catch (IOException e) {
            log.error("[Interview] 업로드된 녹음 파일을 읽지 못했습니다. errorType={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTERVIEW_ANSWER_AUDIO_INVALID);
        }
        // 트랜스코딩 없이 그대로 전달한다. 원본 파일명은 신뢰하지 않고 형식에 맞는 확장자만 붙인다.
        return speechTranscriber.transcribe(audio, "answer." + EXTENSION_BY_CONTENT_TYPE.get(contentType), contentType);
    }

    private void validateTranscription(Transcription transcription) {
        if (!StringUtils.hasText(transcription.text())) {
            throw new BusinessException(ErrorCode.INTERVIEW_ANSWER_NOT_RECOGNIZED);
        }

        Double seconds = transcription.durationSeconds();
        if (seconds == null) {
            // 공급자가 길이를 주지 않으면 길이 검사는 건너뛴다(업로드 크기 한도가 상한 역할).
            log.warn("[Interview] STT 응답에 오디오 길이가 없어 길이 검사를 건너뜁니다.");
            return;
        }
        if (Duration.ofMillis(Math.round(seconds * 1000)).compareTo(maxAnswerDuration) > 0) {
            log.warn("[Interview] 허용 시간을 초과한 답변 녹음. audioSeconds={}, limitSeconds={}",
                    seconds, maxAnswerDuration.toSeconds());
            throw new BusinessException(ErrorCode.INTERVIEW_ANSWER_TOO_LONG);
        }
    }

    private static String baseType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }
}
