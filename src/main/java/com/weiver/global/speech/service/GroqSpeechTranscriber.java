package com.weiver.global.speech.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.speech.config.SpeechConfig;
import com.weiver.global.speech.config.SpeechProperties;
import com.weiver.global.speech.dto.GroqTranscriptionResponse;
import com.weiver.global.speech.dto.Transcription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Groq(OpenAI 호환) Whisper Large v3. 서버 자동 재시도는 하지 않는다.
 */
@Profile("prod")
@Slf4j
@Component
public class GroqSpeechTranscriber implements SpeechTranscriber {

    private static final String PROVIDER = "groq";
    // 오디오 길이(duration)를 받기 위해 verbose_json으로 고정한다.
    private static final String RESPONSE_FORMAT = "verbose_json";

    private final WebClient webClient;
    private final SpeechProperties.Stt stt;

    public GroqSpeechTranscriber(@Qualifier(SpeechConfig.GROQ_WEB_CLIENT) WebClient webClient,
                                 SpeechProperties properties) {
        this.webClient = webClient;
        this.stt = properties.stt();
    }

    @Override
    public Transcription transcribe(byte[] audio, String fileName, String contentType) {
        if (audio == null || audio.length == 0 || !StringUtils.hasText(contentType)) {
            log.warn("[Speech] STT 요청 검증 실패. provider={}, audioBytes={}", PROVIDER, audio == null ? 0 : audio.length);
            throw new BusinessException(ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
        }
        if (!StringUtils.hasText(stt.apiKey())) {
            log.error("[Speech] API 키가 설정되지 않았습니다. provider={}", PROVIDER);
            throw new BusinessException(ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
        }

        long startedAt = System.nanoTime();
        try {
            GroqTranscriptionResponse response = webClient.post()
                    .uri("/audio/transcriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + stt.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(multipartBody(audio, fileName, contentType).build()))
                    .retrieve()
                    .bodyToMono(GroqTranscriptionResponse.class)
                    .timeout(stt.timeout())
                    .block();

            if (response == null) {
                log.error("[Speech] STT 응답이 비어 있습니다. provider={}, model={}", PROVIDER, stt.model());
                throw new BusinessException(ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
            }

            if (response.duration() == null) {
                log.warn("[Speech] STT 응답에 duration이 없습니다. provider={}, model={}", PROVIDER, stt.model());
            }
            log.info("[Speech] STT 성공. provider={}, model={}, elapsedMs={}, audioBytes={}, audioSeconds={}, status=200, fallback=false",
                    PROVIDER, stt.model(), elapsedMs(startedAt), audio.length, response.duration());
            return new Transcription(response.text() == null ? "" : response.text(), response.duration());
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw handleFailure(e, startedAt, audio.length);
        }
    }

    private MultipartBodyBuilder multipartBody(byte[] audio, String fileName, String contentType) {
        String resolvedFileName = StringUtils.hasText(fileName) ? fileName : "answer";
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(audio))
                .filename(resolvedFileName)
                .contentType(MediaType.parseMediaType(contentType));
        body.part("model", stt.model());
        body.part("language", stt.language());
        body.part("temperature", String.valueOf(stt.temperature()));
        body.part("response_format", RESPONSE_FORMAT);
        if (StringUtils.hasText(stt.prompt())) {
            body.part("prompt", stt.prompt());
        }
        return body;
    }

    private BusinessException handleFailure(RuntimeException e, long startedAt, int audioBytes) {
        SpeechProviderFailure failure = SpeechProviderFailure.of(e);
        if (failure.isRateLimited()) {
            log.warn("[Speech] STT 한도 초과. provider={}, model={}, elapsedMs={}, audioBytes={}, status=429, fallback=false",
                    PROVIDER, stt.model(), elapsedMs(startedAt), audioBytes);
            return new BusinessException(ErrorCode.SPEECH_PROVIDER_RATE_LIMITED);
        }
        // 5xx·타임아웃·요청/인증 오류(400·401·403)는 모두 log.error(Sentry). 응답 본문은 남기지 않는다.
        log.error("[Speech] STT 실패. provider={}, model={}, elapsedMs={}, audioBytes={}, reason={}, errorType={}, fallback=false",
                PROVIDER, stt.model(), elapsedMs(startedAt), audioBytes, failure.reason(), e.getClass().getSimpleName());
        return new BusinessException(ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
